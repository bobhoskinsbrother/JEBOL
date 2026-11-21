import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import java.io.File

const val TESTS_THAT_EXHAUST_THE_HEAP = "exhausts-the-heap"
const val TESTS_THAT_OPEN_A_REAL_WINDOW = "opens-a-real-window"
const val DISK_BACKED_TEMPORARY_ROOT = "/var/tmp"

val PROJECTS_WITH_SUITES = listOf(":", ":desktop", ":web")
val SUITE_SOURCE_SETS = listOf("unitTest", "functionalTest", "endToEndTest", "networkTest")

// The core's suites keep the names they always had; a subproject's carry its
// name first, so test-history/ can tell desktopUnitTests from unitTests.
fun Project.suiteResultsName(task: String): String =
    if (this == rootProject) task else name + task.replaceFirstChar(Char::uppercase)

private fun Project.classesDirectoriesOf(sourceSet: String): String =
    PROJECTS_WITH_SUITES.joinToString(File.pathSeparator) { path ->
        rootProject.project(path).layout.buildDirectory.dir("classes/java/$sourceSet").get().asFile.path
    }

fun Test.leavesJvmEvidenceBesideItsResults(dumpsTheHeapWhenItRunsOut: Boolean) {
    val resultsOfThisRun = reports.junitXml.outputLocation.get().asFile
    val jvmEvidence = resultsOfThisRun.resolve("jvm")
    jvmArgs(
        "-XX:ErrorFile=$jvmEvidence/hs_err_pid%p.log",
        "-Xlog:gc,gc+init:file=$jvmEvidence/gc-%p.log:time,uptime",
    )
    if (dumpsTheHeapWhenItRunsOut) {
        jvmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=$jvmEvidence")
    }
    doFirst {
        resultsOfThisRun.listFiles { file -> file.name.endsWith(".xml") }.orEmpty().forEach { it.delete() }
        jvmEvidence.deleteRecursively()
        jvmEvidence.mkdirs()
    }
}

fun Test.keepsTemporaryFilesOnDisk() {
    val folderName = path.trimStart(':').replace(':', '-')
    val temporaryFolder = File(DISK_BACKED_TEMPORARY_ROOT, "jebol-${System.getProperty("user.name")}/$folderName")
    systemProperty("java.io.tmpdir", temporaryFolder.path)
    environment("TMPDIR", temporaryFolder.path)
    doFirst {
        temporaryFolder.deleteRecursively()
        temporaryFolder.mkdirs()
    }
}

fun Test.runsTheTestsIn(
    suite: SourceSet,
    resultsName: String,
    dumpsTheHeapWhenItRunsOut: Boolean = true,
    memoryEachForkNeeds: Long? = null,
    fewestForks: Int = 1,
) {
    val root = project.rootProject
    group = "verification"
    javaLauncher.set(project.extensions.getByType<JavaToolchainService>()
        .launcherFor { languageVersion.set(JavaLanguageVersion.of(DESKTOP_JAVA)) })
    testClassesDirs = suite.output.classesDirs
    classpath = suite.runtimeClasspath
    // Every suite reads the repository from its root, wherever its project is.
    workingDir = root.projectDir
    reports.junitXml.outputLocation.set(root.layout.buildDirectory.dir("test-results/$resultsName"))
    useJUnitPlatform()
    systemProperty("jqwik.database", root.layout.buildDirectory.file("jqwik-database").get().asFile.path)
    systemProperty("java.awt.headless", "true")
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("jebol.mainClassesDirs", project.classesDirectoriesOf("main"))
    systemProperty(
        "jebol.testClassesDirs",
        SUITE_SOURCE_SETS.joinToString(File.pathSeparator) { project.classesDirectoriesOf(it) },
    )
    // How deep a test can recurse is its thread's stack, and HotSpot takes
    // the default for that from the architecture: 1 MB on x86_64, 2 MB on
    // arm64. call-frames.r3 records a real 3.22.5 answering `dp 500`, which
    // needs about 850 KB measured, so under JUnit's own frames it fits on an
    // arm64 Mac and overflows on an x86_64 one. Pinning it to the larger of
    // the two defaults makes the gate answer the same on both.
    jvmArgs("-Xss2m")
    leavesJvmEvidenceBesideItsResults(dumpsTheHeapWhenItRunsOut)
    keepsTemporaryFilesOnDisk()
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    takesForksFromMemoryLeft(memoryEachForkNeeds, fewestForks)
    inputs.dir(root.layout.projectDirectory.dir("corpus"))
        .withPropertyName("corpus")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

fun Test.readsAlsoFrom(what: String, vararg files: Any) {
    inputs.files(*files)
        .withPropertyName(what)
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
