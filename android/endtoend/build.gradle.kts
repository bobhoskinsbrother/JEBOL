plugins {
    id("com.android.test")
}

// androidEndToEndTests: the installed app, driven from this test APK's own
// process by UI Automator, as a person drives it -- taps, typing on the soft
// keyboard, Back, turning the device, Home. It runs on one emulator alone,
// since UI tests share a screen (goal 2b, decision 75).
android {
    namespace = "org.jebol.android.endtoend"
    compileSdk = 35
    targetProjectPath = ":app"

    // The tests run in this APK's own process, as Macrobenchmark's do, so
    // force-stopping, killing and relaunching the app never ends them.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 35
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "META-INF/LICENSE*.md"
    }
}

dependencies {
    implementation(platform("org.junit:junit-bom:5.11.4"))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.assertj:assertj-core:3.26.3")
    implementation("androidx.test:runner:1.6.2")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("de.mannodermaus.junit5:android-test-core:2.0.2")
    runtimeOnly("de.mannodermaus.junit5:android-test-runner:2.0.2")
    runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
