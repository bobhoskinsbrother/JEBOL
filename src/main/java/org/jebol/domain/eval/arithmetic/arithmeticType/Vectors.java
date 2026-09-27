package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.VectorMath;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

import static org.jebol.domain.eval.Arithmetic.notRelated;

public class Vectors implements ArithmeticType {
    @Override
    public boolean shouldHandle(Value left, Value right) {
        return VectorMath.isVectorArithmetic(left, right);
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        if (!operation.worksOnVectors()
                || (!(left instanceof VectorValue) && !operation.isCommutative())) {
            throw notRelated(left, right);
        }
        Value other = left instanceof VectorValue ? right : left;
        if (!(other instanceof VectorValue)
                && !(other instanceof IntegerValue)
                && !(other instanceof DecimalValue)) {
            throw notRelated(left, right);
        }
        return VectorMath.done(left, right, operation);
    }

}
