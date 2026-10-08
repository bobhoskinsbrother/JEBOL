package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.FileValue;

import java.util.List;
import java.util.Set;

public class ChangeDirNative extends HostNative {

    private static final String WHERE_A_SHELL_SAYS_IT_IS_STANDING = "PWD";

    public ChangeDirNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "change-dir";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("path", Set.of(Datatype.FILE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WORKING_DIRECTORY);
            String asked = ((AnyStringValue) arguments.getFirst()).text();
            return throughTheFileSystem(() -> {
                evaluator.files().changeDirectory(asked);
                sayWhereTheInterpreterIsStanding(evaluator);
                return FileValue.of(evaluator.files().workingDirectory());
            });
        };
    }

    private void sayWhereTheInterpreterIsStanding(Evaluator evaluator) {
        if (granted.allow(HostService.ENVIRONMENT)) {
            evaluator.environment().nameHolds(
                    WHERE_A_SHELL_SAYS_IT_IS_STANDING, evaluator.files().workingDirectory());
        }
    }
}
