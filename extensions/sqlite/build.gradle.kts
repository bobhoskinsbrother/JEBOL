plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "sqlite"
    version = "3.46.0.0"
    jvmProvider = "org.jebol.extension.sqlite.SqliteOnTheJvm"
}

dependencies {
    jvmLibraries("io.roastedroot:sqlite4j:3.54.1.0")
}
