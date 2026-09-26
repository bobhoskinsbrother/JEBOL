package org.jebol.domain.eval.arithmetic;

import java.util.LinkedHashMap;
import java.util.Map;

final class TheBitwiseOperations {

    private TheBitwiseOperations() {
    }

    static Map<String, BitwiseOperation> bySpelling() {
        Map<String, BitwiseOperation> operations = new LinkedHashMap<>();
        for (BitwiseOperation operation :
                new BitwiseOperation[] {new And(), new Or(), new Xor()}) {
            operations.put(operation.spelling(), operation);
        }
        return Map.copyOf(operations);
    }
}
