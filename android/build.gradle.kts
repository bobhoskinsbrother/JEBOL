import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.library") version "9.3.0" apply false
    id("com.android.application") version "9.3.0" apply false
    id("com.android.test") version "9.3.0" apply false
    id("de.mannodermaus.android-junit") version "2.0.2" apply false
}

// Android's gate: first that the core reaches nothing Android lacks, then the
// three device suites on emulators this build boots and shuts down itself, or
// on a phone plugged in over USB. Not part of allTests, because it needs the
// Android SDK. CLAUDE.md, "Running the suite"; decisions 73 and 75.
val core = gradle.includedBuilds.single { it.name != "android" }
val coreForAndroid = core.task(":checkCoreForAndroid")
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

// How many emulators the suites are split across: -Pemulators=N, two unless
// asked. Each holds about 3.6 GB beyond what Android is given; three left less
// than 2 GB free beside IntelliJ on 2026-11-16, the condition that ran a 22 GB
// machine out of memory the day before (decision 75).
val emulators = providers.gradleProperty("emulators").map(String::toInt).orElse(2)

// -Pdevice=<serial>[,<serial>...] runs the suites on those devices, phones over
// USB or emulators already running, sharded across them as across emulators,
// and starts and stops no emulator.
val device = providers.gradleProperty("device")
val deviceSerials = device.map { it.split(",").map(String::trim).filter(String::isNotEmpty) }
    .orElse(emulators.map { count -> (0 until count).map { "emulator-${5584 - 2 * it}" } })
val isOnEmulators = !device.isPresent

// -PendToEndDevice=<serial>[,<serial>...] runs the end-to-end suite, every test
// on each, on those devices alone, while the unit and functional suites share
// every device -Pdevice names: CI names its phones here, and its phones and
// emulators there (goal 20.6a).
val endToEndSerials = providers.gradleProperty("endToEndDevice")
    .map { it.split(",").map(String::trim).filter(String::isNotEmpty) }
    .orElse(deviceSerials.map { if (isOnEmulators) it.take(2) else it })

fun fingerprintsOf(serials: Provider<List<String>>) = serials.map { named ->
    if (isOnEmulators) {
        listOf()
    } else {
        named.map { serial ->
            val fingerprint = providers.exec {
                commandLine("${androidSdk.get()}/platform-tools/adb", "-s", serial,
                    "shell", "getprop", "ro.build.fingerprint")
                isIgnoreExitValue = true
            }.standardOutput.asText.get().trim()
            "$serial=$fingerprint"
        }
    }
}

val fingerprintsOfTheDevices = fingerprintsOf(deviceSerials)

val startEmulators = tasks.register<Exec>("startEmulators") {
    group = "verification"
    description = "Boot the API 35 tablet emulators the device suites run on, and wait until each has booted"
    onlyIf { isOnEmulators }
    environment("ANDROID_HOME", androidSdk.get())
    commandLine(emulatorScript, "start", emulators.get().toString())
}

val stopEmulators = tasks.register<Exec>("stopEmulators") {
    group = "verification"
    description = "Shut down the emulators the device suites ran on"
    onlyIf { isOnEmulators }
    environment("ANDROID_HOME", androidSdk.get())
    commandLine(emulatorScript, "stop", emulators.get().toString())
}

val deviceResults = layout.projectDirectory.dir("build/device-results")

// A suite that does not run -- skipped by -Pclasses, or never reached because
// an earlier one failed -- must not leave its last run's results to be
// archived as this one's, so every suite's are cleared once, first.
val forgetLastDeviceResults = tasks.register<Delete>("forgetLastDeviceResults") {
    delete(deviceResults)
}
val runnerBuilder = "runnerBuilder" to "de.mannodermaus.junit5.AndroidJUnit5Builder"
val unitPackage = "org.jebol.android.unit"
val endToEndPackage = "org.jebol.android.endtoend"
val fixturesPackage = "org.jebol.android.fixtures"

