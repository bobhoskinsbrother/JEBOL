package org.jebol.domain.value.sets;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

public interface SetOperation {

    String spelling();

    boolean keepsFromTheFirstSet(boolean inTheirs);

    boolean takesFromTheSecondSet();

    boolean keepsFromTheSecondSet(boolean inOurs);

    int combineBits(int mine, int yours);

    default boolean holdsWhen(boolean inMine, boolean inYours) {
        return combineBits(inMine ? 1 : 0, inYours ? 1 : 0) != 0;
    }

    Map<String, SetOperation> BY_SPELLING = Stream.of(
                    new Union(), new Intersect(), new Difference(), new Exclude())
            .collect(toUnmodifiableMap(
                    SetOperation::spelling, identity()));

    static Optional<SetOperation> findTheOperationNamed(String spelling) {
        return Optional.ofNullable(BY_SPELLING.get(spelling));
    }
}
