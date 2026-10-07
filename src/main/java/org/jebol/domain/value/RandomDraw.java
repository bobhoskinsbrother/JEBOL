package org.jebol.domain.value;

import java.util.List;

public interface RandomDraw {

    long next();

    int below(int limit);

    int belowWithoutNarrowing(int limit);

    long upTo(long limit);

    double fraction();

    <T> void shuffle(List<T> items);
}
