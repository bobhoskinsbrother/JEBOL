plugins {
    id("jebol-extension")
}

jebolExtension {
    name = "brotli"
    version = "0.1.0"
    jvmProvider = "org.jebol.extension.brotli.BrotliExtension"
    webProvider = "org.jebol.extension.brotli.BrotliExtension"
}

dependencies {
    jvmLibraries(project(":optional:brotli"))
    listOf("unitTest", "functionalTest", "endToEndTest").forEach { suite ->
        add("${suite}Implementation", testFixtures(project(":optional:brotli")))
    }
    "testFixturesImplementation"(testFixtures(project(":optional:brotli")))
}
