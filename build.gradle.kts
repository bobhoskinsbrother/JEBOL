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
    mainClass = "org.jebol.adapter.cli.Repl"
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

// The worker's shell script is the page's other half and is served beside the
// module it loads, so it is copied where the WebAssembly build writes.
val copyTheWorkerScript = tasks.register<Copy>("copyTheWorkerScript") {
    group = "build"
    description = "Put the worker's shell script beside the WebAssembly module"
    from("src/teavm/js/jebol-worker.js")
    into(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
}

// The font this build carries is fetched by the page and drawn with, so it is
// served beside the page that asks for it.
val copyTheCarriedFont = tasks.register<Copy>("copyTheCarriedFont") {
    group = "build"
    description = "Put the font this build carries beside the WebAssembly module"
    from("src/main/resources/org/jebol/fonts/NotoSans-Regular.ttf")
    into(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
}

tasks.named("buildWasmGC") {
    dependsOn(copyTheWorkerScript, copyTheCarriedFont)
}

// The static files a host can serve with no server code at all: a runner page
// built around an app, the module and its loader, the worker's shell and the
// font with its licence. The app is `-PsiteDir=my-app`, a directory of REBOL
// whose main.reb is the entry and whose every file is fetched into the
// worker's store before it runs; the shipped demo is examples/r3-gui, served
// the same way. A directory with an index.html of its own keeps it.
val assembleTheSite = tasks.register<Sync>("assembleTheSite") {
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
        include("jebol-web.wasm", "jebol-web-runtime.js", "jebol-worker.js", "NotoSans-Regular.ttf")
    }
    from("src/main/resources/org/jebol/fonts/NotoSans-OFL.txt")
    doLast {
        val site = layout.buildDirectory.dir("site").get().asFile
        if (site.resolve("index.html").exists()) {
            return@doLast
        }
        val app = file(theApp)
        val names = app.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(app).invariantSeparatorsPath }
            .sorted()
            .toList()
        val filesJson = names.joinToString(",", "[", "]") { name ->
            "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
        site.resolve("index.html").writeText(
            theRunner.asFile.readText()
                .replace("__THE_APP_FILES__", filesJson)
                .replace("__THE_APP_ENTRY__", "main.reb"))
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
    outputs.file(list)
    doLast {
        list.get().asFile.parentFile.mkdirs()
        list.get().asFile.writeText(library.files
            .map { it.relativeTo(file("src/main/resources")).invariantSeparatorsPath }
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

val unitTest = sourceSets.create("unitTest")
val functionalTest = sourceSets.create("functionalTest")
val endToEndTest = sourceSets.create("endToEndTest")
val testSuites = listOf(unitTest, functionalTest, endToEndTest)

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
    suite.compileClasspath += sourceSets.main.get().output
    suite.runtimeClasspath += sourceSets.main.get().output
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
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
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
    dependsOn(functionalTest.classesTaskName, endToEndTest.classesTaskName)
    readsAlsoFrom("the other suites' classes", functionalTest.output.classesDirs, endToEndTest.output.classesDirs)
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
    description = "Tests that drive a real interface: the command line, a real browser, and real servers on the internet"
    runsTheTestsIn(endToEndTest)
    systemProperty(
        "jebol.webBuild",
        layout.buildDirectory.dir("generated/teavm/wasm-gc").get().asFile.path,
    )
    systemProperty(
        "jebol.webSite",
        layout.buildDirectory.dir("site").get().asFile.path,
    )
    dependsOn(tasks.named("buildWasmGC"), assembleTheSite)
    outputs.upToDateWhen { false }
    mustRunAfter(functionalTests)
}

val checkSpec = tasks.register<Exec>("checkSpec") {
    group = "verification"
    description = "Validate the Allium specifications in spec/"
    commandLine("./scripts/check-spec.sh")
}

val allTests = tasks.register("allTests") {
    group = "verification"
    description = "The spec check, then unitTests, functionalTests and endToEndTests"
    dependsOn(checkSpec, unitTests, functionalTests, endToEndTests)
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
