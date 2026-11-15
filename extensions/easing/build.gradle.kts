plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "easing"
    version = "1.0.0"
    jvmProvider = "org.jebol.extension.easing.EasingExtension"
    webProvider = "org.jebol.extension.easing.EasingExtension"
}
