plugins {
    java
    application
    jacoco
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

    // Drives a real browser, so that "the page and the window show the same
    // picture" is a thing the build checks rather than a thing anybody says.
    // testImplementation and not implementation: the shipped jar has no
    // dependencies and this does not change that.
    testImplementation("org.seleniumhq.selenium:selenium-java:4.27.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// Drives a real browser, so it needs one, and it needs the network the first
// time it fetches a driver. Kept out of `check` for that reason and for no
// other: it is a second gate rather than a skipped test, it runs everything it
// holds every time it runs, and `./gradlew browserCheck` is the whole of how.
//
// What it is for: proving that the page and the desktop window show the same
// picture. They are handed the same paint list, so the only way they can
// differ is in how one of them draws a stated rectangle in a stated place --
// and this is what looks.
val browserCheck by tasks.registering(Test::class) {
    group = "verification"
    description = "Render the same paint list in Java2D and in a real browser, and compare"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("browser") }
    systemProperty("java.awt.headless", "true")
    outputs.upToDateWhen { false }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("browser")
    }

    systemProperty("jqwik.database", layout.buildDirectory.file("jqwik-database").get().asFile.path)
    systemProperty("java.awt.headless", "true")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    inputs.dir(layout.projectDirectory.dir("corpus"))
        .withPropertyName("corpus")
        .withPathSensitivity(PathSensitivity.RELATIVE)

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

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

val checkSpec by tasks.registering(Exec::class) {
    group = "verification"
    description = "Validate the Allium specifications in spec/"
    commandLine("./scripts/check-spec.sh")
}

tasks.check {
    dependsOn(checkSpec)
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

distributions {
    named("main") {
        contents {
            from("LICENSE", "NOTICE", "using-jebol.md")
        }
    }
}
