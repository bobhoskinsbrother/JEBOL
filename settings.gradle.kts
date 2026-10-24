plugins {
    // Lets Gradle download the JDK the toolchain asks for, so the JDK on the
    // PATH does not have to be 25 and no machine needs one installed by hand.
    // Homebrew has no openjdk@25 bottle for an Intel Mac, so on one this is
    // the only way the build gets a 25 at all.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jebol"
