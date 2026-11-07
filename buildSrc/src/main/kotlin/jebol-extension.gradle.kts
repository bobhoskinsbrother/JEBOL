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
    destination = layout.buildDirectory.dir("generated/jebol-extension")
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

tasks.register<Test>("endToEndTests") {
    description = "The extension's tests that run the shipped jar with and without it"
    runsTheTestsIn(endToEndTest, declaration.name.map { "${it}EndToEndTests" })
    dependsOn(assembleExtension, core.tasks.named("jar"))
    val folder = layout.buildDirectory.dir("jebol-extension")
    val extensionJar = declaration.name.flatMap { name -> folder.map { it.file("jebol-$name.jar") } }
    val shippedJar = core.tasks.named<Jar>("jar").flatMap { it.archiveFile }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Djebol.extensionFolder=" + folder.get().asFile.path,
            "-Djebol.extensionJar=" + extensionJar.get().asFile.path,
            "-Djebol.shippedJar=" + shippedJar.get().asFile.path,
        )
    })
    outputs.upToDateWhen { false }
}
