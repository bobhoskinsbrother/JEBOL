package org.jebol.domain.eval.easing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EasingFromTheSourceTest {

    private static final double LAST_BITS_OF_ONE = 2 * Math.ulp(1.0);

    private static final Map<String, DoubleUnaryOperator> CURVES = Map.ofEntries(
            entry("linear", Easing::linear),
            entry("in-quad", Easing::inQuad),
            entry("in-cubic", Easing::inCubic),
            entry("in-quart", Easing::inQuart),
            entry("in-quint", Easing::inQuint),
            entry("in-sine", Easing::inSine),
            entry("in-circ", Easing::inCirc),
            entry("in-expo", Easing::inExpo),
            entry("in-elastic", Easing::inElastic),
            entry("in-back", Easing::inBack),
            entry("in-bounce", Easing::inBounce),
            entry("out-quad", Easing::outQuad),
            entry("out-cubic", Easing::outCubic),
            entry("out-quart", Easing::outQuart),
            entry("out-quint", Easing::outQuint),
            entry("out-sine", Easing::outSine),
            entry("out-circ", Easing::outCirc),
            entry("out-expo", Easing::outExpo),
            entry("out-elastic", Easing::outElastic),
            entry("out-back", Easing::outBack),
            entry("out-bounce", Easing::outBounce),
            entry("in-out-quad", Easing::inOutQuad),
            entry("in-out-cubic", Easing::inOutCubic),
            entry("in-out-quart", Easing::inOutQuart),
            entry("in-out-sine", Easing::inOutSine),
            entry("in-out-circ", Easing::inOutCirc),
            entry("in-out-expo", Easing::inOutExpo),
            entry("in-out-elastic", Easing::inOutElastic),
            entry("in-out-back", Easing::inOutBack),
            entry("in-out-bounce", Easing::inOutBounce));

    @ParameterizedTest(name = "{0} {1} is {2}")
    @CsvFileSource(resources = "/easing/easing-glibc.csv", numLinesToSkip = 1)
    @DisplayName("each curve answers what the extension's C answers on glibc, to the bit where it is arithmetic alone")
    void answersWhatTheCAnswers(String curve, double x, double glibcAnswers, boolean arithmeticAlone) {
        double answered = CURVES.get(curve).applyAsDouble(x);
        if (arithmeticAlone) {
            assertThat(answered).isEqualTo(glibcAnswers);
        } else {
            assertThat(answered).isCloseTo(glibcAnswers, within(LAST_BITS_OF_ONE));
        }
    }

    @Test
    @DisplayName("the table is the one its README describes: 186 rows over all thirty curves, and its checksum")
    void theTableIsWhatItSays() throws IOException, NoSuchAlgorithmException {
        byte[] table;
        try (InputStream read = Objects.requireNonNull(getClass().getResourceAsStream("/easing/easing-glibc.csv"))) {
            table = read.readAllBytes();
        }
        List<String> rows = new String(table, StandardCharsets.UTF_8).lines().skip(1).toList();
        assertThat(rows).hasSize(186);
        assertThat(rows.stream().map(row -> row.split(",")[0]).collect(Collectors.toSet()))
                .isEqualTo(CURVES.keySet());
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(table)))
                .isEqualTo("d28d430444622ac14cf115855bcc65da86e7734000e04fd0d115d3ed28c17eea");
    }

    @Test
    @DisplayName("out-bounce is continuous across each of its breakpoints, as the C's constants make it")
    void outBounceMeetsItselfAtEachBreakpoint() {
        for (double breakpoint : new double[]{4 / 11.0, 8 / 11.0, 9 / 10.0}) {
            double below = Easing.outBounce(Math.nextDown(breakpoint));
            assertThat(Easing.outBounce(breakpoint)).isCloseTo(below, within(1e-12));
        }
    }

    @Test
    @DisplayName("in-expo and out-expo answer their argument exactly at the ends, as the C's guards do")
    void theExponentialsAreExactAtTheEnds() {
        assertThat(Easing.inExpo(0.0)).isEqualTo(0.0);
        assertThat(Easing.outExpo(1.0)).isEqualTo(1.0);
        assertThat(Easing.inOutExpo(0.0)).isEqualTo(0.0);
        assertThat(Easing.inOutExpo(1.0)).isEqualTo(1.0);
    }
}
