package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;

public class FindScriptNative extends DefaultNative {

    private static final String HEADER_WORD = "rebol";

    private static final char BYTE_ORDER_MARK = '﻿';

    @Override
    public String nativeName() {
        return "find-script";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("script", Set.of(BinaryValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BinaryValue script = (BinaryValue) arguments.getFirst();
            OptionalInt header = headerStartsIn(script.asText());
            return header.isPresent()
                    ? script.atIndex(script.index() + header.getAsInt())
                    : NoneValue.none();
        };
    }

    private OptionalInt headerStartsIn(String text) {
        String lowered = text.toLowerCase(Locale.ROOT);
        for (int at = lowered.indexOf(HEADER_WORD); at >= 0;
                at = lowered.indexOf(HEADER_WORD, at + 1)) {
            if (onlySpacesBefore(text, at) && bracketFollows(text, at + HEADER_WORD.length())) {
                return OptionalInt.of(at);
            }
        }
        return OptionalInt.empty();
    }

    private boolean onlySpacesBefore(String text, int at) {
        for (int back = at - 1; back >= 0; back--) {
            char letter = text.charAt(back);
            if (letter == '\n') {
                return true;
            }
            if (!Character.isWhitespace(letter) && letter != BYTE_ORDER_MARK) {
                return false;
            }
        }
        return true;
    }

    private boolean bracketFollows(String text, int at) {
        int forward = at;
        while (forward < text.length() && Character.isWhitespace(text.charAt(forward))) {
            forward++;
        }
        return forward < text.length() && text.charAt(forward) == '[';
    }
}
