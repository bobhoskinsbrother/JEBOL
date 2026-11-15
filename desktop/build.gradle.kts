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

tasks.jar {
    archiveBaseName = "jebol-desktop"
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
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
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
            exclude("META-INF/MANIFEST.MF")
        }
    }
    manifest {
        attributes("Main-Class" to application.mainClass.get())
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
}

tasks.named<Test>("endToEndTests") {
    dependsOn(":web:buildWasmGC", ":web:assembleSite", "installDist", shippedJar)
    systemProperty(
        "jebol.olderJavaHome",
        javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
            .get().metadata.installationPath.asFile.path,
    )
    for (supported in listOf(21, 25)) {
        systemProperty(
            "jebol.java${supported}Home",
            javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(supported) }
                .get().metadata.installationPath.asFile.path,
        )
    }
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
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    mustRunAfter("endToEndTests")
}
