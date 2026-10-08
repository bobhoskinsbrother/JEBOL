package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Actions;
import org.jebol.domain.eval.CryptPort;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class TakeAction extends DefaultNative implements ActionValue {

    private final CryptPort cryptPort;

    public TakeAction(CryptPort cryptPort) {
        this.cryptPort = cryptPort;
    }

    @Override
    public String nativeName() {
        return "take";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series"),
                Parameter.belongingTo("part", "count", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "last", "deep", "all");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case NoneValue nothing -> nothing;
            case PortValue port when port.schemeName().equals("crypt") -> {
                cryptPort.refuseWhenClosed(port);
                cryptPort.update(port);
                yield cryptPort.read(port);
            }
            case RebolSeries series -> takenFrom(series, arguments, refinements);
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }

    private Value takenFrom(RebolSeries series, List<Value> arguments, Set<String> refinements) {
        if (refinements.contains("all")) {
            return takenSeveral(series, series.lengthFromHere());
        }
        if (!refinements.contains("part")) {
            return deepenedIfAsked(takenOne(series, refinements.contains("last")), refinements);
        }
        Optional<Value> howMuch = argumentOf("part", 0, arguments, refinements);
        if (howMuch.isPresent() && howMuch.get() instanceof RebolSeries upTo) {
            return deepenedIfAsked(takenSeveral(series.earlierOf(upTo),
                    Math.abs(upTo.index() - series.index())), refinements);
        }
        long wanted = howMuch.map(series::countUpTo).orElse(1L);
        return deepenedIfAsked(takenACount(series, wanted, refinements), refinements);
    }

    private Value takenACount(RebolSeries from, long wanted, Set<String> refinements) {
        RebolSeries series = from;
        long howMany;
        if (wanted < 0) {
            howMany = Math.min(-wanted, series.index() - 1L);
            series = series.atIndex((int) (series.index() - howMany));
        } else {
            howMany = Math.min(wanted, series.lengthFromHere());
        }
        if (refinements.contains("last")) {
            int tail = series.storageLength() + 1;
            long start = Math.max(1, tail - howMany);
            return takenSeveral(series.atIndex((int) start), howMany);
        }
        return takenSeveral(series, howMany);
    }

    private Value takenOne(RebolSeries series, boolean fromTheEnd) {
        RebolSeries takingFrom = fromTheEnd && series.lengthFromHere() > 0
                ? series.atIndex(series.index() + series.lengthFromHere() - 1)
                : series;
        return Actions.of(takingFrom).orElseThrow().takenOne();
    }

    private Value takenSeveral(RebolSeries series, long wanted) {
        return Actions.of(series).orElseThrow().takenSeveral(wanted);
    }

    private Value deepenedIfAsked(Value taken, Set<String> refinements) {
        return refinements.contains("deep") && !(taken instanceof ObjectValue)
                ? taken.copied(taken instanceof BlockValue)
                : taken;
    }
}
