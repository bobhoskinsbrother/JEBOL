package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Copying;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class CopyAction extends DefaultNative {

    @Override
    public String name() {
        return "copy";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value", whatCanBeCopied()),
                Parameter.belongingTo("part", "limit", Set.of()),
                Parameter.belongingTo("types", "kinds",
                        Set.of(Datatype.TYPESET, Datatype.DATATYPE)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("part", "deep", "types");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer(name(), List.of(arguments.getFirst()), refinements)
                .orElseGet(() -> copied(arguments, refinements));
    }

    private Set<Datatype> whatCanBeCopied() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.SERIES.members());
        accepted.addAll(Typeset.ANY_FUNCTION.members());
        accepted.addAll(Set.of(Datatype.ACTION, Datatype.CLOSURE, Datatype.COMMAND,
                Datatype.REBCODE, Datatype.STRUCT, Datatype.PORT, Datatype.MAP,
                Datatype.OBJECT, Datatype.BITSET, Datatype.ERROR));
        return Set.copyOf(accepted);
    }

    private Value copied(List<Value> arguments, Set<String> refinements) {
        Value original = arguments.getFirst();
        boolean deeply = refinements.contains("deep");
        Set<Datatype> kinds = whichDatatypesToCopy(arguments, refinements);
        if (original instanceof StructValue struct) {
            if (!refinements.isEmpty()) {
                throw Raised.of(EvaluationFailure.BAD_REFINES,
                        "copy on a struct takes no refinements at all");
            }
            return struct.separateCopy();
        }
        if (!refinements.contains("part")) {
            return original.copied(deeply, kinds);
        }
        if (hasNoOrderToTakeTheFirstSoManyOf(original)) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "/part names the first so many of something with an order, and a "
                            + original.datatype().literalSpelling() + " has none");
        }
        if (!(original instanceof RebolSeries series)) {
            return refuseTheDatatype(original);
        }
        Value limit = argumentOf("part", 0, arguments, refinements).orElseThrow();
        if (series instanceof ImageValue picture && limit instanceof PairValue(double x, double y)) {
            return picture.rectangleCopied((int) x, (int) y);
        }
        return theFrontCopied(series, limit, deeply, kinds);
    }

    private Value theFrontCopied(
            RebolSeries series, Value limit, boolean deeply, Set<Datatype> kinds) {
        long wanted = series.countUpTo(limit);
        RebolSeries from = limit instanceof RebolSeries upTo ? series.earlierOf(upTo) : series;
        if (wanted < 0) {
            int reaching = (int) Math.min(-wanted, from.index() - 1);
            from = from.atIndex(from.index() - reaching);
            wanted = reaching;
        }
        int taking = (int) Math.max(0, Math.min(wanted, from.lengthFromHere()));
        return from.frontCopied(taking, deeply, kinds);
    }

    private Set<Datatype> whichDatatypesToCopy(List<Value> arguments, Set<String> refinements) {
        return argumentOf("types", 0, arguments, refinements)
                .<Set<Datatype>>map(kinds -> switch (kinds) {
                    case DatatypeValue one -> EnumSet.of(one.represents());
                    case TypesetValue several -> EnumSet.copyOf(several.members());
                    default -> Copying.WHAT_A_DEEP_COPY_COPIES;
                })
                .orElse(refinements.contains("deep")
                        ? Copying.WHAT_A_DEEP_COPY_COPIES
                        : Copying.NOTHING_INSIDE);
    }

    private boolean hasNoOrderToTakeTheFirstSoManyOf(Value subject) {
        return subject instanceof ObjectValue || subject instanceof ErrorValue
                || subject instanceof ModuleValue || subject instanceof PortValue;
    }
}
