plugins {
    id("com.android.application")
    id("de.mannodermaus.android-junit")
}

// The app: examples/r3-gui on Android's own screen (goal 2b). Its androidTest
// holds two of Android's three suites, told apart by package when they run:
// androidUnitTests in org.jebol.android.unit, Android's adapters on their own,
// and androidFunctionalTests, everything else -- the core's functional tests and
// Rebol's suite dexed in from the root build, and org.jebol.android.functional.
// The third, androidEndToEndTests, drives the installed app from outside and is
// android/endtoend. Decision 73, decision 75.
android {
    namespace = "org.jebol.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.jebol.android"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "3.22.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// The app the APK carries: examples/r3-gui as Java resources under jebol-app/,
// with an app.json naming its files and its entry as assembleSite writes one
// for a site (decision 73: resources, not assets). The app fills its memory
// filesystem from them and runs the entry from /.
abstract class BundleTheApp : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val resources: DirectoryProperty

    @TaskAction
    fun bundle() {
        val into = resources.get().asFile.resolve("jebol-app")
        into.deleteRecursively()
        into.mkdirs()
        val app = appDirectory.get().asFile
        val names = app.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(app).invariantSeparatorsPath }
            .sorted()
            .toList()
        names.forEach { name -> app.resolve(name).copyTo(into.resolve(name), overwrite = true) }
        val quote = "\""
        fun quoted(text: String) = quote + text.replace("\\", "\\\\").replace(quote, "\\" + quote) + quote
        into.resolve("app.json").writeText(
            "{" + quoted("files") + ":" + names.joinToString(",", "[", "]", transform = ::quoted) +
                "," + quoted("entry") + ":" + quoted("main.reb") + "}")
    }
}

val bundleTheApp = tasks.register<BundleTheApp>("bundleTheApp") {
    appDirectory = rootProject.layout.projectDirectory.dir("../examples/r3-gui")
    resources = layout.buildDirectory.dir("generated/jebol-app")
}

androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(bundleTheApp, BundleTheApp::resources)
    }
}

// The core's functional tests, Rebol's suite among them, compiled once by the
// root build as on the JVM and dexed into this test APK, where the Jupiter
// runner finds them (goal 2a step 7).
val core = gradle.includedBuilds.single { it.name != "android" }
val coreFunctionalTests = files(core.projectDir.resolve("build/libs/jebol-core-3.22.5-functional-tests.jar")) {
    builtBy(core.task(":functionalTestJar"))
}
val sqliteFunctionalTests = files(core.projectDir.resolve("extensions/sqlite/build/libs/jebol-sqlite-3.22.5-functional-tests.jar")) {
    builtBy(core.task(":extensions:sqlite:functionalTestJar"))
}

dependencies {
    implementation(project(":runtime"))
    androidTestImplementation(project(":sqlite"))
    androidTestImplementation(coreFunctionalTests)
    androidTestImplementation(sqliteFunctionalTests)
    androidTestCompileOnly("net.jqwik:jqwik-api:1.9.2")
    androidTestImplementation(testFixtures("org.jebol:jebol:3.22.5"))
    androidTestImplementation(platform("org.junit:junit-bom:5.11.4"))
    androidTestImplementation("org.junit.jupiter:junit-jupiter-api")
    androidTestImplementation("org.junit.jupiter:junit-jupiter-params")
    androidTestImplementation("org.assertj:assertj-core:3.26.3")
    androidTestImplementation("androidx.test:runner:1.6.2")
}

// jqwik's engine cannot run on ART (it reads generics through
// Class.getAnnotatedSuperclass), so the test APK carries none; the core's test
// fixtures would otherwise bring it in.
configurations.configureEach {
    if (name.endsWith("AndroidTestRuntimeClasspath")) {
        exclude(group = "net.jqwik")
    }
}
