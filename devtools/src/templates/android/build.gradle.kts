import java.util.Properties

plugins {
    id("com.android.application") version "9.3.0"
}

val jebolWork = file(providers.gradleProperty("jebol.work").orNull
    ?: error("this project is built by the jebol plugin's packageAndroid task, which says where its app is"))
val jebol = Properties().apply { jebolWork.resolve("jebol.properties").reader().use { load(it) } }
val ownKey = jebol.getProperty("storeFile") != null

android {
    namespace = "org.jebol.app"
    compileSdk = 35

    defaultConfig {
        applicationId = jebol.getProperty("identity")
        minSdk = 35
        targetSdk = 35
        versionCode = jebol.getProperty("build").toInt()
        versionName = jebol.getProperty("version")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        if (ownKey) {
            create("own") {
                storeFile = file(jebol.getProperty("storeFile"))
                storePassword = jebol.getProperty("storePassword")
                keyAlias = jebol.getProperty("keyAlias")
                keyPassword = jebol.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (ownKey) {
                signingConfig = signingConfigs.getByName("own")
            }
        }
    }

    sourceSets.getByName("main") {
        manifest.srcFile(jebolWork.resolve("AndroidManifest.xml"))
        res.directories.add(jebolWork.resolve("res").path)
        resources.directories.add(jebolWork.resolve("resources").path)
    }
}

dependencies {
    implementation(fileTree(jebolWork.resolve("runtime")) { include("*.jar", "*.aar") })
}
