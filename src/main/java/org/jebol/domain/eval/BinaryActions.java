package org.jebol.domain.eval;

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
public final class BinaryActions implements Actions {

    private final BinaryValue bytes;

    public BinaryActions(BinaryValue bytes) {
        this.bytes = bytes;
    }

    @Override
    public Value append(Asked asked) {
        for (int octet : SeriesContents.octetsContributedBy(
                asked.duplicated(), asked.howManyOctetsWanted())) {
            bytes.storage().append(octet);
        }
        return bytes.head();
    }
}
