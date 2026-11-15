plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "zlib-ng"
    version = "2.3.2"
    jvmProvider = "org.jebol.extension.zlibng.ZlibNgExtension"
    webProvider = "org.jebol.extension.zlibng.ZlibNgExtension"
}

dependencies {
    functionalTestImplementation(project(":extensions:deflate"))
}
