import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors

// Runs the test APK on several devices at once, as AGP's connected task
// cannot: it runs every test on every device. The classes are dealt into small
// batches that each device takes from one queue as it comes free, so a faster
// device takes more. Each device's raw instrumentation output becomes
// TEST-shard-i.xml. Decision 73, goal 20.6a.
@CacheableTask
abstract class DeviceShards : DefaultTask() {

    @get:Input
    abstract val serials: ListProperty<String>

    @get:Input
    abstract val deviceFingerprints: ListProperty<String>

    @get:Internal
    abstract val adb: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appApk: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val testApk: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val otherApks: ConfigurableFileCollection

    @get:Input
    abstract val instrumentation: Property<String>

    @get:Input
    abstract val runnerArguments: MapProperty<String, String>

    @get:OutputDirectory
    abstract val results: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val hostServerIdentity: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val everyTestOnEachDevice: Property<Boolean>

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val hostSiteApp: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val webBuild: DirectoryProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runnerPage: RegularFileProperty

    private val hostServerArguments = mutableMapOf<String, String>()

    private companion object {
        const val BATCHES_EACH_DEVICE = 12
        const val MIXING = -0x61c88647
    }

    @TaskAction
    fun runEveryShard() {
        if (!hostSiteApp.isPresent) {
            runWithHostServers()
            return
        }
        HostSite(webBuild.get().asFile, runnerPage.get().asFile, hostSiteApp.get().asFile).use { site ->
            hostServerArguments["hostSitePort"] = site.port.toString()
            runWithHostServers()
        }
    }

    private fun runWithHostServers() {
        if (!hostServerIdentity.isPresent) {
            runOnDevices()
            return
        }
        val identity = hostServerIdentity.get().asFile
        HostServers(File(identity, "emulator-host.pem"), File(identity, "emulator-host-key.pem")).use { servers ->
            hostServerArguments["hostPlainPort"] = servers.plainPort.toString()
            hostServerArguments["hostSecurePort"] = servers.securePort.toString()
            runOnDevices()
        }
    }

