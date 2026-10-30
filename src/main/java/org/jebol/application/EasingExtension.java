package org.jebol.application;

import org.jebol.domain.eval.easing.Easing;
import org.jebol.domain.value.DecimalValue;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

final class EasingExtension {

    private static final String HEADER = "REBOL [Title: {Rebol Easing Extension} Type: module Exports: [tween]]";

    private static final String TWEEN = """
            tween: func [
                {Interpolates a value at t using easing function}
                val1     [number! pair! tuple!] "Value to interpolate from"
                val2     [number! pair! tuple!] "Value to interpolate to"
                t        [decimal!]             "Value from 0.0 to 1.0"
                ease     [any-word!]            "Easing function"
            ][
                t: self/:ease t
                to val1 add val1 * (1 - t) val2 * t
            ]""";

    private EasingExtension() {
    }

    static BundledExtension create() {
        return new BundledExtension(new Extension("easing", "1.0.0", List.of(
                makeCommand("linear", "{Modeled after the line     y = x}", Easing::linear),
                makeCommand("in-quad", "{Modeled after the parabola y = x^^2}", Easing::inQuad),
                makeCommand("in-cubic", "{Modeled after the cubic    y = x^^3}", Easing::inCubic),
                makeCommand("in-quart", "{Modeled after the quartic  y = x^^4}", Easing::inQuart),
                makeCommand("in-quint", "{Modeled after the quintic  y = x^^5}", Easing::inQuint),
                makeCommand("in-sine", "{Modeled after quarter-cycle of sine wave}", Easing::inSine),
                makeCommand("in-circ", "{Modeled after shifted quadrant IV of unit circle}", Easing::inCirc),
                makeCommand("in-expo", "{Modeled after the exponential function y = 2^^(10(x - 1))}", Easing::inExpo),
                makeCommand("in-elastic", "{Modeled after the damped sine wave y = sin(13pi/2*x)*pow(2, 10 * (x - 1))}",
                        Easing::inElastic),
                makeCommand("in-back", "{Modeled after the overshooting cubic y = x^^3-x*sin(x*pi)}", Easing::inBack),
                makeCommand("in-bounce", "{}", Easing::inBounce),
                makeCommand("out-quad", "{Modeled after the parabola y = -x^^2 + 2x}", Easing::outQuad),
                makeCommand("out-cubic", "{Modeled after the cubic    y = (x - 1)^^3 + 1}", Easing::outCubic),
                makeCommand("out-quart", "{Modeled after the quartic  y = 1 - (x - 1)^^4}", Easing::outQuart),
                makeCommand("out-quint", "{Modeled after the quintic  y = (x - 1)^^5 + 1}", Easing::outQuint),
                makeCommand("out-sine", "{Modeled after quarter-cycle of sine wave (different phase)}", Easing::outSine),
                makeCommand("out-circ", "{Modeled after shifted quadrant II of unit circle}", Easing::outCirc),
                makeCommand("out-expo", "{Modeled after the exponential function y = -2^^(-10x) + 1}", Easing::outExpo),
                makeCommand("out-elastic",
                        "{Modeled after the damped sine wave y = sin(-13pi/2*(x + 1))*pow(2, -10x) + 1}",
                        Easing::outElastic),
                makeCommand("out-back", "{Modeled after the overshooting cubic y = 1-((1-x)^^3-(1-x)*sin((1-x)*pi))}",
                        Easing::outBack),
                makeCommand("out-bounce", "{}", Easing::outBounce),
                makeCommand("in-out-quad", "{Modeled after the piecewise parabola  y = (1/2)((2x)^^2) for x [0, 0.5) "
                        + "and y = -(1/2)((2x-1)*(2x-3) - 1) for x [0.5, 1]}", Easing::inOutQuad),
                makeCommand("in-out-cubic", "{Modeled after the piecewise cubic     y = (1/2)((2x)^^3) for x [0, 0.5) "
                        + "and y = (1/2)((2x-2)^^3 + 2) for x [0.5, 1]}", Easing::inOutCubic),
                makeCommand("in-out-quart", "{Modeled after the piecewise quadratic y = (1/2)((2x)^^4) for x [0, 0.5) "
                        + "and y = (1/2)((2x-2)^^4 + 2) for x [0.5, 1]}", Easing::inOutQuart),
                makeCommand("in-out-sine", "{Modeled after half sine wave}", Easing::inOutSine),
                makeCommand("in-out-circ", "{Modeled after the piecewise circular function "
                        + "y = (1/2)(1 - sqrt(1 - 4x^^2)) for x [0, 0.5) "
                        + "and y = (1/2)(sqrt(-(2x - 3)*(2x - 1)) + 1) for x [0.5, 1]}", Easing::inOutCirc),
                makeCommand("in-out-expo", "{Modeled after the piecewise exponential y = (1/2)2^^(10(2x - 1)) "
                        + "for x [0, 0.5) and y = y = -(1/2)*2^^(-10(2x - 1))) + 1 for x [0.5, 1]}",
                        Easing::inOutExpo),
                makeCommand("in-out-elastic", "{Modeled after the piecewise exponentially-damped sine wave "
                        + "y = (1/2)*sin(13pi/2*(2*x))*pow(2, 10 * ((2*x) - 1)) for x [0, 0.5) "
                        + "and y = (1/2)*(sin(-13pi/2*((2x-1)+1))*pow(2,-10(2*x-1)) + 2) for x [0.5, 1]}",
                        Easing::inOutElastic),
                makeCommand("in-out-back", "{Modeled after the piecewise overshooting cubic function "
                        + "y = (1/2)*((2x)^3-(2x)*sin(2*x*pi)) for x [0, 0.5) "
                        + "and y = (1/2)*(1-((1-x)^3-(1-x)*sin((1-x)*pi))+1) for x [0.5, 1]}",
                        Easing::inOutBack),
                makeCommand("in-out-bounce", "{}", Easing::inOutBounce)))
                .withScript(TWEEN), HEADER);
    }

    private static ExtensionCommand makeCommand(String name, String help, DoubleUnaryOperator easing) {
        return new ExtensionCommand(name, help + " x [decimal!]", call -> DecimalValue.of(
                easing.applyAsDouble(((DecimalValue) call.arguments().getFirst()).quantity())));
    }
}
