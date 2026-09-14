package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
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
    void takeOneOutAt(int oneBasedIndex) {
        bytes.storage().removeAt(oneBasedIndex);
    }

    @Override
    List<Value> elementsOf(SeriesValue from) {
        BinaryValue octets = (BinaryValue) from;
        List<Value> read = new ArrayList<>(octets.lengthFromHere());
        for (int at = 0; at < octets.lengthFromHere(); at++) {
            read.add(IntegerValue.of(octets.storage().at(octets.index() + at)));
        }
        return List.copyOf(read);
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
        BinaryValue held = (BinaryValue) Natives.clampedToTail(bytes);
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
