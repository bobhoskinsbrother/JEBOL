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
