package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENHEX's two character sets, SWAP-ENDIAN's /PART, and what the set
 * operations will not take.
 *
 * <p>The first was JEBOL using RFC 3986's idea of which characters are safe
 * where Rebol has its own, written out in {@code boot/sysobj.reb} as two
 * bitsets. They differ in both directions, so a URL came back with brackets
 * left in and an ordinary string came back with its brackets and quotes
 * escaped that Rebol leaves alone.
 */
class HexAndEndianFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String theTextOf(String source) {
        String molded = answerTo(source);
        return molded.startsWith("{") && molded.endsWith("}")
                ? molded.substring(1, molded.length() - 1)
                : molded.replace("\"", "");
    }

    @Nested
    @DisplayName("ENHEX, and which characters it leaves alone")
    class Escaping {

        @Test
        @DisplayName("a url keeps the punctuation a url is made of and escapes the rest")
        void aurlKeepsItsOwnPunctuation() {
            assertThat(theTextOf("form enhex as url! {\"#$%&+,/:;=?@[]\\}"))
                    .as("Rebol's uri bitset is A-Z a-z 0-9 and !#$&'()*+,-./:;=?@_~, "
                            + "which has no brackets in it and no percent sign")
                    .isEqualTo("%22#$%25&+,/:;=?@%5B%5D%5C");
        }

        @Test
        @DisplayName("and anything else keeps the narrower component set")
        void astringKeepsTheComponentSet() {
            assertThat(theTextOf("enhex {!'()*_.-~}"))
                    .as("uri-component is A-Z a-z 0-9 and !'()*-._~, so none of "
                            + "these is touched")
                    .isEqualTo("!'()*_.-~");
            assertThat(theTextOf("enhex {a b}")).isEqualTo("a%20b");
        }

        @Test
        @DisplayName("/ESCAPE changes the character the escapes are written with")
        void escapeChangesTheMarker() {
            assertThat(theTextOf("enhex/escape {(#)} first {#}")).isEqualTo("(#23)");
            assertThat(theTextOf("enhex/escape {(š)} first {#}"))
                    .as("and a character outside ASCII is escaped one UTF-8 octet "
                            + "at a time")
                    .isEqualTo("(#C5#A1)");
        }

        @Test
        @DisplayName("/URI writes a space as a plus, or an underscore under an equals")
        void uriWritesTheSpaceSpecially() {
            assertThat(theTextOf("enhex/uri {a b}")).isEqualTo("a+b");
            assertThat(theTextOf("enhex/uri/escape {a b_} first {=}"))
                    .as("with the space standing for an underscore, an underscore "
                            + "that was already there has to be escaped or the two "
                            + "could not be told apart")
                    .isEqualTo("a_b=5F");
            assertThat(theTextOf("enhex/escape/uri {a á_} first {=}"))
                    .isEqualTo("a_=C3=A1=5F");
        }
    }

    @Nested
    @DisplayName("SWAP-ENDIAN/PART")
    class SwappingPartOfIt {

        @ParameterizedTest
        @CsvSource({
                "4,  '#{FF00FF001122}'",
                "5,  '#{FF00FF001122}'",
                "6,  '#{FF00FF002211}'",
        })
        @DisplayName("swaps only as far as it was told, in whole pairs")
        void onlyAsFarAsAsked(int reach, String expected) {
            assertThat(answerTo("swap-endian/part #{00FF00FF1122} " + reach))
                    .as("an odd byte at the end of the reach is not half a pair "
                            + "and is left where it is")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("and /WIDTH says how big a group is")
        void widthSaysHowBigAGroupIs() {
            assertThat(answerTo("swap-endian/width/part #{1122334455667788} 4 4"))
                    .isEqualTo("#{4433221155667788}");
            assertThat(answerTo("swap-endian/width #{1122334455667788} 8"))
                    .isEqualTo("#{8877665544332211}");
        }

        @Test
        @DisplayName("with no /PART it reaches the whole binary")
        void withoutPart() {
            assertThat(answerTo("swap-endian #{00FF00FF1122}"))
                    .isEqualTo("#{FF00FF002211}");
        }
    }

    @Nested
    @DisplayName("the set operations, and what they refuse")
    class Sets {

        @Test
        @DisplayName("a binary is not a set, and saying so is the declaration's job")
        void abinaryIsNotASet() {
            assertThat(answerTo(
                    "either error? e: try [difference #{0102} #{0203}] [e/id] ['worked]"))
                    .as("with a binary let through, the body cast it to a block and "
                            + "the interpreter came apart in Java rather than "
                            + "answering an error a script could catch")
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("but a map is, and a date is for DIFFERENCE alone")
        void whatTheyDoTake() {
            assertThat(answerTo("error? try [difference 1-Jan-2000 2-Jan-2000]"))
                    .isEqualTo("#(false)");
            assertThat(answerTo(
                    "either error? e: try [union 1-Jan-2000 2-Jan-2000] [e/id] ['worked]"))
                    .as("only DIFFERENCE declares a date, because only subtracting "
                            + "two of them means anything")
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and two blocks still work as they always did")
        void twoblocks() {
            assertThat(answerTo("mold difference [1 2] [2 3]").replace("\"", ""))
                    .isEqualTo("[1 3]");
        }
    }
}
