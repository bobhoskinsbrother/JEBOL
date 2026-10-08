package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.Optional;

public interface Actions {

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

    Value subject();

    default Value append(Asked asked) {
        throw Raised.cannotUseTheAction(asked.subject(), "append");
    }

    default Value insert(Asked asked) {
        throw Raised.cannotUseTheAction(asked.subject(), "insert");
    }

    default Value cleared() {
        throw Raised.cannotUseTheAction(subject(), "clear");
    }

    default int length() {
        throw Raised.cannotUseTheAction(subject(), "length?");
    }

    default void takeOutFrom(int oneBasedIndex, int howMany) {
        throw Raised.cannotUseTheAction(subject(), "remove");
    }

    default Value removed(long howMany) {
        throw Raised.cannotUseTheAction(subject(), "remove");
    }

    default Value takenOne() {
        throw Raised.cannotUseTheAction(subject(), "take");
    }

    default Value takenSeveral(long wanted) {
        throw Raised.cannotUseTheAction(subject(), "take");
    }

    default Value poked(Value position, Value written) {
        throw Raised.cannotUseTheAction(subject(), "poke");
    }

    default Value complemented() {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                "complement wanted a logic or integer, not a "
                        + subject().datatype().literalSpelling());
    }
}
