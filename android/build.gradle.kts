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
val sdkDirFromLocalProperties = providers.fileContents(layout.projectDirectory.file("local.properties")).asText
    .map { text -> java.util.Properties().apply { load(text.reader()) }.getProperty("sdk.dir") ?: "" }
    .filter(String::isNotEmpty)
val defaultSdkForThisSystem = providers.systemProperty("user.home").map { home ->
    if (System.getProperty("os.name").startsWith("Mac")) "$home/Library/Android/sdk" else "$home/Android/Sdk"
}
val androidSdk = providers.environmentVariable("ANDROID_HOME")
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
    .orElse(sdkDirFromLocalProperties)
    .orElse(defaultSdkForThisSystem)

// How many emulators the suites are split across: -Pemulators=N. Each takes
// four cores and about two gigabytes with its own overhead; four at 2 GB ran a
// 22 GB machine out of memory beside IDEA and Gradle (decision 73).
val emulators = providers.gradleProperty("emulators").map(String::toInt).orElse(3)

val startEmulators = tasks.register<Exec>("startEmulators") {
    group = "verification"
    description = "Boot the API 35 emulators the device suites run on, and wait until each has booted"
    environment("ANDROID_HOME", androidSdk.get())
    commandLine(emulatorScript, "start", emulators.get().toString())
}

val stopEmulators = tasks.register<Exec>("stopEmulators") {
    group = "verification"
    description = "Shut down the emulators the device suites ran on"
    environment("ANDROID_HOME", androidSdk.get())
    commandLine(emulatorScript, "stop", emulators.get().toString())
}

val shardResults = layout.projectDirectory.dir("suites/build/outputs/androidTest-results/shards")

// AGP's connected task runs every test on every device, so the shards are run
// here instead: shard i of N on the i-th emulator, all at once. The archive and
// the shutdown hang off it, so a failing run is archived and stopped too.
val deviceShards = tasks.register<DeviceShards>("deviceShards") {
    group = "verification"
    description = "Every device test, split across the emulators in shards"
    dependsOn(coreForAndroid, startEmulators, ":suites:assembleDebug", ":suites:assembleDebugAndroidTest")
    serials = emulators.map { count -> (0 until count).map { "emulator-${5584 + 2 * it}" } }
    adb = androidSdk.map { "$it/platform-tools/adb" }
    appApk = layout.projectDirectory.file("suites/build/outputs/apk/debug/suites-debug.apk")
    testApk = layout.projectDirectory.file("suites/build/outputs/apk/androidTest/debug/suites-debug-androidTest.apk")
    instrumentation = "org.jebol.android.suites.test/androidx.test.runner.AndroidJUnitRunner"
    runnerArguments = providers.gradleProperty("classes")
        .map { mapOf("runnerBuilder" to "de.mannodermaus.junit5.AndroidJUnit5Builder", "class" to it) }
        .orElse(mapOf("runnerBuilder" to "de.mannodermaus.junit5.AndroidJUnit5Builder"))
    results = shardResults
    finalizedBy(stopEmulators, "archiveAndroidTestResults")
}

val archiveAndroidTestResults = tasks.register<Copy>("archiveAndroidTestResults") {
    group = "verification"
    description = "Copy the device suites' results into test-history/<timestamp>-android"
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    from(shardResults) {
        include("**/*.xml")
        into("androidFunctionalTests")
    }
    into(layout.projectDirectory.dir("../test-history/$stamp-android"))
    mustRunAfter(deviceShards)
}

tasks.register("androidTests") {
    group = "verification"
    description = "The core's check against Android 35, then every device test across the emulators, then the archive"
    dependsOn(coreForAndroid)
    dependsOn(deviceShards)
}
