package org.jebol.application;

import org.jebol.domain.eval.mathpresso.MathPressoCompilation;
import org.jebol.domain.eval.mathpresso.MathPressoContext;
import org.jebol.domain.eval.mathpresso.MathPressoExpression;
import org.jebol.domain.eval.mathpresso.MathPressoMessage;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorKind;
import org.jebol.domain.value.VectorStorage;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

final class MathPressoExtension {

    private static final String HEADER = "REBOL [Title: {Rebol Mathpresso Extension} Name: mathpresso Type: module "
            + "Version: 0.1.0 Author: Oldes License: Apache-2.0 Url: https://github.com/Siskin-framework/Rebol-Mathpresso]";

    private static final String SCRIPT = "protect/hide 'words";

    private static final String CONTEXT_HANDLE = "MPContext";
    private static final String EXPRESSION_HANDLE = "MPExpression";

    private static final String NO_EXPRESSION = "Command needs a valid MPExpression handle!";
    private static final String NOT_DOUBLES = "Command needs a vector with double values!";
    private static final String TOO_FEW_DOUBLES = "Insufficient size of input data!";

    private static final int WIDEST_CHARACTER_STORED_IN_BYTE = 0xFF;

    private MathPressoExtension() {
    }

    static BundledExtension create() {
        return new BundledExtension(new Extension("mathpresso", "0.1.0", List.of(
                new ExtensionCommand("context",
                        "{Initialize MPContext handle with given variable names} "
                                + "spec [block!] {Block with variable names used by expressions}",
                        MathPressoExtension::makeContext),
                new ExtensionCommand("compile",
                        "{Compile math expression using the given context} "
                                + "context [handle!] {MPContext} expression [string!] {Math expression}",
                        MathPressoExtension::compile),
                new ExtensionCommand("eval",
                        "{Evaluate precompiled math expressions using given variables} "
                                + "expression [handle!] {MPExpression} "
                                + "variables [vector!] {Variables in a double format}",
                        MathPressoExtension::evaluate)))
                .withHandleType(new HandleType(CONTEXT_HANDLE, MathPressoContext.class))
                .withHandleType(new HandleType(EXPRESSION_HANDLE, MathPressoExpression.class))
                .withScript(SCRIPT), HEADER);
    }

    private static Value makeContext(Command call) {
        AnyBlockValue spec = (AnyBlockValue) call.arguments().getFirst();
        List<String> names = spec.remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(word -> call.canonicalSpellingFor((WordValue) word))
                .toList();
        return call.makeHandle(CONTEXT_HANDLE, MathPressoContext.withVariables(names));
    }

    private static Value compile(Command call) {
        Optional<MathPressoContext> context = findHeld(call.arguments().getFirst(), CONTEXT_HANDLE,
                MathPressoContext.class);
        if (context.isEmpty()) {
            return LogicValue.of(false);
        }
        MathPressoCompilation compiled = context.get().compile(readAsStoredFromHead(call.arguments().get(1)));
        for (MathPressoMessage message : compiled.messages()) {
            call.writeLine((message.isError() ? "ERROR: " : "WARNING: ") + message.text());
        }
        return compiled.expression()
                .<Value>map(expression -> call.makeHandle(EXPRESSION_HANDLE, expression))
                .orElseGet(NoneValue::none);
    }

    private static Value evaluate(Command call) {
        MathPressoExpression expression = findHeld(call.arguments().getFirst(), EXPRESSION_HANDLE,
                MathPressoExpression.class)
                .orElseThrow(() -> new IllegalStateException(NO_EXPRESSION));
        VectorValue vector = (VectorValue) call.arguments().get(1);
        VectorStorage storage = vector.storage();
        if (storage.kind() != VectorKind.FLOAT64) {
            throw new IllegalStateException(NOT_DOUBLES);
        }
        int count = expression.variableCount();
        if (storage.length() - (vector.index() - 1) < count) {
            throw new IllegalStateException(TOO_FEW_DOUBLES);
        }
        long[] variables = new long[count];
        for (int each = 0; each < count; each++) {
            variables[each] = storage.at(vector.index() + each);
        }
        long result = expression.evaluate(variables);
        for (int each = 0; each < count; each++) {
            storage.setBeneathAnyProtection(vector.index() + each, variables[each]);
        }
        return DecimalValue.of(Double.longBitsToDouble(result));
    }

    private static <T> Optional<T> findHeld(Value argument, String kind, Class<T> type) {
        if (argument instanceof HandleValue handle && handle.typeName().equals(kind)
                && handle.payload() instanceof JavaObjectValue carried) {
            return carried.held().filter(type::isInstance).map(type::cast);
        }
        return Optional.empty();
    }

    private static byte[] readAsStoredFromHead(Value text) {
        String characters = ((AnyStringValue) text).head().text();
        boolean isStoredWide = characters.codePoints().anyMatch(code -> code > WIDEST_CHARACTER_STORED_IN_BYTE);
        return characters.getBytes(isStoredWide ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
    }
}
