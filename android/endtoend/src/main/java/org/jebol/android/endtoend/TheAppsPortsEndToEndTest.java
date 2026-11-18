package org.jebol.android.endtoend;

import android.os.Build;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TheAppsPortsEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private static final String EMULATORS_OWN_HARDWARE = "ranchu";

    @Test
    @DisplayName("what a script saves is there after Android ends the app")
    void aSavedFileOutlivesTheProcess() {
        String kept = UUID.randomUUID().toString();
        TheApp saving = TheApp.launchedAfresh("write %saved-across-processes.txt {" + kept + "}" + """

                print "saved"
                """);
        saving.waitForLineStarting("saved", PATIENCE);
        saving.endProcess();

        TheApp reading = TheApp.launchedAfresh("""
                print rejoin ["read " read/string %saved-across-processes.txt]
                delete %saved-across-processes.txt
                """);

        assertThat(reading.waitForLineStarting("read ", PATIENCE)).isEqualTo("read " + kept);
        assertThat(reading.printedAsErrors()).isEmpty();
    }

    @Test
    @DisplayName("a script reaches a server on the host over tcp://, http:// and tls:// with a root the test supplies")
    void aScriptReachesTheHost() throws IOException {
        Bundle given = InstrumentationRegistry.getArguments();
        assumeTrue(Build.HARDWARE.equals(EMULATORS_OWN_HARDWARE), "10.0.2.2 is the host only from an emulator");
        assumeTrue(given.getString("hostPlainPort") != null, "the gate starts the host's servers");
        String plain = given.getString("hostPlainPort");
        String secure = given.getString("hostSecurePort");

        TheApp app = TheApp.launchedAfreshTrusting("""
                said: func [text] [either find text {from the host} ["reached"] [text]]
                reply: copy #{}
                port: open tcp://10.0.2.2:%1$s
                port/awake: func [event] [
                    switch event/type [
                        lookup [open event/port]
                        connect [write event/port {GET / HTTP/1.0^M^J^M^J}]
                        wrote [read event/port]
                        read [append reply event/port/data clear event/port/data read event/port]
                        close [return true]
                    ]
                    false
                ]
                wait [port 30]
                close port
                print rejoin ["tcp " said to string! reply]
                print rejoin ["http " said either error? e: try [read/string http://10.0.2.2:%1$s/] [form e/id] [e]]
                print rejoin ["tls " said either error? e: try [read/string https://10.0.2.2:%2$s/] [form e/id] [e]]
                """.formatted(plain, secure), readEmulatorTestRoot());

        assertThat(app.waitForLineStarting("tcp ", PATIENCE)).isEqualTo("tcp reached");
        assertThat(app.waitForLineStarting("http ", PATIENCE)).isEqualTo("http reached");
        assertThat(app.waitForLineStarting("tls ", PATIENCE)).isEqualTo("tls reached");
    }

    private static String readEmulatorTestRoot() throws IOException {
        try (InputStream carried = TheAppsPortsEndToEndTest.class.getResourceAsStream("/emulator-host/emulator-test-root.pem")) {
            assertThat(carried).as("the emulator's test root in the test APK").isNotNull();
            return new String(carried.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }
}
