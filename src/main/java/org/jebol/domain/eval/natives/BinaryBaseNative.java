package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;

public abstract class BinaryBaseNative extends EncodingNative {

    protected BinaryBaseNative(Encodings encodings) {
        super(encodings);
    }

    abstract Raised refusalOfAnUnknownBase(List<Value> arguments);

    protected int aKnownBase(List<Value> arguments) {
        long base = ((IntegerValue) arguments.get(1)).magnitude();
        if (base != (int) base || !Encodings.BASES.contains((int) base)) {
            throw refusalOfAnUnknownBase(arguments);
        }
        return (int) base;
    }
}
