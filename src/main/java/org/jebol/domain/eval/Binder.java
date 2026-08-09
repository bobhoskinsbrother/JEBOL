package org.jebol.domain.eval;

import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.value.BlockStorage;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * Binds the words in a block to a context.
 *
 * <p>Separate from reading, because a word's binding is not a property of how
 * it was written. TRANSCODE hands back unbound words and this decides what
 * they mean, which is what lets a caller build code as data and choose a
 * context afterwards.
 *
 * <p>Words the context does not know are left unbound rather than given a
 * slot holding unset. An unbound word and a word bound to an unset slot report
 * different errors, and the difference is worth keeping.
 */
public final class Binder {

    private Binder() {
    }

    /** A copy of the block with its words bound, recursively. */
    public static BlockValue bind(BlockValue block, Context context) {
        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(bindValue(item, context));
        }
        return new BlockValue(
                new BlockStorage(bound), 1, block.datatype());
    }

    private static Value bindValue(Value value, Context context) {
        return switch (value) {
            case WordValue word -> context.knows(word.canonical())
                    ? word.boundTo(context)
                    : word;
            case BlockValue block -> bind(block, context);
            default -> value;
        };
    }
}
