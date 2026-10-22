package org.jebol.domain.eval;

import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public final class ScreenForm {

    private static final String FORM_FILE = "/org/jebol/forms/screen-form.reb";

    private static final String FORM_FUNCTION = "ask-on-screen";

    private static final String PASSWORD_LABEL = "Password";

    private static final String LINE_LABEL = "Type a line";

    private final Evaluator evaluator;

    public ScreenForm(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    public Optional<String> askForPassword() {
        return ask(true, PASSWORD_LABEL);
    }

    public Optional<String> askForLine() {
        String question = evaluator.readUnfinishedLine();
        return ask(false, question.isEmpty() ? LINE_LABEL : question);
    }

    private Optional<String> ask(boolean masked, String label) {
        Value form = loadForm();
        ScreenPort screen = evaluator.ports().screen();
        screen.takeTyping(label);
        try {
            Value answered = evaluator.applyFunction(form, List.of(LogicValue.of(masked), StringValue.of(label)));
            return answered instanceof AnyStringValue typed ? Optional.of(typed.text()) : Optional.empty();
        } finally {
            screen.releaseTyping();
        }
    }

    private Value loadForm() {
        String source = new String(evaluator.ports().carriedFiles().readBytes(FORM_FILE), StandardCharsets.UTF_8);
        TranscodeResult read = evaluator.read(source);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        Context ownWords = Context.makeChildOf(evaluator.systemContext());
        evaluator.evaluateOrRaise(Binder.bindAndDefine(read.values().orElseThrow(), ownWords), ownWords);
        return ownWords.findSlotFor(FORM_FUNCTION).value();
    }
}
