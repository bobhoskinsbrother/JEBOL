package org.jebol.devtools;

import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class PackageAndroid extends AppTask {

    private static final String RUNTIME = "runtime/android";
    private static final char QUOTE = '"';
    private static final String BACKSLASH = "\\";

    @Inject
    protected abstract ExecOperations getExec();

    @Internal
    public abstract DirectoryProperty getWorkDirectory();

    @Internal
    public abstract Property<Boolean> getRelease();

    @Internal
    public abstract Property<File> getGradleHome();

    @TaskAction
    public void makePackage() {
        AppDeclaration declaration = declaration();
        if (!Carried.has(RUNTIME)) {
            throw new GradleException("this jebol-devtools carries no Android runtime: it was built on a machine "
                    + "with no Android SDK");
        }
        Path sdk = AndroidSdk.find(System.getenv()).orElseThrow(() -> new GradleException(
                "packageAndroid needs the Android SDK: set ANDROID_HOME to where it is"));
        generate(Platform.ANDROID);
        boolean release = getRelease().get();
        Optional<SigningKey> key = SigningKey.read(appDirectory(), System.getenv());
        if (release && key.isEmpty()) {
            throw new GradleException("a release build is signed with your own key, and there is none: make one with\n  "
                    + SigningKey.KEYTOOL_COMMAND + "\nthen name it in " + SigningKey.FILE + " ("
                    + String.join(", ", SigningKey.FIELDS) + ") or in "
                    + SigningKey.FIELDS.stream().map(SigningKey::variableFor).collect(Collectors.joining(", ")));
        }
        if (!release) {
            getLogger().lifecycle("signed with the debug key, as a dev build is");
        }
        warnIfTracked(SigningKey.FILE);
        key.ifPresent(own -> warnIfTracked(own.storeFile()));

        Path work = getWorkDirectory().get().getAsFile().toPath();
        AppFiles.deleteRecursively(work);
        List<String> names = AppFiles.overlay(declaration, Platform.ANDROID, work.resolve("resources/jebol-app"));
        AppFiles.writeAppJson(names, declaration.entry(), work.resolve("resources/jebol-app/app.json"));
        Carried.copyFolder(RUNTIME, work.resolve("runtime"));
        for (String extension : declaration.requires()) {
            if (Carried.has("runtime/extensions/" + extension + "/android")) {
                Carried.copyFolder("runtime/extensions/" + extension + "/android", work.resolve("runtime"));
            }
        }
        writeResources(declaration, work.resolve("res"));
        Placeholders placeholders = Placeholders.of(declaration)
                .withWritten("androidPermissions", permissions(declaration.uses()));
        FilledFiles.fill(Projects.folderFor(appDirectory(), Platform.ANDROID).resolve("AndroidManifest.xml"),
                work.resolve("AndroidManifest.xml"), placeholders);
        writeProperties(declaration, key, work.resolve("jebol.properties"));

        String variant = release ? "Release" : "Debug";
        runNestedBuild(sdk, work, List.of("assemble" + variant, "bundle" + variant));
        collectOutputs(work.resolve("package"), variant.toLowerCase(Locale.ROOT),
                declaration.identity() + "-" + declaration.version() + "-" + variant.toLowerCase(Locale.ROOT));
    }

    static String permissions(Map<Service, String> uses) {
        return uses.keySet().stream()
                .flatMap(service -> service.androidPermissions().stream())
                .map(permission -> "    <uses-permission android:name=" + QUOTE + permission + QUOTE + " />")
                .collect(Collectors.joining("\n"));
    }

    private static void writeResources(AppDeclaration declaration, Path res) {
        BufferedImage icon = IconImage.read(declaration.icon());
        Optional<BufferedImage> foreground = declaration.iconForeground().map(IconImage::read);
        AdaptiveIcon.DENSITIES.forEach((density, size) -> IconSizes.writePng(
                AdaptiveIcon.foreground(icon, foreground, size), size,
                res.resolve("mipmap-" + density + "/ic_launcher_foreground.png")));
        write(res.resolve("mipmap-anydpi-v26/ic_launcher.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@color/jebol_icon_background" />
                    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
                </adaptive-icon>
                """);
        write(res.resolve("values/jebol.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="jebol_app_name">%s</string>
                    <color name="jebol_icon_background">%s</color>
                </resources>
                """.formatted(androidString(declaration.title()), declaration.colour().hex()));
    }

    private static String androidString(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", BACKSLASH + "'").replace(String.valueOf(QUOTE), BACKSLASH + QUOTE);
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }

    private static void writeProperties(AppDeclaration declaration, Optional<SigningKey> key, Path file) {
        Properties properties = new Properties();
        properties.setProperty("identity", declaration.identity());
        properties.setProperty("version", declaration.version());
        properties.setProperty("build", Integer.toString(declaration.build()));
        properties.setProperty("title", declaration.title());
        key.ifPresent(own -> {
            properties.setProperty("storeFile", own.storeFile());
            properties.setProperty("storePassword", own.storePassword());
            properties.setProperty("keyAlias", own.keyAlias());
            properties.setProperty("keyPassword", own.keyPassword());
        });
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }

    private void warnIfTracked(String file) {
        Path asked = appDirectory().resolve(file);
        if (!Files.exists(asked)) {
            return;
        }
        ByteArrayOutputStream ignored = new ByteArrayOutputStream();
        try {
            var result = getExec().exec(spec -> {
                spec.setExecutable("git");
                spec.setWorkingDir(appDirectory().toFile());
                spec.setArgs(List.of("ls-files", "--error-unmatch", asked.toString()));
                spec.setStandardOutput(ignored);
                spec.setErrorOutput(ignored);
                spec.setIgnoreExitValue(true);
            });
            if (result.getExitValue() == 0) {
                getLogger().warn("warning: " + file + " is tracked by git; a signing key and its passwords belong outside it");
            }
        } catch (RuntimeException noGit) {
            getLogger().info("git could not be asked whether " + file + " is tracked: " + noGit.getMessage());
        }
    }

    private void runNestedBuild(Path sdk, Path work, List<String> tasks) {
        Path project = Projects.folderFor(appDirectory(), Platform.ANDROID);
        File home = getGradleHome().get();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        String gradle = new File(home, windows ? "bin/gradle.bat" : "bin/gradle").getPath();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var result = getExec().exec(spec -> {
            spec.setExecutable(gradle);
            spec.setWorkingDir(project.toFile());
            spec.environment("ANDROID_HOME", sdk.toString());
            spec.args(tasks);
            spec.args("-Pjebol.work=" + work, "--console=plain", "--no-configuration-cache");
            spec.setStandardOutput(output);
            spec.setErrorOutput(output);
            spec.setIgnoreExitValue(true);
        });
        if (result.getExitValue() != 0) {
            throw new GradleException("the app's Android build failed:\n" + output.toString(StandardCharsets.UTF_8));
        }
    }

    private void collectOutputs(Path packages, String variant, String named) {
        Path outputs = Projects.folderFor(appDirectory(), Platform.ANDROID).resolve("build/outputs");
        try {
            Files.createDirectories(packages);
            for (Path folder : List.of(outputs.resolve("apk/" + variant), outputs.resolve("bundle/" + variant))) {
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (Stream<Path> made = Files.list(folder)) {
                    for (Path each : made.filter(path -> path.toString().endsWith(".apk") || path.toString().endsWith(".aab")).toList()) {
                        String ending = each.toString().endsWith(".apk") ? ".apk" : ".aab";
                        Path landed = packages.resolve(named + ending);
                        Files.copy(each, landed, StandardCopyOption.REPLACE_EXISTING);
                        getLogger().lifecycle("made " + landed);
                    }
                }
            }
        } catch (IOException uncollected) {
            throw new UncheckedIOException(uncollected);
        }
    }
}
