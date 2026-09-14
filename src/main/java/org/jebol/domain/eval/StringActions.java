package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.stream.Collectors;

/**
 * What a string does when an action is performed on it, which is what
 * {@code REBTYPE(String)} answers in {@code t-string.c}.
 *
 * <p>Everything put into a string is formed into text first, and a block is
 * run together without spaces between its items -- {@code append "a" [1 2]}
 * gives {@code "a12"}, not {@code "a1 2"}. A path is the exception: its
 * segments are rejoined with the slashes they were written with, because a
 * path formed without them is not the path.
 */
public final class StringActions extends SeriesActions {

    private final StringValue text;

    public StringActions(StringValue text) {
        this.text = text;
    }

    @Override
    StringValue held() {
        return text;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        text.storage().removeAt(oneBasedIndex);
    }

    @Override
    public Value cleared() {
        return clearedOneAtATime();
    }

    @Override
    public Value append(Asked asked) {
        contributedBy(asked).codePoints().forEach(text.storage()::append);
        return text.head();
    }

    @Override
    public Value insert(Asked asked) {
        StringValue held = (StringValue) Natives.clampedToTail(text);
        int[] added = contributedBy(asked).codePoints().toArray();
        for (int at = 0; at < added.length; at++) {
            held.storage().insertAt(held.index() + at, added[at]);
        }
        return held.atIndex(held.index() + added.length);
    }

    /**
     * The text a value contributes to a string, with {@code /dup} already
     * spread and {@code /part} cutting it in codepoints rather than in the
     * chars a Java string counts.
     */
    static String contributedBy(Asked asked) {
        Value adding = asked.duplicated();
        String written = adding instanceof BlockValue added
                && added.datatype() == Datatype.BLOCK
                ? runTogether(added)
                : Molder.form(adding);
        return asked.howMuchOfIt()
                .map(count -> theFirstCodePointsOf(written, count.intValue()))
                .orElse(written);
    }

    /**
     * A value as the text it contributes: a block's items one after another
     * with nothing between them, and a path's segments with their slashes.
     */
    static String runTogether(Value value) {
        if (value.datatype().isAnyPath() && value instanceof BlockValue path) {
            return path.remaining().stream()
                    .map(StringActions::runTogether)
                    .collect(Collectors.joining("/"));
        }
        if (value instanceof BlockValue block) {
            return block.remaining().stream()
                    .map(StringActions::runTogether)
                    .collect(Collectors.joining());
        }
        return Molder.form(value);
    }

    /**
     * The first so many codepoints, which is not the first so many chars: a
     * character outside the basic plane is two chars and one codepoint, and
     * cutting by chars would halve it.
     */
    static String theFirstCodePointsOf(String written, int wanted) {
        int taking = Math.min(wanted, written.codePointCount(0, written.length()));
        return written.substring(0, written.offsetByCodePoints(0, taking));
    }
}
