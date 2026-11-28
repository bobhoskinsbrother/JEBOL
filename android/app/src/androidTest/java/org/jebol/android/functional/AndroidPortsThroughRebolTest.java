package org.jebol.android.functional;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.adapter.android.AndroidHost;
import org.jebol.android.AppSession;
import org.jebol.android.JebolActivity;
import org.jebol.application.Interpreter;
import org.jebol.application.ListedFiles;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.ImagePort;
import org.jebol.tls.LocalTlsServer;
import org.jebol.tls.LocalTlsServer.Identity;
import org.jebol.tls.TestCertificates;
import org.jebol.adapter.host.JavaCertificateAuthority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(300)
class AndroidPortsThroughRebolTest {

    private static final Map<String, String> CARRIED = Map.of(
            "main.reb", "carried main",
            "lib/util.reb", "carried util");

    private static final Instant FIXTURE_TIME = Instant.parse("2030-06-01T12:00:00Z");

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final AtomicBoolean inFront = new AtomicBoolean();
    private static final String TASK_DONE = "the task is done";

    private final PrintedOutput printed = new PrintedOutput();
    private final Path store = AndroidHost.storeFolder(context);

    @BeforeEach
    void emptyTheStore() throws IOException {
        deleteBeneath(store);
        AndroidHost.emptyTemporaryFolder(context);
    }

    @AfterEach
    void leaveTheStoreEmpty() throws IOException {
        deleteBeneath(store);
    }

    private static void deleteBeneath(Path folder) throws IOException {
        try (var held = Files.walk(folder)) {
            for (Path each : held.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (!each.equals(folder)) {
                    Files.delete(each);
                }
            }
        }
    }

    private HostPorts appPorts() {
        return AndroidHost.appPorts(context,
                ListedFiles.of(List.copyOf(CARRIED.keySet()), name -> CARRIED.get(name).getBytes(StandardCharsets.UTF_8)),
                inFront::get, AppSession.appBounds());
    }

    private Interpreter app() {
        return Interpreter.createOn(appPorts(), printed, AppSession.appBounds());
    }

