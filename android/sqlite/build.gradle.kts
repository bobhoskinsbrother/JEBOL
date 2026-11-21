plugins {
    id("com.android.library")
}

android {
    namespace = "org.jebol.extension.sqlite.android"
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
    api("org.jebol:sqlite:3.22.5") {
        exclude(group = "io.roastedroot")
        exclude(group = "run.endive")
    }
    compileOnly("org.jebol:jebol:3.22.5")
}
