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
    public Value poke(Value position, Value written) {
        int at = findPokedStoragePosition(position);
        bytes.storage().set(at, readOctetPokedFrom(written));
        return written;
    }

    private int readOctetPokedFrom(Value written) {
        return switch (written) {
            case IntegerValue number -> asOctet(number);
            case CharacterValue letter when letter.codepoint() > 0xFF ->
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE, letter);
            case CharacterValue(int codepoint) -> codepoint;
            default -> throw Raised.cannotUseAction(bytes, "poke");
        };
    }

    private int asOctet(IntegerValue wanted) {
        if (wanted.magnitude() < 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, wanted);
        }
        if (wanted.magnitude() > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, wanted);
        }
        return (int) wanted.magnitude();
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        bytes.storage().removeAt(oneBasedIndex);
    }


    @Override
    Value ofSameKindHolding(List<Value> items) {
        return BinaryValue.of(items.stream()
                .mapToInt(item -> (int) ((IntegerValue) item).magnitude()).toArray());
    }

    @Override
    public Value clear() {
        return clearOneAtTime();
    }

    @Override
    public Value complement() {
        byte[] flipped = bytes.octetsFromHere();
        for (int at = 0; at < flipped.length; at++) {
            flipped[at] = (byte) ~flipped[at];
        }
        return new BinaryValue(new BinaryStorage(flipped), 1);
    }

    @Override
    public Value append(Asked asked) {
        for (int octet : collectOctetsContributedBy(asked)) {
            bytes.storage().append(octet);
        }
        return bytes.head();
    }

    @Override
    public Value insert(Asked asked) {
        BinaryValue held = (BinaryValue) bytes.clampToTail();
        int[] octets = collectOctetsContributedBy(asked);
        for (int at = octets.length; at > 0; at--) {
            held.storage().insertAt(held.index(), octets[at - 1]);
        }
        return held.atIndex(held.index() + octets.length);
    }

    private static int[] collectOctetsContributedBy(Asked asked) {
        return SeriesContents.collectOctetsContributedBy(
                asked.duplicated(), asked.countOctetsWanted());
    }
}
