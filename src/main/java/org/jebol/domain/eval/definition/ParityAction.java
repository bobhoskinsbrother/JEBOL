package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public abstract class ParityAction extends DefaultNative implements ActionValue {

    private static final double WHERE_A_DECIMAL_STOPS_COUNTING_IN_ONES = 9007199254740992.0;

    protected abstract boolean asksForOdd();

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("number");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                arguments.getFirst() instanceof PairValue pair
                        ? pair.bothHalves(half -> isOdd(roundedHalfUp(half)) == asksForOdd())
                        : isOdd(roundedWholeOf(arguments.getFirst())) == asksForOdd());
    }

    private boolean isOdd(long whole) {
        return Math.abs(whole % 2) == 1;
    }

    private long roundedHalfUp(double half) {
        return (long) Math.floor(half + 0.5);
    }

    private long roundedWholeOf(Value value) {
        return switch (value) {
            case MoneyValue amount -> (int) amount.asDeci().toLong();
            case CharacterValue(int codepoint) -> codepoint;
            case TimeValue(long nanoseconds) -> nanoseconds / 1_000_000_000L;
            case DateValue date -> date.day();
            case DecimalValue fractional -> roundedHalfAwayFromZero(fractional.quantity());
            case IntegerValue(long magnitude) -> magnitude;
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    nativeName() + " takes a whole number, not "
                            + value.datatype().literalSpelling());
        };
    }

    private long roundedHalfAwayFromZero(double magnitude) {
        if (Math.abs(magnitude) >= WHERE_A_DECIMAL_STOPS_COUNTING_IN_ONES) {
            return 0;
        }
        return (long) (magnitude < 0
                ? -Math.floor(-magnitude + 0.5)
                : Math.floor(magnitude + 0.5));
    }
}