    private static String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a file a script writes lands in the app's private store, and another interpreter reads it back")
    void filesLandInThePrivateStore() throws IOException {
        assertThat(run(app(), "write %notes.txt {kept} make-dir %folder/ write %folder/inner.txt {deeper} true"))
                .isEqualTo("#(true)");

        assertThat(Files.readAllBytes(store.resolve("notes.txt"))).isEqualTo("kept".getBytes(StandardCharsets.UTF_8));
        assertThat(store.startsWith(context.getFilesDir().toPath())).isTrue();
        assertThat(run(app(), "{deeper} = read/string %folder/inner.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the carried files read beneath the store, and a script's own file of the same name shadows one")
    void carriedFilesReadBeneath() {
        Interpreter interpreter = app();

        assertThat(run(interpreter, "{carried util} = read/string %lib/util.reb")).isEqualTo("#(true)");
        assertThat(run(interpreter, "write %main.reb {mine} {mine} = read/string %main.reb")).isEqualTo("#(true)");
        assertThat(run(interpreter, "sort read %/")).contains("%lib/", "%main.reb", "%tmp/");
        assertThat(run(interpreter, "e: try [delete %lib/util.reb] e/id = 'read-only")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("home is the root, as system/options/home, HOME and the working directory all say")
    void homeIsTheRoot() {
        Interpreter interpreter = app();

        assertThat(run(interpreter, "system/options/home")).isEqualTo("%/");
        assertThat(run(interpreter, "{/} = get-env {HOME}")).isEqualTo("#(true)");
        assertThat(run(interpreter, "what-dir")).isEqualTo("%/");
    }

    @Test
    @DisplayName("the environment is the app's own: USER is its identifier and none of the device's variables show")
    void theEnvironmentIsTheAppsOwn() {
        Interpreter interpreter = app();

        assertThat(run(interpreter, "(get-env {USER}) = {" + context.getPackageName() + "}")).isEqualTo("#(true)");
        assertThat(run(interpreter, "none? get-env {ANDROID_DATA}")).isEqualTo("#(true)");
        assertThat(run(interpreter, "'dir = exists? to-rebol-file get-env {TMPDIR}")).isEqualTo("#(true)");
        assertThat(run(interpreter, "set-env {GREETING} {hello} {hello} = get-env {GREETING}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a picture saves and loads as PNG, GIF and BMP, WebP through IMAGE, and TIFF is a format the codec has not got")
    void picturesSaveAndLoad() {
        Interpreter interpreter = app();
        run(interpreter, "img: make image! [2x2 #{C800000000C800C800FFFFFF}]");

        for (String format : new String[] {"png", "gif", "bmp"}) {
            assertThat(run(interpreter, "img = load save %pic." + format + " img")).as(format).isEqualTo("#(true)");
        }
        assertThat(run(interpreter, "image? load save %pic.jpg img")).isEqualTo("#(true)");
        assertThat(run(interpreter, "img = image/load/as image/save/as none img 'WEBP 'WEBP")).isEqualTo("#(true)");
        assertThat(run(interpreter, "error? try [image/save/as none img 'TIFF]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the clipboard read from the background answers none")
    void theClipboardFromTheBackgroundIsNone() {
        inFront.set(false);

        assertThat(run(app(), "none? read clipboard://")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the clipboard read in front gives back what the script wrote")
    void theClipboardInFrontReadsBack() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        wakeAndPassSwipeScreen(instrumentation);
        Activity shown = instrumentation.startActivitySync(new Intent(context, JebolActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            assertThat(focusArrives(instrumentation, shown).await(20, TimeUnit.SECONDS))
                    .as(() -> "JebolActivity took the window's focus; the screen is "
                            + (context.getSystemService(PowerManager.class).isInteractive() ? "on" : "off")
                            + ", and the window in front is " + shell(instrumentation, "dumpsys window").lines()
                                    .filter(line -> line.contains("mCurrentFocus"))
                                    .findFirst()
                                    .orElse("not named").trim())
                    .isTrue();
            inFront.set(true);

            assertThat(run(app(), "write clipboard:// {copied on Android} {copied on Android} = read clipboard://"))
                    .isEqualTo("#(true)");
        } finally {
            shown.finish();
        }
    }

    private static void wakeAndPassSwipeScreen(Instrumentation instrumentation) {
        shell(instrumentation, "input keyevent KEYCODE_WAKEUP");
        shell(instrumentation, "wm dismiss-keyguard");
    }

    private static String shell(Instrumentation instrumentation, String command) {
        try (InputStream answered = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(command))) {
            return new String(answered.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unread) {
            throw new UncheckedIOException("the shell did not answer " + command, unread);
        }
    }

    private static CountDownLatch focusArrives(Instrumentation instrumentation, Activity shown) {
        CountDownLatch focused = new CountDownLatch(1);
        instrumentation.runOnMainSync(() -> {
            View decor = shown.getWindow().getDecorView();
            decor.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus -> {
                if (hasFocus) {
                    focused.countDown();
                }
            });
            if (decor.hasWindowFocus()) {
                focused.countDown();
            }
        });
        return focused;
    }

    @Test
    @DisplayName("CALL refuses as a page's does, naming processes, and system/options/boot is none")
    void callRefuses() {
        Interpreter interpreter = app();

        assertThat(run(interpreter, "e: try [call {ls}] all [e/id = 'no-service e/arg1 = 'processes]"))
                .isEqualTo("#(true)");
        assertThat(run(interpreter, "none? system/options/boot")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a tcp connection reaches a server on this device")
    void tcpReachesALocalServer() throws Exception {
        AtomicInteger taken = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}))) {
            Thread serving = new Thread(() -> {
                try (Socket client = server.accept(); OutputStream out = client.getOutputStream()) {
                    taken.incrementAndGet();
                    out.write("from the device".getBytes(StandardCharsets.US_ASCII));
                } catch (IOException closed) {
                    return;
                }
            });
            serving.setDaemon(true);
            serving.start();

            String answer = run(app(), """
                    got: copy #{}
                    port: open tcp://127.0.0.1:%d
                    port/awake: func [event] [
                        switch event/type [
                            lookup [open event/port]
                            connect [read event/port]
                            read [append got event/port/data clear event/port/data read event/port]
                            close [return true]
                        ]
                        false
                    ]
                    wait [port 30]
                    close port
                    {from the device} = to string! got""".formatted(server.getLocalPort()));

            assertThat(answer).isEqualTo("#(true)");
            assertThat(taken).hasValue(1);
        }
    }

    @Test
    @DisplayName("plain http to a host Android's policy does not name is refused before anything is sent")
    void plainHttpIsRefusedByThePolicy() {
        assertThat(run(app(), "e: try [read http://127.0.0.1:9/] all [e/id = 'no-connect to logic! find e/arg2 {policy}]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a TLS server the system's authorities do not vouch for is refused, and one whose root is trusted answers")
    void tlsTrustIsTheSystems() throws Exception {
        TestCertificates fixtures = new TestCertificates();
        Identity identity = new Identity(fixtures.chain("localhost-rsa", "test-intermediate"),
                fixtures.readTheKey("localhost-rsa-key", "RSA"));
        String open = """
                reason: none body: none
                secure: open tls://localhost:%d
                secure/awake: func [event] [
                    switch event/type [
                        connect [write event/port {GET / HTTP/1.0^M^/^M^/}]
                        wrote [read event/port]
                        read [read event/port]
                        error [reason: event/port/extra/error/id return true]
                        close [body: to string! event/port/data return true]
                    ]
                    false
                ]
                wait [secure 60]
                close secure
                either reason [reason] [to logic! find body {checked connection}]""";
        try (LocalTlsServer server = new LocalTlsServer(identity, "TLSv1.3")) {
            assertThat(run(app(), open.formatted(server.port()))).isNotEqualTo("#(true)");

            Interpreter trusting = Interpreter.createOn(appPorts().withCertificateAuthority(new JavaCertificateAuthority(
                    fixtures.trustOnly("test-root"), Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC))),
                    printed, AppSession.appBounds());
            assertThat(run(trusting, open.formatted(server.port()))).isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("a task runs on the app's own ports, so what it writes is in the store")
    void aTaskRunsOnTheAppsPorts() throws Exception {
        run(app(), "do make task! [write %from-a-task.txt {the task wrote this} print {" + TASK_DONE + "}]");

        assertThat(printed.awaitPrinted(TASK_DONE, Duration.ofSeconds(30)))
                .as(() -> "the task printed that it was done; it printed: " + printed.text())
                .isTrue();
        assertThat(Files.readAllBytes(store.resolve("from-a-task.txt")))
                .isEqualTo("the task wrote this".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a script that grows past the app's memory ceiling gets REBOL's no-memory error, and the process lives")
    void growingPastTheCeiling() {
        Interpreter interpreter = app();

        assertThat(run(interpreter, """
                held: copy []
                e: try [forever [append/only held array/initial 131072 0]]
                all [error? e e/id = 'no-memory]""")).isEqualTo("#(true)");
        assertThat(run(interpreter, "held: none 1 + 2")).isEqualTo("3");
        assertThat(AndroidHost.memoryCeiling()).isLessThan(Runtime.getRuntime().maxMemory());
    }

    @Test
    @DisplayName("the platform hands the core's tests Android's own picture codec")
    void thePlatformGivesAndroidsCodec() {
        ImagePort images = org.jebol.application.PlatformUnderTest.current().images();

        assertThat(images.hasImageType("webp")).isTrue();
    }
}
