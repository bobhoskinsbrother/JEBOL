package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionFamilyTest {

    private static final List<String> CONVERTIBLE = List.of(
            "logic", "integer", "decimal", "percent", "money", "char", "pair", "tuple",
            "time", "date", "binary", "string", "file", "email", "ref", "url", "tag",
            "bitset", "image", "vector", "block", "paren", "path", "set-path", "get-path",
            "lit-path", "hash", "map", "datatype", "typeset", "word", "set-word",
            "get-word", "lit-word", "refinement", "issue", "command", "closure",
            "function", "object", "module", "error", "port", "gob", "event");

    private static final List<String> NOT_CONVERTIBLE = List.of(
            "end", "unset", "none", "native", "action", "rebcode", "op", "frame",
            "task", "handle", "struct", "library", "utype", "java-object");

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorFrom(String source) {
        String shown = answerTo(
                "e: try [" + source + "] either error? e [form e/id] [\"no error\"]");
        return shown.replace("\"", "");
    }

    @Test
    @DisplayName("every datatype a value can be made of has a TO-X function")
    void isComplete() {
        String missing = answerTo("""
                lacking: copy []
                foreach name %s [
                    word: to word! rejoin ["to-" name]
                    unless all [
                        find words-of system/contexts/lib word
                        function? get bind word system/contexts/lib
                    ] [append lacking name]
                ]
                mold lacking
                """.formatted(quote(CONVERTIBLE)));
        assertThat(missing).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("the datatypes with nothing to convert to have no TO-X")
    void leavesTheInternalDatatypesOut() {
        String present = answerTo("""
                showing: copy []
                foreach name %s [
                    if find words-of system/contexts/lib to word! rejoin ["to-" name] [
                        append showing name
                    ]
                ]
                mold showing
                """.formatted(quote(NOT_CONVERTIBLE)));
        assertThat(present).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("a generated conversion is the same call as TO")
    void agreesWithTheGeneralConversion() {
        assertThat(answerTo("""
                disagreeing: copy []
                foreach [name value] [
                    tuple  [1 2 3]  binary  "ab"    file   "a/b"
                    word   "abc"    block   "xy"    string [1 2]
                    logic  0        integer 1.9     issue  "z"
                ] [
                    ; Built with DO REDUCE rather than called directly,
                    ; because a paren holding a function is a value and
                    ; not a call -- (:f) x is the function and then x.
                    named: do reduce [
                        get bind to word! rejoin ["to-" name] system/contexts/lib
                        value
                    ]
                    general: do reduce [
                        :to
                        to datatype! to word! rejoin [name "!"]
                        value
                    ]
                    unless :named == :general [append disagreeing name]
                ]
                mold disagreeing
                """)).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("each one carries the title mezz-types.reb builds for it")
    void carriesATitle() {
        assertThat(answerTo("""
                {Converts to tuple! value.} = first spec-of :to-tuple"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("each one takes exactly one argument, after that title")
    void takesOneArgument() {
        assertThat(answerTo("length? spec-of :to-tuple"))
                .as("the title and the one parameter")
                .isEqualTo("2");
        assertThat(answerTo("mold words-of :to-tuple"))
                .as("WORDS-OF is the question about parameters alone")
                .isEqualTo("\"[value]\"");
        assertThat(readTheErrorFrom("to-tuple")).isEqualTo("no-arg");
    }

    @Test
    @DisplayName("a value the target type cannot be made from is refused")
    void refusesAValueThatCannotConvert() {
        assertThat(readTheErrorFrom("to-tuple \"abc\"")).isEqualTo("bad-make-arg");
        assertThat(readTheErrorFrom("to-tuple none")).isEqualTo("bad-make-arg");
        assertThat(readTheErrorFrom("to-integer none")).isEqualTo("bad-make-arg");
    }

    @Test
    @DisplayName("an empty source converts rather than failing, type by type")
    void convertsTheDegenerateSources() {
        assertThat(answerTo("(to-tuple []) = 0.0.0")).isEqualTo("#(true)");
        assertThat(answerTo("empty? to-binary \"\"")).isEqualTo("#(true)");
        assertThat(answerTo("(to-file \"\") = to file! \"\"")).isEqualTo("#(true)");
        assertThat(answerTo("(to-block \"\") = reduce [copy \"\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a word cannot be made from nothing, unlike the others")
    void refusesAnEmptyWord() {
        assertThat(readTheErrorFrom("to-word \"\"")).isEqualTo("too-short");
    }

    @Test
    @DisplayName("converting to logic asks whether the value is truthy, not whether it is zero")
    void followsTruthinessForToLogic() {
        assertThat(answerTo("mold to-logic 0")).isEqualTo("\"#(true)\"");
        assertThat(answerTo("mold to-logic none")).isEqualTo("\"#(false)\"");
    }

    @Test
    @DisplayName("converting a whole number away from a fraction truncates")
    void truncatesForToInteger() {
        assertThat(answerTo("mold to-integer 1.9")).isEqualTo("\"1\"");
        assertThat(answerTo("mold to-integer -1.9")).isEqualTo("\"-1\"");
    }

    private static String quote(List<String> names) {
        return "[" + String.join(" ", names.stream().map(name -> "\"" + name + "\"").toList())
                + "]";
    }
}
