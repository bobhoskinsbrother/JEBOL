plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "bzip2"
    version = "1.1.0"
    jvmProvider = "org.jebol.extension.bzip2.Bzip2Extension"
    webProvider = "org.jebol.extension.bzip2.Bzip2Extension"
}
