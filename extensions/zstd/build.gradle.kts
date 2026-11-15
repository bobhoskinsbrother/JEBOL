plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "zstd"
    version = "0.1.0"
    jvmProvider = "org.jebol.extension.zstd.ZstdExtension"
    webProvider = "org.jebol.extension.zstd.ZstdExtension"
}
