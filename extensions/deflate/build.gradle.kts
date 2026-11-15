plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "deflate"
    version = "0.1.0"
    jvmProvider = "org.jebol.extension.deflate.DeflateExtension"
    webProvider = "org.jebol.extension.deflate.DeflateExtension"
}
