import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.library") version "9.5.1" apply false
    id("com.android.application") version "9.5.1" apply false
    id("de.mannodermaus.android-junit") version "2.0.2" apply false
}

// Android's gate: first that the core reaches nothing Android lacks, then the
// suites on an emulator this build boots and shuts down itself. Not part of
// allTests, because it needs the Android SDK. CLAUDE.md, "Running the suite".
val coreForAndroid = gradle.includedBuilds.single().task(":checkCoreForAndroid")
val emulatorScript = layout.projectDirectory.file("emulator.sh").asFile.path
val androidSdk = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.systemProperty("user.home").map { "$it/Android/Sdk" })

val startEmulator = tasks.register<Exec>("startEmulator") {
    group = "verification"
    description = "Boot the API 35 emulator the device suites run on, and wait until it has booted"
    environment("ANDROID_HOME", androidSdk.get())
    commandLine(emulatorScript, "start")
}

val stopEmulator = tasks.register<Exec>("stopEmulator") {
    group = "verification"
    description = "Shut down the emulator the device suites ran on"
    environment("ANDROID_HOME", androidSdk.get())
    commandLine(emulatorScript, "stop")
}

val deviceSuites = listOf(":suites:connectedDebugAndroidTest")

subprojects {
    tasks.matching { it.path in deviceSuites }.configureEach {
        dependsOn(coreForAndroid, startEmulator)
        finalizedBy(stopEmulator)
    }
}

val archiveAndroidTestResults = tasks.register<Copy>("archiveAndroidTestResults") {
    group = "verification"
    description = "Copy the device suites' results into test-history/<timestamp>-android"
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    from(layout.projectDirectory.dir("suites/build/outputs/androidTest-results/connected")) {
        include("**/*.xml")
        into("androidFunctionalTests")
    }
    into(layout.projectDirectory.dir("../test-history/$stamp-android"))
    mustRunAfter(deviceSuites)
}

tasks.register("androidTests") {
    group = "verification"
    description = "The core's check against Android 35, then every device suite on the emulator, then the archive"
    dependsOn(coreForAndroid)
    dependsOn(deviceSuites)
    finalizedBy(archiveAndroidTestResults)
}
