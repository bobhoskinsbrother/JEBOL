import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property

abstract class JebolExtensionDeclaration {

    abstract val name: Property<String>

    abstract val version: Property<String>

    abstract val jvmProvider: Property<String>

    abstract val webProvider: Property<String>

    abstract val webAssets: ConfigurableFileCollection
}
