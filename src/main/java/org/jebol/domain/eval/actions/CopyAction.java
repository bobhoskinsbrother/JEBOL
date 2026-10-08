package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CopyAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "copy";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value", whatCanBeCopied()),
                Parameter.belongingTo("part", "limit", Set.of()),
                Parameter.belongingTo("types", "kinds",
                        Set.of(TypesetValue.TYPE, Datatype.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "deep", "types");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer(nativeName(), List.of(arguments.getFirst()), refinements)
                .orElseGet(() -> copied(arguments, refinements));
    }

    private Set<Datatype> whatCanBeCopied() {
        Set<Datatype> accepted = new LinkedHashSet<>(Typeset.SERIES.members());
        accepted.addAll(Typeset.ANY_FUNCTION.members());
        accepted.addAll(Set.of(ActionValue.TYPE, ClosureValue.TYPE, CommandValue.TYPE,
                RebcodeValue.TYPE, StructValue.TYPE, PortValue.TYPE, MapValue.TYPE,
                ObjectValue.TYPE, BitsetValue.TYPE, ErrorValue.TYPE));
        return accepted;
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
                    case Datatype one -> Set.of(one);
                    case TypesetValue several -> several.members();
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
