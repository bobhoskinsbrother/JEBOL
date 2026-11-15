import javax.inject.Inject

plugins {
    id("jebol-java")
    id("jebol-suites")
    id("org.teavm") version "0.16.0"
}

// The page's ports and the browser's own program. The core is the program;
// what the browser needs on top of it is here. The command line's
// --screen=web, which serves this page from the JVM, is the desktop's.
val core = rootProject

dependencies {
    implementation(core)
    // The web build's substitution targets and library resources are written
    // against these, and they are never on the shipped jar's classpath.
    "teavmImplementation"(core)
    CARRIED_EXTENSIONS.forEach { "teavmImplementation"(project(":extensions:$it")) }
    "teavmCompileOnly"("org.teavm:teavm-classlib:0.16.0")
    "teavmCompileOnly"("org.teavm:teavm-core:0.16.0")
    "teavmCompileOnly"("org.teavm:teavm-extension-spi:0.16.0")
    "teavmCompileOnly"("org.teavm:teavm-interop:0.16.0")
    "teavmCompileOnly"("org.jetbrains:annotations:24.1.0")
}

tasks.jar {
    archiveBaseName = "jebol-web"
}

sourceSets.named("teavm") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

teavm {
    wasmGC {
        mainClass.set("org.jebol.adapter.web.WorkerInterpreter")
        targetFileName.set("jebol-web")
        obfuscated.set(false)
        outOfProcess.set(false)
    }
}

// What the web build makes stays where the root build always put it, so every
// script, test and page that names build/generated/teavm or build/site still
// finds it.
val webBuild = core.layout.buildDirectory.dir("generated/teavm/wasm-gc")
val coreResources = core.layout.projectDirectory.dir("src/main/resources")

tasks.named<org.teavm.gradle.tasks.GenerateWasmGCTask>("generateWasmGC") {
    outputDir.set(webBuild.map { it.asFile })
}

val copyWorkerScript = tasks.register<Copy>("copyWorkerScript") {
    group = "build"
    description = "Put the worker's shell script beside the WebAssembly module"
    from("src/teavm/js/jebol-worker.js")
    into(webBuild)
}

val copyPageScripts = tasks.register<Copy>("copyPageScripts") {
    group = "build"
    description = "Put the page's scripts for the interpreter's asks and the history beside the WebAssembly module"
    from("src/main/resources/jebol-page/jebol-asks.js", "src/main/resources/jebol-page/jebol-navigation.js",
        "src/main/resources/jebol-page/jebol-paint.js", "src/main/resources/jebol-page/jebol-screen.js",
        "src/main/resources/jebol-page/jebol-input.js", "site/jebol-loading.js", "site/jebol-runner.js")
    into(webBuild)
}

val copyCarriedFont = tasks.register<Copy>("copyCarriedFont") {
    group = "build"
    description = "Put the font this build carries beside the WebAssembly module"
    from(coreResources.file("org/jebol/fonts/NotoSans-Regular.ttf"))
    into(webBuild)
}

tasks.named("buildWasmGC") {
    dependsOn(copyWorkerScript, copyPageScripts, copyCarriedFont)
}

val extensionProjects = listOf("sqlite")

// An extension reaches a site as a jar named with -Pextensions: its classes join
// the WasmGC build, which finds its web provider through META-INF/services, and its
// assets go beside jebol-web.wasm. Decision 63.
val siteExtensionJars: FileCollection = files(providers.gradleProperty("extensions")
    .map { named -> named.split(",").map(String::trim).filter(String::isNotEmpty).map { core.file(it) } }
    .getOrElse(emptyList()))

abstract class CheckSiteExtensions : DefaultTask() {

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:InputFiles
    abstract val jars: ConfigurableFileCollection

    @TaskAction
    fun check() {
        jars.files.forEach { jar ->
            val descriptor = archives.zipTree(jar).matching { include("META-INF/jebol/extension.reb") }.files
            val text = descriptor.firstOrNull()?.readText() ?: ""
            if (!Regex("""(?m)^\s*web\s*\[""").containsMatchIn(text)) {
                throw GradleException("$jar has no web setup in META-INF/jebol/extension.reb, so a site cannot carry it")
            }
        }
    }
}

val checkSiteExtensions = tasks.register<CheckSiteExtensions>("checkSiteExtensions") {
    group = "verification"
    description = "Refuse an extension named with -Pextensions that has no web setup"
    jars.from(siteExtensionJars)
}

val unpackSiteExtensions = tasks.register<Sync>("unpackSiteExtensions") {
    group = "build"
    description = "Unpack each jar named with -Pextensions, so the WasmGC build never reads a jar rebuilt under it"
    dependsOn(checkSiteExtensions)
    siteExtensionJars.forEach { jar ->
        from(zipTree(jar)) {
            into(jar.nameWithoutExtension)
        }
    }
    into(layout.buildDirectory.dir("generated/site-extensions"))
}

