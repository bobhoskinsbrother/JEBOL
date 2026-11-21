package org.jebol.devtools;

import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class DesktopPackage extends AppTask {

    public static final String CARRIED_FOLDER = "jebol-app";
    public static final String MAIN_JAR = "jebol.jar";
    public static final String MAIN_CLASS = "org.jebol.adapter.cli.JebolLauncher";
    public static final List<String> RUNTIME_MODULES = List.of("java.base", "java.desktop", "java.datatransfer",
            "jdk.httpserver", "jdk.charsets", "jdk.crypto.ec");

    protected abstract Platform platform();

    @Inject
    protected abstract ExecOperations getExec();

    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    @Internal
    public abstract Property<JavaLauncher> getJdk();

    @TaskAction
    public void makePackage() {
        Platform platform = platform();
        Platform.Machine here = Platform.Machine.runningHere(System.getProperty("os.name", ""));
        if (!platform.canBuildOn(here)) {
            throw new GradleException(getName() + " builds only on " + platform.buildsOn().named()
                    + ", and this is " + here.named());
        }
        AppDeclaration declaration = declaration();
        generate(platform);
        Path work = getWorkDirectory().get().getAsFile().toPath();
        AppFiles.deleteRecursively(work);
        Path input = work.resolve("input");
        Path resources = work.resolve("resources");
        Path packages = work.resolve("package");
        AppFiles.overlay(declaration, platform, input.resolve(CARRIED_FOLDER));
        Carried.copy("runtime/desktop/" + MAIN_JAR, input.resolve(MAIN_JAR));
        for (String extension : declaration.requires()) {
            Carried.copyFolder("runtime/extensions/" + extension + "/jvm", input);
        }
        Path runtime = linkRuntime(declaration, work.resolve("runtime"));
        Placeholders placeholders = placeholdersFor(declaration);
        fillProject(placeholders, resources, input);
        writeIcons(declaration, resources);
        List<String> arguments = new ArrayList<>(List.of(
                "--name", launcherName(declaration),
                "--app-version", declaration.version(),
                "--vendor", declaration.publisher(),
                "--description", declaration.summary(),
                "--input", input.toString(),
                "--main-jar", MAIN_JAR,
                "--main-class", MAIN_CLASS,
                "--arguments", launchArguments(declaration),
                "--runtime-image", runtime.toString(),
                "--resource-dir", resources.toString(),
                "--dest", packages.toString()));
        for (List<String> each : platformArgumentSets(declaration, resources)) {
            List<String> run = new ArrayList<>(arguments);
            run.addAll(each);
            run(tool("jpackage"), run);
        }
        finishPackages(declaration, packages);
        try (Stream<Path> made = Files.list(packages)) {
            made.forEach(each -> getLogger().lifecycle("made " + each));
        } catch (IOException unlisted) {
            throw new UncheckedIOException(unlisted);
        }
    }

    protected String launcherName(AppDeclaration declaration) {
        return Placeholders.launcherName(declaration);
    }

    protected Placeholders placeholdersFor(AppDeclaration declaration) {
        return Placeholders.of(declaration);
    }

    protected abstract List<List<String>> platformArgumentSets(AppDeclaration declaration, Path resources);

    protected static List<String> aboutUrl(AppDeclaration declaration) {
        return declaration.website().map(website -> List.of("--about-url", website)).orElse(List.of());
    }

    protected static List<String> joined(List<String> first, List<String> second) {
        List<String> both = new ArrayList<>(first);
        both.addAll(second);
        return both;
    }

    protected abstract void writeIcons(AppDeclaration declaration, Path resources);

    protected void fillProject(Placeholders placeholders, Path resources, Path input) {
        Path project = Projects.folderFor(appDirectory(), platform());
        try (Stream<Path> walked = Files.walk(project)) {
            for (Path each : walked.filter(Files::isRegularFile).toList()) {
                Path relative = project.relativize(each);
                if (!relative.startsWith(Projects.OVERLAY_FOLDER)) {
                    FilledFiles.fill(each, resources.resolve(relative.toString()), placeholders);
                }
            }
        } catch (IOException unlisted) {
            throw new UncheckedIOException(unlisted);
        }
    }

    protected void finishPackages(AppDeclaration declaration, Path packages) {
    }

    private static String launchArguments(AppDeclaration declaration) {
        return String.join(" ", List.of("--packaged", declaration.identity(),
                "--carried", "$APPDIR/" + CARRIED_FOLDER, "--screen=window", quotedIfSpaced(declaration.entry())));
    }

    private static String quotedIfSpaced(String argument) {
        return argument.contains(" ") ? '"' + argument + '"' : argument;
    }

    private Path linkRuntime(AppDeclaration declaration, Path runtime) {
        Set<String> modules = new LinkedHashSet<>(RUNTIME_MODULES);
        if (declaration.requires().contains("sqlite")) {
            modules.add("java.sql");
        }
        run(tool("jlink"), List.of("--add-modules", String.join(",", modules), "--strip-debug", "--no-header-files",
                "--no-man-pages", "--output", runtime.toString()));
        return runtime;
    }

    protected String tool(String name) {
        Path bin = getJdk().get().getMetadata().getInstallationPath().getAsFile().toPath().resolve("bin");
        Path windowsTool = bin.resolve(name + ".exe");
        return (Files.isRegularFile(windowsTool) ? windowsTool : bin.resolve(name)).toString();
    }

    protected void run(String executable, List<String> arguments) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var result = getExec().exec(spec -> {
            spec.setExecutable(executable);
            spec.setArgs(arguments);
            spec.setStandardOutput(output);
            spec.setErrorOutput(output);
            spec.setIgnoreExitValue(true);
        });
        if (result.getExitValue() != 0) {
            throw new GradleException(Path.of(executable).getFileName() + " failed:\n"
                    + output.toString(StandardCharsets.UTF_8));
        }
    }
}
