plugins {
    java
    application
    jacoco
    id("org.teavm") version "0.16.0"
}

group = "org.jebol"
version = "1.0.0"

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
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")

    // Drives a real browser, so that "the page and the window show the same
    // picture" is a thing the build checks rather than a thing anybody says.
    // testImplementation and not implementation: the shipped jar has no
    // dependencies and this does not change that.
    testImplementation("org.seleniumhq.selenium:selenium-java:4.27.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

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
        mainClass.set("org.jebol.adapter.web.TheInterpreterInAWorker")
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
val copyTheWorkerScript by tasks.registering(Copy::class) {
    group = "build"
    description = "Put the worker's shell script beside the WebAssembly module"
    from("src/teavm/js/jebol-worker.js")
    into(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
}

// The font this build carries is fetched by the page and drawn with, so it is
// served beside the page that asks for it.
val copyTheCarriedFont by tasks.registering(Copy::class) {
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
val assembleTheSite by tasks.registering(Sync::class) {
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
val writeTheWebResourceList by tasks.registering {
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

// Drives a real browser, so it needs one, and it needs the network the first
// time it fetches a driver. Kept out of `check` for that reason and for no
// other: it is a second gate rather than a skipped test, it runs everything it
// holds every time it runs, and `./gradlew browserCheck` is the whole of how.
//
// What it is for: proving that the page and the desktop window show the same
// picture. They are handed the same paint list, so the only way they can
// differ is in how one of them draws a stated rectangle in a stated place --
// and this is what looks.
val browserCheck by tasks.registering(Test::class) {
    group = "verification"
    description = "Render the same paint list in Java2D and in a real browser, and compare"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("browser") }
    systemProperty("java.awt.headless", "true")
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
}

val networkCheck by tasks.registering(Test::class) {
    group = "verification"
    description = "Put JEBOL's TLS to real servers on the internet that a browser would refuse"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("network") }
    systemProperty("java.awt.headless", "true")
    outputs.upToDateWhen { false }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("browser", "network")
    }

    systemProperty("jqwik.database", layout.buildDirectory.file("jqwik-database").get().asFile.path)
    systemProperty("java.awt.headless", "true")
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    inputs.dir(layout.projectDirectory.dir("corpus"))
        .withPropertyName("corpus")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    systemProperty(
        "jebol.mainClassesDirs",
        sourceSets.main.get().output.classesDirs.asPath,
    )
    testLogging {
        events("failed")
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

val checkSpec by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validate the Allium specifications in spec/"
    commandLine("./scripts/check-spec.sh")
}

tasks.check {
    dependsOn(checkSpec)
}

val allTests by tasks.registering {
    group = "verification"
    description = "Run all three gates: check, browserCheck and networkCheck"
    dependsOn(tasks.check, browserCheck, networkCheck)
}

browserCheck { mustRunAfter(tasks.check) }
networkCheck { mustRunAfter(browserCheck) }

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
