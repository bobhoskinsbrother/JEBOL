import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class WriteExtensionDescriptor : DefaultTask() {

    @get:Input
    abstract val extensionName: Property<String>

    @get:Input
    abstract val extensionVersion: Property<String>

    @get:Input
    abstract val jvmProvider: Property<String>

    @get:Input
    @get:Optional
    abstract val webProvider: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val jvmLibraries: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val webAssets: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun write() {
        val descriptor = destination.get().file("META-INF/jebol/extension.reb").asFile
        descriptor.parentFile.mkdirs()
        val lines = mutableListOf(
            "name: ${extensionName.get()}",
            "version: ${extensionVersion.get()}",
            "setups: [",
            "    jvm [",
            "        provider: ${jvmProvider.get()}",
            "        needs: [${filesAsRebol(jvmLibraries)}]",
            "    ]",
        )
        if (webProvider.isPresent) {
            lines += listOf(
                "    web [",
                "        provider: ${webProvider.get()}",
                "        assets: [${filesAsRebol(webAssets)}]",
                "    ]",
            )
        }
        lines += "]"
        descriptor.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun filesAsRebol(files: ConfigurableFileCollection): String =
        files.files.map { "%" + it.name }.sorted().joinToString(" ")
}