// -Pclasses=a.B,c.D runs only those classes. AndroidJUnitRunner drops its
// package filter once it is given classes, so each run is handed the named
// classes that are its own, and a run with none of them is skipped.
val namedClasses = providers.gradleProperty("classes").map { it.split(",").map(String::trim).filter(String::isNotEmpty) }
fun classesOf(isOwn: (String) -> Boolean) = namedClasses.map { named -> named.filter(isOwn) }
val isUnit = { name: String -> name.startsWith("$unitPackage.") }
val timingsPackage = "org.jebol.android.timings"
val isEndToEnd = { name: String -> name.startsWith("$endToEndPackage.") || name.startsWith("$timingsPackage.") }
val unitClasses = classesOf(isUnit)
val functionalClasses = classesOf { !isUnit(it) && !isEndToEnd(it) }
val endToEndClasses = classesOf(isEndToEnd)

fun argumentsFor(classes: Provider<List<String>>, packageFilter: Pair<String, String>) =
    classes.map { named -> mapOf(runnerBuilder, "class" to named.joinToString(",")) }
        .orElse(mapOf(runnerBuilder, packageFilter))

fun isWanted(classes: Provider<List<String>>) = !namedClasses.isPresent || classes.get().isNotEmpty()

val theAppsApk = layout.projectDirectory.file("app/build/outputs/apk/debug/app-debug.apk")
val appTestApk = layout.projectDirectory.file("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")

// AGP's connected task runs every test on every device, so the shards are run
// here instead: shard i of N on the i-th emulator, all at once. The archive and
// the shutdown hang off each run, so a failing run is archived and stopped too.
val androidUnitTests = tasks.register<DeviceShards>("androidUnitTests") {
    group = "verification"
    description = "Android's adapters on their own, with no interpreter, in shards across the devices"
    dependsOn(forgetLastDeviceResults, startEmulators, ":app:assembleDebug", ":app:assembleDebugAndroidTest")
    onlyIf { isWanted(unitClasses) }
    serials = deviceSerials
    deviceFingerprints = fingerprintsOfTheDevices
    outputs.cacheIf("it ran on phones, whose fingerprints are inputs") { !isOnEmulators }
    adb = androidSdk.map { "$it/platform-tools/adb" }
    appApk = theAppsApk
    testApk = appTestApk
    instrumentation = "org.jebol.android.test/androidx.test.runner.AndroidJUnitRunner"
    runnerArguments = argumentsFor(unitClasses, "package" to unitPackage)
    results = deviceResults.dir("androidUnitTests")
    finalizedBy(stopEmulators, "archiveAndroidTestResults")
}

val androidFunctionalTests = tasks.register<DeviceShards>("androidFunctionalTests") {
    group = "verification"
    description = "An interpreter on ART running REBOL, Rebol's suite included, in shards across the devices"
    dependsOn(forgetLastDeviceResults, coreForAndroid, startEmulators, ":app:assembleDebug",
        ":app:assembleDebugAndroidTest")
    onlyIf { isWanted(functionalClasses) }
    serials = deviceSerials
    deviceFingerprints = fingerprintsOfTheDevices
    outputs.cacheIf("it ran on phones, whose fingerprints are inputs") { !isOnEmulators }
    adb = androidSdk.map { "$it/platform-tools/adb" }
    appApk = theAppsApk
    testApk = appTestApk
    instrumentation = "org.jebol.android.test/androidx.test.runner.AndroidJUnitRunner"
    runnerArguments = argumentsFor(functionalClasses, "notPackage" to "$unitPackage,$fixturesPackage")
    results = deviceResults.dir("androidFunctionalTests")
    mustRunAfter(androidUnitTests)
    finalizedBy(stopEmulators, "archiveAndroidTestResults")
}

val examplesApk = layout.projectDirectory.file(
    "../examples/an-app-of-ones-own/build/jebol/android/package/org.jebol.example.notes-1.0.0-debug.apk")

