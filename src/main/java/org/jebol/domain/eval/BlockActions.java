package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Value;

/**
 * What a block does when an action is performed on it, which is what
 * {@code REBTYPE(Block)} answers in {@code t-block.c}.
 *
 * <p>A block is the one series where what goes in may be one thing or many.
 * Another block is spliced -- its items go in separately -- unless /ONLY says
 * to put the block itself in as a single item. Anything that is not a block
 * goes in whole, and /ONLY has nothing to say about it.
 *
 * <p>The line breaks come with the items. A block written across several
 * lines keeps those breaks when it is spliced into another, because where a
 * line ends is a property of the block rather than of how it is printed.
 */
public final class BlockActions implements Actions {

    private final BlockValue block;

    public BlockActions(BlockValue block) {
        this.block = block;
    }

    @Override
    public Value append(Asked asked) {
        if (asked.duplicated() instanceof BlockValue added
                && splicesRatherThanGoesInWhole(added, asked)) {
            block.storage().spliceInAt(
                    block.storage().length() + 1,
                    asked.theFirstFewOf(added.remaining()),
                    added.storage(), added.index());
        } else {
            block.storage().append(asked.given());
        }
        return block.head();
    }

    /** A paren or a path goes in whole, and so does a block /ONLY asked for. */
    private static boolean splicesRatherThanGoesInWhole(BlockValue added, Asked asked) {
        return added.datatype() == Datatype.BLOCK && !asked.wholeRatherThanSpliced();
    }
}
