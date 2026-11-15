plugins {
    id("com.android.library")
}

// The core as an Android app carries it, with Android's own host beside it.
// The core comes whole from the root build; nothing of the desktop or the web
// is in it (goal 2a step 4).
android {
    namespace = "org.jebol.adapter.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api("org.jebol:jebol:3.22.5")
}
