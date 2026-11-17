import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    jacoco
    id("jebol-suites")
}

// The root is the core every platform shares: the domain, the application and
// the adapters a desktop, a browser and a phone all use. desktop/ holds the
// command line, the window and the JVM's own machine, and builds what ships;
// web/ holds the page and the browser's program. Each keeps the tests of its
// own code. Goal 2a step 4.

group = "org.jebol"
version = "3.22.5"

java {
    // Every JVM the build uses is 21: the toolchain, the tests' launcher and
    // the daemon (gradle/gradle-daemon-jvm.properties). Android's ART and
    // TeaVM both stop at 21, so nothing newer creeps in on any platform.
    // Decision 76.
    //
    // A reader raising this should know ArchUnit 1.4.1 carries an ASM that
    // stops at class file 69, so a 26 compiles a test tree that
    // ClassFileImporter reads as empty and every rule over it passes saying
    // nothing. EveryPropertyIsActuallyRunTest's guards caught it; decision 55.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

tasks.jar {
    archiveBaseName = "jebol-core"
}

val extensionProjects = EVERY_EXTENSION

fun extensionsWithSuite(sourceSet: String) =
    extensionProjects.filter { file("extensions/$it/src/$sourceSet").isDirectory }

fun optionalPartsWithSuite(sourceSet: String) =
    OPTIONAL_PARTS.filter { file("optional/$it/src/$sourceSet").isDirectory }

fun optionalResultsName(part: String, suite: String) =
    "optional" + part.replaceFirstChar(Char::uppercase) + suite

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-proc:none"))
}

val functionalTest = sourceSets["functionalTest"]

// The core's tests build their interpreters on the platform they run on, which
// they find through ServiceLoader as a PlatformUnderTest. On the JVM that is
// the desktop's; on a phone, Android's.
dependencies {
    listOf("unitTest", "functionalTest", "endToEndTest", "networkTest").forEach { suite ->
        add(sourceSets[suite].runtimeOnlyConfigurationName, testFixtures(project(":desktop")))
    }
}

val unitTests = tasks.named<Test>("unitTests") {
    // The architecture tests read every project's classes, main and test.
    PROJECTS_WITH_SUITES.forEach { path ->
        val inProject = if (path == ":") "" else "$path:"
        dependsOn("${inProject}classes")
        SUITE_SOURCE_SETS.forEach { suite -> dependsOn("$inProject${suite}Classes") }
    }
    readsAlsoFrom("the editor grammar", fileTree("editors"))
    readsAlsoFrom("the shipped examples", fileTree("examples"))
}

val functionalTests = tasks.named<Test>("functionalTests") {
    description = "Tests that build an interpreter and run REBOL through it, Rebol's own suite included"
    readsAlsoFrom(
        "Rebol's own source",
        fileTree("rebol3-source/src/boot"),
        fileTree("rebol3-source/src/modules"),
        fileTree("rebol3-source/src/tests"),
    )
}

// A Java resource cannot be listed inside a jar or an APK, so the fixtures
// carry a list of their own files, written from the files themselves so it
// cannot drift from them. CarriedFixtures reads it, on the JVM and on ART alike.
writesItsFixtureIndex()

// The core's functional tests as a jar, which android/ dexes into its test APK
// and runs on ART against Android's platform (goal 2a step 7).
tasks.register<Jar>("functionalTestJar") {
    group = "build"
    description = "The core's functional tests as a jar, for android/ to run on ART"
    archiveBaseName = "jebol-core"
    archiveClassifier = "functional-tests"
    from(functionalTest.output)
}

val heapExhaustionTests = tasks.register<Test>("heapExhaustionTests") {
    description = "The functional tests that run the heap out on purpose, apart, so a heap dump in the others always means something"
    runsTheTestsIn(functionalTest, "heapExhaustionTests", dumpsTheHeapWhenItRunsOut = false)
    useJUnitPlatform { includeTags(TESTS_THAT_EXHAUST_THE_HEAP) }
    maxParallelForks = 1
    mustRunAfter(unitTests)
}

val checkSpec = tasks.register<Exec>("checkSpec") {
    group = "verification"
    description = "Validate the Allium specifications in spec/"
    commandLine("./scripts/check-spec.sh")
}

abstract class ArchiveTestResults : DefaultTask() {

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @get:Inject
    abstract val processes: ExecOperations

    @get:Internal
    abstract val results: DirectoryProperty

