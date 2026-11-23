package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.LocalFileConvention;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Set;

public class ToLocalFileNative extends HostNative {

    private final LocalFileConvention convention;

    public ToLocalFileNative(GrantedServices granted, LocalFileConvention convention) {
        super(granted);
        this.convention = convention;
    }

    @Override
    public String nativeName() {
        return "to-local-file";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("path", Set.of(FileValue.TYPE, StringValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("full");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            String path = ((AnyStringValue) arguments.getFirst()).text();
            if (!refinements.contains("full")) {
                return StringValue.of(convention.writeLocalPath(path));
            }
            String from = "";
            if (!path.startsWith("/")) {
                granted.require(HostService.WORKING_DIRECTORY);
                from = ((AnyStringValue) runThroughFileSystem(() ->
                        StringValue.of(evaluator.ports().files().workingDirectory()))).text();
            }
            return StringValue.of(convention.writeLocalPathResolvingDots(from + path));
        };
    }
}
