import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

// Runs the test APK on several emulators at once, shard i of N on the i-th,
// as AGP's connected task cannot: it runs every test on every device. Each
// shard's raw instrumentation output becomes TEST-shard-i.xml. Decision 73.
@DisableCachingByDefault(because = "it drives emulators")
abstract class DeviceShards : DefaultTask() {

    @get:Input
    abstract val serials: ListProperty<String>

    @get:Input
    abstract val adb: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appApk: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val testApk: RegularFileProperty

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

    private val hostServerArguments = mutableMapOf<String, String>()

    @TaskAction
    fun runEveryShard() {
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
            val shards = pool.invokeAll(devices.mapIndexed { index, serial ->
                Callable { runShard(serial, index, devices.size) }
            }).map { it.get() }
            shards.forEach { it.writeXmlInto(folder) }
            report(shards)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun install(serial: String) {
        listOf(appApk, testApk).forEach { apk ->
            val installing = ProcessBuilder(adb.get(), "-s", serial, "install", "-r", "-t", apk.get().asFile.path)
                .redirectErrorStream(true).start()
            val said = installing.inputStream.bufferedReader().readText()
            if (installing.waitFor() != 0 || !said.contains("Success")) {
                throw GradleException("installing ${apk.get().asFile.name} on $serial failed: $said")
            }
        }
    }

    private fun runShard(serial: String, index: Int, count: Int): Shard {
        val command = mutableListOf(adb.get(), "-s", serial, "shell", "am", "instrument", "-w", "-r")
        (runnerArguments.get() + hostServerArguments).forEach { (name, value) -> command += listOf("-e", name, value) }
        command += listOf("-e", "numShards", count.toString(), "-e", "shardIndex", index.toString())
        command += instrumentation.get()
        val shard = Shard(index, serial)
        val running = ProcessBuilder(command).redirectErrorStream(true).start()
        running.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                shard.read(line)?.let { done -> logEvery500(shard, done) }
            }
        }
        running.waitFor()
        return shard
    }

    private fun logEvery500(shard: Shard, done: Int) {
        if (done % 500 == 0) {
            logger.lifecycle("shard ${shard.index} on ${shard.serial}: $done of ${shard.planned}, " +
                "${shard.failed().size} failed")
        }
    }

    private fun report(shards: List<Shard>) {
        val failed = shards.flatMap { it.failed() }
        val broken = shards.filter { !it.finished }
        shards.forEach {
            logger.lifecycle("shard ${it.index} on ${it.serial}: ${it.cases.size} tests, " +
                "${it.failed().size} failed, ${it.cases.count { case -> case.skipped }} skipped" +
                if (it.finished) "" else ", and it never finished: ${it.ending}")
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
        var planned = 0
        var finished = false
        var ending = "no output"
        private val fields = LinkedHashMap<String, String>()
        private var lastField: String? = null
        private var running: Case? = null

        fun failed(): List<Case> = cases.filter { it.failure.isNotEmpty() }

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
                else -> lastField?.let { fields[it] = fields[it] + "\n" + line }
            }
            return null
        }

        private fun settle(code: Int): Int? {
            fields["numtests"]?.toIntOrNull()?.let { planned = it }
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
            xml.append("errors=\"${if (finished) 0 else 1}\" skipped=\"${cases.count { it.skipped }}\" ")
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
            if (!finished) {
                xml.append("  <system-err>${escape("the shard never finished: $ending")}</system-err>\n")
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
