plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "blurhash"
    version = "1.0.0"
    jvmProvider = "org.jebol.extension.blurhash.BlurHashExtension"
    webProvider = "org.jebol.extension.blurhash.BlurHashExtension"
}
