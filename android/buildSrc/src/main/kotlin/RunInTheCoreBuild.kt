import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@DisableCachingByDefault(because = "it runs a build of its own")
abstract class RunInTheCoreBuild : DefaultTask() {

    @get:Inject
    abstract val exec: ExecOperations

    @get:Internal
    abstract val gradleExecutable: Property<String>

    @get:Internal
    abstract val coreBuild: DirectoryProperty

    @get:Input
    abstract val tasksToRun: ListProperty<String>

    @TaskAction
    fun runThem() {
        val output = ByteArrayOutputStream()
        val result = exec.exec {
            executable = gradleExecutable.get()
            workingDir = coreBuild.get().asFile
            args(tasksToRun.get() + "--console=plain")
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException("${tasksToRun.get()} in the core build failed:\n$output")
        }
    }
}
