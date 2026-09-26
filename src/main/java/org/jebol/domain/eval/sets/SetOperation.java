package org.jebol.domain.eval.sets;

import java.util.Map;
import java.util.Optional;

public interface SetOperation {

    String spelling();

    boolean theFirstSetKeeps(boolean inTheirs);

    boolean theSecondSetContributes();

    boolean theSecondSetKeeps(boolean inOurs);

    int combinedBits(int mine, int yours);

    default boolean holdsWhen(boolean inMine, boolean inYours) {
        return combinedBits(inMine ? 1 : 0, inYours ? 1 : 0) != 0;
    }

    Map<String, SetOperation> BY_SPELLING = TheSetOperations.bySpelling();

    static Optional<SetOperation> named(String spelling) {
        return Optional.ofNullable(BY_SPELLING.get(spelling));
    }
}
