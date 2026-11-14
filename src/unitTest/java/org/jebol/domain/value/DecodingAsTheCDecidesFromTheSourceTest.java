package org.jebol.domain.value;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecodingAsTheCDecidesFromTheSourceTest {

    private static final String REPLACED = "�";

    private static final String FALSE_PAIR = "-a!=p!";

    private static final String FALSE_PAIR_JOINED = Character.toString(0x28421);

    private static final String FALSE_PAIR_CUT_AND_JOINED = Character.toString(0x28400);

    private static final String REAL_PAIR_JOINED = Character.toString(0x10000);

    private static byte[] hex(String digits) {
        return HexFormat.of().parseHex(digits);
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            joined.writeBytes(part);
        }
        return joined.toByteArray();
    }

    private static UtfStringDecoder.Decoded decode(byte[] bytes) {
        return UtfStringDecoder.decode(bytes, 0);
    }

    private static String textOf(UtfStringDecoder.Decoded decoded) {
        assertThat(decoded).isInstanceOf(UtfStringDecoder.Text.class);
        return ((UtfStringDecoder.Text) decoded).text();
    }

    private static int refusedAt(UtfStringDecoder.Decoded decoded) {
        assertThat(decoded).isInstanceOf(UtfStringDecoder.Refused.class);
        return ((UtfStringDecoder.Refused) decoded).acceptedRunEnd();
    }

    @Nested
    @DisplayName("bytes strict UTF-8 accepts")
    class StrictlyValid {

        @Test
        @DisplayName("no bytes at all are empty text")
        void decodesNothing() {
            assertThat(textOf(decode(new byte[0]))).isEmpty();
        }

        @Test
        @DisplayName("one character of each length reads as itself")
        void decodesEachLength() {
            assertThat(textOf(decode(hex("41C3A9E282ACF09F9880")))).isEqualTo("Aé€😀");
        }

        @Test
        @DisplayName("a pair-shaped ASCII run in good text is left as it is, because the decode never rejected")
        void leavesAPairShapedRunInGoodText() {
            assertThat(textOf(decode(ascii(FALSE_PAIR)))).isEqualTo(FALSE_PAIR);
        }
    }

    @Nested
    @DisplayName("an invalid byte with no pair after it")
    class InvalidWithoutAPair {

        @Test
        @DisplayName("first is refused at the start")
        void refusesAnInvalidFirstByte() {
            assertThat(refusedAt(decode(join(hex("E1"), ascii("bc hello ok"))))).isZero();
        }

        @Test
        @DisplayName("in the middle is refused after the bytes before it")
        void refusesAnInvalidMiddleByte() {
            assertThat(refusedAt(decode(join(ascii("ab"), hex("FF"), ascii("cd"))))).isEqualTo(2);
        }

        @Test
        @DisplayName("last is refused after the bytes before it")
        void refusesAnInvalidLastByte() {
            assertThat(refusedAt(decode(join(ascii("abc"), hex("80"))))).isEqualTo(3);
        }

        @Test
        @DisplayName("a sequence cut at the end is refused at its lead")
        void refusesACutSequence() {
            assertThat(refusedAt(decode(join(ascii("abc"), hex("E182"))))).isEqualTo(3);
        }

        @Test
        @DisplayName("a pair-shaped run before it does not help, because nothing had been rejected yet")
        void refusesAfterAPairShapedRunInGoodText() {
            assertThat(refusedAt(decode(join(ascii(FALSE_PAIR), hex("FF"))))).isEqualTo(6);
        }

        @Test
        @DisplayName("half a real surrogate pair is refused at its lead")
        void refusesALoneSurrogateHalf() {
            assertThat(refusedAt(decode(hex("41EDA08042")))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("a pair after a rejected byte resets the decode")
    class APairResets {

        @Test
        @DisplayName("an invalid lead with ASCII after it, then the pair, as in u-parse.c")
        void acceptsTheCaseFromUParse() {
            assertThat(textOf(decode(join(hex("E1626320"), ascii(FALSE_PAIR + " ok")))))
                    .isEqualTo(REPLACED + "bc " + FALSE_PAIR_JOINED + " ok");
        }

        @Test
        @DisplayName("an invalid first byte with the pair straight after it")
        void acceptsAnInvalidFirstByteThenThePair() {
            assertThat(textOf(decode(join(hex("80"), ascii(FALSE_PAIR)))))
                    .isEqualTo(REPLACED + FALSE_PAIR_JOINED);
        }

        @Test
        @DisplayName("an overlong lead keeps both of its bytes, each replaced")
        void replacesEachByteOfAnOverlong() {
            assertThat(textOf(decode(join(hex("C080"), ascii(FALSE_PAIR)))))
                    .isEqualTo(REPLACED + REPLACED + FALSE_PAIR_JOINED);
        }

        @Test
        @DisplayName("a sequence cut short before the pair")
        void acceptsACutSequenceThenThePair() {
            assertThat(textOf(decode(join(hex("E182"), ascii(FALSE_PAIR)))))
                    .isEqualTo(REPLACED + FALSE_PAIR_JOINED);
        }

        @Test
        @DisplayName("a real pair of ED sequences is joined the same way")
        void joinsARealPair() {
            assertThat(textOf(decode(hex("EDA080EDB080")))).isEqualTo(REAL_PAIR_JOINED);
            assertThat(textOf(decode(hex("41EDA080EDB08042")))).isEqualTo("A" + REAL_PAIR_JOINED + "B");
        }

        @Test
        @DisplayName("good bytes after the pair are read again, and a failure among them is refused where they end")
        void refusesAfterThePairWhereTheAcceptedRunEnds() {
            assertThat(refusedAt(decode(join(hex("FF"), ascii(FALSE_PAIR + "ab"), hex("80"))))).isEqualTo(9);
        }
    }

    @Nested
    @DisplayName("the byte after a pair is copied without being read")
    class TheByteAfterAPair {

        @Test
        @DisplayName("an invalid byte there stays in the text, replaced")
        void keepsAnInvalidByteThere() {
            assertThat(textOf(decode(join(hex("FF"), ascii(FALSE_PAIR), hex("FF")))))
                    .isEqualTo(REPLACED + FALSE_PAIR_JOINED + REPLACED);
            assertThat(textOf(decode(join(hex("FF"), ascii(FALSE_PAIR), hex("FF"), ascii("zz")))))
                    .isEqualTo(REPLACED + FALSE_PAIR_JOINED + REPLACED + "zz");
        }

        @Test
        @DisplayName("after a real pair too")
        void keepsAnInvalidByteAfterARealPair() {
            assertThat(textOf(decode(hex("41EDA080EDB080FF42"))))
                    .isEqualTo("A" + REAL_PAIR_JOINED + REPLACED + "B");
        }

        @Test
        @DisplayName("a lead there is not read, so ASCII after it is accepted")
        void keepsALoneLeadThere() {
            assertThat(textOf(decode(join(hex("FF"), ascii(FALSE_PAIR), hex("C3"), ascii("z")))))
                    .isEqualTo(REPLACED + FALSE_PAIR_JOINED + REPLACED + "z");
        }

        @Test
        @DisplayName("a lead there is not read, so its continuation is read alone and refused")
        void refusesTheContinuationOfALeadThere() {
            assertThat(refusedAt(decode(join(hex("FF"), ascii(FALSE_PAIR), hex("C3A9"))))).isZero();
            assertThat(refusedAt(decode(join(hex("FF"), ascii(FALSE_PAIR), hex("E182"))))).isZero();
        }
    }

    @Nested
    @DisplayName("the pair check past either end")
    class PastTheEnds {

        @Test
        @DisplayName("past the last byte it reads the terminator, a zero, which can end a pair")
        void readsZeroPastTheLastByte() {
            assertThat(textOf(decode(join(hex("FF"), ascii("-a!=p")))))
                    .isEqualTo(REPLACED + FALSE_PAIR_CUT_AND_JOINED);
        }

        @Test
        @DisplayName("before the first byte it reads nothing, so no pair begins there")
        void beginsNoPairBeforeTheStart() {
            byte[] bytes = join(ascii("-"), hex("A1"), ascii("!=p!"));
            assertThat(refusedAt(UtfStringDecoder.decode(bytes, 1))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("where the decode starts")
    class WhereItStarts {

        @Test
        @DisplayName("from a position into the bytes, with the refusal counted from the head")
        void startsFromAPosition() {
            byte[] bytes = join(ascii("ab"), hex("FF"), ascii("cd"));
            assertThat(refusedAt(UtfStringDecoder.decode(bytes, 1))).isEqualTo(2);
            assertThat(textOf(UtfStringDecoder.decode(bytes, 3))).isEqualTo("cd");
        }

        @Test
        @DisplayName("after a UTF-8 byte order mark, which is not part of the text")
        void startsAfterAByteOrderMark() {
            assertThat(textOf(decode(join(hex("EFBBBF"), ascii("abc"))))).isEqualTo("abc");
            assertThat(textOf(decode(join(hex("EFBBBFFF"), ascii(FALSE_PAIR + " z")))))
                    .isEqualTo(REPLACED + FALSE_PAIR_JOINED + " z");
        }

        @Test
        @DisplayName("a UTF-8 byte order mark with nothing after it is refused, because the C copies nothing as failure")
        void refusesAMarkAlone() {
            assertThat(refusedAt(decode(hex("EFBBBF")))).isEqualTo(3);
        }

        @Test
        @DisplayName("a UTF-16 byte order mark with nothing after it is empty text")
        void decodesAUtf16MarkAlone() {
            assertThat(textOf(decode(hex("FEFF")))).isEmpty();
            assertThat(textOf(decode(hex("FFFE")))).isEmpty();
        }

        @Test
        @DisplayName("a refusal after a byte order mark counts the mark")
        void countsTheMarkInARefusal() {
            assertThat(refusedAt(decode(join(hex("EFBBBF"), ascii("ab"), hex("80"))))).isEqualTo(5);
        }

        @Test
        @DisplayName("a UTF-16 mark decodes as UTF-16, whatever pair-shaped bytes follow")
        void decodesUtf16ByItsMark() {
            assertThat(textOf(decode(join(hex("FFFE"), ascii("x-a!=p!y")))))
                    .isEqualTo(new String(ascii("x-a!=p!y"), StandardCharsets.UTF_16LE));
            assertThat(textOf(decode(hex("FEFF00410042")))).isEqualTo("AB");
        }

        @Test
        @DisplayName("line endings are left as they are, for the caller to standardise")
        void leavesLineEndings() {
            assertThat(textOf(decode(join(hex("FF"), ascii(FALSE_PAIR + "\r\nx")))))
                    .isEqualTo(REPLACED + FALSE_PAIR_JOINED + "\r\nx");
        }
    }
}
