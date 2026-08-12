plugins {
    java
    application
}

group = "org.jebol"
version = "0.1.0-SNAPSHOT"

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
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("net.jqwik:jqwik:1.9.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()

    // The tests run with no display, which is what a server has and what CI
    // has. Two reasons, and the second is the one that cost a killed build.
    //
    // It makes the window adapter's refusal path the one that runs, rather
    // than being skipped on every developer machine and exercised only in CI.
    // And it makes it impossible for a test to open a dialog and wait: Swing
    // asks for a screen, finds none, and the adapter refuses before it gets
    // that far. A test that opened a colour chooser hung the build until
    // someone noticed.
    systemProperty("java.awt.headless", "true")

    // Run the classes across several JVMs, because the suite is boot-bound
    // rather than work-bound. `Interpreter.create()` costs about 68ms -- it
    // loads and evaluates the whole imported library every time -- and nearly
    // every test asks for a fresh one, while the evaluation each test then does
    // is too fast to measure. So the wall clock is very close to
    // 68ms times the number of tests, and on one fork that is nine minutes.
    //
    // The forks share nothing: each is a separate process with its own
    // single-threaded interpreter, and no test writes to a fixed path outside
    // its own temporary directory. Nothing about the interpreter becomes
    // concurrent.
    //
    // Half the cores rather than all of them, because more buys nothing. Gradle
    // hands out whole classes, so the wall clock cannot go below the slowest
    // single class -- `BorrowedLibraryTest` at about two minutes -- and six forks
    // already reach that floor. Ten measured the same to within two seconds.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // The corpus is test input. Without this, changing a .corpus file leaves
    // the test task up to date and the change never runs, which is how a new
    // corpus file can look green before anything has read it.
    inputs.dir(layout.projectDirectory.dir("corpus"))
        .withPropertyName("corpus")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // ArchUnit cannot scan the test runtime classpath reliably, because
    // Gradle may hand it over via a pathing jar. Point it at the compiled
    // production classes directly.
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

// The specifications are the primary artefact, so they are checked by the
// same gate as the code rather than by a step somebody has to remember.
val checkSpec by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validate the Allium specifications in spec/"
    commandLine("./scripts/check-spec.sh")
}

tasks.check {
    dependsOn(checkSpec)
}

// So `gradlew run` can be fed a script on standard input.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
