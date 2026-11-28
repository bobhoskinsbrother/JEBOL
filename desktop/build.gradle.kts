plugins {
    id("jebol-java")
    id("jebol-suites")
    application
}

// The command line and the desktop's window, the JVM's own machine and
// programs, and the command line's --screen=web, which serves the web
// project's page from the JVM. It builds what ships: one jar with no
// dependencies, and the distribution around it.
val core = rootProject

dependencies {
    implementation(core)
    implementation(project(":web"))
}

// R3's external extensions JEBOL ports ship inside the jar, as the site carries
// them in its WebAssembly, so `import 'zstd` works on the desktop as it does in
// R3. The shipped jar takes each one's jar task; none is on the desktop's own
// classpath, so a core test that runs on the desktop's platform never sees them.
val listCarriedExtensions = tasks.register("listCarriedExtensions") {
    group = "build"
    description = "Write META-INF/jebol/carried.txt, naming each extension the shipped jar carries"
    val listing = layout.buildDirectory.file("generated/carried-extensions/META-INF/jebol/carried.txt")
    val names = CARRIED_EXTENSIONS
    inputs.property("names", names)
    outputs.file(listing)
    doLast {
        listing.get().asFile.parentFile.mkdirs()
        listing.get().asFile.writeText(names.joinToString("\n", postfix = "\n"))
    }
}

// R3's optional compressors ship inside the jar as well, so the desktop answers
// every method R3's Bulk product does. Each part names its provider in the
// same services file, and the jar carries one: this one.
val mergeOptionalCompressions = tasks.register("mergeOptionalCompressions") {
    group = "build"
    description = "Write the shipped jar's one services file naming every optional compressor it carries"
    val merged = layout.buildDirectory.file("generated/optional-compressions/$OPTIONAL_COMPRESSION_SERVICES")
    val named = files(OPTIONAL_PARTS.map { core.file("optional/$it/src/main/resources/$OPTIONAL_COMPRESSION_SERVICES") })
    inputs.files(named).withPropertyName("servicesFiles")
    outputs.file(merged)
    doLast {
        merged.get().asFile.parentFile.mkdirs()
        merged.get().asFile.writeText(named.files.filter { it.isFile }
            .flatMap { it.readLines() }
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .joinToString("\n", postfix = "\n"))
    }
}

tasks.jar {
    archiveBaseName = "jebol-desktop"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(DESKTOP_JAVA)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(DESKTOP_JAVA)
}

// The first class a launch loads, built so that every Java from 8 on can load
// it: it tells a Java too old for the rest of JEBOL so, in words, rather than
// leaving the JVM to report a class file version. spec/repl.allium.
val launcher = sourceSets.create("launcher")

tasks.named<JavaCompile>(launcher.compileJavaTaskName) {
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

application {
    mainClass = "org.jebol.adapter.cli.JebolLauncher"
    applicationName = "jebol"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}

// The jar runs the console on its own, so `java -jar` is a whole
// installation: no launcher script, no dependencies, nothing to unpack. It is
// the core, the page and the desktop in one, and it stays at
// build/libs/jebol-3.22.5.jar in the root, where every script and page that
// names it looks.
val shippedJar = tasks.register<Jar>("shippedJar") {
    group = "build"
    description = "The jar that ships: the core, the web project's page and the desktop, with no dependencies"
    archiveBaseName = "jebol"
    destinationDirectory = core.layout.buildDirectory.dir("libs")
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(sourceSets.main.get().output)
    from(launcher.output)
    listOf(core, project(":web")).forEach { part ->
        from(part.tasks.named<Jar>("jar").map { zipTree(it.archiveFile) }) {
            exclude("META-INF/MANIFEST.MF", OPTIONAL_COMPRESSION_SERVICES)
        }
    }
    OPTIONAL_PARTS.forEach { name ->
        from(project(":optional:$name").tasks.named<Jar>("jar").map { zipTree(it.archiveFile) }) {
            exclude("META-INF/MANIFEST.MF", OPTIONAL_COMPRESSION_SERVICES)
        }
    }
    from(layout.buildDirectory.dir("generated/optional-compressions"))
    dependsOn(mergeOptionalCompressions)
    CARRIED_EXTENSIONS.forEach { name ->
        val extensionJar = project(":extensions:$name").tasks.named<Jar>("jar").map { zipTree(it.archiveFile) }
        from(extensionJar) {
            exclude("META-INF/MANIFEST.MF", "META-INF/services/**", "META-INF/jebol/**")
        }
        from(extensionJar) {
            include("META-INF/jebol/extension.reb")
            eachFile { path = "META-INF/jebol/carried/$name.reb" }
            includeEmptyDirs = false
        }
    }
    from(layout.buildDirectory.dir("generated/carried-extensions"))
    dependsOn(listCarriedExtensions)
    manifest {
        attributes("Main-Class" to application.mainClass.get(), "Enable-Native-Access" to "ALL-UNNAMED")
    }
}

tasks.jar {
    finalizedBy(shippedJar)
}

tasks.named<CreateStartScripts>("startScripts") {
    classpath = files(shippedJar)
}

distributions {
    named("main") {
        distributionBaseName = "jebol"
        contents {
            exclude("jebol-core-*.jar", "jebol-web-*.jar", "jebol-desktop-*.jar")
            into("lib") {
                from(shippedJar)
            }
            from(core.files("LICENSE", "NOTICE", "using-jebol.md"))
        }
    }
}

tasks.named<Sync>("installDist") {
    destinationDir = core.layout.buildDirectory.dir("install/jebol").get().asFile
}

listOf("distZip", "distTar").forEach { archive ->
    tasks.named<AbstractArchiveTask>(archive) {
        destinationDirectory = core.layout.buildDirectory.dir("distributions")
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = core.layout.projectDirectory.asFile
}

// The desktop's tests drive the command line's page as the web's drive a
// site's, so they share the web's browser fixtures, and they need the
// launcher's classes beside the console's.
dependencies {
    listOf("unitTest", "functionalTest", "endToEndTest", "networkTest", "testFixtures").forEach { suite ->
        add("${suite}Implementation", testFixtures(project(":web")))
    }
    listOf("unitTest", "functionalTest", "endToEndTest", "networkTest").forEach { suite ->
        add("${suite}Implementation", launcher.output)
    }
    "endToEndTestImplementation"(project(":extensions:blend2d"))
}

tasks.named<Test>("endToEndTests") {
    dependsOn(":web:buildWasmGC", ":web:assembleSite", "installDist", shippedJar)
    systemProperty(
        "jebol.olderJavaHome",
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(CORE_JAVA) }
            .get().metadata.installationPath.asFile.path,
    )
    for (supported in listOf(DESKTOP_JAVA)) {
        systemProperty(
            "jebol.java${supported}Home",
            javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(supported) }
                .get().metadata.installationPath.asFile.path,
        )
    }
}

