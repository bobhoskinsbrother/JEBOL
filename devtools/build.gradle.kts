plugins {
    id("jebol-java")
    id("jebol-suites")
    `java-gradle-plugin`
    `maven-publish`
}

val core = rootProject

dependencies {
    implementation(core)
    "testFixturesImplementation"(gradleTestKit())
    "endToEndTestImplementation"(testFixtures(project(":web")))
}

val androidPackagingTest = sourceSets.create("androidPackagingTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations.named("androidPackagingTestImplementation") {
    extendsFrom(configurations["functionalTestImplementation"])
}
configurations.named("androidPackagingTestRuntimeOnly") {
    extendsFrom(configurations["functionalTestRuntimeOnly"])
}
resolvesForDesktopJava(androidPackagingTest)

gradlePlugin {
    plugins {
        create("jebolDevtools") {
            id = "org.jebol.devtools"
            implementationClass = "org.jebol.devtools.JebolDevtoolsPlugin"
        }
    }
    testSourceSets(sourceSets["functionalTest"], sourceSets["endToEndTest"], androidPackagingTest)
}

val sqliteWebAssets = listOf("jebol-sqlite.js", "sqlite3.js", "sqlite3.wasm")
val carriedRoot = layout.buildDirectory.dir("generated/devtools-carried")

val assembleDevtoolsRuntime = tasks.register<Sync>("assembleDevtoolsRuntime") {
    group = "build"
    description = "Gather what jebol-devtools carries: the shipped jar, the web runtime, SQLite and the page's template"
    into(carriedRoot.map { it.dir("org/jebol/devtools") })
    from(project(":desktop").tasks.named("shippedJar")) {
        into("runtime/desktop")
        rename { "jebol.jar" }
    }
    from(project(":extensions:sqlite").tasks.named("assembleExtension")) {
        into("runtime/extensions/sqlite/jvm")
    }
    from(project(":web").tasks.named("assembleWebBuildWithExtensions")) {
        into("runtime/web")
        exclude(sqliteWebAssets)
    }
    from(project(":web").tasks.named("assembleWebBuildWithExtensions")) {
        into("runtime/extensions/sqlite/web")
        include(sqliteWebAssets)
    }
    from(core.file("src/main/resources/org/jebol/fonts/NotoSans-OFL.txt")) {
        into("runtime/web")
    }
    from(project(":web").file("site/runner.html")) {
        into("templates/web")
        rename { "index.html" }
        val pageHead = """
            <title>${'$'}{jebol.title}</title>
            <link rel="manifest" href="manifest.webmanifest">
            <meta name="theme-color" content="${'$'}{jebol.colour}">
            """.trimIndent()
        filter { line -> if (line.trim() == "<title>JEBOL</title>") pageHead else line }
    }
    from(layout.projectDirectory.dir("src/templates")) {
        into("templates")
    }
    gradle.includedBuilds.firstOrNull { it.name == "android" }?.let { android ->
        dependsOn(android.task(":runtime:assembleRelease"), android.task(":host:assembleRelease"),
            android.task(":sqlite:assembleRelease"))
        from(android.projectDir.resolve("sqlite/build/outputs/aar/sqlite-release.aar")) {
            into("runtime/extensions/sqlite/android")
            rename { "jebol-sqlite-android.aar" }
        }
        from(project(":extensions:sqlite").tasks.named("jar")) {
            into("runtime/extensions/sqlite/android")
        }
        from(android.projectDir.resolve("runtime/build/outputs/aar/runtime-release.aar")) {
            into("runtime/android")
            rename { "jebol-android-runtime.aar" }
        }
        from(android.projectDir.resolve("host/build/outputs/aar/host-release.aar")) {
            into("runtime/android")
            rename { "jebol-android-host.aar" }
        }
        from(core.tasks.named("jar")) {
            into("runtime/android")
            rename { "jebol-core.jar" }
        }
    }
    val known = EVERY_EXTENSION.sorted()
    inputs.property("knownExtensions", known)
    doLast {
        val root = destinationDir
        root.resolve("known-extensions.txt").writeText(known.joinToString("\n", postfix = "\n"))
        val listed = root.walkTopDown().filter { it.isFile && it.name != "carried.txt" }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()
        root.resolve("carried.txt").writeText(listed.joinToString("\n", postfix = "\n"))
    }
}

sourceSets.main {
    resources.srcDir(assembleDevtoolsRuntime.map { carriedRoot.get() })
}

tasks.jar {
    archiveBaseName = "jebol-devtools"
}

(components["java"] as AdhocComponentWithVariants).apply {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

publishing {
    repositories {
        maven {
            name = "devtools"
            url = uri(core.layout.buildDirectory.dir("devtools-repo"))
        }
    }
}

afterEvaluate {
    publishing.publications.withType<MavenPublication>().named("pluginMaven") {
        artifactId = "jebol-devtools"
    }
}

val publishDevtools = tasks.register("publishDevtools") {
    group = "publishing"
    description = "Publish jebol-devtools and the core it reads app.reb with into build/devtools-repo"
    dependsOn("publishAllPublicationsToDevtoolsRepository", ":publishCorePublicationToDevtoolsRepository")
}

tasks.named<Test>("endToEndTests") {
    dependsOn(publishDevtools)
    systemProperty("jebol.exampleApp", core.file("examples/an-app-of-ones-own").path)
}

tasks.register<Test>("androidPackagingTests") {
    description = "packageAndroid over a test app, with the Android SDK; run by android/'s androidTests, not allTests"
    runsTheTestsIn(androidPackagingTest, "devtoolsAndroidPackagingTests")
    maxParallelForks = 1
    outputs.upToDateWhen { false }
}

tasks.register<Test>("windowTests") {
    description = "The end-to-end tests that open a packaged app's real window, in one fork that is not headless"
    runsTheTestsIn(sourceSets["endToEndTest"], "devtoolsWindowTests")
    useJUnitPlatform { includeTags(TESTS_THAT_OPEN_A_REAL_WINDOW) }
    systemProperty("java.awt.headless", "false")
    systemProperty("jebol.exampleApp", core.file("examples/an-app-of-ones-own").path)
    listOf("DISPLAY", "XAUTHORITY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR").forEach { name ->
        providers.environmentVariable(name).orNull?.let { environment(name, it) }
    }
    dependsOn(publishDevtools)
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    mustRunAfter("endToEndTests")
}
