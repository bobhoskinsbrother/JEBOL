plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "brotli"
    version = "0.1.0"
    jvmProvider = "org.jebol.extension.brotli.BrotliExtension"
    webProvider = "org.jebol.extension.brotli.BrotliExtension"
}
