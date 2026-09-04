package org.jebol.application;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether one interpreter can change what the next one reads.
 *
 * <p>{@link LibrarySource} reads each library file once for the whole process
 * and hands out copies, which is what took the gate from twenty-two minutes to
 * five. The whole risk of that is here: a block loaded from source is the block
 * a function's body <em>is</em>, and a script that appends to a literal inside
 * one has changed that literal for good. That is REBOL behaving correctly
 * within one interpreter and cross-contamination across two.
 *
 * <p>So this does not assert that the copying is careful. It tries to break
 * through it -- mutating a block, a nested block and a string in one reading,
 * and mutating the library's own state from a running script -- and checks the
 * next reader gets what the file says.
 */
class LibrarySourceIsolationTest {

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a reading handed out shares nothing with the one kept")
    class TheCopying {

        private static final String SOURCE = """
                a: [1 2 [3 4]]
                b: "text"
                c: #{0102}
                d: [[["deep" #{0304}]]]""";

        private static BlockValue readingOf(String name) {
            TranscodeResult read = LibrarySource.reading(name, SOURCE);
            return read.values().orElseThrow();
        }

        @Test
        @DisplayName("appending to a block does not reach the next reading")
        void appendingToABlock() {
            for (int reading = 0; reading < 3; reading++) {
                BlockValue held = (BlockValue) readingOf("/probe/blocks.reb")
                        .remaining().get(1);
                assertThat(held.storage().length())
                        .as("reading %d is the file, whatever the last one did", reading)
                        .isEqualTo(3);
                held.storage().append(IntegerValue.of(99));
                assertThat(held.storage().length())
                        .as("the mutation happened, so the test is asking something")
                        .isEqualTo(4);
            }
        }

        @Test
        @DisplayName("nor does appending to a block inside that block")
        void appendingToANestedBlock() {
            for (int reading = 0; reading < 3; reading++) {
                BlockValue outer = (BlockValue) readingOf("/probe/nested.reb")
                        .remaining().get(1);
                BlockValue inner = (BlockValue) outer.remaining().get(2);
                assertThat(inner.storage().length()).isEqualTo(2);
                inner.storage().append(IntegerValue.of(99));
                assertThat(inner.storage().length()).isEqualTo(3);
            }
        }

        @Test
        @DisplayName("nor does changing a string")
        void changingAString() {
            for (int reading = 0; reading < 3; reading++) {
                StringValue text = (StringValue) readingOf("/probe/strings.reb")
                        .remaining().get(3);
                assertThat(text.text()).isEqualTo("text");
                text.storage().append('!');
                assertThat(text.text()).isEqualTo("text!");
            }
        }

        @Test
        @DisplayName("and every series in the reading is a different object")
        void nothingIsShared() {
            readingOf("/probe/identity.reb");
            BlockValue first = readingOf("/probe/identity.reb");
            BlockValue second = readingOf("/probe/identity.reb");
            assertThat(sharedStorageBetween(first, second))
                    .as("series shared between two readings of the same file")
                    .isEmpty();
        }

        private static java.util.List<String> sharedStorageBetween(
                Value left, Value right) {

            java.util.List<String> shared = new java.util.ArrayList<>();
            if (left instanceof BlockValue one && right instanceof BlockValue other) {
                if (one.storage() == other.storage()) {
                    shared.add("block");
                }
                for (int at = 0; at < one.remaining().size(); at++) {
                    shared.addAll(sharedStorageBetween(
                            one.remaining().get(at), other.remaining().get(at)));
                }
            }
            if (left instanceof StringValue one && right instanceof StringValue other
                    && one.storage() == other.storage()) {
                shared.add("string");
            }
            if (left instanceof BinaryValue one && right instanceof BinaryValue other
                    && one.storage() == other.storage()) {
                shared.add("binary");
            }
            return shared;
        }
    }

    /**
     * Belt and braces: nothing in today's library is exposed either.
     *
     * <p>These pass with the copying taken out as well, because binding
     * deep-copies every block on its way into an interpreter and the library
     * happens to keep no string or binary literal a script can reach. That
     * makes them a check on the library rather than on the copying -- worth
     * having, and not what holds the line. The tests above are what holds it.
     */
    @Nested
    @DisplayName("and nothing in today's library is reachable either")
    class TheRunningScript {

        @Test
        @DisplayName("a library list one script appends to is the same length in the next")
        void appendingToALibraryList() {
            Interpreter first = Interpreter.create();
            String was = answerTo(first, "length? system/catalog/file-types");
            answerTo(first, "append system/catalog/file-types [%.zzz zzz]");
            assertThat(answerTo(first, "length? system/catalog/file-types"))
                    .isNotEqualTo(was);
            assertThat(answerTo(Interpreter.create(),
                    "length? system/catalog/file-types")).isEqualTo(was);
        }

        @Test
        @DisplayName("a library string one script changes is unchanged in the next")
        void changingALibraryString() {
            Interpreter first = Interpreter.create();
            answerTo(first, "append system/locale/months/1 \"!\"");
            assertThat(answerTo(first, "system/locale/months/1"))
                    .isEqualTo("\"January!\"");
            assertThat(answerTo(Interpreter.create(), "system/locale/months/1"))
                    .isEqualTo("\"January\"");
        }

        @Test
        @DisplayName("and a library function's body is the body the file says")
        void aLibraryFunctionsBody() {
            Interpreter first = Interpreter.create();
            String was = answerTo(first, "length? body-of :join");
            answerTo(first, "b: body-of :join  append b [1 2 3]");
            assertThat(answerTo(Interpreter.create(), "length? body-of :join"))
                    .isEqualTo(was);
        }

        @Test
        @DisplayName("booting many times leaves the library the size it started")
        void bootingManyTimes() {
            String was = answerTo(Interpreter.create(),
                    "length? system/catalog/file-types");
            for (int at = 0; at < 5; at++) {
                assertThat(answerTo(Interpreter.create(),
                        "length? system/catalog/file-types")).isEqualTo(was);
            }
        }
    }
}