    private fun runOnDevices() {
        val devices = serials.get()
        val folder = results.get().asFile
        folder.listFiles()?.forEach { it.delete() }
        val pool = Executors.newFixedThreadPool(devices.size)
        try {
            pool.invokeAll(devices.map { serial -> Callable { install(serial) } }).forEach { it.get() }
            if (everyTestOnEachDevice.getOrElse(false)) {
                val shards = pool.invokeAll(devices.mapIndexed { index, serial ->
                    Callable { runEveryTest(serial, index) }
                }).map { it.get() }
                shards.forEach { it.writeXmlInto(folder) }
                report(shards, 0)
                return
            }
            val batches = batchesFor(classesToRun(devices.first()), devices.size)
            val queue = ConcurrentLinkedDeque(batches)
            val shards = pool.invokeAll(devices.mapIndexed { index, serial ->
                Callable { takeBatches(serial, index, queue, batches.size) }
            }).map { it.get() }
            shards.forEach { it.writeXmlInto(folder) }
            report(shards, queue.size)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun classesToRun(serial: String): List<String> {
        val named = runnerArguments.get()["class"]?.split(",")?.filter(String::isNotEmpty).orEmpty()
        if (named.isNotEmpty()) {
            return topLevelClasses(named)
        }
        val listing = Shard(-1, serial)
        runInstrumentation(serial, listing, runnerArguments.get() + ("log" to "true"))
        if (!listing.completed()) {
            throw GradleException("listing the tests on $serial failed: ${listing.unfinished.joinToString("; ")}")
        }
        return topLevelClasses(listing.cases.map { it.className }.filter(String::isNotEmpty))
    }

    private fun topLevelClasses(classes: List<String>): List<String> = classes.map { it.substringBefore('$') }.distinct()

    private fun batchesFor(classes: List<String>, deviceCount: Int): List<List<String>> {
        val wanted = deviceCount * BATCHES_EACH_DEVICE
        val size = maxOf(1, (classes.size + wanted - 1) / wanted)
        return classes.sortedWith(compareBy<String> { Integer.toUnsignedLong(it.hashCode() * MIXING) }.thenBy { it })
            .chunked(size)
    }

    private fun takeBatches(serial: String, index: Int, queue: ConcurrentLinkedDeque<List<String>>, total: Int): Shard {
        val shard = Shard(index, serial)
        val asked = runnerArguments.get() - setOf("class", "package", "notPackage")
        while (true) {
            val batch = queue.pollFirst() ?: break
            val before = shard.cases.size
            val exit = runInstrumentation(serial, shard, asked + hostServerArguments + ("class" to batch.joinToString(",")))
            if (!shard.lastRunFinished() && !isConnected(serial)) {
                shard.forgetCasesAfter(before)
                shard.forgetLastRun()
                shard.lost = "$serial was lost (adb exited $exit), and its batch went back for another device"
                logger.lifecycle(shard.lost)
                queue.addFirst(batch)
                break
            }
            logger.lifecycle("$serial: ${total - queue.size} of $total batches taken, ${shard.cases.size} tests done " +
                "here, ${shard.failed().size} failed")
        }
        return shard
    }

    private fun isConnected(serial: String): Boolean {
        val asking = ProcessBuilder(adb.get(), "-s", serial, "get-state").redirectErrorStream(true).start()
        val said = asking.inputStream.bufferedReader().readText().trim()
        return asking.waitFor() == 0 && said == "device"
    }

    private fun install(serial: String) {
        (listOf(appApk.get().asFile, testApk.get().asFile) + otherApks.files).forEach { apk ->
            val installing = ProcessBuilder(adb.get(), "-s", serial, "install", "-r", "-t", apk.path)
                .redirectErrorStream(true).start()
            val said = installing.inputStream.bufferedReader().readText()
            if (installing.waitFor() != 0 || !said.contains("Success")) {
                throw GradleException("installing ${apk.name} on $serial failed: $said")
            }
        }
    }

    private fun runEveryTest(serial: String, index: Int): Shard {
        val shard = Shard(index, serial)
        runInstrumentation(serial, shard, runnerArguments.get() + hostServerArguments)
        return shard
    }

    private fun runInstrumentation(serial: String, shard: Shard, arguments: Map<String, String>): Int {
        val command = mutableListOf(adb.get(), "-s", serial, "shell", "am", "instrument", "-w", "-r")
        arguments.forEach { (name, value) -> command += listOf("-e", name, value) }
        command += instrumentation.get()
        shard.startRun()
        val running = ProcessBuilder(command).redirectErrorStream(true).start()
        running.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                shard.read(line)?.let { done -> logEvery500(shard, done) }
            }
        }
        val exit = running.waitFor()
        shard.endRun(exit)
        return exit
    }

    private fun logEvery500(shard: Shard, done: Int) {
        if (shard.index >= 0 && done % 500 == 0) {
            logger.lifecycle("${shard.serial}: $done tests done, ${shard.failed().size} failed")
        }
    }

    private fun report(shards: List<Shard>, batchesNeverRun: Int) {
        val failed = shards.flatMap { it.failed() }
        val broken = shards.filter { !it.completed() }
        shards.forEach {
            logger.lifecycle("${it.serial}: ${it.cases.size} tests, " +
                "${it.failed().size} failed, ${it.cases.count { case -> case.skipped }} skipped" +
                (if (it.completed()) "" else ", and a run never finished: ${it.unfinished.joinToString("; ")}") +
                (if (it.lost.isEmpty()) "" else "; ${it.lost}"))
        }
        if (batchesNeverRun > 0) {
            throw GradleException("$batchesNeverRun batches of tests never ran, because every device was lost; " +
                "the results are in ${results.get().asFile}")
        }
        failed.forEach { logger.lifecycle("FAILED ${it.className} > ${it.name}\n${it.stack.lines().take(6).joinToString("\n")}") }
        if (failed.isNotEmpty() || broken.isNotEmpty()) {
            throw GradleException("${failed.size} device tests failed and ${broken.size} shards never finished; " +
                "the results are in ${results.get().asFile}")
        }
    }

    class Case(val className: String, val name: String, val started: Long) {
        var seconds = 0.0
        var skipped = false
        var failure = ""
        var stack = ""
    }

    class Shard(val index: Int, val serial: String) {
        val cases = mutableListOf<Case>()
        val unfinished = mutableListOf<String>()
        var lost = ""
        private var finished = false
        private var ending = "no output"
        private val fields = LinkedHashMap<String, String>()
        private var lastField: String? = null
        private var running: Case? = null

