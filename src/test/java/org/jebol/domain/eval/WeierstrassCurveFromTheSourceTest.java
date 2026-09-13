package org.jebol.domain.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WeierstrassCurveFromTheSourceTest {

    static Stream<String> everyCurveCarriedHere() {
        return WeierstrassCurve.namesCarriedHere().stream();
    }

    @Test
    @DisplayName("the eight the JDK dropped are the eight carried here")
    void theEightTheJdkDroppedAreCarriedHere() {
        assertThat(WeierstrassCurve.namesCarriedHere())
                .containsExactlyInAnyOrder("secp192r1", "secp224r1", "secp192k1",
                        "secp224k1", "secp256k1", "bp256r1", "bp384r1", "bp512r1");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCurveCarriedHere")
    @DisplayName("the generator is on the curve and has the order written beside it")
    void theGeneratorIsOnTheCurveAndHasTheStatedOrder(String name) {
        WeierstrassCurve curve = WeierstrassCurve.named(name).orElseThrow();

        assertThat(curve.itsGeneratorIsOnItAndHasTheStatedOrder())
                .as("%s: a mistyped parameter would fail this and nothing else", name)
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCurveCarriedHere")
    @DisplayName("and the arithmetic obeys the laws a group obeys")
    void theArithmeticObeysTheGroupLaws(String name) {
        WeierstrassCurve curve = WeierstrassCurve.named(name).orElseThrow();
        SecureRandom randomly = new SecureRandom();
        BigInteger first = curve.aPrivateNumber(randomly);
        BigInteger second = curve.aPrivateNumber(randomly);

        WeierstrassCurve.Point onePoint = curve.timesTheGenerator(first);
        WeierstrassCurve.Point another = curve.timesTheGenerator(second);

        assertThat(curve.holds(onePoint)).as("%s: a multiple is on the curve", name)
                .isTrue();
        assertThat(curve.added(onePoint, another))
                .as("%s: adding commutes", name)
                .isEqualTo(curve.added(another, onePoint));
        assertThat(curve.timesTheGenerator(first.add(second).mod(curve.order())))
                .as("%s: the generator's multiples add", name)
                .isEqualTo(curve.added(onePoint, another));
        assertThat(curve.doubled(onePoint))
                .as("%s: doubling is adding to itself", name)
                .isEqualTo(curve.added(onePoint, onePoint));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCurveCarriedHere")
    @DisplayName("and the exchange agrees from either side")
    void theExchangeAgreesFromEitherSide(String name) {
        WeierstrassCurve curve = WeierstrassCurve.named(name).orElseThrow();
        SecureRandom randomly = new SecureRandom();
        BigInteger hers = curve.aPrivateNumber(randomly);
        BigInteger his = curve.aPrivateNumber(randomly);

        assertThat(curve.multiply(curve.timesTheGenerator(his), hers))
                .as("%s", name)
                .isEqualTo(curve.multiply(curve.timesTheGenerator(hers), his));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCurveCarriedHere")
    @DisplayName("a point survives the round trip through the wire form")
    void aPointSurvivesTheWireForm(String name) {
        WeierstrassCurve curve = WeierstrassCurve.named(name).orElseThrow();
        WeierstrassCurve.Point point =
                curve.timesTheGenerator(curve.aPrivateNumber(new SecureRandom()));

        byte[] written = new byte[1 + 2 * curve.coordinateWidth()];
        written[0] = 0x04;
        System.arraycopy(curve.coordinateOnTheWire(point.x()), 0,
                written, 1, curve.coordinateWidth());
        System.arraycopy(curve.coordinateOnTheWire(point.y()), 0,
                written, 1 + curve.coordinateWidth(), curve.coordinateWidth());

        assertThat(curve.pointFromTheWire(written)).as("%s", name)
                .contains(point);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyCurveCarriedHere")
    @DisplayName("and a point that is not on the curve is refused rather than used")
    void aPointNotOnTheCurveIsRefused(String name) {
        WeierstrassCurve curve = WeierstrassCurve.named(name).orElseThrow();

        byte[] written = new byte[1 + 2 * curve.coordinateWidth()];
        written[0] = 0x04;
        written[written.length - 1] = 0x01;

        assertThat(curve.pointFromTheWire(written)).as("%s", name).isEmpty();
    }
}
