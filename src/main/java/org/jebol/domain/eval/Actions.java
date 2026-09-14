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
        throw Raised.cannotUse(asked.subject(), "append");
    }

    default Value insert(Asked asked) {
        throw Raised.cannotUse(asked.subject(), "insert");
    }

    default Value cleared() {
        throw Raised.cannotUse(subject(), "clear");
    }

    default int length() {
        throw Raised.cannotUse(subject(), "length?");
    }

    default Value complemented() {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                "complement wanted a logic or integer, not a "
                        + subject().datatype().literalSpelling());
    }
}