        fun failed(): List<Case> = cases.filter { it.failure.isNotEmpty() }

        fun completed(): Boolean = unfinished.isEmpty()

        fun lastRunFinished(): Boolean = finished

        fun startRun() {
            finished = false
            ending = "no output"
            running = null
            fields.clear()
            lastField = null
        }

        fun endRun(exit: Int) {
            if (!finished) {
                unfinished += "$ending (adb exited $exit)"
            }
        }

        fun forgetLastRun() {
            if (!finished) {
                unfinished.removeLast()
            }
        }

        fun forgetCasesAfter(count: Int) {
            while (cases.size > count) {
                cases.removeLast()
            }
        }

        fun read(line: String): Int? {
            when {
                line.startsWith(STATUS) -> {
                    val field = line.removePrefix(STATUS)
                    val equals = field.indexOf('=')
                    val key = if (equals < 0) field else field.substring(0, equals)
                    fields[key] = if (equals < 0) "" else field.substring(equals + 1)
                    lastField = key
                }
                line.startsWith(STATUS_CODE) -> {
                    val done = settle(line.removePrefix(STATUS_CODE).trim().toInt())
                    fields.clear()
                    lastField = null
                    return done
                }
                line.startsWith(RESULT) -> ending = line.removePrefix(RESULT)
                line.startsWith(CODE) -> finished = running == null && !ending.startsWith("shortMsg")
                lastField == null && line.isNotBlank() -> ending = if (ending == "no output") line else ending + "\n" + line
                else -> lastField?.let { fields[it] = fields[it] + "\n" + line }
            }
            return null
        }

        private fun settle(code: Int): Int? {
            if (code == STARTED) {
                running = Case(fields["class"].orEmpty(), fields["test"].orEmpty(), System.nanoTime())
                return null
            }
            val case = running ?: Case(fields["class"].orEmpty(), fields["test"].orEmpty(), System.nanoTime())
            running = null
            case.seconds = (System.nanoTime() - case.started) / 1e9
            when (code) {
                IGNORED, ASSUMPTION_FAILED -> case.skipped = true
                FAILED, ERRORED -> {
                    case.stack = fields["stack"].orEmpty()
                    case.failure = case.stack.lineSequence().firstOrNull().orEmpty().ifEmpty { "failed" }
                }
            }
            cases += case
            return cases.size
        }

        fun writeXmlInto(folder: File) {
            val xml = StringBuilder()
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            xml.append("<testsuite name=\"shard-$index\" tests=\"${cases.size}\" failures=\"${failed().size}\" ")
            xml.append("errors=\"${unfinished.size}\" skipped=\"${cases.count { it.skipped }}\" ")
            xml.append("time=\"${"%.3f".format(cases.sumOf { it.seconds })}\" hostname=\"$serial\">\n")
            cases.forEach { case ->
                xml.append("  <testcase name=\"${escape(case.name)}\" classname=\"${escape(case.className)}\" ")
                xml.append("time=\"${"%.3f".format(case.seconds)}\"")
                when {
                    case.failure.isNotEmpty() -> xml.append(">\n    <failure message=\"${escape(case.failure)}\">")
                        .append(escape(case.stack)).append("</failure>\n  </testcase>\n")
                    case.skipped -> xml.append(">\n    <skipped/>\n  </testcase>\n")
                    else -> xml.append("/>\n")
                }
            }
            if (!completed()) {
                xml.append("  <system-err>${escape("a run never finished: " + unfinished.joinToString("; "))}</system-err>\n")
            }
            xml.append("</testsuite>\n")
            folder.mkdirs()
            File(folder, "TEST-shard-$index.xml").writeText(xml.toString())
        }

        private fun escape(text: String): String = text
            .filter { it == '\n' || it == '\t' || it >= ' ' }
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        private companion object {
            const val STATUS = "INSTRUMENTATION_STATUS: "
            const val STATUS_CODE = "INSTRUMENTATION_STATUS_CODE: "
            const val RESULT = "INSTRUMENTATION_RESULT: "
            const val CODE = "INSTRUMENTATION_CODE: "
            const val STARTED = 1
            const val ERRORED = -1
            const val FAILED = -2
            const val IGNORED = -3
            const val ASSUMPTION_FAILED = -4
        }
    }
}
