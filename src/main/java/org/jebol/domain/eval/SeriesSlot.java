package org.jebol.domain.eval;

import org.jebol.domain.value.*;

record SeriesSlot(RebolSeries series, int at, Value held) implements Slot {

    @Override
    public Value value() {
        return held;
    }

    @Override
    public void setValue(Value replacement) {
        write(series, at, replacement);
    }

    static void write(RebolSeries series, int at, Value value) {
        switch (series) {
            case AnyBlockValue block -> block.storage().set(at, value);
            case AnyStringValue text -> text.storage().set(at,
                    value instanceof CharacterValue(int codepoint)
                            ? codepoint
                            : Molder.form(value).codePointAt(0));
            case BinaryValue bytes -> bytes.storage().set(at, readOctetFrom(value));
            case ImageValue image -> ImagePath.write(image, at, value);
            case GobValue ignored -> {
            }
            case VectorValue vector -> vector.storage().set(at,
                    VectorPath.encodeForStorage(vector.kind(), value));
        }
    }

    private static int readOctetFrom(Value value) {
        if (!(value instanceof IntegerValue(long wanted))) {
            return 0;
        }
        if (wanted < 0) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        }
        if (wanted > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, value);
        }
        return (int) wanted;
    }
}
