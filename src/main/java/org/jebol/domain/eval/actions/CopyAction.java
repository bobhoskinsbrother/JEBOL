package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ports.PortRequest;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.value.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CopyAction extends PortAction {

    public CopyAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "copy";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("value", listWhatCanBeCopied()),
                Parameter.belongTo("part", "limit", Set.of()),
                Parameter.belongTo("types", "kinds",
                        Set.of(TypesetValue.TYPE, Datatype.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("part", "deep", "types");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.getFirst() instanceof PortValue port && port.isAFile()
                        ? ports.readFromFileBehind(port, evaluator, howMuchToCopy(arguments, refinements))
                        : copy(arguments, refinements);
    }

    private PortRequest howMuchToCopy(List<Value> arguments, Set<String> refinements) {
        return new PortRequest(
                refinementArgument("part", 0, arguments, refinements),
                Optional.empty(),
                refinements);
    }

    private Set<Datatype> listWhatCanBeCopied() {
        Set<Datatype> accepted = new LinkedHashSet<>(TypesetValue.SERIES.members());
        accepted.addAll(TypesetValue.ANY_FUNCTION.members());
        accepted.addAll(Set.of(ActionValue.TYPE, ClosureValue.TYPE, CommandValue.TYPE,
                RebcodeValue.TYPE, StructValue.TYPE, PortValue.TYPE, MapValue.TYPE,
                ObjectValue.TYPE, BitsetValue.TYPE, ErrorValue.TYPE));
        return accepted;
    }

    private Value copy(List<Value> arguments, Set<String> refinements) {
        Value original = arguments.getFirst();
        boolean deeply = refinements.contains("deep");
        Set<Datatype> kinds = chooseWhichDatatypesToCopy(arguments, refinements);
        if (original instanceof StructValue struct) {
            if (!refinements.isEmpty()) {
                throw Raised.of(EvaluationFailure.BAD_REFINES,
                        "copy on a struct takes no refinements at all");
            }
            return struct.separateCopy();
        }
        if (!refinements.contains("part")) {
            return original.copy(deeply, kinds);
        }
        if (isObjectErrorModuleOrPort(original)) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "/part names the first so many of something with an order, and a "
                            + original.datatype().literalSpelling() + " has none");
        }
        if (!(original instanceof RebolSeries series)) {
            return refuseDatatype(original);
        }
        Value limit = refinementArgument("part", 0, arguments, refinements).orElseThrow();
        if (series instanceof ImageValue picture && limit instanceof PairValue(double x, double y)) {
            return picture.copyRectangle((int) x, (int) y);
        }
        return copyFront(series, limit, deeply, kinds);
    }

    private Value copyFront(
            RebolSeries series, Value limit, boolean deeply, Set<Datatype> kinds) {
        long wanted = series.countUpTo(limit);
        RebolSeries from = limit instanceof RebolSeries upTo ? series.earlierSeries(upTo) : series;
        if (wanted < 0) {
            int reaching = (int) Math.min(-wanted, from.index() - 1);
            from = from.atIndex(from.index() - reaching);
            wanted = reaching;
        }
        int taking = (int) Math.max(0, Math.min(wanted, from.lengthFromHere()));
        return from.copyFront(taking, deeply, kinds);
    }

    private Set<Datatype> chooseWhichDatatypesToCopy(List<Value> arguments, Set<String> refinements) {
        return refinementArgument("types", 0, arguments, refinements)
                .<Set<Datatype>>map(kinds -> switch (kinds) {
                    case Datatype one -> Set.of(one);
                    case TypesetValue several -> several.members();
                    default -> Copying.DEEP_COPY_DATATYPES;
                })
                .orElse(refinements.contains("deep")
                        ? Copying.DEEP_COPY_DATATYPES
                        : Copying.NOTHING_INSIDE);
    }

    private boolean isObjectErrorModuleOrPort(Value subject) {
        return subject instanceof ObjectValue || subject instanceof ErrorValue
                || subject instanceof ModuleValue || subject instanceof PortValue;
    }
}
