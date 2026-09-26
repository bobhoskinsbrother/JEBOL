package org.jebol.domain.eval.arithmetic;

import java.util.Map;
import java.util.Optional;

/** AND, OR and XOR, which meet numbers, logics, tuples, binaries and bitsets. */
public interface BitwiseOperation extends ValueOperation {

    boolean onLogics(boolean ours, boolean theirs);

    @Override
    default boolean isBitwise() {
        return true;
    }

    @Override
    default boolean worksOnVectors() {
        return true;
    }

    @Override
    default boolean divides() {
        return false;
    }

    @Override
    default boolean keepsTheSignOfTheDividend() {
        return false;
    }

    @Override
    default boolean needsANonZeroDivisor() {
        return false;
    }

    @Override
    default double onMeasuredElements(double ours, double theirs) {
        return ours;
    }

    Map<String, BitwiseOperation> BY_SPELLING = TheBitwiseOperations.bySpelling();

    static BitwiseOperation theOneCalled(String spelling) {
        return Optional.ofNullable(BY_SPELLING.get(spelling)).orElseThrow();
    }
}
