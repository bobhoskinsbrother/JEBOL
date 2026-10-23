import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    java
    application
    jacoco
    `java-test-fixtures`
    id("org.teavm") version "0.16.0"
}

group = "org.jebol"
version = "3.22.5"

java {
    // 25 rather than 21 for the Foreign Function and Memory API, which
    // struct!, routine! and library! will need. Final in 22, but 22 is
    // non-LTS and out of support, and 25 is the current LTS.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "org.jebol.adapter.cli.JebolLauncher"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

repositories {
    mavenCentral()
}

dependencies {
    // The web build's substitution targets and library resources are written
    // against these, and they are never on the shipped jar's classpath.
    "teavmCompileOnly"("org.teavm:teavm-classlib:0.16.0")
    "teavmCompileOnly"("org.teavm:teavm-core:0.16.0")
    "teavmCompileOnly"("org.teavm:teavm-extension-spi:0.16.0")
    "teavmCompileOnly"("org.teavm:teavm-interop:0.16.0")
    "teavmCompileOnly"("org.jetbrains:annotations:24.1.0")
}

// The web build compiles the whole core with TeaVM: src/teavm holds only what
// the browser needs on top of it. Main's classes and resources are the program.
sourceSets.named("teavm") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

teavm {
    wasmGC {
        mainClass.set("org.jebol.adapter.web.WorkerInterpreter")
        targetFileName.set("jebol-web")
        obfuscated.set(false)
        outOfProcess.set(true)
    }
}

tasks.named<JavaCompile>("compileTeavmJava") {
    options.release.set(21)
}

val copyWorkerScript = tasks.register<Copy>("copyWorkerScript") {
    group = "build"
    description = "Put the worker's shell script beside the WebAssembly module"
    from("src/teavm/js/jebol-worker.js")
    into(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
}

val copyPageScripts = tasks.register<Copy>("copyPageScripts") {
    group = "build"
    description = "Put the page's scripts for the interpreter's asks and the history beside the WebAssembly module"
    from("src/main/resources/jebol-page/jebol-asks.js", "src/main/resources/jebol-page/jebol-navigation.js")
    into(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
}

val copyCarriedFont = tasks.register<Copy>("copyCarriedFont") {
    group = "build"
    description = "Put the font this build carries beside the WebAssembly module"
    from("src/main/resources/org/jebol/fonts/NotoSans-Regular.ttf")
    into(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
}

tasks.named("buildWasmGC") {
    dependsOn(copyWorkerScript, copyPageScripts, copyCarriedFont)
}

val assembleSite = tasks.register<Sync>("assembleSite") {
    group = "build"
    description = "Assemble the static site: a runner page and the app it runs"
    dependsOn(tasks.named("buildWasmGC"))
    val theApp = providers.gradleProperty("siteDir").getOrElse("examples/r3-gui")
    val theRunner = layout.projectDirectory.file("site/runner.html")
    inputs.property("appDirectory", theApp)
    inputs.file(theRunner).withPropertyName("runnerPage")
    into(layout.buildDirectory.dir("site"))
    from(theApp)
    from(layout.buildDirectory.dir("generated/teavm/wasm-gc")) {
        include("jebol-web.wasm", "jebol-web-runtime.js", "jebol-worker.js", "jebol-asks.js",
            "jebol-navigation.js", "NotoSans-Regular.ttf")
    }
    from("src/main/resources/org/jebol/fonts/NotoSans-OFL.txt")
    val siteDirectory = layout.buildDirectory.dir("site")
    val app = file(theApp)
    doLast {
        val site = siteDirectory.get().asFile
        if (site.resolve("index.html").exists()) {
            return@doLast
        }
        val names = app.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(app).invariantSeparatorsPath }
            .sorted()
            .toList()
        val filesJson = names.joinToString(",", "[", "]") { name ->
            "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
        site.resolve("index.html").writeText(
            theRunner.asFile.readText()
                .replace("__APP_FILES__", filesJson)
                .replace("__APP_ENTRY__", "main.reb"))
    }
}

// TeaVM only embeds a resource it is told about. The list is written from the
// resources themselves so it cannot drift from them.
val writeTheWebResourceList = tasks.register("writeTheWebResourceList") {
    group = "build"
    description = "List the library files the web build must carry"
    val library = fileTree("src/main/resources/org/jebol")
    inputs.files(library).withPropertyName("libraryFiles")
    val list = layout.buildDirectory.file("generated/web/jebol-resources.txt")
    val resourcesRoot = file("src/main/resources")
    outputs.file(list)
    doLast {
        list.get().asFile.parentFile.mkdirs()
        list.get().asFile.writeText(library.files
            .map { it.relativeTo(resourcesRoot).invariantSeparatorsPath }
            .sorted()
            .joinToString("\n"))
    }
}

sourceSets["teavm"].resources.srcDir(layout.buildDirectory.dir("generated/web"))

tasks.named("processTeavmResources") {
    dependsOn(writeTheWebResourceList)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-proc:none"))
}

// The first class a launch loads, built so that every Java from 8 on can load
// it: it tells a Java too old for the rest of JEBOL so, in words, rather than
// leaving the JVM to report a class file version. spec/repl.allium.
val launcher = sourceSets.create("launcher")

tasks.named<JavaCompile>(launcher.compileJavaTaskName) {
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.jar {
    from(launcher.output)
}

val unitTest = sourceSets.create("unitTest")
val functionalTest = sourceSets.create("functionalTest")
val endToEndTest = sourceSets.create("endToEndTest")
val networkTest = sourceSets.create("networkTest")
val testSuites = listOf(unitTest, functionalTest, endToEndTest, networkTest)

val testLibraries = listOf(
    "org.junit.jupiter:junit-jupiter:5.11.4",
    "net.jqwik:jqwik:1.9.2",
    "org.assertj:assertj-core:3.26.3",
    "com.tngtech.archunit:archunit-junit5:1.4.1",
    "org.seleniumhq.selenium:selenium-java:4.27.0",
)

dependencies {
    testLibraries.forEach { "testFixturesImplementation"(it) }
    testSuites.forEach { suite ->
        add(suite.implementationConfigurationName, testFixtures(project(project.path)))
        testLibraries.forEach { add(suite.implementationConfigurationName, it) }
        add(suite.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    }
}

testSuites.forEach { suite ->
    suite.compileClasspath += sourceSets.main.get().output + launcher.output
    suite.runtimeClasspath += sourceSets.main.get().output + launcher.output
}

fun Test.runsTheTestsIn(suite: SourceSet) {
    group = "verification"
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform()
    systemProperty("jqwik.database", layout.buildDirectory.file("jqwik-database").get().asFile.path)
    systemProperty("java.awt.headless", "true")
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("jebol.mainClassesDirs", sourceSets.main.get().output.classesDirs.asPath)
    systemProperty(
        "jebol.testClassesDirs",
        testSuites.joinToString(File.pathSeparator) { it.output.classesDirs.asPath },
    )
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    inputs.dir(layout.projectDirectory.dir("corpus"))
        .withPropertyName("corpus")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

fun Test.readsAlsoFrom(what: String, vararg files: Any) {
    inputs.files(*files)
        .withPropertyName(what)
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

val unitTests = tasks.register<Test>("unitTests") {
    description = "Tests that never build an interpreter: values, codecs, the reader, the domain on its own"
    runsTheTestsIn(unitTest)
    dependsOn(functionalTest.classesTaskName, endToEndTest.classesTaskName, networkTest.classesTaskName)
    readsAlsoFrom(
        "the other suites' classes",
        functionalTest.output.classesDirs,
        endToEndTest.output.classesDirs,
        networkTest.output.classesDirs,
    )
    readsAlsoFrom("the editor grammar", fileTree("editors"))
    readsAlsoFrom("the shipped examples", fileTree("examples"))
}

val functionalTests = tasks.register<Test>("functionalTests") {
    description = "Tests that build an interpreter and run REBOL through it, Rebol's own suite included"
    runsTheTestsIn(functionalTest)
    readsAlsoFrom(
        "Rebol's own source",
        fileTree("rebol3-source/src/boot"),
        fileTree("rebol3-source/src/modules"),
        fileTree("rebol3-source/src/tests"),
    )
    mustRunAfter(unitTests)
}

val endToEndTests = tasks.register<Test>("endToEndTests") {
    description = "Tests that drive a real interface: the command line, a real browser, and local servers"
    runsTheTestsIn(endToEndTest)
    systemProperty("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation")
    systemProperty(
        "jebol.webBuild",
        layout.buildDirectory.dir("generated/teavm/wasm-gc").get().asFile.path,
    )
    systemProperty(
        "jebol.webSite",
        layout.buildDirectory.dir("site").get().asFile.path,
    )
    dependsOn(tasks.named("buildWasmGC"), assembleSite, tasks.named("installDist"), tasks.jar)
    systemProperty(
        "jebol.installedLauncher",
        layout.buildDirectory.file("install/jebol/bin/jebol").get().asFile.path,
    )
    systemProperty("jebol.shippedJar", tasks.jar.get().archiveFile.get().asFile.path)
    systemProperty(
        "jebol.olderJavaHome",
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
            .get().metadata.installationPath.asFile.path,
    )
    systemProperty(
        "jebol.supportedJavaHome",
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
            .get().metadata.installationPath.asFile.path,
    )
    outputs.upToDateWhen { false }
    mustRunAfter(functionalTests)
}

// Rebol's own assertions that reach a host on the internet live in a gate of
// their own, out of `allTests`: a gate that runs everywhere can only run them
// where the network answers, and a run that reached out would measure the
// machine rather than JEBOL. What belongs to this gate is listed, with the line
// in each that decides it, in
// src/testFixtures/resources/rebol-suite/needs-the-network.txt.
val networkTests = tasks.register<Test>("networkTests") {
    description = "Rebol's own assertions that need the network, run against the hosts they name"
    runsTheTestsIn(networkTest)
    outputs.upToDateWhen { false }
    mustRunAfter(functionalTests)
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

val gateSuites = listOf("unitTests", "functionalTests", "endToEndTests")

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
}

val allTests = tasks.register("allTests") {
    group = "verification"
    description = "The spec check, then unitTests, functionalTests and endToEndTests, then archiveTestResults"
    dependsOn(checkSpec, unitTests, functionalTests, endToEndTests)
    finalizedBy(archiveTestResults)
}

listOf(unitTests, functionalTests, endToEndTests).forEach { suite ->
    suite { finalizedBy(archiveTestResults) }
}

unitTests { mustRunAfter(checkSpec) }

tasks.test {
    description = "The same as allTests"
    dependsOn(allTests)
}

tasks.check {
    description = "The same as allTests"
    dependsOn(allTests)
}

tasks.jacocoTestReport {
    dependsOn(unitTests, functionalTests, endToEndTests)
    executionData(unitTests.get(), functionalTests.get(), endToEndTests.get())
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// The jar runs the console on its own, so `java -jar` is a whole
// installation: no launcher script, no dependencies, nothing to unpack.
tasks.jar {
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
}

distributions {
    named("main") {
        contents {
            from("LICENSE", "NOTICE", "using-jebol.md")
        }
    }
}
