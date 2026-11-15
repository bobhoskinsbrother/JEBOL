plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "blend2d"
    version = "0.12.0"
    jvmProvider = "org.jebol.extension.blend2d.Blend2DExtension"
    webProvider = "org.jebol.extension.blend2d.Blend2DExtension"
}
