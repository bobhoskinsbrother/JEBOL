package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.WindowArrangement;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class GuiMetricNative extends ScreenNative {

    private static final int FIRST_DISPLAY = 0;
    private static final String NO_SERVICE = "no-service";

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
                Parameter.of("keyword", Set.of(WordValue.TYPE)),
                Parameter.belongTo("set", "val", TypesetValue.ANY_TYPE.members()),
                Parameter.belongTo("display", "idx", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("set", "display");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            Optional<Value> setTo = refinementArgument("set", 0, arguments, refinements);
            Optional<WindowArrangement> arrangement = findArrangementNamedBy(arguments.getFirst());
            if (setTo.isPresent() && arrangement.isPresent()) {
                return arrange(evaluator.ports().screen(), setTo.get(), arrangement.get());
            }
            ScreenMetric metric = readMetricNamedBy(arguments.getFirst());
            int display = displayAskedFor(arguments, refinements);
            return measure(evaluator.ports().screen(), metric, display);
        };
    }

    private static Optional<WindowArrangement> findArrangementNamedBy(Value asked) {
        return asked instanceof WordValue word
                ? WindowArrangement.findArrangementNamed(word.canonical())
                : Optional.empty();
    }

    private Value arrange(ScreenPort screen, Value window, WindowArrangement arrangement) {
        if (!screen.hasDisplay()) {
            throw refuseForHost(NO_SERVICE, "there is no display to arrange a window on");
        }
        if (!(window instanceof GobValue gob) || !screen.isShowingWindow(gob)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, window);
        }
        runThroughScreen(() -> screen.arrangeWindow(gob, arrangement));
        return window;
    }

    private ScreenMetric readMetricNamedBy(Value asked) {
        if (!(asked instanceof WordValue word)) {
            throw argumentRefusal(asked, "keyword");
        }
        return ScreenMetric.findMetricNamed(word.canonical()).orElseThrow(() ->
                Raised.of(EvaluationFailure.COMMAND_FAIL, NoneValue.none()));
    }

    private int displayAskedFor(List<Value> arguments, Set<String> refinements) {
        return refinementArgument("display", 0, arguments, refinements)
                .map(this::displayNumberedBy)
                .orElse(FIRST_DISPLAY);
    }

    private int displayNumberedBy(Value written) {
        if (!(written instanceof IntegerValue(long magnitude))) {
            throw argumentRefusal(written, "idx");
        }
        return (int) magnitude;
    }

    private Value measure(ScreenPort screen, ScreenMetric metric, int display) {
        if (metric.isCount()) {
            return IntegerValue.of(screen.displayCount());
        }
        if (!screen.hasDisplay()) {
            return PairValue.of(0, 0);
        }
        if (!servesDisplay(screen, display)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, IntegerValue.of(display));
        }
        return screen.measure(metric, display);
    }

    private boolean servesDisplay(ScreenPort screen, int display) {
        return display >= 0 && display < screen.displayCount();
    }
}
