const val TRY_BUNDLE_TARGET = "DESKTOP_WINDOWS"

const val TRY_BUNDLE_HEAP = "-Xmx2g"

const val TRY_BUNDLE_LAUNCHER_CLASS = "org.junit.platform.console.ConsoleLauncher"

val TRY_BUNDLE_TREE = REPOSITORY_FILES_TESTS_READ_BY_PATH + listOf(
    "corpus", "README.md", "src/main/resources", "src/testFixtures/resources", "desktop/src/testFixtures/resources",
)

private val JARS_THE_CONSOLE_LAUNCHER_CARRIES = listOf("junit-jupiter", "junit-platform", "opentest4j", "apiguardian")

fun tryBundleJvmOptions(): List<String> =
    SUITE_JVM_ARGUMENTS + TRY_BUNDLE_HEAP +
        SUITE_SYSTEM_PROPERTIES.map { (name, value) -> "-D$name=$value" } +
        "-Djebol.target=$TRY_BUNDLE_TARGET"

fun tryBundleLauncherOptions(): List<String> =
    listOf(
        "--include-classname", ".*",
        "--exclude-tag", TESTS_THAT_EXHAUST_THE_HEAP, "--exclude-tag", TESTS_THAT_OPEN_A_REAL_WINDOW,
    )

fun isCarriedByTheConsoleLauncher(jarName: String): Boolean =
    JARS_THE_CONSOLE_LAUNCHER_CARRIES.any { jarName.startsWith("$it-") }

private const val SERVICES_FOLDER = "META-INF/services/"

fun servicesNamedIn(entry: java.io.File): List<Pair<String, List<String>>> =
    if (entry.isDirectory) {
        entry.resolve(SERVICES_FOLDER).listFiles().orEmpty().filter { it.isFile }.map { it.name to it.readLines() }
    } else {
        java.util.zip.ZipFile(entry).use { archive ->
            archive.entries().toList()
                .filter { !it.isDirectory && it.name.startsWith(SERVICES_FOLDER) }
                .map { it.name.removePrefix(SERVICES_FOLDER) to archive.getInputStream(it).bufferedReader().readLines() }
        }
    }

fun mergedServices(found: List<Pair<String, List<String>>>): Map<String, List<String>> {
    val merged = LinkedHashMap<String, LinkedHashSet<String>>()
    found.forEach { (service, lines) ->
        val providers = merged.getOrPut(service) { LinkedHashSet() }
        lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { providers.add(it) }
    }
    return merged.mapValues { it.value.toList() }
}
