package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ScreenForm;
import org.jebol.domain.host.ConsolePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

public class RequestPasswordNative extends WindowNative {

    public RequestPasswordNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "request-password";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            ConsolePort console = evaluator.ports().console();
            if (console.canRead()) {
                return readFromConsole(evaluator, console);
            }
            granted.require(HostService.WINDOWS);
            return runThroughWindows(() -> askThroughScreenOrPort(evaluator)
                    .<Value>map(StringValue::of)
                    .orElseGet(NoneValue::none));
        };
    }

    private Value readFromConsole(Evaluator evaluator, ConsolePort console) {
        granted.require(HostService.CONSOLE);
        Value answer = runThroughFileSystem(() -> {
            String typed = console.readHiddenLine();
            return typed == null ? NoneValue.none() : StringValue.of(typed);
        });
        evaluator.haltIfEscaped();
        return answer;
    }

    private Optional<String> askThroughScreenOrPort(Evaluator evaluator) {
        if (evaluator.ports().screen().hasDisplay()) {
            return new ScreenForm(evaluator).askForPassword();
        }
        return evaluator.ports().windows().askForPassword();
    }
}
