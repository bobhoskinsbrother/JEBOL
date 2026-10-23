package org.jebol.application;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibrarySourceIsolationTest {

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
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

        private static AnyBlockValue takeAReadingOf(String name) {
            TranscodeResult read = LibrarySource.read(name, () -> SOURCE);
            return read.values().orElseThrow();
        }

        @Test
        @DisplayName("appending to a block does not reach the next reading")
        void keepsAnAppendFromTheNextReading() {
            for (int reading = 0; reading < 3; reading++) {
                AnyBlockValue held = (AnyBlockValue) takeAReadingOf("/probe/blocks.reb")
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
        void keepsANestedAppendFromTheNextReading() {
            for (int reading = 0; reading < 3; reading++) {
                AnyBlockValue outer = (AnyBlockValue) takeAReadingOf("/probe/nested.reb")
                        .remaining().get(1);
                AnyBlockValue inner = (AnyBlockValue) outer.remaining().get(2);
                assertThat(inner.storage().length()).isEqualTo(2);
                inner.storage().append(IntegerValue.of(99));
                assertThat(inner.storage().length()).isEqualTo(3);
            }
        }

        @Test
        @DisplayName("nor does changing a string")
        void keepsAChangedStringFromTheNextReading() {
            for (int reading = 0; reading < 3; reading++) {
                AnyStringValue text = (AnyStringValue) takeAReadingOf("/probe/strings.reb")
                        .remaining().get(3);
                assertThat(text.text()).isEqualTo("text");
                text.storage().append('!');
                assertThat(text.text()).isEqualTo("text!");
            }
        }

        @Test
        @DisplayName("and every series in the reading is a different object")
        void sharesNothing() {
            takeAReadingOf("/probe/identity.reb");
            AnyBlockValue first = takeAReadingOf("/probe/identity.reb");
            AnyBlockValue second = takeAReadingOf("/probe/identity.reb");
            assertThat(listTheStorageSharedBetween(first, second))
                    .as("series shared between two readings of the same file")
                    .isEmpty();
        }

        private static java.util.List<String> listTheStorageSharedBetween(
                Value left, Value right) {

            java.util.List<String> shared = new java.util.ArrayList<>();
            if (left instanceof AnyBlockValue one && right instanceof AnyBlockValue other) {
                if (one.storage() == other.storage()) {
                    shared.add("block");
                }
                for (int at = 0; at < one.remaining().size(); at++) {
                    shared.addAll(listTheStorageSharedBetween(
                            one.remaining().get(at), other.remaining().get(at)));
                }
            }
            if (left instanceof AnyStringValue one && right instanceof AnyStringValue other
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

    @Nested
    @DisplayName("and nothing in today's library is reachable either")
    class TheRunningScript {

        @Test
        @DisplayName("a library list one script appends to is the same length in the next")
        void keepsALibraryListTheSameLengthInTheNext() {
            Interpreter first = AnInterpreter.create();
            String was = answerTo(first, "length? system/catalog/file-types");
            answerTo(first, "append system/catalog/file-types [%.zzz zzz]");
            assertThat(answerTo(first, "length? system/catalog/file-types"))
                    .isNotEqualTo(was);
            assertThat(answerTo(AnInterpreter.create(),
                    "length? system/catalog/file-types")).isEqualTo(was);
        }

        @Test
        @DisplayName("a library string one script changes is unchanged in the next")
        void keepsALibraryStringUnchangedInTheNext() {
            Interpreter first = AnInterpreter.create();
            answerTo(first, "append system/locale/months/1 \"!\"");
            assertThat(answerTo(first, "system/locale/months/1"))
                    .isEqualTo("\"January!\"");
            assertThat(answerTo(AnInterpreter.create(), "system/locale/months/1"))
                    .isEqualTo("\"January\"");
        }

        @Test
        @DisplayName("a library map one script changes is unchanged in the next")
        void keepsALibraryMapUnchangedInTheNext() {
            Interpreter first = AnInterpreter.create();
            String was = answerTo(first, "system/options/log/rebol");
            answerTo(first, "system/options/log/rebol: 9");
            assertThat(answerTo(first, "system/options/log/rebol")).isEqualTo("9");
            assertThat(answerTo(AnInterpreter.create(), "system/options/log/rebol")).isEqualTo(was);
        }

        @Test
        @DisplayName("a library bitset one script changes is unchanged in the next")
        void keepsALibraryBitsetUnchangedInTheNext() {
            Interpreter first = AnInterpreter.create();
            assertThat(answerTo(first, "did find whitespace first {x}")).isEqualTo("#(false)");
            answerTo(first, "poke whitespace first {x} true");
            assertThat(answerTo(first, "did find whitespace first {x}")).isEqualTo("#(true)");
            assertThat(answerTo(AnInterpreter.create(), "did find whitespace first {x}"))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and a library function's body is the body the file says")
        void keepsALibraryFunctionsBodyAsTheFileSays() {
            Interpreter first = AnInterpreter.create();
            String was = answerTo(first, "length? body-of :join");
            answerTo(first, "b: body-of :join  append b [1 2 3]");
            assertThat(answerTo(AnInterpreter.create(), "length? body-of :join"))
                    .isEqualTo(was);
        }

        @Test
        @DisplayName("booting many times leaves the library the size it started")
        void keepsTheLibrarySizeAcrossManyBoots() {
            String was = answerTo(AnInterpreter.create(),
                    "length? system/catalog/file-types");
            for (int at = 0; at < 5; at++) {
                assertThat(answerTo(AnInterpreter.create(),
                        "length? system/catalog/file-types")).isEqualTo(was);
            }
        }
    }
}