tasks.named<org.teavm.gradle.tasks.GenerateWasmGCTask>("generateWasmGC") {
    siteExtensionJars.forEach { jar ->
        classpath.from(unpackSiteExtensions.map { it.destinationDir.resolve(jar.nameWithoutExtension) })
    }
    dependsOn(unpackSiteExtensions)
}

val buildWasmGCWithExtensions = tasks.register<org.teavm.gradle.tasks.GenerateWasmGCTask>("buildWasmGCWithExtensions") {
    group = "build"
    description = "The WasmGC build again, carrying every extension in extensions/, for their end-to-end tests"
    val plain = tasks.named<org.teavm.gradle.tasks.GenerateWasmGCTask>("generateWasmGC")
    classpath.from(plain.map { it.classpath })
    extensionProjects.forEach { name ->
        classpath.from(project(":extensions:$name").extensions.getByType<SourceSetContainer>()["main"].output)
    }
    mainClass.set(plain.flatMap { it.mainClass })
    targetFileName.set(plain.flatMap { it.targetFileName })
    obfuscated.set(plain.flatMap { it.obfuscated })
    strict.set(plain.flatMap { it.strict })
    debugInformation.set(plain.flatMap { it.debugInformation })
    optimization.set(plain.flatMap { it.optimization })
    fastGlobalAnalysis.set(plain.flatMap { it.fastGlobalAnalysis })
    outOfProcess.set(plain.flatMap { it.outOfProcess })
    processMemory.set(plain.flatMap { it.processMemory })
    daemonClasspath.from(plain.map { it.daemonClasspath })
    outputDir.set(core.layout.buildDirectory.dir("generated/teavm/wasm-gc-extensions").map { it.asFile })
    mustRunAfter(plain)
}

tasks.register<Sync>("assembleWebBuildWithExtensions") {
    group = "build"
    description = "The web build's files with the WasmGC build that carries every extension, and their assets"
    dependsOn(tasks.named("buildWasmGC"))
    from(webBuild) {
        exclude("jebol-web.wasm")
    }
    from(buildWasmGCWithExtensions) {
        include("jebol-web.wasm")
    }
    extensionProjects.forEach { extension ->
        from(project(":extensions:$extension").tasks.named("jar").map { zipTree((it as Jar).archiveFile) }) {
            include("META-INF/jebol/assets/**")
            eachFile { path = this.name }
            includeEmptyDirs = false
        }
    }
    into(core.layout.buildDirectory.dir("generated/web-with-extensions"))
}

tasks.register<Sync>("assembleSite") {
    group = "build"
    description = "Assemble the static site: a runner page and the app it runs"
    dependsOn(tasks.named("buildWasmGC"))
    val theApp = core.file(providers.gradleProperty("siteDir").getOrElse("examples/r3-gui"))
    val theRunner = layout.projectDirectory.file("site/runner.html")
    inputs.property("appDirectory", theApp.path)
    inputs.file(theRunner).withPropertyName("runnerPage")
    val siteDirectory = core.layout.buildDirectory.dir("site")
    into(siteDirectory)
    from(theApp)
    from(webBuild) {
        include("jebol-web.wasm", "jebol-web-runtime.js", "jebol-worker.js", "jebol-asks.js",
            "jebol-navigation.js", "jebol-paint.js", "jebol-screen.js", "jebol-input.js", "jebol-loading.js",
            "jebol-runner.js",
            "NotoSans-Regular.ttf")
    }
    from(coreResources.file("org/jebol/fonts/NotoSans-OFL.txt"))
    from(unpackSiteExtensions) {
        include("*/META-INF/jebol/assets/**")
        eachFile { path = this.name }
        includeEmptyDirs = false
    }
    doLast {
        val site = siteDirectory.get().asFile
        val names = theApp.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(theApp).invariantSeparatorsPath }
            .sorted()
            .toList()
        val filesJson = names.joinToString(",", "[", "]") { name ->
            "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
        site.resolve("app.json").writeText("{\"files\":$filesJson,\"entry\":\"main.reb\"}")
        if (!site.resolve("index.html").exists()) {
            theRunner.asFile.copyTo(site.resolve("index.html"))
        }
    }
}

// TeaVM only embeds a resource it is told about. The list is written from the
// resources themselves so it cannot drift from them.
val writeTheWebResourceList = tasks.register("writeTheWebResourceList") {
    group = "build"
    description = "List the library files the web build must carry"
    val library = core.fileTree("src/main/resources/org/jebol")
    inputs.files(library).withPropertyName("libraryFiles")
    val list = layout.buildDirectory.file("generated/web/jebol-resources.txt")
    val resourcesRoot = coreResources.asFile
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

// The web's tests build their interpreters on the platform they run on, as
// the core's do; on the JVM that is the desktop's.
dependencies {
    listOf("unitTest", "functionalTest", "endToEndTest", "networkTest").forEach { suite ->
        add("${suite}RuntimeOnly", testFixtures(project(":desktop")))
    }
}

tasks.named<Test>("endToEndTests") {
    dependsOn("buildWasmGC", "assembleSite")
}
