package org.jebol.domain.eval.sets;

import java.util.Map;
import java.util.Optional;

/**
 * What INTERSECT, UNION, EXCLUDE and DIFFERENCE each decide.
 *
 * <p>Three questions settle a set operation over any pair of series, and a
 * fourth settles it over a pair of bitsets or typesets. Every caller asks
 * these rather than testing which operation it was handed.
 */
public interface SetOperation {

    String spelling();

    /** Whether a member of the first set survives, given whether the second holds it. */
    boolean theFirstSetKeeps(boolean inTheirs);

    /** Whether the second set contributes members of its own at all. */
    boolean theSecondSetContributes();

    /** Whether a member of the second survives, given whether the first holds it. */
    boolean theSecondSetKeeps(boolean inOurs);

    /** The same decision on a whole octet at a time, which is how bitsets meet. */
    int combinedBits(int mine, int yours);

    /** The same decision on one membership, which is how typesets meet. */
    default boolean holdsWhen(boolean inMine, boolean inYours) {
        return combinedBits(inMine ? 1 : 0, inYours ? 1 : 0) != 0;
    }

    Map<String, SetOperation> BY_SPELLING = TheSetOperations.bySpelling();

    static Optional<SetOperation> named(String spelling) {
        return Optional.ofNullable(BY_SPELLING.get(spelling));
    }
}
