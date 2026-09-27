package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheSchemesRebolDeclaresFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String THE_BUILDS_OWN_SCHEME = "bundled";

    static List<String> theSchemesTheLibraryDeclares() {
        List<String> declared = new ArrayList<>();
        boolean inside = false;
        for (String line : theBorrowedPortFile().split("\n")) {
            if (line.startsWith("init-schemes:")) {
                inside = true;
            }
            if (inside && line.startsWith("\t\tname: '")) {
                declared.add(line.substring("\t\tname: '".length()).trim());
            }
        }
        return declared;
    }

    private static String theBorrowedPortFile() {
        try (InputStream open = TheSchemesRebolDeclaresFromTheSourceTest.class
                .getResourceAsStream("/org/jebol/mezz/sys-ports.reb")) {
            return new String(open.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Nested
    @DisplayName("the names, which are Rebol's declaration and not a list kept here")
    class TheNames {

        @Test
        @DisplayName("the library declares thirteen, and the four that were missing are among them")
        void theLibraryDeclaresThirteen() {
            assertThat(theSchemesTheLibraryDeclares())
                    .hasSize(13)
                    .contains("callback", "clipboard", "serial", "udp");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("org.jebol.application."
                + "TheSchemesRebolDeclaresFromTheSourceTest#theSchemesTheLibraryDeclares")
        void everySchemeTheLibraryDeclaresIsRegistered(String scheme) {
            assertThat(answerTo("object? get in system/schemes (quote "
                    + scheme + ")")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and BUNDLED beside them, which is this build's own")
        void theBundledSchemeIsStillThere() {
            assertThat(answerTo("object? get in system/schemes (quote "
                    + THE_BUILDS_OWN_SCHEME + ")")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("nothing is registered that Rebol does not declare, bar the protocols and BUNDLED")
        void nothingIsInventedBesideBundled() {
            List<String> allowed = new ArrayList<>(theSchemesTheLibraryDeclares());
            allowed.add(THE_BUILDS_OWN_SCHEME);
            String registered = answerTo("mold sort copy words-of system/schemes");
            for (String name : registered
                    .replaceAll("[\\[\\]\"{}]", "").trim().split("\\s+")) {
                assertThat(name).satisfiesAnyOf(
                        each -> assertThat(allowed).contains(each),
                        each -> assertThat(theProtocolsTheLibraryBrings()).contains(each));
            }
        }

        private static List<String> theProtocolsTheLibraryBrings() {
            return List.of("http", "https", "tls", "smtp", "smtps", "pop3", "pop3s",
                    "mysql", "rdap", "whois", "daytime", "mail", "safe");
        }
    }

    @Nested
    @DisplayName("the standard ports the declaration opens")
    class TheStandardPorts {

        @Test
        @DisplayName("input is open, and it is the same port as output")
        void theInputAndOutputPortsAreOne() {
            assertThat(answerTo("""
                    reduce [
                        port? system/ports/input
                        port? system/ports/output
                        same? system/ports/input system/ports/output
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and the callback port is open too")
        void theCallbackPortIsOpen() {
            assertThat(answerTo("port? system/ports/callback")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what opening one answers")
    class WhatOpeningAnswers {

        @Test
        @DisplayName("the callback scheme answers a port, which is what a real 3.22.5 answers")
        void openingTheCallbackSchemeAnswersAPort() {
            assertThat(answerTo("port? open callback://")).isEqualTo("#(true)");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"serial://usb/9600"})
        void openingASchemeWithNoDeviceNamesIt(String url) {
            String scheme = url.split(":")[0];
            assertThat(answerTo("failure: try [open " + url + "] failure/id"))
                    .isEqualTo("no-service");
            assertThat(answerTo(
                    "failure: try [open " + url + "] find form failure/arg1 \""
                            + scheme + "\""))
                    .isNotEqualTo("_");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"udp://:40999", "clipboard://"})
        void aSchemeWithADeviceRefusesForWantOfItsGrant(String url) {
            assertThat(answerTo("failure: try [open " + url + "] failure/id"))
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("and a name nobody declares is still refused as no scheme at all")
        void anUnknownSchemeIsStillRefused() {
            assertThat(answerTo("""
                    failure: try [open frobozz://] failure/id"""))
                    .isEqualTo("no-scheme");
        }
    }

    @Nested
    @DisplayName("every spelling of a specification reaches the same registered scheme")
    class EverySpelling {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "open udp://:40999",
                "open UDP://:40999",
                "open [scheme: 'udp]",
                "open quote udp"})
        void everySpellingReachesTheScheme(String written) {
            assertThat(answerTo("failure: try [" + written + "] failure/id"))
                    .isEqualTo("no-service");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"make port! quote udp", "make port! [scheme: 'udp]"})
        void makingAPortOfOneAnswersAPort(String written) {
            assertThat(answerTo("port? " + written)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a specification naming no scheme at all is still no scheme")
        void anEmptySpecificationNamesNoScheme() {
            assertThat(answerTo("failure: try [open []] failure/id"))
                    .isEqualTo("no-scheme");
        }
    }

    @Nested
    @DisplayName("none of them is a device, and asking one to do anything says so")
    class NoneOfThemIsADevice {

        @Test
        @DisplayName("the callback port opens and then serves nothing")
        void theCallbackPortServesNothing() {
            assertThat(answerTo("""
                    failure: try [read open callback://] failure/id"""))
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("and DO-CALLBACK, which is the only thing that would drive it")
        void doCallbackIsRefused() {
            assertThat(answerTo("""
                    failure: try [do-callback []] failure/id"""))
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("reading the clipboard is refused, not answered with an empty one")
        void readingTheClipboardIsRefused() {
            assertThat(answerTo("""
                    failure: try [read clipboard://] failure/id"""))
                    .isEqualTo("no-service");
        }
    }
}
