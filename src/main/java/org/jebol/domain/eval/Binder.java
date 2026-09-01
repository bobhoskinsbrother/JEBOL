package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 *
 * <p>A word is bound to whichever context actually holds its slot, not to the
 * one it was bound through. Binding a block into a short-lived inner scope
 * must not make its outer words look as though they live in that scope.
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
        return laidOutLike(block, new BlockStorage(bound));
    }

    /**
     * A new storage wearing the old one's line starts, as a block value.
     *
     * <p>Binding copies, and a copy that dropped the flags molded every
     * script's blocks on one line. The flags shift with the copy, because a
     * bound block starts at its head where the one it came from may not.
     */
    private static BlockValue laidOutLike(BlockValue older, BlockStorage bound) {
        bound.takeLineBreaksFrom(older.storage(), older.index());
        return new BlockValue(bound, 1, older.datatype());
    }

    /**
     * The same block, with its own words bound where they stand.
     *
     * <p>{@code Bind_Block(frame, VAL_BLK(word), BIND_DEEP)} and then
     * {@code return R_ARG2}: Rebol binds the caller's block and answers that
     * block, thus the caller's block is bound afterwards. IN's special form
     * depends on it, because `b: [a]  in o b  do b` reads the object's field.
     *
     * <p>{@link #bind} copies instead, which is right everywhere else: binding
     * a body the caller still holds would change code the caller wrote. Only
     * IN asks for the other behavior, and it asks for it on purpose.
     */
    public static BlockValue bindInPlace(BlockValue block, Context context) {
        for (int at = 0; at < block.lengthFromHere(); at++) {
            int where = block.index() + at;
            block.storage().set(where, bindValue(block.storage().at(where), context));
        }
        return block;
    }

    /**
     * Binds only the names given, leaving every other word as it stands.
     *
     * <p>{@code Bind_Relative} in the C, and the difference from {@link #bind}
     * is the whole of REBOL's binding model: a function body is bound to its
     * own arguments and locals, and every other word keeps the binding it
     * already had from wherever the body was written.
     *
     * <p>Rebinding everything instead is subtly wrong and hard to see. FUNC is
     * itself a REBOL function, so {@code make function!} runs inside FUNC's
     * frame; a body rebound through that chain resolves its free words in the
     * library rather than where they were written. Rebol's own COLLECT is
     * exactly that shape -- it builds its KEEP function inside itself and
     * KEEP writes to COLLECT's own OUTPUT -- and OUTPUT came out none.
     */
    public static BlockValue bindOnly(
            BlockValue block, Context context, Set<String> names) {

        List<Value> bound = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            bound.add(bindValueOnly(item, context, names));
        }
        return laidOutLike(block, new BlockStorage(bound));
    }

    private static Value bindValueOnly(
            Value value, Context context, Set<String> names) {

        return switch (value) {
            case WordValue word when names.contains(word.canonical()) ->
                    word.boundTo(context.knows(word.canonical())
                            ? context.holderOf(word.canonical())
                            : context);
            case WordValue word -> word;
            case BlockValue nested -> bindOnly(nested, context, names);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, bindValueOnly(map.select(key), context, names));
                }
                yield map;
            }
            default -> value;
        };
    }

    private static Value bindValue(Value value, Context context) {
        return switch (value) {
            case WordValue word -> context.knows(word.canonical())
                    ? word.boundTo(context.holderOf(word.canonical()))
                    : word;
            case BlockValue block -> bind(block, context);
            case MapValue map -> {
                for (Value key : map.keys()) {
                    map.put(key, bindValue(map.select(key), context));
                }
                yield map;
            }
            default -> value;
        };
    }

    /**
     * The same, but a word the context does not know gets a slot rather
     * than staying unbound.
     *
     * <p>{@code BIND_ALL} in the C, and {@code Do_String} uses it for
     * every piece of source that arrives at run time. Without it nothing
     * loaded at run time can name anything new: {@code do "total: 1"}
     * fails on TOTAL, because the word was not in the text the
     * interpreter was started with and so was never given a slot.
     *
     * <p>The new slot holds unset until something assigns to it. That is
     * what changes a mistyped word from "not defined" to "has no value",
     * which is the answer a real R3 gives for the same typo.
     */
    public static BlockValue bindAndDefine(BlockValue block, Context context) {
        for (Value item : block.remaining()) {
            defineWordsIn(item, context);
        }
        return bind(block, context);
    }

    private static void defineWordsIn(Value value, Context context) {
        switch (value) {
            case WordValue word -> {
                if (!context.knows(word.canonical())) {
                    context.define(word.spelling());
                }
            }
            case BlockValue nested -> {
                for (Value item : nested.remaining()) {
                    defineWordsIn(item, context);
                }
            }
            case MapValue map -> {
                for (Value stored : map.values()) {
                    defineWordsIn(stored, context);
                }
            }
            default -> {
            }
        }
    }
}
