plugins {
    java
    `java-test-fixtures`
}

// The four suites every project with code of its own keeps, as the core always
// has: unit, functional, end-to-end and network. A test lives in the project
// whose code it exercises. CLAUDE.md, "Running the suite".
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

val core = rootProject

dependencies {
    testLibraries.forEach { "testFixturesImplementation"(it) }
    if (project != core) {
        "testFixturesApi"(testFixtures(core))
    }
    testSuites.forEach { suite ->
        add(suite.implementationConfigurationName, testFixtures(project))
        testLibraries.forEach { add(suite.implementationConfigurationName, it) }
        add(suite.runtimeOnlyConfigurationName, "org.junit.platform:junit-platform-launcher")
    }
}

testSuites.forEach { suite ->
    suite.compileClasspath += sourceSets.main.get().output
    suite.runtimeClasspath += sourceSets.main.get().output
    resolvesForDesktopJava(suite)
}

val unitTests = tasks.register<Test>("unitTests") {
    description = "Tests that never build an interpreter: values, codecs, the reader, the domain on its own"
    runsTheTestsIn(unitTest, suiteResultsName("unitTests"))
    // Gradle's default of 512 MB is what ZstdCompressionFromTheSourceTest's
    // level 22 streams need on their own, so sharing a fork ran it out of heap.
    maxHeapSize = "1g"
}

val functionalTests = tasks.register<Test>("functionalTests") {
    description = "Tests that build an interpreter and run REBOL through it"
    runsTheTestsIn(functionalTest, suiteResultsName("functionalTests"))
    useJUnitPlatform { excludeTags(TESTS_THAT_EXHAUST_THE_HEAP) }
    // -Dparity.write=true repaints the Java2D references a device's pictures
    // are compared with (decision 75), instead of checking them.
    providers.systemProperty("parity.write").orNull?.let { systemProperty("parity.write", it) }
    mustRunAfter(unitTests)
}

tasks.register<Test>("endToEndTests") {
    description = "Tests that drive a real interface: the command line, a real browser, and local servers"
    runsTheTestsIn(endToEndTest, suiteResultsName("endToEndTests"),
        memoryEachForkNeeds = MEMORY_AN_END_TO_END_FORK_NEEDS, fewestForks = 2)
    // Each process here holds a browser for every configuration its classes
    // touch, and a Chrome with its renderers is hundreds of megabytes, so what
    // bounds these forks is memory and not the processor count the other two
    // suites use. Twelve on a six-core 16 GB machine put swap at its ceiling
    // and Chrome took a SIGSEGV mid-run, failing two tests with a browser that
    // had stopped answering: one waited 30 seconds for a browser, the other
    // timed out removing its BiDi user context on the way out. Four gibibytes
    // a fork leaves a machine with memory to spare at its processor count.
    // The memory counted is what is available when the stage starts, not the
    // machine's total: the IDE and Gradle's own daemon hold six or seven
    // gigabytes between them, and forks sized on the total sent a 23 GB machine
    // two gigabytes into swap, where one shipped jar took more than twenty
    // seconds to print its first prompt.
    providers.gradleProperty("endToEndForks").map(String::toInt).orNull?.let { maxParallelForks = it }
    systemProperty("junit.jupiter.testclass.order.default", "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation")
    val built = core.layout.buildDirectory
    systemProperty("jebol.webBuild", built.dir("generated/teavm/wasm-gc").get().asFile.path)
    systemProperty("jebol.webSite", built.dir("site").get().asFile.path)
    systemProperty("jebol.installedLauncher", built.file("install/jebol/bin/jebol").get().asFile.path)
    systemProperty("jebol.shippedJar", built.file("libs/jebol-3.22.5.jar").get().asFile.path)
    useJUnitPlatform { excludeTags(TESTS_THAT_OPEN_A_REAL_WINDOW) }
    outputs.upToDateWhen { false }
    mustRunAfter(functionalTests)
}

// Rebol's own assertions that reach a host on the internet live in a gate of
// their own, out of `allTests`: a gate that runs everywhere can only run them
// where the network answers, and a run that reached out would measure the
// machine rather than JEBOL. What belongs to this gate is listed, with the line
// in each that decides it, in
// src/testFixtures/resources/rebol-suite/needs-the-network.txt.
tasks.register<Test>("networkTests") {
    description = "Rebol's own assertions that need the network, run against the hosts they name"
    runsTheTestsIn(networkTest, suiteResultsName("networkTests"))
    outputs.upToDateWhen { false }
    mustRunAfter(functionalTests)
}
