package org.jebol.domain.eval.arithmetic;

import java.util.LinkedHashMap;
import java.util.Map;

final class TheArithmeticOperations {

    private TheArithmeticOperations() {
    }

    static Map<String, ArithmeticOperation> bySpelling() {
        Map<String, ArithmeticOperation> operations = new LinkedHashMap<>();
        for (ArithmeticOperation operation : new ArithmeticOperation[] {
                new Add(), new Subtract(), new Multiply(),
                new Divide(), new Remainder(), new Modulo()}) {
            operations.put(operation.spelling(), operation);
        }
        return Map.copyOf(operations);
    }
}
