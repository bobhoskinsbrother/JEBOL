plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "mathpresso"
    version = "0.1.0"
    jvmProvider = "org.jebol.extension.mathpresso.MathPressoExtension"
    webProvider = "org.jebol.extension.mathpresso.MathPressoExtension"
}
