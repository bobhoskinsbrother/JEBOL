package org.jebol.devtools;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(900)
class TheAndroidPackageTest {

    private static final String APK = "build/jebol/android/package/com.example.notes-1.2.3-debug.apk";
    private static final String BUNDLE = "build/jebol/android/package/com.example.notes-1.2.3-debug.aab";

    @TempDir
    static Path shared;

    private static AnAppBuild complete;
    private static BuildResult made;

    @BeforeAll
    static void packageTheCompleteApp() {
        complete = AnAppBuild.of(ATestApp.completeIn(shared.resolve("complete")));
        made = complete.run("packageAndroid");
    }

    private static Path buildTools() throws IOException {
        Path sdk = AndroidSdk.find(System.getenv()).orElseThrow();
        try (Stream<Path> versions = Files.list(sdk.resolve("build-tools"))) {
            return versions.max(Comparator.comparing(path -> path.getFileName().toString())).orElseThrow();
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process running = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(running.waitFor()).as(String.join(" ", command) + "\n" + output).isZero();
        return output;
    }

    @Test
    @DisplayName("the APK carries the identity, the version name and code, and the label from app.reb, and a bundle is made beside it")
    void theApkCarriesTheDeclaration() throws Exception {
        String badging = run(buildTools().resolve("aapt2").toString(), "dump", "badging",
                complete.directory().resolve(APK).toString());

        assertThat(badging)
                .contains("package: name='com.example.notes' versionCode='7' versionName='1.2.3'")
                .contains("application-label:'Notes'")
                .contains("launchable-activity: name='org.jebol.android.JebolActivity'");
        assertThat(complete.directory().resolve(BUNDLE)).isRegularFile();
        assertThat(made.getOutput()).contains("signed with the debug key");
    }

    @Test
    @DisplayName("each use app.reb declares becomes the permissions Android asks for it")
    void eachUseBecomesItsPermissions() throws Exception {
        String badging = run(buildTools().resolve("aapt2").toString(), "dump", "badging",
                complete.directory().resolve(APK).toString());

        assertThat(badging)
                .contains("uses-permission: name='android.permission.POST_NOTIFICATIONS'")
                .contains("uses-permission: name='android.permission.CAMERA'")
                .doesNotContain("ACCESS_FINE_LOCATION");
    }

    @Test
    @DisplayName("an extension app.reb requires is carried with its Android setup")
    void aRequiredExtensionIsCarried() throws Exception {
        String listed = run("unzip", "-l", complete.directory().resolve(APK).toString());

        assertThat(listed).contains("META-INF/jebol/extension.reb").contains("org/jebol/extension/sqlite/sqlite-scheme.reb");
    }

    @Test
    @DisplayName("a release build with no key of the developer's own refuses, giving the keytool command")
    void aReleaseWithNoKeyRefuses(@TempDir Path directory) {
        BuildResult refused = AnAppBuild.of(ATestApp.smallestIn(directory)).fail("packageAndroid", "-Pjebol.release=true");

        assertThat(refused.getOutput())
                .contains("a release build is signed with your own key")
                .contains("keytool -genkeypair")
                .contains("android-signing.properties");
    }

    @Test
    @DisplayName("a release build signs with the developer's key, named in the environment, and warns when the signing file is tracked by git")
    void aReleaseSignsWithTheDevelopersKey(@TempDir Path directory) throws Exception {
        ATestApp app = ATestApp.smallestIn(directory);
        Path keystore = directory.resolve("keys/upload.jks");
        Files.createDirectories(keystore.getParent());
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        run(keytool, "-genkeypair", "-keystore", keystore.toString(), "-storepass", "store-secret",
                "-alias", "upload", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "365", "-dname", "CN=Notes Test Key");
        app.withFile(SigningKey.FILE, "storeFile=keys/upload.jks\n");
        run("git", "-C", directory.toString(), "init", "-q");
        run("git", "-C", directory.toString(), "add", SigningKey.FILE);
        AnAppBuild build = AnAppBuild.of(app);

        BuildResult signed = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withPluginClasspath()
                .withArguments("packageAndroid", "-Pjebol.release=true")
                .withEnvironment(withSigning(Map.of(
                        "JEBOL_ANDROID_STORE_PASSWORD", "store-secret",
                        "JEBOL_ANDROID_KEY_ALIAS", "upload",
                        "JEBOL_ANDROID_KEY_PASSWORD", "store-secret")))
                .build();

        assertThat(signed.getOutput()).contains(SigningKey.FILE + " is tracked by git");
        String certificates = run(buildTools().resolve("apksigner").toString(), "verify", "--print-certs",
                build.directory().resolve("build/jebol/android/package/com.example.notes-1.2.3-release.apk").toString());
        assertThat(certificates).contains("CN=Notes Test Key");
    }

    private static Map<String, String> withSigning(Map<String, String> signing) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        environment.putAll(signing);
        return environment;
    }

    @Test
    @DisplayName("the generated android/ project holds no shared value: the identity stays in app.reb")
    void theProjectHoldsNoSharedValue() throws IOException {
        List<String> buildScript = Files.readAllLines(complete.directory().resolve("android/build.gradle.kts"));

        assertThat(buildScript).noneMatch(line -> line.contains("com.example.notes"));
    }
}
