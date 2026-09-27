package org.jebol.application;

import org.jebol.domain.eval.ClipboardPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TheClipboardPortFromTheSourceTest {

    private static final class ARememberedClipboard implements ClipboardPort {

        private String held;

        private ARememberedClipboard(String held) {
            this.held = held;
        }

        @Override
        public String read() {
            return held;
        }

        @Override
        public void write(String text) {
            held = text;
        }
    }

    private static String answerTo(String source) {
        return answerWith(new ARememberedClipboard(""), source);
    }

    private static String answerWith(ClipboardPort clipboard, String source) {
        Interpreter interpreter = Interpreter.writingTo(
                message -> { },
                Bounds.standard().granting(HostService.CLIPBOARD));
        interpreter.useClipboard(clipboard);
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String withoutTheGrant(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.useClipboard(new ARememberedClipboard("secret"));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("reading what the operator last copied")
    class Reading {

        @Test
        @DisplayName("answers it as a string")
        void answersAString() {
            assertThat(answerWith(new ARememberedClipboard("hello jebol"),
                    "read clipboard://")).isEqualTo("\"hello jebol\"");
        }

        @Test
        @DisplayName("an empty clipboard is an empty string, not none")
        void anEmptyClipboard() {
            assertThat(answerWith(new ARememberedClipboard(""),
                    "read clipboard://")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("and reading needs no OPEN around it")
        void readingNeedsNoOpen() {
            assertThat(answerWith(new ARememberedClipboard("straight in"),
                    "read clipboard://")).isEqualTo("\"straight in\"");
        }

        @Test
        @DisplayName("through a port the caller opened, it reads the same")
        void throughAnOpenedPort() {
            assertThat(answerWith(new ARememberedClipboard("opened first"),
                    "read open clipboard://")).isEqualTo("\"opened first\"");
        }
    }

    @Nested
    @DisplayName("/PART, at its boundaries")
    class ReadingPart {

        @ParameterizedTest(name = "/part {0}")
        @CsvSource({
                "0,   ''",
                "1,   a",
                "4,   abcd",
                "5,   abcde",
                "6,   abcde",
                "-1,  ''",
        })
        void partLimitsTheLength(String asked, String expected) {
            assertThat(answerWith(new ARememberedClipboard("abcde"),
                    "read/part clipboard:// " + asked))
                    .isEqualTo("\"" + expected.replace("''", "") + "\"");
        }
    }

    @Nested
    @DisplayName("/LINES, which answers a block instead of a string")
    class ReadingLines {

        @Test
        @DisplayName("one line for each line the text holds")
        void oneEntryPerLine() {
            assertThat(answerWith(new ARememberedClipboard("first\nsecond\nthird"),
                    "read/lines clipboard://"))
                    .isEqualTo("[\"first\" \"second\" \"third\"]");
        }

        @Test
        @DisplayName("text with no line break at all is one line")
        void textWithNoBreak() {
            assertThat(answerWith(new ARememberedClipboard("alone"),
                    "read/lines clipboard://")).isEqualTo("[\"alone\"]");
        }

        @Test
        @DisplayName("and an empty clipboard has no lines")
        void anEmptyClipboardHasNoLines() {
            assertThat(answerWith(new ARememberedClipboard(""),
                    "read/lines clipboard://")).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("writing, which answers the port so writes chain")
    class Writing {

        @Test
        @DisplayName("a string goes on and comes back")
        void aStringRoundTrips() {
            assertThat(answerTo("""
                    write clipboard:// "put me on"
                    read clipboard://""")).isEqualTo("\"put me on\"");
        }

        @Test
        @DisplayName("an empty string is a write like any other")
        void anEmptyStringRoundTrips() {
            assertThat(answerWith(new ARememberedClipboard("was here"), """
                    write clipboard:// ""
                    read clipboard://""")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("a binary goes on as its bytes read as text")
        void aBinaryRoundTrips() {
            assertThat(answerTo("""
                    write clipboard:// #{4142}
                    read clipboard://""")).isEqualTo("\"AB\"");
        }

        @Test
        @DisplayName("and the answer is the port itself")
        void theAnswerIsThePort() {
            assertThat(answerTo("""
                    port? write clipboard:// "anything\"""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what it will not carry")
    class WhatItWillNotCarry {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"5", "0", "-1", "1.5", "[a b]", "none", "true",
                "quote word", "1-Jan-2020", "#\"c\"", "10:30"})
        void anythingButTextAndBytesIsRefused(String written) {
            assertThat(answerTo(
                    "failure: try [write clipboard:// " + written + "] failure/id"))
                    .isEqualTo("invalid-port-arg");
        }
    }

    @Nested
    @DisplayName("the port itself, and the verbs its actor has no arm for")
    class ThePortItself {

        @Test
        @DisplayName("a read leaves what it read in the port's data")
        void aReadLeavesItsAnswerInTheData() {
            assertThat(answerWith(new ARememberedClipboard("left here"), """
                    p: open clipboard://
                    read p
                    reduce [type? p/data p/data]""")).isEqualTo("[#(string!) \"left here\"]");
        }

        @Test
        @DisplayName("and a write leaves none there, because the write is done")
        void aWriteLeavesNone() {
            assertThat(answerTo("""
                    p: open clipboard://
                    write p "gone"
                    type? p/data""")).isEqualTo("#(none!)");
        }

        @Test
        @DisplayName("opening answers an open port and closing shuts it")
        void openingAndClosing() {
            assertThat(answerTo("""
                    p: open clipboard://
                    was: open? p
                    close p
                    reduce [was open? p]""")).isEqualTo("[#(true) #(false)]");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "delete clipboard://",
                "rename clipboard:// %elsewhere",
                "length? open clipboard://",
                "query clipboard:// 'size"})
        void averbItHasNoArmForIsRefusedByName(String written) {
            assertThat(answerTo("failure: try [" + written + "] failure/id"))
                    .isEqualTo("no-port-action");
        }
    }

    @Nested
    @DisplayName("the grant, which is the clipboard's own and not the console's")
    class TheGrant {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "read clipboard://",
                "write clipboard:// \"x\"",
                "open clipboard://"})
        void withoutTheGrantItRefuses(String written) {
            assertThat(withoutTheGrant("failure: try [" + written + "] failure/id"))
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("and nothing of what was on it reaches the script")
        void nothingLeaks() {
            assertThat(withoutTheGrant("""
                    failure: try [read clipboard://] mold failure"""))
                    .doesNotContain("secret");
        }
    }
}
