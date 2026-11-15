plugins {
    id("com.android.application")
    id("de.mannodermaus.android-junit")
}

// An application to run the suites in on a device, which 2b replaces with the
// app itself. Its tests run on an x86_64 API 35 emulator that androidTests
// boots and shuts down itself (android/emulator.sh); Gradle Managed Devices
// refused the image sdkmanager installs, and their own download stalled
// (decision 73).
android {
    namespace = "org.jebol.android.suites"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.jebol.android.suites"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "3.22.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":host"))
    androidTestImplementation(testFixtures("org.jebol:jebol:3.22.5"))
    androidTestImplementation(platform("org.junit:junit-bom:5.11.4"))
    androidTestImplementation("org.junit.jupiter:junit-jupiter-api")
    androidTestImplementation("org.junit.jupiter:junit-jupiter-params")
    androidTestImplementation("org.assertj:assertj-core:3.26.3")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
