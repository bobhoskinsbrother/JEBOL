package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.LocalFileSeparator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Set;

public class ToLocalFileNative extends HostNative {

    private static final char REBOLS_SEPARATOR = '/';

    private static final char DOT = '.';

    private final LocalFileSeparator localSeparator;

    public ToLocalFileNative(GrantedServices granted, LocalFileSeparator localSeparator) {
        super(granted);
        this.localSeparator = localSeparator;
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
            boolean resolvingDots = refinements.contains("full");
            String from = "";
            if (resolvingDots && !path.startsWith("/")) {
                granted.require(HostService.WORKING_DIRECTORY);
                from = ((AnyStringValue) runThroughFileSystem(() ->
                        StringValue.of(evaluator.ports().files().workingDirectory()))).text();
            }
            return StringValue.of(localPath(from + path, resolvingDots, localSeparator.separator()));
        };
    }

    private String localPath(String path, boolean resolvingDots, char separator) {
        StringBuilder built = new StringBuilder();
        int at = 0;
        while (at < path.length()) {
            if (resolvingDots) {
                at = skipAnyDots(path, at, built, separator);
            }
            while (at < path.length()) {
                char letter = path.charAt(at);
                at++;
                if (letter == REBOLS_SEPARATOR) {
                    if (built.isEmpty() || built.charAt(built.length() - 1) != separator) {
                        built.append(separator);
                    }
                    break;
                }
                built.append(letter);
            }
        }
        return built.toString();
    }

    private int skipAnyDots(String path, int at, StringBuilder built, char separator) {
        if (at >= path.length() || path.charAt(at) != DOT) {
            return at;
        }
        boolean twoDots = at + 1 < path.length() && path.charAt(at + 1) == DOT;
        int after = at + (twoDots ? 2 : 1);
        boolean wholeSegment = after >= path.length() || path.charAt(after) == REBOLS_SEPARATOR;
        if (!wholeSegment) {
            return at;
        }
        if (twoDots) {
            backOutOneDirectory(built, separator);
        }
        return after;
    }

    private void backOutOneDirectory(StringBuilder built, char separator) {
        int length = built.length() > 2 ? built.length() - 2 : 0;
        while (length > 0 && built.charAt(length) != separator) {
            length--;
        }
        built.setLength(length);
        built.append(separator);
    }
}
