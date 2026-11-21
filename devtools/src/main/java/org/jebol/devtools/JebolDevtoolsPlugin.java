package org.jebol.devtools;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class JebolDevtoolsPlugin implements Plugin<Project> {

    public static final int DESKTOP_JAVA = 25;

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("jvm-toolchains");
        JebolExtension extension = project.getExtensions().create("jebol", JebolExtension.class);
        extension.getAppDirectory().convention(project.getLayout().getProjectDirectory());
        extension.getRelease().convention(project.getProviders().gradleProperty("jebol.release")
                .map(Boolean::parseBoolean).orElse(false));
        Provider<Directory> work = project.getLayout().getBuildDirectory().dir("jebol");
        Provider<JavaLauncher> jdk = project.getExtensions().getByType(JavaToolchainService.class)
                .launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(DESKTOP_JAVA)));

        project.getTasks().register("generateProjects", GenerateProjects.class, task -> {
            describe(task, "Write a project for each platform into the app's directory, only where it is absent");
            task.getAppDirectory().set(extension.getAppDirectory());
        });
        TaskProvider<PackageWeb> web = project.getTasks().register("packageWeb", PackageWeb.class, task -> {
            describe(task, "Make the app's site: its scripts, JEBOL's page and runtime, the manifest and its icons");
            task.getAppDirectory().set(extension.getAppDirectory());
            task.getSite().set(work.map(directory -> directory.dir("web/site")));
        });
        TaskProvider<PackageDeb> deb = registerDesktop(project, "packageDeb", PackageDeb.class, extension, work, jdk,
                "Make the app's .deb with jpackage, carrying its own Java runtime");
        TaskProvider<PackageMac> mac = registerDesktop(project, "packageMac", PackageMac.class, extension, work, jdk,
                "Make the app's .app with jpackage, its usage strings in Info.plist, signed ad hoc");
        TaskProvider<PackageWindows> windows = registerDesktop(project, "packageWindows", PackageWindows.class,
                extension, work, jdk, "Make the app's installer and portable app image with jpackage");
        TaskProvider<PackageAndroid> android = project.getTasks().register("packageAndroid", PackageAndroid.class, task -> {
            describe(task, "Make the app's APK and bundle from its android/ project, with the Android Gradle plugin");
            task.getAppDirectory().set(extension.getAppDirectory());
            task.getWorkDirectory().set(work.map(directory -> directory.dir("android")));
            task.getRelease().set(extension.getRelease());
            task.getGradleHome().set(project.getGradle().getGradleHomeDir());
        });

        Set<String> skipped = Arrays.stream(project.getProviders().gradleProperty("jebol.skip").getOrElse("").split(","))
                .map(String::trim).map(name -> name.toLowerCase(Locale.ROOT)).filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
        Platform.Machine here = Platform.Machine.runningHere(System.getProperty("os.name", ""));
        project.getTasks().register("packageAll", task -> {
            task.setGroup("jebol");
            task.setDescription("Make every package this machine can build");
            if (!skipped.contains(Platform.WEB.folder())) {
                task.dependsOn(web);
            }
            if (!skipped.contains(Platform.ANDROID.folder()) && AndroidSdk.find(System.getenv()).isPresent()) {
                task.dependsOn(android);
            }
            switch (here) {
                case LINUX -> dependUnlessSkipped(task, deb, Platform.LINUX, skipped);
                case MAC -> dependUnlessSkipped(task, mac, Platform.MACOS, skipped);
                case WINDOWS -> dependUnlessSkipped(task, windows, Platform.WINDOWS, skipped);
                case ANY -> {
                }
            }
        });
    }

    private static void dependUnlessSkipped(Task task, TaskProvider<?> packaging, Platform platform,
                                            Set<String> skipped) {
        if (!skipped.contains(platform.folder())) {
            task.dependsOn(packaging);
        }
    }

    private static <T extends DesktopPackage> TaskProvider<T> registerDesktop(Project project, String name, Class<T> type,
            JebolExtension extension, Provider<Directory> work, Provider<JavaLauncher> jdk, String description) {
        return project.getTasks().register(name, type, task -> {
            describe(task, description);
            task.getAppDirectory().set(extension.getAppDirectory());
            task.getWorkDirectory().set(work.map(directory -> directory.dir(task.platform().folder())));
            task.getJdk().set(jdk);
        });
    }

    private static void describe(AppTask task, String description) {
        task.setGroup("jebol");
        task.setDescription(description);
        task.doNotTrackState("reads the app's whole directory, and app.reb decides what it makes");
    }
}
