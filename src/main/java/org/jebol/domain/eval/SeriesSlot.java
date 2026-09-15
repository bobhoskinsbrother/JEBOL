package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Slot;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

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
                    value instanceof CharacterValue character
                            ? character.codepoint()
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
        if (!(value instanceof IntegerValue number)) {
            return 0;
        }
        long wanted = number.magnitude();
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
