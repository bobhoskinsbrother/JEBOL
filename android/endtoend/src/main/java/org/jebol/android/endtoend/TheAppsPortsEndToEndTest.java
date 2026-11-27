package org.jebol.android.endtoend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TheAppsPortsEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

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
        String host = TheHost.address();
        String plain = TheHost.port("hostPlainPort");
        String secure = TheHost.port("hostSecurePort");

        TheApp app = TheApp.launchedAfreshTrusting("""
                said: func [text] [either find text {from the host} ["reached"] [text]]
                reply: copy #{}
                port: open tcp://%3$s:%1$s
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
                print rejoin ["http " said either error? e: try [read/string http://%3$s:%1$s/] [form e/id] [e]]
                print rejoin ["tls " said either error? e: try [read/string https://%3$s:%2$s/] [form e/id] [e]]
                """.formatted(plain, secure, host), readDeviceTestRoot());

        assertThat(app.waitForLineStarting("tcp ", PATIENCE)).isEqualTo("tcp reached");
        assertThat(app.waitForLineStarting("http ", PATIENCE)).isEqualTo("http reached");
        assertThat(app.waitForLineStarting("tls ", PATIENCE)).isEqualTo("tls reached");
    }

    private static String readDeviceTestRoot() throws IOException {
        try (InputStream carried = TheAppsPortsEndToEndTest.class.getResourceAsStream("/device-host/device-test-root.pem")) {
            assertThat(carried).as("the device's test root in the test APK").isNotNull();
            return new String(carried.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }
}