    @get:Internal
    abstract val history: DirectoryProperty

    @get:Internal
    abstract val suites: ListProperty<String>

    @TaskAction
    fun archive() {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val archived = history.get().dir(stamp).asFile
        suites.get().forEach { suite ->
            fileSystem.copy {
                from(results.get().dir(suite))
                exclude("binary/**")
                into(archived.resolve(suite))
            }
        }
        archived.resolve("summary.txt").writeText(summarise(archived))
        logger.lifecycle("Test results archived in {}", archived)
    }

    private fun summarise(archived: File): String {
        val lines = mutableListOf("commit ${readCommit()}")
        var earliestStart = Long.MAX_VALUE
        var latestEnd = Long.MIN_VALUE
        suites.get().forEach { suite ->
            val reports = archived.resolve(suite).listFiles { file -> file.name.endsWith(".xml") }.orEmpty()
            var tests = 0
            var failed = 0
            var skipped = 0
            var seconds = 0.0
            var suiteStart = Long.MAX_VALUE
            var suiteEnd = Long.MIN_VALUE
            reports.forEach { report ->
                val suiteElement = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(report).documentElement
                tests += suiteElement.getAttribute("tests").toInt()
                failed += suiteElement.getAttribute("failures").toInt() + suiteElement.getAttribute("errors").toInt()
                skipped += suiteElement.getAttribute("skipped").toInt()
                val took = suiteElement.getAttribute("time").toDouble()
                seconds += took
                val started = Instant.parse(suiteElement.getAttribute("timestamp")).toEpochMilli()
                suiteStart = minOf(suiteStart, started)
                suiteEnd = maxOf(suiteEnd, started + (took * 1000).toLong())
            }
            earliestStart = minOf(earliestStart, suiteStart)
            latestEnd = maxOf(latestEnd, suiteEnd)
            val wall = if (reports.isEmpty()) "not run" else formatMinutes(suiteEnd - suiteStart)
            lines += "$suite: ${reports.size} classes, $tests tests, $failed failed, $skipped skipped, " +
                "wall $wall, summed ${"%.0f".format(seconds)}s"
            val jvmEvidence = archived.resolve(suite).resolve("jvm")
            val heapDumps = jvmEvidence.listFiles { file -> file.name.endsWith(".hprof") }.orEmpty().size
            val crashLogs = jvmEvidence.listFiles { file -> file.name.startsWith("hs_err") }.orEmpty().size
            if (heapDumps + crashLogs > 0) {
                lines += "jvm evidence for $suite: $heapDumps heap dumps and $crashLogs JVM crash logs in $suite/jvm"
            }
        }
        if (latestEnd > earliestStart) {
            lines += "tests wall, first class started to last class finished: ${formatMinutes(latestEnd - earliestStart)}"
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun formatMinutes(milliseconds: Long): String =
        "${milliseconds / 60_000}m${(milliseconds / 1000) % 60}s"

    private fun readCommit(): String {
        val head = ByteArrayOutputStream()
        processes.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            standardOutput = head
            isIgnoreExitValue = true
        }
        val changed = ByteArrayOutputStream()
        processes.exec {
            commandLine("git", "status", "--porcelain")
            standardOutput = changed
            isIgnoreExitValue = true
        }
        val uncommitted = if (changed.toString().isBlank()) "" else " with uncommitted changes"
        return head.toString().trim() + uncommitted
    }
}

// The gate, project by project and in order: every unit suite, then every
// functional suite, then every end-to-end suite, then the window that needs a
// display. The machine's cores are spent one stage at a time, because the
// functional tests' five-second bounds do not survive sharing them.
val unitStage = listOf(":unitTests", ":desktop:unitTests", ":web:unitTests") +
    extensionsWithSuite("unitTest").map { ":extensions:$it:unitTests" } +
    optionalPartsWithSuite("unitTest").map { ":optional:$it:unitTests" }
val functionalStage = listOf(":functionalTests", ":heapExhaustionTests", ":desktop:functionalTests",
    ":web:functionalTests") + extensionsWithSuite("functionalTest").map { ":extensions:$it:functionalTests" } +
    optionalPartsWithSuite("functionalTest").map { ":optional:$it:functionalTests" }
val endToEndStage = listOf(":endToEndTests", ":desktop:endToEndTests", ":web:endToEndTests") +
    extensionsWithSuite("endToEndTest").map { ":extensions:$it:endToEndTests" } +
    optionalPartsWithSuite("endToEndTest").map { ":optional:$it:endToEndTests" }
val windowStage = listOf(":desktop:desktopWindowTests")
val gateStages = listOf(unitStage, functionalStage, endToEndStage, windowStage)
val gateTasks = gateStages.flatten()

val gateSuites = listOf("unitTests", "desktopUnitTests", "webUnitTests",
    "functionalTests", "heapExhaustionTests", "desktopFunctionalTests", "webFunctionalTests",
    "endToEndTests", "desktopEndToEndTests", "webEndToEndTests", "desktopWindowTests") +
    extensionsWithSuite("unitTest").map { "${it}UnitTests" } +
    extensionsWithSuite("functionalTest").map { "${it}FunctionalTests" } +
    extensionsWithSuite("endToEndTest").map { "${it}EndToEndTests" } +
    optionalPartsWithSuite("unitTest").map { optionalResultsName(it, "UnitTests") } +
    optionalPartsWithSuite("functionalTest").map { optionalResultsName(it, "FunctionalTests") } +
    optionalPartsWithSuite("endToEndTest").map { optionalResultsName(it, "EndToEndTests") }

val gateWasAskedFor = gradle.startParameter.taskNames
    .map { it.removePrefix(":") }
    .any { it in setOf("allTests", "test", "check") }

val archiveTestResults = tasks.register<ArchiveTestResults>("archiveTestResults") {
    group = "verification"
    description = "Copy the gate's test results into test-history/<timestamp>, with a summary, so a later run cannot overwrite them"
    results = layout.buildDirectory.dir("test-results")
    history = layout.projectDirectory.dir("test-history")
    suites = gateSuites
    val asked = gateWasAskedFor
    onlyIf("the gate was asked for") { asked }
    gateTasks.forEach { mustRunAfter(it) }
}

val allTests = tasks.register("allTests") {
    group = "verification"
    description = "The spec check, then every project's unit, functional and end-to-end suites in stages, " +
        "then the desktop's window, then archiveTestResults"
    dependsOn(checkSpec)
    dependsOn(gateTasks)
    finalizedBy(archiveTestResults)
}

gradle.projectsEvaluated {
    gateStages.zipWithNext().forEach { (earlier, later) ->
        later.forEach { laterTask ->
            rootProject.tasks.findByPath(laterTask)?.mustRunAfter(earlier)
        }
    }
    rootProject.tasks.findByPath(":unitTests")?.mustRunAfter(checkSpec)
    gateTasks.forEach { suite -> rootProject.tasks.findByPath(suite)?.finalizedBy(archiveTestResults) }
}

tasks.test {
    description = "The same as allTests"
    dependsOn(allTests)
}

tasks.check {
    description = "The same as allTests"
    dependsOn(allTests)
}

tasks.jacocoTestReport {
    dependsOn(unitTests, functionalTests, heapExhaustionTests)
    executionData(unitTests.get(), functionalTests.get(), heapExhaustionTests.get())
    reports {
        xml.required = true
        html.required = true
    }
}

// The core is what Android carries, so it may reach nothing Android 35's
// android.jar lacks and no class it does not carry itself. javac cannot
// compile against android.jar as the platform past level 8, so
// scripts/AndroidApiGaps.java reads the core's jar instead and writes what it
// found to scripts/android-api-gaps.txt (goal 2a). It needs the SDK, so it is
// not part of allTests; ANDROID_HOME, or ~/Android/Sdk, says where it is.
tasks.register<Exec>("checkCoreForAndroid") {
    group = "verification"
    description = "Fails if the core reaches anything Android 35 lacks or any class it does not carry"
    val script = layout.projectDirectory.file("scripts/AndroidApiGaps.java").asFile
    val report = layout.projectDirectory.file("scripts/android-api-gaps.txt").asFile
    val coreJar = tasks.jar.flatMap { it.archiveFile }
    val androidSdk = providers.environmentVariable("ANDROID_HOME")
        .orElse(providers.systemProperty("user.home").map { "$it/Android/Sdk" })
    inputs.file(coreJar)
    inputs.file(script)
    outputs.file(report)
    executable(
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
            .get().executablePath.asFile.path,
    )
    args(
        script.path, "--fail-on-gaps", "--report", report.path,
        coreJar.get().asFile.path, androidSdk.get() + "/platforms/android-35/android.jar",
    )
}
