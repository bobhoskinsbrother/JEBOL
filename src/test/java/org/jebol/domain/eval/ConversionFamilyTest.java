package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The TO-X family: one function per datatype a value can be made of.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1, name list included. Forty-five datatypes get one and thirteen
 * do not, and which thirteen is not something to reason out: END, UNSET and
 * NONE hold one value each so there is nothing to convert to, and the rest
 * are the interpreter's own.
 *
 * <p>Every generated function is exactly {@code to <type>! :value}, so the
 * cases below are checked against TO rather than against a written-out
 * answer wherever the point is agreement rather than the value itself.
 */
class ConversionFamilyTest {

    /** Every datatype R3 gives a TO-X, measured from the binary. */
    private static final List<String> CONVERTIBLE = List.of(
            "logic", "integer", "decimal", "percent", "money", "char", "pair", "tuple",
            "time", "date", "binary", "string", "file", "email", "ref", "url", "tag",
            "bitset", "image", "vector", "block", "paren", "path", "set-path", "get-path",
            "lit-path", "hash", "map", "datatype", "typeset", "word", "set-word",
            "get-word", "lit-word", "refinement", "issue", "command", "closure",
            "function", "object", "module", "error", "port", "gob", "event");

    /**
     * Every datatype R3 gives no TO-X.
     *
     * <p>JAVA-OBJECT! is JEBOL's own and joins the list until the interop
     * boundary is specified. A conversion that nothing has asked for would
     * be a decision about that boundary taken by accident.
     */
    private static final List<String> NOT_CONVERTIBLE = List.of(
            "end", "unset", "none", "native", "action", "rebcode", "op", "frame",
            "task", "handle", "struct", "library", "utype", "java-object");

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no error" if it raises none. */
    private static String errorFrom(String source) {
        String shown = answerTo(
                "e: try [" + source + "] either error? e [form e/id] [\"no error\"]");
        return shown.replace("\"", "");
    }

    @Test
    @DisplayName("every datatype a value can be made of has a TO-X function")
    void theFamilyIsComplete() {
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
                """.formatted(quoted(CONVERTIBLE)));
        assertThat(missing).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("the datatypes with nothing to convert to have no TO-X")
    void theInternalDatatypesAreLeftOut() {
        String present = answerTo("""
                showing: copy []
                foreach name %s [
                    if find words-of system/contexts/lib to word! rejoin ["to-" name] [
                        append showing name
                    ]
                ]
                mold showing
                """.formatted(quoted(NOT_CONVERTIBLE)));
        assertThat(present).isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("a generated conversion is the same call as TO")
    void aGeneratedConversionAgreesWithTheGeneralOne() {
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
    @DisplayName("each one carries no title, because this build has no autodocs")
    void theyCarryNoTitle() {
        assertThat(answerTo("first spec-of :to-tuple")).isEqualTo("value");
        assertThat(answerTo("mold spec-of :to-tuple")).isEqualTo("\"[value]\"");
    }

    @Test
    @DisplayName("each one takes exactly one argument")
    void theyTakeOneArgument() {
        assertThat(answerTo("length? spec-of :to-tuple")).isEqualTo("1");
        assertThat(errorFrom("to-tuple")).isEqualTo("no-arg");
    }

    @Test
    @DisplayName("a value the target type cannot be made from is refused")
    void aValueThatCannotConvertIsRefused() {
        assertThat(errorFrom("to-tuple \"abc\"")).isEqualTo("bad-make-arg");
        assertThat(errorFrom("to-tuple none")).isEqualTo("bad-make-arg");
        assertThat(errorFrom("to-integer none")).isEqualTo("bad-make-arg");
    }

    @Test
    @DisplayName("an empty source converts rather than failing, type by type")
    void theDegenerateSourcesConvert() {
        assertThat(answerTo("(to-tuple []) = 0.0.0")).isEqualTo("#(true)");
        assertThat(answerTo("empty? to-binary \"\"")).isEqualTo("#(true)");
        assertThat(answerTo("(to-file \"\") = to file! \"\"")).isEqualTo("#(true)");
        assertThat(answerTo("(to-block \"\") = reduce [copy \"\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a word cannot be made from nothing, unlike the others")
    void anEmptyWordHasNoCharactersToBeMadeOf() {
        assertThat(errorFrom("to-word \"\"")).isEqualTo("too-short");
    }

    @Test
    @DisplayName("converting to logic asks whether the value is truthy, not whether it is zero")
    void toLogicFollowsTruthiness() {
        assertThat(answerTo("mold to-logic 0")).isEqualTo("\"#(true)\"");
        assertThat(answerTo("mold to-logic none")).isEqualTo("\"#(false)\"");
    }

    @Test
    @DisplayName("converting a whole number away from a fraction truncates")
    void toIntegerTruncates() {
        assertThat(answerTo("mold to-integer 1.9")).isEqualTo("\"1\"");
        assertThat(answerTo("mold to-integer -1.9")).isEqualTo("\"-1\"");
    }

    /** A REBOL block literal of the names, so the loop above can read them. */
    private static String quoted(List<String> names) {
        return "[" + String.join(" ", names.stream().map(name -> "\"" + name + "\"").toList())
                + "]";
    }
}
