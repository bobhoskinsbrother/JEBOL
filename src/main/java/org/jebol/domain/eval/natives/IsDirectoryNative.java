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
        return List.of(Parameter.of("target", Set.of(FileValue.TYPE, UrlValue.TYPE, NoneValue.TYPE)));
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
                    && liesOnDiskAsDirectory(evaluator, address.text())) {
                return LogicValue.of(true);
            }
            return LogicValue.of(endsWayDirectoryIsWritten(address.text()));
        };
    }

    private boolean endsWayDirectoryIsWritten(String path) {
        char last = path.charAt(path.length() - 1);
        return last == '/' || last == '\\';
    }

    private boolean liesOnDiskAsDirectory(Evaluator evaluator, String path) {
        granted.require(HostService.FILES);
        return runThroughFileSystem(() -> LogicValue.of(evaluator.ports().files().isDirectory(path))).isTruthy();
    }
}
