import com.sun.management.OperatingSystemMXBean
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskOperationDescriptor
import java.io.File
import java.lang.management.ManagementFactory
import javax.inject.Inject

private const val MEBIBYTE = 1024L * 1024

private const val GIBIBYTE = 1024 * MEBIBYTE

const val MEMORY_AN_END_TO_END_FORK_NEEDS = 4 * GIBIBYTE

private const val GRADLES_DEFAULT_TEST_HEAP = 512 * MEBIBYTE

private const val MEMORY_A_FORK_NEEDS_BEYOND_ITS_HEAP = 512 * MEBIBYTE

private const val MEMORY_KEPT_FOR_THE_BUILD_ITSELF = 2 * GIBIBYTE

private const val TEST_CLASSES_A_FORK_IS_WORTH = 8

private const val LONGEST_WAIT_FOR_MEMORY_NANOS = 5L * 60 * 1_000_000_000

private const val MEMORY_RECHECK_MILLIS = 10_000L

abstract class TestForkMemory : BuildService<BuildServiceParameters.None>, OperationCompletionListener {

    private val claims = LinkedHashMap<String, Long>()

    private var budget = 0L

    @Synchronized
    fun claimForks(taskPath: String, memoryEachForkNeeds: Long, mostForks: Int, fewestForks: Int): Int {
        val ceiling = maxOf(1, mostForks)
        val giveUpWaitingAt = System.nanoTime() + LONGEST_WAIT_FOR_MEMORY_NANOS
        while (true) {
            if (claims.isEmpty()) {
                budget = (readAvailableMemory() ?: readTotalMemory()) - MEMORY_KEPT_FOR_THE_BUILD_ITSELF
            }
            val wanted = (budget / memoryEachForkNeeds).toInt().coerceIn(1, ceiling)
            val affordable = ((budget - claims.values.sum()) / memoryEachForkNeeds).toInt()
            val waitedLongEnough = System.nanoTime() >= giveUpWaitingAt
            if (claims.isEmpty() || affordable * 2 >= wanted || waitedLongEnough) {
                val forks = affordable.coerceIn(minOf(fewestForks, ceiling), wanted)
                claims[taskPath] = forks * memoryEachForkNeeds
                return forks
            }
            (this as Object).wait(MEMORY_RECHECK_MILLIS)
        }
    }

    @Synchronized
    override fun onFinish(event: FinishEvent) {
        val finished = event.descriptor as? TaskOperationDescriptor ?: return
        if (claims.remove(finished.taskPath) != null) {
            (this as Object).notifyAll()
        }
    }
}

interface BuildEvents {
    @get:Inject
    val registry: BuildEventsListenerRegistry
}

private fun Project.testForkMemory(): Provider<TestForkMemory> {
    val memory = gradle.sharedServices.registerIfAbsent("testForkMemory", TestForkMemory::class.java) {}
    objects.newInstance(BuildEvents::class.java).registry.onTaskCompletion(memory)
    return memory
}

fun Test.takesForksFromMemoryLeft(memoryEachForkNeeds: Long? = null, fewestForks: Int = 1) {
    val memory = project.testForkMemory()
    usesService(memory)
    val taskPath = path
    doFirst {
        val test = this as Test
        val need = memoryEachForkNeeds ?: (heapBytes(test.maxHeapSize) + MEMORY_A_FORK_NEEDS_BEYOND_ITS_HEAP)
        val mostForks = minOf(test.maxParallelForks, forksItsClassesCanUse(test))
        test.maxParallelForks = memory.get().claimForks(taskPath, need, mostForks, fewestForks)
        test.logger.lifecycle("{} runs in {} forks of {} MB", taskPath, test.maxParallelForks, need / MEBIBYTE)
    }
}

private fun forksItsClassesCanUse(test: Test): Int {
    val classes = test.candidateClassFiles.files.count { !it.name.contains('$') }
    return maxOf(1, (classes + TEST_CLASSES_A_FORK_IS_WORTH - 1) / TEST_CLASSES_A_FORK_IS_WORTH)
}

private fun heapBytes(written: String?): Long {
    if (written.isNullOrBlank()) {
        return GRADLES_DEFAULT_TEST_HEAP
    }
    val amount = written.dropLast(1).toLong()
    return when (written.last().lowercaseChar()) {
        'g' -> amount * GIBIBYTE
        'm' -> amount * MEBIBYTE
        'k' -> amount * 1024
        else -> written.toLong()
    }
}

private fun readTotalMemory(): Long =
    (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalMemorySize

private fun readAvailableMemory(): Long? {
    val meminfo = File("/proc/meminfo")
    if (!meminfo.isFile) {
        return null
    }
    return meminfo.readLines()
        .firstOrNull { it.startsWith("MemAvailable:") }
        ?.split(Regex("\\s+"))
        ?.getOrNull(1)
        ?.toLongOrNull()
        ?.times(1024)
}
