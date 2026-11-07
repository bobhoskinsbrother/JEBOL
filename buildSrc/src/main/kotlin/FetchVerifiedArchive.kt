import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipInputStream

abstract class FetchVerifiedArchive : DefaultTask() {

    @get:Input
    abstract val address: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Input
    abstract val entries: ListProperty<String>

    @get:Internal
    abstract val download: RegularFileProperty

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun fetch() {
        val archive = download.get().asFile
        if (!archive.isFile || digest(archive.readBytes()) != sha256.get()) {
            archive.parentFile.mkdirs()
            URI(address.get()).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
        }
        val found = digest(archive.readBytes())
        if (found != sha256.get()) {
            archive.delete()
            throw GradleException("${address.get()} has SHA-256 $found, not the pinned ${sha256.get()}")
        }
        val wanted = entries.get().toSet()
        val written = mutableSetOf<String>()
        val folder = destination.get().asFile
        folder.deleteRecursively()
        folder.mkdirs()
        ZipInputStream(archive.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name in wanted) {
                    folder.resolve(entry.name.substringAfterLast('/')).writeBytes(zip.readBytes())
                    written += entry.name
                }
            }
        }
        if (written != wanted) {
            throw GradleException("${address.get()} has none of ${wanted - written}")
        }
    }

    private fun digest(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
