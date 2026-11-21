plugins {
    java
    `java-test-fixtures`
}

// One of R3's optional compressors: its codec, its provider named in
// META-INF/services, its tests and its fixtures. Unlike an extension it has no
// descriptor, because nothing imports it: OptionalCompressions finds it on the
// classpath. OptionalParts.kt lists them for the carriers and the gate.

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-proc:none"))
}

val core = project(":")

dependencies {
    "compileOnly"(core)
}

tasks.jar {
    archiveFileName = "jebol-optional-${project.name}.jar"
}

val testLibraries = listOf(
    "org.junit.jupiter:junit-jupiter:5.11.4",
    "org.assertj:assertj-core:3.26.3",
)

writesItsFixtureIndex()

val unitTest = sourceSets.create("unitTest")
val functionalTest = sourceSets.create("functionalTest")
val endToEndTest = sourceSets.create("endToEndTest")

dependencies {
    "testFixturesCompileOnly"(core)
    "testFixturesImplementation"(testFixtures(core))
    testLibraries.forEach { "testFixturesImplementation"(it) }
}

val testFixturesOutput = sourceSets["testFixtures"].output

listOf(unitTest, functionalTest, endToEndTest).forEach { suite ->
    suite.compileClasspath += sourceSets.main.get().output + testFixturesOutput
    suite.runtimeClasspath += sourceSets.main.get().output + testFixturesOutput
    resolvesForDesktopJava(suite)
    dependencies {
        add(suite.implementationConfigurationName, core)
        add(suite.implementationConfigurationName, testFixtures(core))
        add(suite.implementationConfigurationName, "net.jqwik:jqwik:1.9.2")
        add(suite.runtimeOnlyConfigurationName, testFixtures(project(":desktop")))
        testLibraries.forEach { add(suite.implementationConfigurationName, it) }
        add(suite.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    }
}

val resultsPrefix = "optional" + project.name.replaceFirstChar(Char::uppercase)

val unitTests = tasks.register<Test>("unitTests") {
    description = "The optional part's tests that never build an interpreter"
    runsTheTestsIn(unitTest, "${resultsPrefix}UnitTests")
    maxHeapSize = "1g"
}

val functionalTests = tasks.register<Test>("functionalTests") {
    description = "The optional part's tests that build an interpreter with the part present"
    runsTheTestsIn(functionalTest, "${resultsPrefix}FunctionalTests")
    useJUnitPlatform { excludeTags(TESTS_THAT_EXHAUST_THE_HEAP) }
    mustRunAfter(unitTests)
}

tasks.register<Test>("endToEndTests") {
    description = "The optional part's tests that drive the shipped jar or a built site"
    runsTheTestsIn(endToEndTest, "${resultsPrefix}EndToEndTests")
    outputs.upToDateWhen { false }
    mustRunAfter(functionalTests)
}
