package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class RequestFileNative extends WindowNative {

    public RequestFileNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "request-file";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.belongingTo("file", "name", Set.of(Datatype.FILE)),
                Parameter.belongingTo("title", "text", Set.of(Datatype.STRING)),
                Parameter.belongingTo("filter", "list", Set.of(Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("save", "multi", "file", "title", "filter");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            List<String> filters = filterPairsIn(arguments, refinements);
            return throughTheWindows(() -> {
                List<String> chosen = evaluator.windows().chooseFiles(
                        refinements.contains("save"),
                        refinements.contains("multi"),
                        textGivenFor("file", arguments, refinements),
                        textGivenFor("title", arguments, refinements),
                        filters);
                if (refinements.contains("multi")) {
                    return BlockValue.block(chosen.stream()
                            .<Value>map(one -> StringValue.of(one, Datatype.FILE))
                            .toList());
                }
                return chosen.isEmpty() ? NoneValue.none() : StringValue.of(chosen.getFirst(), Datatype.FILE);
            });
        };
    }

    private List<String> filterPairsIn(List<Value> arguments, Set<String> refinements) {
        return argumentOf("filter", 0, arguments, refinements)
                .map(listed -> ((BlockValue) listed).remaining())
                .map(this::pairedUp)
                .orElse(List.of());
    }

    private List<String> pairedUp(List<Value> items) {
        if (items.size() % 2 != 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a filter list pairs a name with a pattern, and "
                            + items.size() + " items do not pair up");
        }
        return items.stream().map(Molder::form).toList();
    }
}
