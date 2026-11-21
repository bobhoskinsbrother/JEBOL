import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.SourceSet

const val CORE_JAVA = 21
const val DESKTOP_JAVA = 25

fun Project.resolvesForDesktopJava(suite: SourceSet) {
    configurations.named(suite.runtimeClasspathConfigurationName) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, DESKTOP_JAVA)
    }
}
