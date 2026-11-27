// JEBOL on Android: a build of its own, so the root build, allTests and the
// shipped jar never need the Android SDK. It takes the core from the root
// build through includeBuild, as an app would take a published jar. Run it
// with the root's wrapper: ./gradlew -p android <task>. Goal 2, decision 73.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jebol-android"

includeBuild("..") {
    dependencySubstitution {
        substitute(module("org.jebol:jebol")).using(project(":"))
        substitute(module("org.jebol:sqlite")).using(project(":extensions:sqlite"))
        substitute(module("org.jebol:test-targets")).using(project(":test-targets"))
    }
}

include("host", "runtime", "sqlite", "app", "endtoend")
