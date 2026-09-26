package org.jebol.domain.eval.arithmetic;

/**
 * What every operation over a pair of values answers, arithmetic or bitwise.
 *
 * <p>A vector applies one of these to each element and a bitset to each
 * octet, so both widths live here. What separates the two families is
 * {@link #isBitwise()}, and what a vector will not take is
 * {@link #worksOnVectors()}.
 */
public interface ValueOperation {

    String spelling();

    boolean isBitwise();

    /** Whether a vector will take this at all. MODULO is the one that will not. */
    boolean worksOnVectors();

    boolean divides();

    /** What separates a remainder from a modulo: whose sign the rest follows. */
    boolean keepsTheSignOfTheDividend();

    /** Whether a zero on the right is a zero-divide rather than an ordinary number. */
    boolean needsANonZeroDivisor();

    /** One element of a whole-number vector, or one octet, against another. */
    long onWholeElements(long ours, long theirs);

    /** One element of a vector that measures rather than counts. */
    double onMeasuredElements(double ours, double theirs);
}
