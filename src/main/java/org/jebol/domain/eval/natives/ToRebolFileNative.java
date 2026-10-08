package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Set;

public class ToRebolFileNative extends DefaultNative {

    private static final char REBOLS_SEPARATOR = '/';

    @Override
    public String nativeName() {
        return "to-rebol-file";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("path", Set.of(Datatype.FILE, Datatype.STRING)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> StringValue.of(
                oneSlashPerRunOfSeparators(((StringValue) arguments.getFirst()).text()),
                Datatype.FILE);
    }

    private String oneSlashPerRunOfSeparators(String path) {
        StringBuilder built = new StringBuilder(path.length());
        boolean afterASeparator = false;
        for (int at = 0; at < path.length(); at++) {
            char letter = path.charAt(at);
            if (!isASeparator(letter)) {
                built.append(letter);
                afterASeparator = false;
            } else if (!afterASeparator) {
                built.append(REBOLS_SEPARATOR);
                afterASeparator = true;
            }
        }
        return built.toString();
    }

    private boolean isASeparator(char letter) {
        return letter == '/' || letter == '\\';
    }
}
