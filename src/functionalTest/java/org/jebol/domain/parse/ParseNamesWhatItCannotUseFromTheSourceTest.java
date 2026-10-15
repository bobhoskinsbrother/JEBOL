package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParseNamesWhatItCannotUseFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String WRAPPER_BEFORE = "e: try [";

    private static final String WRAPPER_AFTER = """
            ]
            either error? e [
                reduce [e/id  any [attempt [mold e/arg1] {absent}]]
            ] [reduce ['ok]]""";

    private static String failureOf(String setUp, String parse) {
        return answerTo(setUp + "\n" + WRAPPER_BEFORE + parse + "\n" + WRAPPER_AFTER);
    }

    private static String failureOf(String parse) {
        return failureOf("", parse);
    }

    @Test
    @DisplayName("a get-word rule holding something that is not a series names parse-series")
    void aGetWordHoldingSomethingThatIsNotASeriesNamesParseSeries() {
        assertThat(failureOf("counted: 5", """
                parse "abc" [:counted]"""))
                .isEqualTo("""
                        [parse-series ":counted"]""");
        assertThat(failureOf("counted: 5", "parse [a b c] [:counted]"))
                .isEqualTo("""
                        [parse-series ":counted"]""");
    }

    @Test
    @DisplayName("and so does one holding a function, or nothing at all")
    void andSoDoesOneHoldingAFunctionOrNothingAtAll() {
        assertThat(failureOf("""
                parse "abc" [:append]"""))
                .isEqualTo("""
                        [parse-series ":append"]""");
        assertThat(failureOf("set/any 'empty ()", """
                parse "abc" [:empty]"""))
                .isEqualTo("""
                        [parse-series ":empty"]""");
        assertThat(failureOf("""
                parse "abc" [:neverdefinedanywhere]"""))
                .isEqualTo("""
                        [parse-series ":neverdefinedanywhere"]""");
    }

    @Test
    @DisplayName("a get-word holding a series switches the input instead")
    void aGetWordHoldingASeriesSwitchesTheInput() {
        assertThat(failureOf("""
                other: "bc"
                parse "abc" [:other]"""))
                .isEqualTo("[ok]");
        assertThat(failureOf("other: [b c]", "parse [a b c] [:other]"))
                .isEqualTo("[ok]");
    }

    @Test
    @DisplayName("a get-path naming something that is not a series names parse-series too")
    void aGetPathNamingSomethingThatIsNotASeriesNamesParseSeries() {
        assertThat(failureOf("holder: object [part: 5]", """
                parse "abc" [:holder/part]"""))
                .isEqualTo("""
                        [parse-series ":holder/part"]""");
        assertThat(failureOf("holder: object [part: {bc}]", """
                parse "abc" [:holder/part]"""))
                .isEqualTo("[ok]");
    }

    @Test
    @DisplayName("a SET or COPY target that is not a word names parse-variable and molds it")
    void aTargetThatIsNotAWordNamesParseVariable() {
        assertThat(failureOf("""
                parse "abc" [set 1 skip]"""))
                .isEqualTo("""
                        [parse-variable "1"]""");
        assertThat(failureOf("""
                parse "abc" [copy 1.5 skip]"""))
                .isEqualTo("""
                        [parse-variable "1.5"]""");
        assertThat(failureOf("""
                parse "abc" [set (1) skip]"""))
                .isEqualTo("""
                        [parse-variable "(1)"]""");
        assertThat(failureOf("""
                parse "abc" [copy {q} skip]"""))
                .isEqualTo("[parse-variable {\"q\"}]");
        assertThat(failureOf("parse [a b c] [set 1 skip]"))
                .isEqualTo("""
                        [parse-variable "1"]""");
    }

    @Test
    @DisplayName("a quoted word is not a word to write into either")
    void aQuotedWordIsNotAWordToWriteInto() {
        assertThat(failureOf("""
                parse "abc" [set 'target skip]"""))
                .isEqualTo("""
                        [parse-variable "'target"]""");
        assertThat(failureOf("""
                parse "abc" [set :target skip]"""))
                .isEqualTo("""
                        [parse-variable ":target"]""");
    }

    @Test
    @DisplayName("a plain word and a set-word are the two it does accept")
    void aPlainWordAndASetWordAreTheTwoItAccepts() {
        assertThat(answerTo("""
                parse "abc" [set target skip]
                reduce [target]""")).isEqualTo("[#\"a\"]");
        assertThat(answerTo("""
                parse "abc" [copy target: 2 skip]
                reduce [target]""")).isEqualTo("""
                        ["ab"]""");
    }

    @Test
    @DisplayName("SET or COPY with nothing after it to write into names parse-variable")
    void setOrCopyWithNothingAfterItNamesParseVariable() {
        assertThat(failureOf("""
                parse "abc" [set]""")).startsWith("[parse-variable");
        assertThat(failureOf("""
                parse "abc" [copy]""")).startsWith("[parse-variable");
    }

    @Test
    @DisplayName("but a target with no rule after it is only a parse that did not match")
    void aTargetWithNoRuleAfterItIsOnlyAParseThatDidNotMatch() {
        assertThat(answerTo("""
                parse "abc" [set target skip]
                parse "abc" [set target]""")).isEqualTo("#(false)");
        assertThat(answerTo("""
                parse "abc" [collect set gathered [keep skip]]
                parse "abc" [collect set gathered]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("a target that is one of the dialect's own words names parse-command")
    void aTargetThatIsOneOfTheDialectsOwnWordsNamesParseCommand() {
        assertThat(failureOf("""
                parse "abc" [set skip skip]"""))
                .isEqualTo("""
                        [parse-command "skip"]""");
        assertThat(failureOf("""
                parse "abc" [copy end skip]"""))
                .isEqualTo("""
                        [parse-command "end"]""");
        assertThat(failureOf("""
                parse "abc" [set set skip]"""))
                .isEqualTo("""
                        [parse-command "set"]""");
        assertThat(failureOf("""
                parse "abc" [set quote skip]"""))
                .isEqualTo("""
                        [parse-command "quote"]""");
        assertThat(failureOf("parse [a b c] [set into skip]"))
                .isEqualTo("""
                        [parse-command "into"]""");
    }

    @Test
    @DisplayName("and a set-word spelling one of them is refused the same way")
    void aSetWordSpellingOneOfThemIsRefusedTheSameWay() {
        assertThat(failureOf("""
                parse "abc" [set skip: skip]"""))
                .isEqualTo("""
                        [parse-command "skip:"]""");
    }

    @Test
    @DisplayName("a word the dialect does not reserve is a variable, even a datatype's name")
    void aWordTheDialectDoesNotReserveIsAVariable() {
        assertThat(failureOf("""
                parse "abc" [set integer! skip]""")).isEqualTo("[ok]");
        assertThat(failureOf("""
                parse "abc" [set none skip]""")).isEqualTo("[ok]");
        assertThat(failureOf("""
                parse "abc" [set true skip]""")).isEqualTo("[ok]");
    }

    @Test
    @DisplayName("COLLECT INTO and COLLECT AFTER name parse-variable for anything but a word")
    void collectIntoAndAfterNameParseVariableForAnythingButAWord() {
        assertThat(failureOf("""
                parse "abc" [collect into 5 [keep skip]]"""))
                .isEqualTo("""
                        [parse-variable "5"]""");
        assertThat(failureOf("""
                parse "abc" [collect after 5 [keep skip]]"""))
                .isEqualTo("""
                        [parse-variable "5"]""");
        assertThat(failureOf("""
                parse "abc" [collect into gathered: [keep skip]]"""))
                .isEqualTo("""
                        [parse-variable "gathered:"]""");
        assertThat(answerTo("""
                gathered: copy [x]
                parse "abc" [collect into :gathered [keep skip]]
                gathered""")).isEqualTo("[#\"a\" x]");
    }

    @Test
    @DisplayName("COLLECT SET takes a word or a set-word, and refuses a quoted one")
    void collectSetTakesAWordOrASetWord() {
        assertThat(failureOf("""
                parse "abc" [collect set 'gathered [keep skip]]"""))
                .isEqualTo("""
                        [parse-variable "'gathered"]""");
        assertThat(answerTo("""
                parse "abc" [collect set gathered: [some [keep skip]]]
                gathered""")).isEqualTo("[#\"a\" #\"b\" #\"c\"]");
    }

    @Test
    @DisplayName("a COLLECT INTO word holding something it cannot hold names parse-into-type, and names nothing else")
    void collectIntoAWordHoldingSomethingItCannotHoldNamesParseIntoType() {
        assertThat(failureOf("counted: 5", """
                parse "abc" [collect into counted [keep skip]]"""))
                .isEqualTo("""
                        [parse-into-type "_"]""");
        assertThat(failureOf("""
                parse "abc" [collect into skip [keep skip]]"""))
                .isEqualTo("""
                        [parse-into-type "_"]""");
        assertThat(failureOf("""
                parse "abc" [collect into neverdefinedanywhere [keep skip]]"""))
                .isEqualTo("""
                        [parse-into-type "_"]""");
    }
}
