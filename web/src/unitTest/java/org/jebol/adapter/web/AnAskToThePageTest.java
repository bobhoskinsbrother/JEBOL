package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnAskToThePageTest {

    @Test
    @DisplayName("an ask is written as one JSON object carrying its number, its kind and whether it needs a gesture")
    void writesTheNumberKindAndGesture() {
        assertThat(new PageAsk("colour", true).writeNumbered(7)).isEqualTo("""
                {"type":"ask","ask":7,"kind":"colour","gesture":true}""");
    }

    @Test
    @DisplayName("an ask that needs no gesture says so")
    void saysWhenNoGestureIsNeeded() {
        assertThat(new PageAsk("midi-ports", false).writeNumbered(1)).isEqualTo("""
                {"type":"ask","ask":1,"kind":"midi-ports","gesture":false}""");
    }

    @Test
    @DisplayName("the first ask a page sees is numbered one, and the number runs to the largest whole number")
    void numbersFromOneToTheLargest() {
        assertThat(new PageAsk("colour", true).writeNumbered(1)).contains("""
                "ask":1,""");
        assertThat(new PageAsk("colour", true).writeNumbered(Integer.MAX_VALUE)).contains("""
                "ask":2147483647,""");
    }

    @Test
    @DisplayName("an ask is never numbered nought or less, since the page answers by number from one")
    void refusesANumberBelowOne() {
        assertThatThrownBy(() -> new PageAsk("colour", true).writeNumbered(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from one");
        assertThatThrownBy(() -> new PageAsk("colour", true).writeNumbered(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an ask must name its kind")
    void refusesAnEmptyKind() {
        assertThatThrownBy(() -> new PageAsk("", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind");
        assertThatThrownBy(() -> new PageAsk(" ", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("text, flags and whole numbers follow in the order they were added")
    void writesFieldsInOrder() {
        PageAsk asked = new PageAsk("open-files", true)
                .with("many", true)
                .with("accept", ".r3,.txt")
                .with("input", 2);

        assertThat(asked.writeNumbered(3)).isEqualTo("""
                {"type":"ask","ask":3,"kind":"open-files","gesture":true,"many":true,"accept":".r3,.txt","input":2}""");
    }

    @Test
    @DisplayName("text is escaped so a quote, a backslash, a newline or a control character cannot break the object")
    void escapesText() {
        String written = new PageAsk("clipboard-write", false)
                .with("text", """
                        say "hi"\\
                        \t\u0001""")
                .writeNumbered(1);

        assertThat(written).isEqualTo("""
                {"type":"ask","ask":1,"kind":"clipboard-write","gesture":false,"text":"say \\"hi\\"\\\\\\n\\t\\u0001"}""");
    }

    @Test
    @DisplayName("text beyond ASCII crosses as itself")
    void keepsTextBeyondAscii() {
        assertThat(new PageAsk("clipboard-write", false).with("text", "café 漢字 🎹").writeNumbered(1))
                .endsWith("""
                        "text":"café 漢字 🎹"}""");
    }

    @Test
    @DisplayName("bytes cross as base64")
    void writesOctetsAsBase64() {
        PageAsk asked = new PageAsk("send-saved-file", false)
                .withOctets("octets", "hello".getBytes(StandardCharsets.UTF_8));

        assertThat(asked.writeNumbered(1)).endsWith("""
                "octets":"aGVsbG8="}""");
    }

    @Test
    @DisplayName("no bytes at all cross as an empty string rather than being left out")
    void writesEmptyOctets() {
        assertThat(new PageAsk("send-saved-file", false).withOctets("octets", new byte[0]).writeNumbered(1))
                .endsWith("""
                        "octets":""}""");
    }

    @Test
    @DisplayName("an ask keeps its kind and gesture for the transport to read")
    void answersItsKindAndGesture() {
        PageAsk asked = new PageAsk("open-tab", true);

        assertThat(asked.kind()).isEqualTo("open-tab");
        assertThat(asked.needsGesture()).isTrue();
    }

    @Test
    @DisplayName("adding a field leaves the ask it was added to as it was")
    void addingAFieldMakesANewAsk() {
        PageAsk bare = new PageAsk("colour", true);
        bare.with("suggested", "#0000ff");

        assertThat(bare.writeNumbered(1)).doesNotContain("suggested");
    }

    @Test
    @DisplayName("a field is named, and the names the ask itself writes cannot be taken")
    void refusesAFieldThatWouldOverwriteTheEnvelope() {
        PageAsk asked = new PageAsk("colour", true);

        assertThatThrownBy(() -> asked.with("", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asked.with("type", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asked.with("ask", 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asked.with("kind", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asked.with("gesture", false)).isInstanceOf(IllegalArgumentException.class);
    }
}