val packageTheExampleApp = tasks.register<PackageTheExampleApp>("packageTheExampleApp") {
    group = "build"
    description = "Package examples/an-app-of-ones-own for Android with jebol-devtools, as its own build would"
    dependsOn(":runtime:assembleRelease", ":host:assembleRelease", ":app:assembleDebug", ":endtoend:assembleDebug")
    gradleExecutable = gradle.gradleHomeDir!!.resolve("bin/gradle").path
    coreBuild = layout.projectDirectory.dir("..")
    exampleApp = layout.projectDirectory.dir("../examples/an-app-of-ones-own")
    apk = examplesApk
    outputs.upToDateWhen { false }
}

val androidEndToEndTests = tasks.register<DeviceShards>("androidEndToEndTests") {
    group = "verification"
    description = "The installed app driven from outside by UI Automator, every test on each device: a tablet and a phone"
    dependsOn(forgetLastDeviceResults, startEmulators, ":app:assembleDebug", ":endtoend:assembleDebug")
    onlyIf { isWanted(endToEndClasses) }
    serials = endToEndSerials
    everyTestOnEachDevice = true
    deviceFingerprints = fingerprintsOf(endToEndSerials)
    outputs.cacheIf("it ran on phones, whose fingerprints are inputs") { !isOnEmulators }
    adb = androidSdk.map { "$it/platform-tools/adb" }
    appApk = theAppsApk
    otherApks.from(packageTheExampleApp)
    testApk = layout.projectDirectory.file("endtoend/build/outputs/apk/debug/endtoend-debug.apk")
    instrumentation = "org.jebol.android.endtoend/androidx.test.runner.AndroidJUnitRunner"
    runnerArguments = argumentsFor(endToEndClasses, "package" to endToEndPackage)
    hostServerIdentity = layout.projectDirectory.dir("endtoend/host-server")
    hostSiteApp = layout.projectDirectory.dir("endtoend/host-site")
    webBuild = layout.projectDirectory.dir("../build/generated/teavm/wasm-gc")
    runnerPage = layout.projectDirectory.file("../web/site/runner.html")
    dependsOn(core.task(":web:buildWasmGC"))
    results = deviceResults.dir("androidEndToEndTests")
    mustRunAfter(androidUnitTests, androidFunctionalTests)
    finalizedBy(stopEmulators, "archiveAndroidTestResults")
}

stopEmulators { mustRunAfter(androidUnitTests, androidFunctionalTests, androidEndToEndTests) }

val archiveAndroidTestResults = tasks.register<Copy>("archiveAndroidTestResults") {
    group = "verification"
    description = "Copy the device suites' results into test-history/<timestamp>-android"
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    from(deviceResults) {
        include("*/*.xml")
    }
    from(layout.projectDirectory.dir("../build/test-results/devtoolsAndroidPackagingTests")) {
        include("*.xml")
        into("devtoolsAndroidPackagingTests")
    }
    into(layout.projectDirectory.dir("../test-history/$stamp-android"))
    mustRunAfter(androidUnitTests, androidFunctionalTests, androidEndToEndTests, "androidPackagingTests")
}

val androidPackagingTests = tasks.register<RunInTheCoreBuild>("androidPackagingTests") {
    group = "verification"
    description = "jebol-devtools' packageAndroid over a test app, run by the core build, which carries the Android runtime"
    dependsOn(":runtime:assembleRelease", ":host:assembleRelease", ":sqlite:assembleRelease")
    gradleExecutable = gradle.gradleHomeDir!!.resolve("bin/gradle").path
    coreBuild = layout.projectDirectory.dir("..")
    tasksToRun = listOf(":devtools:androidPackagingTests")
    onlyIf("no classes were named with -Pclasses") { !namedClasses.isPresent }
    mustRunAfter(androidEndToEndTests)
    finalizedBy("archiveAndroidTestResults")
}

tasks.register("androidTests") {
    group = "verification"
    description = "The core's check against Android 35, then the unit, functional and end-to-end device suites, " +
        "then jebol-devtools' Android packaging, then the archive"
    dependsOn(coreForAndroid, androidUnitTests, androidFunctionalTests, androidEndToEndTests, androidPackagingTests)
}
