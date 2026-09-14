package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Value;

/**
 * What a binary does when an action is performed on it, which is what
 * {@code REBTYPE(Binary)} answers through {@code t-string.c}'s shared arms.
 *
 * <p>A binary holds octets, so everything put into one is read as octets
 * first: a character contributes its UTF-8, a string contributes the UTF-8
 * of its text, an integer contributes one byte, and a block contributes
 * whatever each of its items contributes in turn.
 */
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
