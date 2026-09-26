package org.jebol.domain.eval.sets;

import java.util.LinkedHashMap;
import java.util.Map;

final class TheSetOperations {

    private TheSetOperations() {
    }

    static Map<String, SetOperation> bySpelling() {
        Map<String, SetOperation> operations = new LinkedHashMap<>();
        for (SetOperation operation : new SetOperation[] {
                new Intersect(), new Union(), new Exclude(), new Difference()}) {
            operations.put(operation.spelling(), operation);
        }
        return Map.copyOf(operations);
    }
}
