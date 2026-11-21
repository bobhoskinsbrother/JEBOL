import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@DisableCachingByDefault(because = "it runs two builds of their own")
abstract class PackageTheExampleApp : DefaultTask() {

    @get:Inject
    abstract val exec: ExecOperations

    @get:Internal
    abstract val gradleExecutable: Property<String>

    @get:Internal
    abstract val coreBuild: DirectoryProperty

    @get:Internal
    abstract val exampleApp: DirectoryProperty

    @get:OutputFile
    abstract val apk: RegularFileProperty

    @TaskAction
    fun packageIt() {
        runGradle(coreBuild.get().asFile, ":devtools:publishDevtools")
        runGradle(exampleApp.get().asFile, "packageAndroid")
        if (!apk.get().asFile.isFile) {
            throw GradleException("packageAndroid made no ${apk.get().asFile.name}")
        }
    }

    private fun runGradle(where: java.io.File, task: String) {
        val output = ByteArrayOutputStream()
        val result = exec.exec {
            executable = gradleExecutable.get()
            workingDir = where
            args(task, "--console=plain")
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException("$task in $where failed:\n$output")
        }
    }
}
