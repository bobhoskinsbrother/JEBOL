import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType

// CarriedFixtures reads every jebol-fixtures.txt on the classpath to know what
// a project's fixtures carry, since a resource folder cannot be listed inside a
// jar or an APK.
fun Project.writesItsFixtureIndex() {
    val fixturesRoot = file("src/testFixtures/resources")
    val fixtures = fileTree(fixturesRoot)
    val index = layout.buildDirectory.file("generated/fixture-index/jebol-fixtures.txt")
    val writeTheFixtureIndex = tasks.register("writeTheFixtureIndex") {
        group = "build"
        description = "List every file the test fixtures carry, for CarriedFixtures"
        inputs.files(fixtures).withPropertyName("fixtureFiles")
        outputs.file(index)
        doLast {
            index.get().asFile.parentFile.mkdirs()
            index.get().asFile.writeText(fixtures.files
                .map { it.relativeTo(fixturesRoot).invariantSeparatorsPath }
                .sorted()
                .joinToString("\n"))
        }
    }
    extensions.getByType<SourceSetContainer>().getByName("testFixtures").resources
        .srcDir(layout.buildDirectory.dir("generated/fixture-index"))
    tasks.named("processTestFixturesResources") {
        dependsOn(writeTheFixtureIndex)
    }
}
