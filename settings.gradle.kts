plugins {
    // Lets Gradle download the JDK the toolchain asks for. Only Java 21 is
    // installed on this machine and the build targets 25.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jebol"
