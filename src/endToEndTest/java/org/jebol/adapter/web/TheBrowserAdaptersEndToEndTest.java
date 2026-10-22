package org.jebol.adapter.web;

import org.jebol.application.TheCarriedFont;
import org.jebol.domain.host.JvmDigests;
import org.jebol.domain.render.Colour;
import org.jebol.domain.render.TextExtent;
import org.jebol.domain.render.TextRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheBrowserAdaptersEndToEndTest {

    @TempDir
    Path downloads;

    private ShippedRunner servingTheScript(ShippedRunner runner) {
        return runner
                .servingFile("/hello", "hello".getBytes(StandardCharsets.UTF_8), "text/plain")
                .serving("/missing", exchange -> runner.respond(exchange,
                        "gone".getBytes(StandardCharsets.UTF_8), "text/plain", 404))
                .running("""
                        the-url: to url! rejoin [{THE-ORIGIN} "/hello"]
                        print read the-url
                        print (read/binary the-url) = #{68656C6C6F}
                        if exists? %kept.txt [print rejoin ["again " read/string %kept.txt]]
                        write %kept.txt "kept"
                        print read/string %kept.txt
                        print input
                        print error? try [read tcp://127.0.0.1:1]
                        page: to url! rejoin [{THE-ORIGIN} "/missing"]
                        e: try [read page]
                        print e/id
                        print mold checksum "abc" 'sha1
                        print mold checksum/with "abc" 'sha1 "key"
                        sized: make gob! [size: 200x40]
                        sized/text: "MMMM"
                        print size-text sized
                        print ["clock" date? now]
                        print "done"
                        """);
    }

    @Test
    @DisplayName("a script on the shipped page fetches, keeps a file, reads a line typed into the form, tells the time, measures text, and is refused the rest")
    void letsTheScriptReachTheOutside() throws IOException {
        try (ShippedRunner runner = servingTheScript(new ShippedRunner());
             AnOperatorAtThePage operator = new AnOperatorAtThePage("chrome", runner, downloads).opensThePage()) {

            operator.waitUntilOutputContains("kept");
            operator.types("typed-here");
            operator.pressesEnter();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).containsSubsequence(
                    "hello",
                    "true",
                    "kept",
                    "typed-here",
                    "true",
                    "protocol");
            assertThat(operator.readOutput())
                    .as("the worker's SHA-1 is the same digest the JVM takes")
                    .contains("#{" + writeHexOf("abc") + "}");
            assertThat(operator.readOutput())
                    .as("and its HMAC is the same keyed digest the JVM takes")
                    .contains("#{" + writeKeyedHexOf("abc") + "}");
            assertThat(operator.readOutput())
                    .as("the worker measured with the font this build carries")
                    .contains(measureLabelsText());
            assertThat(operator.readOutput())
                    .as("the page grants the clock")
                    .contains("clock true");

            operator.reloadsThePage();
            operator.waitUntilOutputContains("again kept");
        }
    }

    private String measureLabelsText() {
        TextExtent extent = TheCarriedFont.measurement().textExtent(
                new TextRun("MMMM", Colour.BLACK, 12, false, false));
        return Math.round(extent.wide()) + "x" + Math.round(extent.high());
    }

    private String writeHexOf(String text) {
        return encodeAsHex(new JvmDigests().digestOf("sha1", text.getBytes(StandardCharsets.UTF_8)));
    }

    private String writeKeyedHexOf(String text) {
        return encodeAsHex(new JvmDigests().digestWithKey(
                "sha1", text.getBytes(StandardCharsets.UTF_8), "key".getBytes(StandardCharsets.UTF_8)));
    }

    private String encodeAsHex(byte[] octets) {
        StringBuilder written = new StringBuilder();
        for (byte octet : octets) {
            written.append(String.format("%02X", octet));
        }
        return written.toString();
    }
}
