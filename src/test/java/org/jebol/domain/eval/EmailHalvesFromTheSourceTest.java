package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An address has two halves, and both can be read and written through a path.
 *
 * <p>{@code t-string.c} gives {@code email!} the string path handler and then
 * adds two words to it that no other string answers, guarded by
 * {@code if (!IS_EMAIL(pvs->value)) return PE_BAD_SELECT}. Everything about
 * them turns on where the first {@code @} is, including what happens when
 * there is not one.
 */
class EmailHalvesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String moldOf(String source) {
        return answerTo("mold " + source).replace("\"", "");
    }

    @Nested
    @DisplayName("reading a half")
    class Reading {

        @Test
        @DisplayName("the user is what is in front of the at sign")
        void theuser() {
            assertThat(answerTo("e: someone@gmail.com e/user").replace("\"", ""))
                    .isEqualTo("someone");
        }

        @Test
        @DisplayName("and the host is what is behind it")
        void thehost() {
            assertThat(answerTo("e: someone@gmail.com e/host").replace("\"", ""))
                    .isEqualTo("gmail.com");
        }

        @Test
        @DisplayName("each is a plain string, not another address")
        void eachHalfIsAString() {
            assertThat(answerTo("e: someone@gmail.com string? e/user"))
                    .as("Copy_String and Set_String make a string!, and an address "
                            + "handed back here would mold with its own punctuation")
                    .isEqualTo("#(true)");
            assertThat(answerTo("e: someone@gmail.com string? e/host")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("with no at sign at all, the whole thing is the user and there is no host")
        void withoutAnAtSign() {
            assertThat(answerTo("e: as email! {foo} e/user").replace("\"", ""))
                    .isEqualTo("foo");
            assertThat(moldOf("e: as email! {foo} e/host"))
                    .as("NOT_FOUND answers PE_NONE rather than an empty string")
                    .isEqualTo("_");
        }

        @Test
        @DisplayName("and no other string answers to either word")
        void onlyAnAddressHasHalves() {
            assertThat(answerTo(
                    "s: {a@b} either error? e: try [s/user] [e/id] ['answered]"))
                    .isEqualTo("invalid-path");
        }
    }

    @Nested
    @DisplayName("writing a half")
    class Writing {

        @Test
        @DisplayName("replaces that half and leaves the other alone")
        void replacingAhalf() {
            assertThat(answerTo("e: someone@gmail.com e/user: {foo} mold e")
                    .replace("\"", "")).isEqualTo("foo@gmail.com");
            assertThat(answerTo("e: someone@gmail.com e/host: {x.com} mold e")
                    .replace("\"", "")).isEqualTo("someone@x.com");
        }

        @Test
        @DisplayName("and setting a host where there is no at sign adds one")
        void settingAhostAddsTheAtSign() {
            assertThat(answerTo("e: as email! {bob} e/host: {rebol.tech} mold e")
                    .replace("\"", ""))
                    .as("the C appends the character and then appends the value "
                            + "behind it, which is the only way a bare name becomes "
                            + "an address")
                    .isEqualTo("bob@rebol.tech");
        }

        @Test
        @DisplayName("the write goes to the storage, so every name for it sees the change")
        void thewriteIsShared() {
            assertThat(answerTo("e: someone@gmail.com f: e e/user: {foo} mold f")
                    .replace("\"", "")).isEqualTo("foo@gmail.com");
        }
    }

    @Nested
    @DisplayName("MAKE EMAIL! from a block")
    class Making {

        @Test
        @DisplayName("takes the first item as the user and the rest as the host")
        void theblockIsAUserAndAHost() {
            assertThat(moldOf("make email! [aaa bbb]")).isEqualTo("aaa@bbb");
            assertThat(moldOf("make email! [aaa bbb cc]"))
                    .as("each item after the first is one label of the host, so the "
                            + "dots are put in rather than written")
                    .isEqualTo("aaa@bbb.cc");
            assertThat(moldOf("make email! [aaa bbb cc dd]")).isEqualTo("aaa@bbb.cc.dd");
        }

        @Test
        @DisplayName("but an empty block names nobody")
        void anemptyBlock() {
            assertThat(answerTo(
                    "either error? e: try [make email! []] [e/id] ['made]"))
                    .isEqualTo("bad-make-arg");
        }
    }
}
