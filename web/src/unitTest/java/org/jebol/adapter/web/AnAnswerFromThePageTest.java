package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AnAnswerFromThePageTest {

    @Test
    @DisplayName("an answer names the ask it answers")
    void readsTheAskItAnswers() {
        assertThat(new PageAnswer("""
                {"type":"answer","ask":7,"answer":"given"}""").answering()).contains(7);
    }

    @Test
    @DisplayName("an answer with no number, or a number written as text, answers no ask")
    void answersNoAskWithoutAWholeNumber() {
        assertThat(new PageAnswer("""
                {"type":"answer","answer":"given"}""").answering()).isEmpty();
        assertThat(new PageAnswer("""
                {"type":"answer","ask":"7","answer":"given"}""").answering()).isEmpty();
        assertThat(new PageAnswer("""
                {"type":"answer","ask":7.5,"answer":"given"}""").answering()).isEmpty();
    }

    @Test
    @DisplayName("given, declined and refused are the three answers, and each is only itself")
    void readsTheThreeAnswers() {
        PageAnswer given = new PageAnswer("""
                {"ask":1,"answer":"given"}""");
        PageAnswer declined = new PageAnswer("""
                {"ask":1,"answer":"declined"}""");
        PageAnswer refused = new PageAnswer("""
                {"ask":1,"answer":"refused","why":"the browser blocked the new window"}""");

        assertThat(List.of(given.isGiven(), given.isDeclined(), given.isRefused()))
                .containsExactly(true, false, false);
        assertThat(List.of(declined.isGiven(), declined.isDeclined(), declined.isRefused()))
                .containsExactly(false, true, false);
        assertThat(List.of(refused.isGiven(), refused.isDeclined(), refused.isRefused()))
                .containsExactly(false, false, true);
        assertThat(refused.why()).isEqualTo("the browser blocked the new window");
    }

    @Test
    @DisplayName("a refusal that gives no reason still says the page refused")
    void refusalWithoutAReasonSaysThePageRefused() {
        assertThat(new PageAnswer("""
                {"ask":1,"answer":"refused"}""").why()).isEqualTo("the page refused");
    }

    @Test
    @DisplayName("an answer word the interpreter does not know is a refusal naming what came back")
    void readsAnUnknownAnswerAsARefusal() {
        PageAnswer odd = new PageAnswer("""
                {"ask":1,"answer":"maybe"}""");

        assertThat(odd.isRefused()).isTrue();
        assertThat(odd.why()).contains("maybe");
    }

    @Test
    @DisplayName("the answer word is matched exactly, so Given is not given")
    void matchesTheAnswerWordExactly() {
        assertThat(new PageAnswer("""
                {"ask":1,"answer":"Given"}""").isGiven()).isFalse();
        assertThat(new PageAnswer("""
                {"ask":1,"answer":"DECLINED"}""").isDeclined()).isFalse();
    }

    @Test
    @DisplayName("an answer with no answer word, or that is not an object at all, is a refusal")
    void readsSomethingUnreadableAsARefusal() {
        assertThat(new PageAnswer("""
                {"ask":1}""").isRefused()).isTrue();
        assertThat(new PageAnswer("not json").isRefused()).isTrue();
        assertThat(new PageAnswer("").isRefused()).isTrue();
        assertThat(new PageAnswer("""
                {"ask":1,"answer":true}""").isRefused()).isTrue();
    }

    @Test
    @DisplayName("text fields are read back unescaped, and an absent or non-text field is empty")
    void readsTextFields() {
        PageAnswer answer = new PageAnswer("""
                {"ask":1,"answer":"given","text":"line one\\nsaid \\"two\\" twice","count":3}""");

        assertThat(answer.text("text")).contains("""
                line one
                said "two" twice""");
        assertThat(answer.text("missing")).isEmpty();
        assertThat(answer.text("count")).isEmpty();
    }

    @Test
    @DisplayName("whole numbers are read back, and text that looks like one is not one")
    void readsWholeNumbers() {
        PageAnswer answer = new PageAnswer("""
                {"ask":1,"answer":"given","connection":4,"written":"4","huge":99999999999}""");

        assertThat(answer.wholeNumber("connection")).contains(4);
        assertThat(answer.wholeNumber("written")).isEmpty();
        assertThat(answer.wholeNumber("huge")).isEmpty();
        assertThat(answer.wholeNumber("missing")).isEmpty();
    }

    @Test
    @DisplayName("bytes come back from base64, and none at all is an empty array")
    void readsOctets() {
        PageAnswer answer = new PageAnswer("""
                {"ask":1,"answer":"given","octets":"aGVsbG8=","nothing":""}""");

        assertThat(answer.octets("octets")).hasValueSatisfying(octets ->
                assertThat(new String(octets, StandardCharsets.UTF_8)).isEqualTo("hello"));
        assertThat(answer.octets("nothing")).hasValueSatisfying(octets -> assertThat(octets).isEmpty());
    }

    @Test
    @DisplayName("bytes that are not base64, or not text, or absent, are empty rather than guessed at")
    void refusesToGuessAtOctets() {
        PageAnswer answer = new PageAnswer("""
                {"ask":1,"answer":"given","octets":"not base64!","number":5}""");

        assertThat(answer.octets("octets")).isEmpty();
        assertThat(answer.octets("number")).isEmpty();
        assertThat(answer.octets("missing")).isEmpty();
    }

    @Test
    @DisplayName("a list of objects is read one answer per object, in order")
    void readsAListOfObjects() {
        PageAnswer answer = new PageAnswer("""
                {"ask":2,"answer":"given","files":[{"name":"a.txt","octets":"YQ=="},{"name":"b, \\"c\\".txt","octets":""}]}""");

        List<PageAnswer> files = answer.listed("files");

        assertThat(files).hasSize(2);
        assertThat(files.get(0).text("name")).contains("a.txt");
        assertThat(files.get(1).text("name")).contains("""
                b, "c".txt""");
        assertThat(files.get(1).octets("octets")).hasValueSatisfying(octets -> assertThat(octets).isEmpty());
    }

    @Test
    @DisplayName("an empty list, an absent one, or one that is not a list reads as no objects")
    void readsNoObjects() {
        PageAnswer answer = new PageAnswer("""
                {"ask":2,"answer":"given","files":[],"text":"x"}""");

        assertThat(answer.listed("files")).isEmpty();
        assertThat(answer.listed("missing")).isEmpty();
        assertThat(answer.listed("text")).isEmpty();
    }

    @Test
    @DisplayName("a list of strings is read in order, and anything in it that is not a string is skipped")
    void readsAListOfStrings() {
        PageAnswer answer = new PageAnswer("""
                {"ask":3,"answer":"given","inputs":["Keys, left","Pads"],"outputs":[],"mixed":["one",2,"three"]}""");

        assertThat(answer.texts("inputs")).containsExactly("Keys, left", "Pads");
        assertThat(answer.texts("outputs")).isEmpty();
        assertThat(answer.texts("mixed")).containsExactly("one", "three");
        assertThat(answer.texts("missing")).isEmpty();
    }

    @Test
    @DisplayName("fields the interpreter does not ask about are ignored")
    void ignoresExtraFields() {
        PageAnswer answer = new PageAnswer("""
                {"type":"answer","ask":5,"answer":"given","colour":"#ff8000","extra":{"deep":[1,2]}}""");

        assertThat(answer.isGiven()).isTrue();
        assertThat(answer.text("colour")).isEqualTo(Optional.of("#ff8000"));
    }
}
