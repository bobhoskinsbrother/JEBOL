package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FORM-OID writes an object identifier the way people write one.
 *
 * <p>{@code n-oid.c}, whose whole job is turning the ASN.1 encoding of an
 * object identifier into numbers separated by full stops. An identifier names
 * a thing in a registry that every certificate and key format leans on:
 * {@code 1.2.840.113549.1.1.1} is RSA encryption, and recognising it is how a
 * script knows what a certificate holds.
 *
 * <p>Two rules make the encoding. The first byte carries <em>two</em> numbers,
 * {@code oid[0] / 40} and {@code oid[0] % 40} -- which is why every identifier
 * anyone writes begins 0, 1 or 2, since the first arc cannot pass 2 without
 * the division carrying. Every byte after is base 128, seven bits at a time,
 * high bit set on all but the last of its group.
 *
 * <p>Every expectation below was run on a real 3.22.1 before being written
 * down. One case deliberately disagrees with it and says so, because the
 * binary is wrong there -- see {@link TheCsOwnBufferBug}.
 *
 * <p>Specified in {@code spec/natives.allium} under FORM-OID.
 */
class FormOidFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String oidOf(String binaryLiteral) {
        return answerTo("form-oid " + binaryLiteral);
    }

    @Nested
    @DisplayName("the first byte is two arcs, divided by forty")
    class TheFirstByte {

        @Test
        @DisplayName("42 is one and two, which is where every OID a script meets begins")
        void fortyTwoIsOneAndTwo() {
            assertThat(oidOf("#{2A}")).isEqualTo("\"1.2\"");
        }

        @Test
        @DisplayName("zero is nought and nought")
        void zeroIsNoughtAndNought() {
            assertThat(oidOf("#{00}")).isEqualTo("\"0.0\"");
        }

        @Test
        @DisplayName("thirty-nine is the last of the first arc, and forty starts the next")
        void thirtyNineAndForty() {
            assertThat(oidOf("#{27}")).isEqualTo("\"0.39\"");
            assertThat(oidOf("#{28}")).isEqualTo("\"1.0\"");
        }

        @Test
        @DisplayName("one twenty-seven is three and seven")
        void oneTwentySevenSplits() {
            assertThat(oidOf("#{7F}")).isEqualTo("\"3.7\"");
        }

        @Test
        @DisplayName("and the largest byte gives six and fifteen, so no first arc passes six")
        void theLargestByte() {
            assertThat(oidOf("#{FF}")).isEqualTo("\"6.15\"");
        }
    }

    @Nested
    @DisplayName("the bytes after it are base 128")
    class TheContinuationGroups {

        @Test
        @DisplayName("a byte under 128 is its own group")
        void aSmallByteStandsAlone() {
            assertThat(oidOf("#{2A00}")).isEqualTo("\"1.2.0\"");
            assertThat(oidOf("#{2A09}")).isEqualTo("\"1.2.9\"");
            assertThat(oidOf("#{2A0A}")).isEqualTo("\"1.2.10\"");
            assertThat(oidOf("#{2A63}")).isEqualTo("\"1.2.99\"");
        }

        @Test
        @DisplayName("two bytes with the high bit set on the first make one number")
        void twoBytesMakeOneNumber() {
            assertThat(oidOf("#{2A8100}")).isEqualTo("\"1.2.128\"");
            assertThat(oidOf("#{2A817F}")).isEqualTo("\"1.2.255\"");
        }

        @Test
        @DisplayName("and three bytes reach well past what one would hold")
        void threeBytesReachFurther() {
            assertThat(oidOf("#{2A81FF7F}")).isEqualTo("\"1.2.32767\"");
        }

        @Test
        @DisplayName("groups follow one another, each ending where its high bit clears")
        void groupsFollowOneAnother() {
            assertThat(oidOf("#{2A7F7F}")).isEqualTo("\"1.2.127.127\"");
            assertThat(oidOf("#{2A0F0F}")).isEqualTo("\"1.2.15.15\"");
            assertThat(oidOf("#{2A8100000000}")).isEqualTo("\"1.2.128.0.0.0\"");
        }

        @Test
        @DisplayName("RSA encryption, the identifier a certificate reader meets first")
        void theRsaIdentifier() {
            assertThat(oidOf("#{2A864886F70D010101}"))
                    .isEqualTo("\"1.2.840.113549.1.1.1\"");
        }
    }

    @Nested
    @DisplayName("the edges of the input")
    class TheEdges {

        @Test
        @DisplayName("no bytes, no arcs: an empty string rather than a refusal")
        void anEmptyBinaryGivesAnEmptyString() {
            assertThat(oidOf("#{}")).isEqualTo("\"\"");
        }

        @Test
        @DisplayName("a group whose last byte never comes is dropped, silently")
        void anUnterminatedGroupIsDropped() {
            assertThat(oidOf("#{2A81}")).isEqualTo("\"1.2\"");
            assertThat(oidOf("#{2A818181}")).isEqualTo("\"1.2\"");
        }

        @Test
        @DisplayName("it reads from where the binary stands, not from its head")
        void itReadsFromThePosition() {
            assertThat(answerTo("form-oid next #{FF2A}")).isEqualTo("\"1.2\"");
            assertThat(answerTo("form-oid skip #{FFFF2A} 2")).isEqualTo("\"1.2\"");
        }

        @Test
        @DisplayName("and answers a string, whatever it was given")
        void itAlwaysAnswersAString() {
            assertThat(answerTo("string? form-oid #{2A}")).isEqualTo("#(true)");
            assertThat(answerTo("string? form-oid #{}")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a value that is not a binary is refused by the declaration")
        void anythingElseIsRefused() {
            assertThat(answerTo("""
                    e: try [form-oid "1.2"] e/id""")).isEqualTo("expect-arg");
            assertThat(answerTo("""
                    e: try [form-oid 42] e/id""")).isEqualTo("expect-arg");
            assertThat(answerTo("""
                    e: try [form-oid none] e/id""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("and it needs one, so no argument is no-arg")
        void itNeedsAnArgument() {
            assertThat(answerTo("""
                    e: try [form-oid] e/id""")).isEqualTo("no-arg");
        }
    }

    @Nested
    @DisplayName("where a real 3.22.1 is wrong and this is not")
    class TheCsOwnBufferBug {

        @Test
        @DisplayName("a two-byte OID whose second arc needs three digits")
        void theThreeDigitSecondArc() {
            // The binary answers "1.2.10" followed by a NUL byte here, and
            // "1.2.12" and a NUL for #{2A7F}. The output is sized at three
            // bytes per input byte, one short for this exact shape, and the
            // retry after the truncated write does not take. A string with a
            // NUL in the middle of a number is not a rule anybody meant.
            assertThat(oidOf("#{2A64}")).isEqualTo("\"1.2.100\"");
            assertThat(oidOf("#{2A7F}")).isEqualTo("\"1.2.127\"");
        }

        @Test
        @DisplayName("and it carries no NUL, which is how the fault showed itself")
        void theAnswerHoldsNoNul() {
            assertThat(answerTo("""
                    none? find form-oid #{2A64} #"^(00)\"""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("while the shapes either side of it were right all along")
        void theNeighbouringShapesAreUnchanged() {
            assertThat(oidOf("#{2A63}")).isEqualTo("\"1.2.99\"");
            assertThat(oidOf("#{2A817F}")).isEqualTo("\"1.2.255\"");
            assertThat(oidOf("#{2A64FF7F}")).isEqualTo("\"1.2.100.16383\"");
        }
    }
}
