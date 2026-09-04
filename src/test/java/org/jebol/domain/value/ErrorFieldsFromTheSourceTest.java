package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An error's fields can be written, because an error is an object.
 *
 * <p>{@code boot/types.reb} gives error! the object path handler, and
 * {@code PD_Object} answers {@code PE_SET} for any field the frame holds. So
 * {@code e/id: 'boom} is an ordinary field write and not a special case
 * anybody had to allow.
 *
 * <p>It was refused here because JEBOL derived the eight fields rather than
 * holding them: {@code code} came from the catalogue, {@code type} from the
 * category and {@code id} from a string. A derived field has nowhere to write
 * to, so the whole of the difference is that an error now carries what was
 * written over the top.
 *
 * <p>Why anybody would: rethrowing with a different id, and testing a handler
 * by building the error it is meant to see rather than provoking one. Rebol's
 * own {@code cause-error} is the second of those written down.
 *
 * <p>Every expectation was checked against a real 3.22.1.
 *
 * <p>Specified in {@code spec/values.allium}.
 */
class ErrorFieldsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String A_CAUGHT_ERROR = """
            e: try [1 / 0]
            """;

    @Nested
    @DisplayName("writing a field")
    class TheWrites {

        @Test
        @DisplayName("the id, which is what a rethrow changes")
        void theIdCanBeWritten() {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    e/id: 'boom
                    mold e/id""")).isEqualTo("\"boom\"");
        }

        @Test
        @DisplayName("and it really replaces what was there")
        void itreplacesWhatWasThere() {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    was: e/id
                    e/id: 'boom
                    all [was = 'zero-divide  e/id = 'boom]""")).isEqualTo("#(true)");
        }

        @ParameterizedTest
        @ValueSource(strings = {"code", "type", "id", "arg1", "arg2", "arg3",
                "near", "where"})
        @DisplayName("every one of the eight the frame holds")
        void everyFieldCanBeWritten(String field) {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    e/%s: 99
                    e/%s""".formatted(field, field)))
                    .as("%s is one of the eight", field)
                    .isEqualTo("99");
        }

        @Test
        @DisplayName("a string into arg1, which is what an argument usually is")
        void astringIntoArgOne() {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    e/arg1: "hello"
                    mold e/arg1""")).isEqualTo("{\"hello\"}");
        }

        @Test
        @DisplayName("and it is still an error afterwards")
        void itisStillAnError() {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    e/id: 'boom
                    error? e""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        @Test
        @DisplayName("a field the frame has not got")
        void anunknownFieldIsRefused() {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    e2: try [e/nosuch: 1]
                    e2/id""")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("and reading one is refused the same way")
        void readingAnUnknownFieldIsRefusedToo() {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    error? try [e/nosuch]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("an error is a reference, not a copy")
    class TheSharing {

        @Test
        @DisplayName("so two words holding one error see one change")
        void twoWordsSeeOneChange() {
            // The reason this is a modelling change rather than a line. An
            // error is an object, and an object in REBOL is shared: writing
            // through one name is visible through the other.
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    f: e
                    e/id: 'boom
                    f/id""")).isEqualTo("boom");
        }

        @Test
        @DisplayName("and the eight fields are the ones WORDS-OF names")
        void thefieldsAreTheOnesWordsOfNames() {
            assertThat(answerTo(A_CAUGHT_ERROR + """
                    mold words-of e"""))
                    .isEqualTo("\"[code type id arg1 arg2 arg3 near where]\"");
        }
    }
}
