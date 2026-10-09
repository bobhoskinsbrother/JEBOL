package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class GuiMetricNative extends ScreenNative {

    private static final int THE_FIRST_DISPLAY = 0;

    public GuiMetricNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "gui-metric";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("keyword", Set.of(WordValue.TYPE)),
                Parameter.belongingTo("set", "val", TypesetValue.ANY_TYPE.members()),
                Parameter.belongingTo("display", "idx", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("set", "display");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            ScreenMetric metric = metricNamedBy(arguments.getFirst());
            int display = displayAskedFor(arguments, refinements);
            return measurementOf(evaluator.screen(), metric, display);
        };
    }

    private ScreenMetric metricNamedBy(Value asked) {
        if (!(asked instanceof WordValue word)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "gui-metric takes a word, not " + asked.datatype().literalSpelling());
        }
        return ScreenMetric.named(word.canonical()).orElseThrow(() ->
                Raised.of(EvaluationFailure.INVALID_ARG, "no host serves the metric " + word.canonical()));
    }

    private int displayAskedFor(List<Value> arguments, Set<String> refinements) {
        return argumentOf("display", 0, arguments, refinements)
                .map(this::displayNumberedBy)
                .orElse(THE_FIRST_DISPLAY);
    }

    private int displayNumberedBy(Value written) {
        if (!(written instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "a display is numbered with an integer, not " + written.datatype().literalSpelling());
        }
        return (int) magnitude;
    }

    private Value measurementOf(ScreenPort screen, ScreenMetric metric, int display) {
        if (metric.isACount()) {
            return IntegerValue.of(screen.displayCount());
        }
        if (!screen.hasADisplay()) {
            return PairValue.of(0, 0);
        }
        if (!servesDisplay(screen, display)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, "there is no display " + display);
        }
        return screen.measure(metric, display);
    }

    private boolean servesDisplay(ScreenPort screen, int display) {
        return display >= 0 && display < screen.displayCount();
    }
}
