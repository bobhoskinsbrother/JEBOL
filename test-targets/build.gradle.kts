plugins {
    id("jebol-java")
    `java-library`
}

// Where a test runs, said one way (goal 20.4c): TestTarget, @RunsOn, the
// filter that leaves out what another target runs, and expected(...). A jar
// of its own, with nothing of JEBOL's in it, so the core's test fixtures and
// Android's end-to-end APK can both carry it. CLAUDE.md, "Where a test runs".
dependencies {
    compileOnly(platform("org.junit:junit-bom:5.11.4"))
    compileOnly("org.junit.platform:junit-platform-launcher")
}
