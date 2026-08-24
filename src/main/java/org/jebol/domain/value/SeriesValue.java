package org.jebol.domain.value;

/**
 * A value that points into shared, mutable storage at a position.
 *
 * <p>This is the part of REBOL that surprises people who expect values to be
 * immutable. Two series values can point into the same storage at different
 * positions, and a mutation through one is visible through the other. That is
 * the semantics, not an implementation shortcut, and it is why an interpreter
 * instance is single-threaded and owns everything reachable from it.
 *
 * <p>Positions are 1-based, and one past the last element is the tail. The
 * tail is a legal position to hold and an illegal one to read from, which is
 * what lets {@code until [tail? series: next series]} terminate.
 */
public sealed interface SeriesValue extends Value
        permits StringValue, BinaryValue, BlockValue, ImageValue, GobValue, VectorValue {

    /** 1-based position within the storage. {@code length() + 1} is the tail. */
    int index();

    /** How many elements the underlying storage holds, ignoring the position. */
    int storageLength();

    /** How many elements remain from this position to the tail. */
    default int lengthFromHere() {
        return Math.max(0, storageLength() - index() + 1);
    }

    /**
     * Whether the position is beyond what the storage now holds.
     *
     * <p>A series value keeps its own index, so shortening the storage
     * underneath one leaves it standing past the end: take three items
     * off a four-item block that something else is holding at position
     * four, and that other value is stranded. REBOL answers PAST? for
     * exactly this and treats the value as empty everywhere else.
     */
    default boolean isPastTheEnd() {
        return index() > storageLength() + 1;
    }

    /** Whether this value sits at the head of its storage. */
    default boolean atHead() {
        return index() == 1;
    }

    /** Whether this value sits one past the last element. */
    default boolean atTail() {
        return index() >= storageLength() + 1;
    }

    /** The same storage, seen from a different position. */
    SeriesValue atIndex(int oneBasedIndex);

    /** The same storage, seen from its head. */
    default SeriesValue head() {
        return atIndex(1);
    }

    /** The same storage, seen from its tail. */
    default SeriesValue tail() {
        return atIndex(storageLength() + 1);
    }

    /**
     * Whether two series values point into the very same storage. Distinct
     * from equality, which compares contents: {@code same?} against
     * {@code equal?}.
     */
    boolean sharesStorageWith(SeriesValue other);
}
