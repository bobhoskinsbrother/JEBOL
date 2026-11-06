package org.jebol.domain.eval.mathpresso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MathPressoFromTheSourceTest {

    private static final int ROWS = 30275;

    private static final String TABLE_DIGEST = "43662e064245d83d83f9315647dea6d9256ab9825c909313a3562de03046c13d";

    private static final double CLOSE_ENOUGH = 1e-12;

    private static final Pattern CALLS_JAVAS_MATHS = Pattern.compile(
            "\\b(exp|log|log2|log10|sin|cos|tan|sinh|cosh|tanh|asin|acos|atan|pow|atan2|hypot)\\(");

    private record Row(List<String> names, String expression, long[] given, String result, String after,
                       String messages) {
    }

    private record Answer(String result, String after, String messages) {
    }

    private static byte[] readTable() throws IOException {
        try (InputStream in = new GZIPInputStream(Objects.requireNonNull(
                MathPressoFromTheSourceTest.class.getResourceAsStream("/mathpresso/mathpresso-answers.tsv.gz")))) {
            return in.readAllBytes();
        }
    }

    private static List<Row> readRows() throws IOException {
        String table = new String(readTable(), StandardCharsets.ISO_8859_1);
        List<Row> rows = new ArrayList<>();
        for (String line : table.substring(0, table.length() - 1).split("\n", -1)) {
            String[] fields = line.split("\t", -1);
            rows.add(new Row(Arrays.stream(fields[0].split(" ")).filter(name -> !name.isEmpty()).toList(),
                    unescape(fields[1]), parseBits(fields[2]), fields[3], fields[4], fields[5]));
        }
        return rows;
    }

    private static long[] parseBits(String spaced) {
        return Arrays.stream(spaced.split(" ")).filter(each -> !each.isEmpty())
                .mapToLong(each -> Long.parseUnsignedLong(each, 16)).toArray();
    }

    private static String unescape(String escaped) {
        StringBuilder plain = new StringBuilder();
        for (int at = 0; at < escaped.length(); at++) {
            char each = escaped.charAt(at);
            if (each == '\\' && at + 1 < escaped.length()) {
                char next = escaped.charAt(++at);
                plain.append(next == 'n' ? '\n' : next == 't' ? '\t' : next);
            } else {
                plain.append(each);
            }
        }
        return plain.toString();
    }

    private static Answer answer(Row row) {
        long[] variables = Arrays.copyOf(row.given(), Math.max(row.given().length, row.names().size()));
        MathPressoCompilation compiled = MathPressoContext.withVariables(row.names())
                .compile(row.expression().getBytes(StandardCharsets.ISO_8859_1));
        StringBuilder messages = new StringBuilder();
        for (MathPressoMessage message : compiled.messages()) {
            messages.append(message.isError() ? "ERROR: " : "WARNING: ")
                    .append(message.text().replace("\n", "\\n").replace("\t", "\\t")).append("\\n");
        }
        if (compiled.expression().isEmpty()) {
            return new Answer("none", "", messages.toString());
        }
        long result = compiled.expression().get().evaluate(variables);
        return new Answer(hex(result), String.join(" ", Arrays.stream(variables).mapToObj(
                MathPressoFromTheSourceTest::hex).toList()), messages.toString());
    }

    private static String hex(long bits) {
        return String.format("%016x", bits);
    }

    private static boolean isClose(String expected, String actual) {
        if (expected.equals(actual)) {
            return true;
        }
        double wanted = Double.longBitsToDouble(Long.parseUnsignedLong(expected, 16));
        double got = Double.longBitsToDouble(Long.parseUnsignedLong(actual, 16));
        if (!Double.isFinite(wanted) || !Double.isFinite(got)) {
            return false;
        }
        return Math.abs(wanted - got) <= CLOSE_ENOUGH * Math.max(Math.abs(wanted), Math.abs(got));
    }

    private static boolean answersAlike(Row row, Answer answer, boolean allowingCloseness) {
        if (!row.messages().equals(answer.messages())) {
            return false;
        }
        if (!allowingCloseness || row.result().equals("none") || answer.result().equals("none")) {
            return row.result().equals(answer.result()) && row.after().equals(answer.after());
        }
        String[] wanted = row.after().split(" ");
        String[] got = answer.after().split(" ");
        if (wanted.length != got.length || !isClose(row.result(), answer.result())) {
            return false;
        }
        for (int each = 0; each < wanted.length; each++) {
            if (!isClose(wanted[each], got[each])) {
                return false;
            }
        }
        return true;
    }

    private static List<String> findDisagreements(boolean callingJavasMaths) throws IOException {
        List<String> disagreements = new ArrayList<>();
        for (Row row : readRows()) {
            if (CALLS_JAVAS_MATHS.matcher(row.expression()).find() != callingJavasMaths) {
                continue;
            }
            Answer answer = answer(row);
            if (!answersAlike(row, answer, callingJavasMaths)) {
                disagreements.add(row.names() + " " + row.expression() + " -> expected " + row.result() + " "
                        + row.after() + " " + row.messages() + " but answered " + answer);
            }
        }
        return disagreements;
    }

    @Test
    @DisplayName("the table is the one the probe wrote: thirty thousand rows and the digest beside them")
    void readsTheTableItSaysItReads() throws IOException, NoSuchAlgorithmException {
        byte[] table = readTable();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(table));
        assertThat(digest).isEqualTo(TABLE_DIGEST);
        assertThat(readRows()).hasSize(ROWS);
    }

    @Test
    @DisplayName("every expression that stays out of the maths library answers MathPresso's own bits, and its messages")
    void answersEveryExactRowBitForBit() throws IOException {
        assertThat(findDisagreements(false)).isEmpty();
    }

    @Test
    @DisplayName("an expression that calls the maths library answers close to glibc's, its NaNs and limits exactly")
    void answersLibraryRowsClosely() throws IOException {
        assertThat(findDisagreements(true)).isEmpty();
    }

    private static long evaluate(String expression, double... given) {
        List<String> names = List.of("x", "y", "z").subList(0, Math.max(1, given.length));
        long[] variables = Arrays.stream(given).mapToLong(Double::doubleToRawLongBits).toArray();
        if (variables.length == 0) {
            variables = new long[1];
        }
        return MathPressoContext.withVariables(names).compile(expression.getBytes(StandardCharsets.ISO_8859_1))
                .expression().orElseThrow().evaluate(variables);
    }

    private static double valueOf(String expression, double... given) {
        return Double.longBitsToDouble(evaluate(expression, given));
    }

    @Nested
    @DisplayName("where the JIT and the folding at compile time disagree, each answers as its own code does")
    class FoldingAgainstTheJit {

        @Test
        @DisplayName("isfinite of infinity is 1 at run time, where the comparison sees minus infinity <= 0, and 0 folded")
        void isFiniteOfInfinity() {
            assertThat(valueOf("isfinite(x)", Double.POSITIVE_INFINITY)).isEqualTo(1.0);
            assertThat(valueOf("isfinite(INF)")).isEqualTo(0.0);
            assertThat(valueOf("isfinite(x)", Double.NaN)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("abs of minus zero is minus zero at run time, from 0 - x and maxsd, and plus zero folded")
        void absOfMinusZero() {
            assertThat(evaluate("abs(x)", -0.0)).isEqualTo(Double.doubleToRawLongBits(-0.0));
            assertThat(evaluate("abs(-0)")).isZero();
        }

        @Test
        @DisplayName("minus zero negated is plus zero at run time, from 0 - x, and minus zero folded")
        void negatedZero() {
            assertThat(evaluate("-x", 0.0)).isZero();
            assertThat(evaluate("-0")).isEqualTo(Double.doubleToRawLongBits(-0.0));
        }

        @Test
        @DisplayName("greater than and at least are true of a NaN at run time, as NLE and NLT are, and false folded")
        void greaterThanNaN() {
            assertThat(valueOf("x > 1", Double.NaN)).isEqualTo(1.0);
            assertThat(valueOf("x >= 1", Double.NaN)).isEqualTo(1.0);
            assertThat(valueOf("x < 1", Double.NaN)).isEqualTo(0.0);
            assertThat(valueOf("NaN > 1")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("modulo truncates the quotient at run time, which is not fmod, and folds with fmod")
        void moduloTruncatesTheQuotient() {
            assertThat(valueOf("x % y", 1e17, 3)).isEqualTo(0.0);
            assertThat(valueOf("1e17 % 3")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("min of a variable and NaN swaps them, because the variable is read only, so minsd answers the variable")
        void minimumSwapsAReadOnlyOperand() {
            assertThat(valueOf("min(x, NaN)", 1.0)).isEqualTo(1.0);
            assertThat(valueOf("min(NaN, x)", 1.0)).isEqualTo(1.0);
            assertThat(valueOf("min(NaN, 1)")).isNaN();
            assertThat(valueOf("y = y; min(x, y)", Double.NaN, 2.0)).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("the JIT's one register for both sides of a variable with itself")
    class OneRegister {

        @Test
        @DisplayName("x % x is x - 1, because the quotient overwrites the divisor before it is multiplied back")
        void moduloOfItself() {
            assertThat(valueOf("x % x", 1.5)).isEqualTo(0.5);
            assertThat(valueOf("x % x", -1.0)).isEqualTo(-2.0);
            assertThat(valueOf("x % (x + 0)", 1.5)).isEqualTo(0.5);
            assertThat(valueOf("x % (x + y)", 1.5, -0.0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("copysign(x, x) is plus zero, because both masks land on the same register")
        void copySignOfItself() {
            assertThat(evaluate("copysign(x, x)", -2.5)).isZero();
            assertThat(valueOf("copysign(x, y)", 2.5, -1.0)).isEqualTo(-2.5);
        }
    }

    @Nested
    @DisplayName("the JIT rounds with SSE2 arithmetic, because AsmJit's CPU table is wiped after MathPresso reads it")
    class RoundingWithoutSse41 {

        @Test
        @DisplayName("ceil of a tiny negative number is plus zero, where roundsd would answer minus zero")
        void ceilOfTinyNegative() {
            assertThat(evaluate("ceil(x)", -Double.MIN_VALUE)).isZero();
            assertThat(evaluate("ceil(-1e-320)")).isEqualTo(Double.doubleToRawLongBits(-0.0));
        }

        @Test
        @DisplayName("a signalling NaN passes floor, ceil, trunc and round unquieted")
        void keepsASignallingNaN() {
            long signalling = 0x7FF0000000000123L;
            for (String rounding : List.of("floor(x)", "ceil(x)", "trunc(x)", "round(x)", "roundeven(x)")) {
                assertThat(evaluate(rounding, Double.longBitsToDouble(signalling))).as(rounding)
                        .isEqualTo(signalling);
            }
        }

        @Test
        @DisplayName("roundeven ties to even and round ties upwards, each from adding and taking away a shifter")
        void tiesFromTheShifter() {
            assertThat(valueOf("roundeven(x)", 2.5)).isEqualTo(2.0);
            assertThat(valueOf("roundeven(x)", -2.5)).isEqualTo(-2.0);
            assertThat(valueOf("round(x)", 2.5)).isEqualTo(3.0);
            assertThat(valueOf("round(x)", -2.5)).isEqualTo(-2.0);
            assertThat(valueOf("roundeven(x)", -1e17)).isEqualTo(-1e17);
        }
    }

    @Nested
    @DisplayName("the compiler's own faults are kept")
    class KeptFaults {

        @Test
        @DisplayName("an assignment from a variable keeps the constant before it, so x = 5; x = y; x answers 5")
        void keepsAStaleConstant() {
            long[] variables = {Double.doubleToRawLongBits(1), Double.doubleToRawLongBits(7)};
            long result = MathPressoContext.withVariables(List.of("x", "y")).compile(
                    "x = 5; x = y; x".getBytes(StandardCharsets.ISO_8859_1)).expression().orElseThrow()
                    .evaluate(variables);
            assertThat(Double.longBitsToDouble(result)).isEqualTo(5.0);
            assertThat(Double.longBitsToDouble(variables[0])).isEqualTo(7.0);
        }

        @Test
        @DisplayName("an exponent of sixteen reads past the powers of ten into the character classes stored after them")
        void readsPastThePowersOfTen() {
            assertThat(evaluate("1e16")).isEqualTo(0x4141414141414141L);
            assertThat(valueOf("1e-16")).isEqualTo(1 / Double.longBitsToDouble(0x4141414141414141L));
            assertThat(valueOf("1e15")).isEqualTo(1e15);
            assertThat(valueOf("1e17")).isEqualTo(1e17);
        }

        @Test
        @DisplayName("a unary plus on a number cannot be folded, so +1 does not compile")
        void refusesUnaryPlusOnANumber() {
            MathPressoCompilation compiled = MathPressoContext.withVariables(List.of("x"))
                    .compile("+1".getBytes(StandardCharsets.ISO_8859_1));
            assertThat(compiled.expression()).isEmpty();
            assertThat(compiled.messages()).containsExactly(
                    new MathPressoMessage(true, "Invalid unary operation '<none>'."));
            assertThat(valueOf("+x", 4.0)).isEqualTo(4.0);
        }

        @Test
        @DisplayName("a block's last statement needs its semicolon, so {1} does not compile and {1;} is NaN")
        void wantsASemicolonInABlock() {
            assertThat(MathPressoContext.withVariables(List.of("x"))
                    .compile("{1}".getBytes(StandardCharsets.ISO_8859_1)).expression()).isEmpty();
            assertThat(valueOf("{1;}")).isNaN();
            assertThat(valueOf("1;")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("the text stops at its first zero byte, as strlen stops it")
        void stopsAtTheFirstZero() {
            assertThat(valueOf("1 + 2\u0000 junk")).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("the maths library answers glibc's NaN for each function")
    class LibraryNaNs {

        @Test
        @DisplayName("out of its domain each function answers the sign glibc gives it")
        void answersTheDomainNaN() {
            assertThat(evaluate("sin(x)", Double.POSITIVE_INFINITY)).isEqualTo(0xFFF8000000000000L);
            assertThat(evaluate("asin(x)", 2.0)).isEqualTo(0x7FF8000000000000L);
            assertThat(evaluate("log10(x)", -1.0)).isEqualTo(0x7FF8000000000000L);
            assertThat(evaluate("log(x)", -1.0)).isEqualTo(0xFFF8000000000000L);
            assertThat(evaluate("sqrt(x)", -1.0)).isEqualTo(0xFFF8000000000000L);
        }

        @Test
        @DisplayName("pow follows C99 where Java does not, and drops a NaN's sign for an odd power")
        void powersAsC99() {
            assertThat(valueOf("pow(x, y)", 1.0, Double.NaN)).isEqualTo(1.0);
            assertThat(valueOf("pow(x, y)", -1.0, Double.POSITIVE_INFINITY)).isEqualTo(1.0);
            assertThat(evaluate("pow(x, y)", Double.longBitsToDouble(0xFFF8000000000000L), 3.0))
                    .isEqualTo(0x7FF8000000000000L);
            assertThat(evaluate("pow(x, y)", Double.longBitsToDouble(0xFFF8000000000000L), 2.0))
                    .isEqualTo(0xFFF8000000000000L);
        }

        @Test
        @DisplayName("log2 is exact at every power of two, subnormals included")
        void logarithmInBits() {
            for (int exponent = -1074; exponent < 1024; exponent++) {
                assertThat(valueOf("log2(x)", Math.scalb(1.0, exponent))).isEqualTo(exponent);
            }
            assertThat(valueOf("log2(x)", 10.0)).isCloseTo(3.321928094887362, within(1e-12));
            assertThat(valueOf("log2(x)", 0.0)).isEqualTo(Double.NEGATIVE_INFINITY);
            assertThat(evaluate("log2(x)", -1.0)).isEqualTo(0xFFF8000000000000L);
        }
    }
}