val windowsTryLauncher = configurations.create("windowsTryLauncher") {
    isCanBeConsumed = false
}

dependencies {
    "windowsTryLauncher"("org.junit.platform:junit-platform-console-standalone:1.11.4")
}

val coreSourceSets = core.extensions.getByType<SourceSetContainer>()
val windowsTryClasspath = files(
    coreSourceSets["functionalTest"].runtimeClasspath,
    sourceSets["unitTest"].runtimeClasspath,
    sourceSets["functionalTest"].runtimeClasspath,
)
val windowsTryEntries = windowsTryClasspath.elements.map { entries ->
    entries.map { it.asFile }.filter { it.exists() && !isCarriedByTheConsoleLauncher(it.name) }
}

val mergeWindowsTryServices = tasks.register("mergeWindowsTryServices") {
    group = "build"
    description = "Merge every services file on the Windows try bundle's classpath into one of each"
    val merged = layout.buildDirectory.dir("generated/windows-try-services")
    val entries = windowsTryEntries
    inputs.files(windowsTryClasspath).withPropertyName("classpath")
    outputs.dir(merged)
    doLast {
        val found = entries.get().flatMap { servicesNamedIn(it) }
        val folder = merged.get().asFile.resolve("META-INF/services")
        folder.deleteRecursively()
        folder.mkdirs()
        mergedServices(found).forEach { (service, providers) ->
            folder.resolve(service).writeText(providers.joinToString("\n", postfix = "\n"))
        }
    }
}

interface ArchivesForTheWindowsTryJar {
    @get:Inject
    val archives: ArchiveOperations
}

val windowsTryArchives = objects.newInstance<ArchivesForTheWindowsTryJar>().archives

val windowsTryJar = tasks.register<Jar>("windowsTryJar") {
    group = "build"
    description = "The desktop's unit and functional tests and the core's functional tests, with every library but JUnit's"
    archiveFileName = "jebol-tests.jar"
    destinationDirectory = layout.buildDirectory.dir("windows-try-jar")
    isZip64 = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val archives = windowsTryArchives
    from(windowsTryEntries.map { entries -> entries.map { if (it.isDirectory) it else archives.zipTree(it) } }) {
        exclude(
            "META-INF/MANIFEST.MF", "META-INF/services/**", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA",
            "module-info.class", "META-INF/versions/*/module-info.class",
        )
    }
    from(mergeWindowsTryServices)
}

val projectsWhoseClassesAreScanned = listOf(core, project(":desktop"), project(":web"), project(":devtools"))

tasks.register<Sync>("windowsTryBundle") {
    group = "build"
    description = "The tests CI runs on Windows, in one jar beside JUnit's console launcher and the files they read"
    into(core.layout.buildDirectory.dir("windows-try"))
    from(windowsTryJar)
    from(windowsTryLauncher) {
        rename { "junit-platform-console-standalone.jar" }
    }
    into("tree") {
        from(core.layout.projectDirectory) {
            TRY_BUNDLE_TREE.forEach { include(if (core.file(it).isDirectory) "$it/**" else it) }
            exclude("**/build/**", "**/.gradle/**")
        }
    }
    projectsWhoseClassesAreScanned.forEach { owner ->
        into("classes/main/${owner.name}") {
            from(owner.extensions.getByType<SourceSetContainer>()["main"].output.classesDirs)
        }
    }
    val options = layout.buildDirectory.dir("generated/windows-try-options")
    doFirst {
        val folder = options.get().asFile
        folder.mkdirs()
        folder.resolve("jvm-options.txt").writeText(tryBundleJvmOptions().joinToString("\n", postfix = "\n"))
        folder.resolve("launcher-options.txt").writeText(tryBundleLauncherOptions().joinToString("\n", postfix = "\n"))
    }
    from(options)
}

// Every other suite runs headless, and a JVM decides that once, when AWT
// starts. A test that opens a real desktop window -- tagged
// `opens-a-real-window` -- runs here instead, in one fork with the toolkit
// switched on, and fails on a machine with no display rather than skipping.
tasks.register<Test>("desktopWindowTests") {
    description = "The end-to-end tests that open a real desktop window, in one fork that is not headless"
    runsTheTestsIn(sourceSets["endToEndTest"], "desktopWindowTests")
    useJUnitPlatform { includeTags(TESTS_THAT_OPEN_A_REAL_WINDOW) }
    systemProperty("java.awt.headless", "false")
    listOf("DISPLAY", "XAUTHORITY", "WAYLAND_DISPLAY").forEach { name ->
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    mustRunAfter("endToEndTests")
}
