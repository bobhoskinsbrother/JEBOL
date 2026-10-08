package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class IsDirectoryNative extends HostNative {

    public IsDirectoryNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "dir?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("target", Set.of(FileValue.TYPE, UrlValue.TYPE, NoneValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("check");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value target = arguments.getFirst();
            if (!(target instanceof AnyStringValue address) || address.text().isEmpty()) {
                return LogicValue.of(false);
            }
            if (refinements.contains("check")
                    && target instanceof FileValue
                    && liesOnTheDiskAsADirectory(evaluator, address.text())) {
                return LogicValue.of(true);
            }
            return LogicValue.of(endsTheWayADirectoryIsWritten(address.text()));
        };
    }

    private boolean endsTheWayADirectoryIsWritten(String path) {
        char last = path.charAt(path.length() - 1);
        return last == '/' || last == '\\';
    }

    private boolean liesOnTheDiskAsADirectory(Evaluator evaluator, String path) {
        granted.require(HostService.FILES);
        return throughTheFileSystem(() -> LogicValue.of(evaluator.files().isDirectory(path))).isTruthy();
    }
}
