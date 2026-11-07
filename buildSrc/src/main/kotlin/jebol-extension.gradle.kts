plugins {
    java
}

val declaration = extensions.create<JebolExtensionDeclaration>("jebolExtension")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-proc:none"))
}

val jvmLibraries: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

configurations.named("implementation") {
    extendsFrom(jvmLibraries)
}

val core = project(":")

dependencies {
    "compileOnly"(core)
}

val writeExtensionDescriptor = tasks.register<WriteExtensionDescriptor>("writeExtensionDescriptor") {
    group = "build"
    description = "Write META-INF/jebol/extension.reb from the extension's declaration"
    extensionName = declaration.name
    extensionVersion = declaration.version
    jvmProvider = declaration.jvmProvider
    webProvider = declaration.webProvider
    jvmLibraries.from(configurations.named("jvmLibraries"))
    webAssets.from(declaration.webAssets)
    val resourcesDirectory = layout.projectDirectory.dir("src/main/resources")
    resources.from(resourcesDirectory)
    resourcesRoot = resourcesDirectory.asFile.path
    destination = layout.buildDirectory.dir("generated/jebol-extension")
}

dependencies {
    "compileOnly"("org.teavm:teavm-jso:0.16.0")
    "compileOnly"("org.teavm:teavm-jso-apis:0.16.0")
    "compileOnly"("org.jetbrains:annotations:24.1.0")
}

sourceSets.main {
    resources.srcDir(writeExtensionDescriptor)
}

tasks.jar {
    archiveFileName = declaration.name.map { "jebol-$it.jar" }
    from(declaration.webAssets) {
        into("META-INF/jebol/assets")
    }
    val libraries: FileCollection = jvmLibraries
    inputs.files(libraries).withPropertyName("jvmLibraries").withPathSensitivity(PathSensitivity.NAME_ONLY)
    doFirst {
        manifest.attributes["Class-Path"] = libraries.files.map { it.name }.sorted().joinToString(" ")
    }
}

val assembleExtension = tasks.register<Sync>("assembleExtension") {
    group = "build"
    description = "Put the extension's jar and the library jars its manifest names in one folder"
    from(tasks.jar)
    from(jvmLibraries)
    into(layout.buildDirectory.dir("jebol-extension"))
}

val testLibraries = listOf(
    "org.junit.jupiter:junit-jupiter:5.11.4",
    "org.assertj:assertj-core:3.26.3",
)

val functionalTest = sourceSets.create("functionalTest")
val endToEndTest = sourceSets.create("endToEndTest")

listOf(functionalTest, endToEndTest).forEach { suite ->
    suite.compileClasspath += sourceSets.main.get().output
    suite.runtimeClasspath += sourceSets.main.get().output
    dependencies {
        add(suite.implementationConfigurationName, core)
        add(suite.implementationConfigurationName, testFixtures(core))
        testLibraries.forEach { add(suite.implementationConfigurationName, it) }
        add(suite.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    }
    configurations.named(suite.implementationConfigurationName) {
        extendsFrom(jvmLibraries)
    }
}

val gateResults = core.layout.buildDirectory.dir("test-results")

fun Test.runsTheTestsIn(suite: SourceSet, resultsName: Provider<String>) {
    group = "verification"
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    useJUnitPlatform()
    jvmArgs("-Xss2m")
    systemProperty("java.awt.headless", "true")
    systemProperty("jqwik.database", layout.buildDirectory.file("jqwik-database").get().asFile.path)
    reports.junitXml.outputLocation = resultsName.flatMap { gateResults.map { results -> results.dir(it) } }
    testLogging {
        events("failed")
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<Test>("functionalTests") {
    description = "The extension's tests that build an interpreter and run REBOL through it"
    runsTheTestsIn(functionalTest, declaration.name.map { "${it}FunctionalTests" })
    maxParallelForks = Runtime.getRuntime().availableProcessors()
}

val corePageTests = core.extensions.getByType<SourceSetContainer>()["endToEndTest"]

dependencies {
    add(endToEndTest.implementationConfigurationName, corePageTests.output)
    add(endToEndTest.implementationConfigurationName, "org.seleniumhq.selenium:selenium-java:4.27.0")
}

tasks.register<Test>("endToEndTests") {
    description = "The extension's tests that run the shipped jar and a built site, with and without it"
    runsTheTestsIn(endToEndTest, declaration.name.map { "${it}EndToEndTests" })
    dependsOn(assembleExtension, core.tasks.named("jar"), core.tasks.named("assembleWebBuildWithExtensions"),
        core.tasks.named("buildWasmGC"))
    workingDir = core.layout.projectDirectory.asFile
    val folder = layout.buildDirectory.dir("jebol-extension")
    val extensionJar = declaration.name.flatMap { name -> folder.map { it.file("jebol-$name.jar") } }
    val shippedJar = core.tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val webBuildWith = core.layout.buildDirectory.dir("generated/web-with-extensions")
    val webBuildWithout = core.layout.buildDirectory.dir("generated/teavm/wasm-gc")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Djebol.extensionFolder=" + folder.get().asFile.path,
            "-Djebol.extensionJar=" + extensionJar.get().asFile.path,
            "-Djebol.shippedJar=" + shippedJar.get().asFile.path,
            "-Djebol.webBuildWithExtensions=" + webBuildWith.get().asFile.path,
            "-Djebol.webBuild=" + webBuildWithout.get().asFile.path,
        )
    })
    systemProperty("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation")
    outputs.upToDateWhen { false }
}
