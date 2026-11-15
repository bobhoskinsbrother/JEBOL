package org.jebol.domain.eval.mathpresso;

import org.jebol.domain.eval.mathpresso.MathPressoNode.Block;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class MathPressoContext {

    private static final long PI = Double.doubleToRawLongBits(3.14159265358979323846);
    private static final long E = Double.doubleToRawLongBits(2.7182818284590452354);

    private final MathPressoScope scope = new MathPressoScope(null, MathPressoScope.Type.GLOBAL);
    private final int variableCount;

    private MathPressoContext(List<String> variableNames) {
        MathPressoOperator.intrinsics().forEach(operator -> scope.put(MathPressoSymbol.intrinsic(operator)));
        scope.put(MathPressoSymbol.constant("NaN", SseDoubles.QUIET_NAN));
        scope.put(MathPressoSymbol.constant("INF", SseDoubles.POSITIVE_INFINITY));
        scope.put(MathPressoSymbol.constant("PI", PI));
        scope.put(MathPressoSymbol.constant("E", E));
        for (int index = 0; index < variableNames.size(); index++) {
            String name = variableNames.get(index);
            if (!scope.has(name)) {
                scope.put(MathPressoSymbol.contextVariable(name, index));
            }
        }
        this.variableCount = variableNames.size();
    }

    public static MathPressoContext withVariables(List<String> variableNames) {
        return new MathPressoContext(variableNames);
    }

    public int variableCount() {
        return variableCount;
    }

    public MathPressoCompilation compile(byte[] text) {
        byte[] source = truncateAtFirstZero(text);
        MathPressoReporter reporter = new MathPressoReporter(source);
        MathPressoScope rootScope = new MathPressoScope(scope, MathPressoScope.Type.SHADOW);
        try {
            MathPressoParser parser = new MathPressoParser(new MathPressoTokenizer(source), reporter, rootScope);
            Block program = parser.parseProgram();
            new MathPressoOptimizer(reporter).optimize(program);
            MathPressoExpression expression = new MathPressoExpression(program, parser.slotsUsed(),
                    rootScope.symbols(), variableCount);
            return new MathPressoCompilation(Optional.of(expression), reporter.messages());
        } catch (MathPressoReporter.Refused refused) {
            return new MathPressoCompilation(Optional.empty(), reporter.messages());
        }
    }

    private static byte[] truncateAtFirstZero(byte[] text) {
        for (int index = 0; index < text.length; index++) {
            if (text[index] == 0) {
                return Arrays.copyOf(text, index);
            }
        }
        return text;
    }
}
