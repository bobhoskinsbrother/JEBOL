package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheSystemObjectIsRebolsDeclarationTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static List<String> theFieldsTheDeclarationOpensWith() {
        List<String> declared = new ArrayList<>();
        for (String line : theDeclarationFile().split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0 || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            String word = line.substring(0, colon);
            if (word.chars().allMatch(TheSystemObjectIsRebolsDeclarationTest::spellsAWord)) {
                declared.add(word);
            }
        }
        return declared;
    }

    private static boolean spellsAWord(int character) {
        return Character.isLowerCase(character) || character == '-';
    }

    private static String theDeclarationFile() {
        try (InputStream open = TheSystemObjectIsRebolsDeclarationTest.class
                .getResourceAsStream("/org/jebol/sysobj.reb")) {
            return new String(open.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Nested
    @DisplayName("the fields are the ones Rebol declares")
    class TheFields {

        @Test
        @DisplayName("twenty at the top, none missing and none invented")
        void theTopLevelFieldsAreTheOnesDeclared() {
            List<String> declared = theFieldsTheDeclarationOpensWith();
            assertThat(declared).hasSize(20);
            assertThat(answerTo("equal? sort copy words-of system sort ["
                    + String.join(" ", declared) + "]")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("the error template, which CONSTRUCT fills without evaluating")
    class TheErrorTemplate {

        @Test
        @DisplayName("its type is the word USER and not a lit-word")
        void theTemplatesTypeIsAPlainWord() {
            assertThat(answerTo("""
                    reduce [
                        mold system/standard/error/type
                        word? system/standard/error/type
                        lit-word? system/standard/error/type
                    ]""")).isEqualTo("[\"user\" #(true) #(false)]");
        }

        @Test
        @DisplayName("its id is the word MESSAGE and not a lit-word")
        void theTemplatesIdIsAPlainWord() {
            assertThat(answerTo("""
                    reduce [
                        mold system/standard/error/id
                        word? system/standard/error/id
                        lit-word? system/standard/error/id
                    ]""")).isEqualTo("[\"message\" #(true) #(false)]");
        }

        @Test
        @DisplayName("its code is zero, which is the other constant declared")
        void theTemplatesCodeIsZero() {
            assertThat(answerTo("system/standard/error/code")).isEqualTo("0");
        }

        @Test
        @DisplayName("and a raised error fills the same fields the template names")
        void aRaisedErrorFillsTheTemplatesFields() {
            assertThat(answerTo("""
                    failure: try [1 / 0]
                    reduce [
                        equal? words-of system/standard/error words-of failure
                        failure/id
                        failure/type
                    ]""")).isEqualTo("[#(true) zero-divide Math]");
        }
    }

    @Nested
    @DisplayName("the state object")
    class TheState {

        @Test
        @DisplayName("carries the note the declaration writes about it")
        void theStateCarriesItsNote() {
            assertThat(answerTo("system/state/note"))
                    .isEqualTo("\"contains protected hidden fields\"");
        }
    }
}
