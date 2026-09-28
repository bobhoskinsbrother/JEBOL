package org.jebol.domain.eval;

import org.jebol.domain.value.*;

record SeriesSlot(SeriesValue series, int at, Value held) implements Slot {

    @Override
    public Value value() {
        return held;
    }

    @Override
    public void setValue(Value replacement) {
        write(series, at, replacement);
    }

    static void write(SeriesValue series, int at, Value value) {
        switch (series) {
            case BlockValue block -> block.storage().set(at, value);
            case StringValue text -> text.storage().set(at,
                    value instanceof CharacterValue(int codepoint)
                            ? codepoint
                            : Molder.form(value).codePointAt(0));
            case BinaryValue bytes -> bytes.storage().set(at, octetFrom(value));
            case ImageValue image -> ImagePath.write(image, at, value);
            case GobValue gob ->
                    GobPath.pokeWhichInsertsRatherThanReplaces(gob, at, value);
            case VectorValue vector -> vector.storage().set(at,
                    VectorPath.storedFormOf(vector.kind(), value));
        }
    }

    private static int octetFrom(Value value) {
        if (!(value instanceof IntegerValue(long wanted))) {
            return 0;
        }
        if (wanted < 0) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        if (wanted > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        return (int) wanted;
    }
}
