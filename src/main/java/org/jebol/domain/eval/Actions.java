package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

import java.util.Optional;

/**
 * The arms one datatype answers an action with -- JEBOL's
 * {@code Value_Dispatch}, which the C indexes by the type of argument one.
 *
 * <p>Every method refuses by default, which is {@code REBTYPE}'s
 * {@code default: Trap_Action} and the reason a datatype implements only what
 * it actually serves.
 *
 * <p><strong>Half built on purpose.</strong> {@link #of} answers nothing for a
 * datatype whose arms have not moved yet, and the registry falls back to its
 * own switch for those. The interface becomes sealed, and the fallback goes,
 * when the last datatype is in -- sealing it now would block the migration
 * rather than help it.
 */
public interface Actions {

    /**
     * The arms for one value, or nothing when that datatype has not moved yet.
     *
     * <p>The datatypes listed here are the ones whose arms live in their own
     * class. Everything else is still answered by the registry's switch.
     */
    static Optional<Actions> of(Value subject) {
        return switch (subject) {
            case BitsetValue members -> Optional.of(new BitsetActions(members));
            case MapValue pairs -> Optional.of(new MapActions(pairs));
            case BinaryValue bytes -> Optional.of(new BinaryActions(bytes));
            case StringValue text -> Optional.of(new StringActions(text));
            case BlockValue block -> Optional.of(new BlockActions(block));
            case ObjectValue object -> Optional.of(new ObjectActions(object));
            case GobValue gob -> Optional.of(new GobActions(gob));
            case ImageValue picture -> Optional.of(new ImageActions(picture));
            case VectorValue numbers -> Optional.of(new VectorActions(numbers));
            default -> Optional.empty();
        };
    }

    /** The value these arms answer for, so a refusal can name it. */
    Value subject();

    /** APPEND: put this at the end, and answer the series from its head. */
    default Value append(Asked asked) {
        throw Raised.cannotUse(asked.subject(), "append");
    }

    /**
     * INSERT: put this in where the series is held, and answer the position
     * just past what went in, so a second insert carries on after the first.
     */
    default Value insert(Asked asked) {
        throw Raised.cannotUse(asked.subject(), "insert");
    }

    /**
     * CLEAR: throw away everything from here to the end, and answer the value
     * still standing where it was.
     *
     * <p>From <em>here</em>, not from the head: clearing a block held at its
     * third item leaves the first two. A datatype with no position of its own
     * empties completely, because everywhere is its head.
     */
    default Value cleared() {
        throw Raised.cannotUse(subject(), "clear");
    }

    /** LENGTH?: how many, counted in whatever this datatype counts in. */
    default int length() {
        throw Raised.cannotUse(subject(), "length?");
    }

    /**
     * COMPLEMENT: everything this one is not.
     *
     * <p>What that means is the datatype's own: a bitset turns its
     * membership round, a binary and an integer turn their bits over, a
     * typeset answers the datatypes it excluded, and an image flips every
     * channel of every pixel.
     */
    default Value complemented() {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                "complement wanted a logic or integer, not a "
                        + subject().datatype().literalSpelling());
    }
}
