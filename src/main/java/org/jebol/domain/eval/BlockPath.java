package org.jebol.domain.eval;

import java.util.Optional;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * Reaching into a block through a path, out of {@code PD_Block} in
 * {@code t-block.c}.
 *
 * <p>Three kinds of selector meaning three different things. A number is a
 * position. A word is a name to look up, and the answer is the item <em>after</em>
 * the word -- so {@code [a 1 b 2]/a} is 1, which makes a block a usable lookup
 * table without being a map or an object. Anything else is searched for by value,
 * and again the answer is the item after it.
 *
 * <p>The word form is the one worth knowing. Rebol's own code leans on it
 * constantly -- a block of settings read as {@code defs/types} -- and a path
 * that refused it stopped {@code prot-mysql.reb} and Rebol's own URL parser.
 */
final class BlockPath {

    private BlockPath() {
    }

    /**
     * What a path segment reads out of a block, or none.
     *
     * <p>{@code if (n < 0 || (REBCNT)n >= VAL_TAIL(pvs->value)) { if
     * (pvs->setval) return PE_BAD_SELECT; return PE_NONE; }} -- a read answers
     * none for anything it cannot find and only a write refuses. That one line
     * covers a position past the tail, a name the block has not got, and a name
     * sitting at the tail with no value after it.
     */
    static Value read(BlockValue block, Value selector) {
        return positionOf(block, selector)
                .map(at -> block.storage().at(at))
                .orElseGet(NoneValue::none);
    }

    /**
     * Where a path segment points, as a position in the block's storage.
     *
     * <p>Empty where the C would answer none. Both the read and the write need
     * this answered, and the only difference between them is what they do when
     * it is empty.
     */
    static Optional<Integer> positionOf(BlockValue block, Value selector) {
        int at = switch (selector) {
            // `if (i == 0) return PE_NONE; // like in case: path/0` and then
            // `if (i < 0) i++; n = i + VAL_INDEX(pvs->value) - 1;`. So a
            // negative position counts back from where the block is and can
            // reach behind it, and there is no zero: `b/0` is nothing at all.
            case IntegerValue position -> positionFrom(position.magnitude(), block.index());
            // A decimal is truncated, which is what Int32 does to one, so
            // `b/2.7` and `b/2` are the same request.
            case DecimalValue fraction ->
                    positionFrom((long) fraction.quantity(), block.index());
            // `n = Find_Word(...); if (n != NOT_FOUND) n++;` -- the answer is
            // the item after the name. Find_Word takes "word (of any type)", so
            // `[a: 1]/a` and `['a 1]/a` both answer 1: what is being matched is
            // the spelling and not how it was written.
            case WordValue name -> afterTheFirst(block, item ->
                    item instanceof WordValue held
                            && held.canonical().equals(name.canonical()));
            // `Find_Block_Simple(...) + 1` -- anything else is looked for by
            // value, so a block of strings and values reads by string.
            default -> afterTheFirst(block, item ->
                    Comparison.looselyEqual(item, selector));
        };
        return at >= 1 && at <= block.storageLength() ? Optional.of(at) : Optional.empty();
    }

    /**
     * Whether the selector is the one that means nowhere at all.
     *
     * <p>{@code if (i == 0) return PE_NONE; // like in case: path/0} comes
     * before the write check, so a write through position zero is not refused
     * the way a write through a missing name is -- it quietly does nothing.
     * Nothing downstream looks at whether it happened, so the only evidence is
     * that the block is as it was.
     */
    static boolean isNowhereAtAll(Value selector) {
        return switch (selector) {
            case IntegerValue position -> position.magnitude() == 0;
            case DecimalValue fraction -> (long) fraction.quantity() == 0;
            default -> false;
        };
    }

    private static int positionFrom(long wanted, int here) {
        if (wanted == 0) {
            return 0;
        }
        long counted = wanted < 0 ? wanted + 1 : wanted;
        return (int) (counted + here - 1);
    }

    /**
     * The position after the first item that matches, or nothing.
     *
     * <p>The search starts where the block is rather than at its head, so a
     * block stepped past its first pair finds the second: {@code Find_Word} and
     * {@code Find_Block_Simple} both take the index as their starting point.
     */
    private static int afterTheFirst(
            BlockValue block, java.util.function.Predicate<Value> matches) {

        for (int at = block.index(); at <= block.storageLength(); at++) {
            if (matches.test(block.storage().at(at))) {
                return at + 1;
            }
        }
        return 0;
    }
}
