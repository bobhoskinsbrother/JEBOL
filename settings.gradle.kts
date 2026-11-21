plugins {
    // Lets Gradle download the JDK the toolchain and the daemon ask for, so the
    // JDK on the PATH does not have to be 21 and no machine needs one
    // installed by hand.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "jebol"

val androidSdk: File? = run {
    val localProperties = file("android/local.properties")
    val sdkDirFromLocalProperties = if (localProperties.isFile) {
        java.util.Properties().apply { localProperties.reader().use(::load) }.getProperty("sdk.dir")
    } else {
        null
    }
    val home = System.getProperty("user.home")
    listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        sdkDirFromLocalProperties,
        "$home/Android/Sdk",
        "$home/Library/Android/sdk",
    ).map(::File).firstOrNull { it.resolve("platforms").isDirectory }
}

if (androidSdk != null) {
    val localProperties = file("android/local.properties")
    if (!localProperties.exists()) {
        localProperties.writeText("sdk.dir=" + androidSdk.absolutePath.replace("\\", "\\\\") + "\n")
    }
    includeBuild("android")
}

include("desktop", "web", "devtools")

include("extensions:sqlite")

include("optional:lzw", "optional:crush", "optional:lzav", "optional:lz4", "optional:lzma", "optional:brotli")

include(
    "extensions:easing", "extensions:blurhash", "extensions:mathpresso", "extensions:deflate",
    "extensions:zlib-ng", "extensions:zstd", "extensions:bzip2", "extensions:brotli", "extensions:blend2d",
)
