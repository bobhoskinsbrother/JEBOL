package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;

public final class BinaryActions extends SeriesActions {

    private final BinaryValue bytes;

    public BinaryActions(BinaryValue bytes) {
        this.bytes = bytes;
    }

    @Override
    BinaryValue held() {
        return bytes;
    }

    @Override
    public Value poked(Value position, Value written) {
        int at = pokedStoragePosition(position);
        bytes.storage().set(at, octetPokedFrom(written));
        return written;
    }

    private int octetPokedFrom(Value written) {
        return switch (written) {
            case IntegerValue number -> asAnOctet(number.magnitude());
            case CharacterValue(int codepoint) when codepoint > 0xFF ->
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                            codepoint + " does not fit in a byte");
            case CharacterValue(int codepoint) -> codepoint;
            default -> throw Raised.cannotUse(bytes, "poke");
        };
    }

    private int asAnOctet(long wanted) {
        if (wanted < 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        if (wanted > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        return (int) wanted;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        bytes.storage().removeAt(oneBasedIndex);
    }


    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        return BinaryValue.of(items.stream()
                .mapToInt(item -> (int) ((IntegerValue) item).magnitude()).toArray());
    }

    @Override
    public Value cleared() {
        return clearedOneAtATime();
    }

    @Override
    public Value complemented() {
        byte[] flipped = bytes.octetsFromHere();
        for (int at = 0; at < flipped.length; at++) {
            flipped[at] = (byte) ~flipped[at];
        }
        return new BinaryValue(new BinaryStorage(flipped), 1);
    }

    @Override
    public Value append(Asked asked) {
        for (int octet : octetsContributedBy(asked)) {
            bytes.storage().append(octet);
        }
        return bytes.head();
    }

    @Override
    public Value insert(Asked asked) {
        BinaryValue held = (BinaryValue) bytes.clampedToTail();
        int[] octets = octetsContributedBy(asked);
        for (int at = octets.length; at > 0; at--) {
            held.storage().insertAt(held.index(), octets[at - 1]);
        }
        return held.atIndex(held.index() + octets.length);
    }

    private static int[] octetsContributedBy(Asked asked) {
        return SeriesContents.octetsContributedBy(
                asked.duplicated(), asked.howManyOctetsWanted());
    }
}
