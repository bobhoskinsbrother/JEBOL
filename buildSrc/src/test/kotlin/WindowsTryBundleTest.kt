import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsTryBundleTest {

    private val options = tryBundleJvmOptions()

    @Test
    fun `the bundle's JVM is given what every suite's fork is given`() {
        assertTrue(options.containsAll(SUITE_JVM_ARGUMENTS))
        SUITE_SYSTEM_PROPERTIES.forEach { (name, value) -> assertTrue("-D$name=$value" in options, name) }
    }

    @Test
    fun `the bundle's tests run as Windows' desktop`() {
        assertTrue("-Djebol.target=DESKTOP_WINDOWS" in options)
    }

    @Test
    fun `the bundle names no path, since a relative one gives the tests relative temporary folders and the machine gives the absolute ones`() {
        assertTrue(options.none { it.contains("tmpdir") || it.contains("ClassesDirs") || it.contains("jqwik.database") })
    }

    @Test
    fun `the launcher leaves out what Gradle's runs of these suites leave out`() {
        val launcher = tryBundleLauncherOptions()
        assertEquals(
            listOf("--exclude-tag", TESTS_THAT_EXHAUST_THE_HEAP, "--exclude-tag", TESTS_THAT_OPEN_A_REAL_WINDOW),
            launcher.filter { it == "--exclude-tag" || it == TESTS_THAT_EXHAUST_THE_HEAP || it == TESTS_THAT_OPEN_A_REAL_WINDOW },
        )
    }

    @Test
    fun `the launcher runs every test class whatever its name, as Gradle does, and not only those named Test`() {
        val launcher = tryBundleLauncherOptions()
        assertEquals(".*", launcher[launcher.indexOf("--include-classname") + 1])
    }

    @Test
    fun `services named in several places are merged, each provider once, in the order found`() {
        val merged = mergedServices(listOf(
            "org.jebol.Platform" to listOf("org.jebol.TheJvmDesktop", "# a comment", ""),
            "org.jebol.Codec" to listOf("org.jebol.Zstd"),
            "org.jebol.Platform" to listOf("org.jebol.TheJvmDesktop", "org.jebol.Other"),
        ))
        assertEquals(
            mapOf(
                "org.jebol.Platform" to listOf("org.jebol.TheJvmDesktop", "org.jebol.Other"),
                "org.jebol.Codec" to listOf("org.jebol.Zstd"),
            ),
            merged,
        )
    }

    @Test
    fun `junit's own jars stay out of the tests' jar, since the console launcher carries them`() {
        assertTrue(isCarriedByTheConsoleLauncher("junit-jupiter-engine-5.11.4.jar"))
        assertTrue(isCarriedByTheConsoleLauncher("junit-platform-launcher-1.11.4.jar"))
        assertTrue(isCarriedByTheConsoleLauncher("opentest4j-1.3.0.jar"))
        assertTrue(!isCarriedByTheConsoleLauncher("jqwik-engine-1.9.2.jar"))
        assertTrue(!isCarriedByTheConsoleLauncher("assertj-core-3.26.3.jar"))
    }
}
