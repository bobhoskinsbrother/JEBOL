package org.jebol.domain.eval.arithmetic;

public interface ValueOperation {

    String spelling();

    boolean isBitwise();

    boolean worksOnVectors();

    boolean divides();

    boolean keepsTheSignOfTheDividend();

    boolean needsANonZeroDivisor();

    long onWholeElements(long ours, long theirs);

    double onMeasuredElements(double ours, double theirs);
}
