package org.jebol.domain.value;

public interface ValueOperation {

    String spelling();

    boolean isBitwise();

    boolean worksOnVectors();

    boolean divides();

    boolean keepsTheSignOfTheDividend();

    boolean needsNonZeroDivisor();

    long onWholeElements(long ours, long theirs);

    double onMeasuredElements(double ours, double theirs);
}
