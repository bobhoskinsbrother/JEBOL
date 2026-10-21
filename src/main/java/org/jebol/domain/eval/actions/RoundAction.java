package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RoundAction extends DefaultNative implements ActionValue {

    private static final int DIGITS_A_DECIMAL_KEEPS = 15;

    private static final double HALFWAY = 0.5;

    @Override
    public String nativeName() {
        return "round";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("value"),
                Parameter.belongTo("to", "multiple", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("to", "down", "even", "half-down", "floor", "ceiling", "half-ceiling");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value subject = arguments.getFirst();
            Optional<Value> scale = readTheArgumentOf("to", 0, arguments, refinements);
            if (subject instanceof TimeValue time) {
                return roundTheTime(time, scale);
            }
            if (subject instanceof PairValue(double x, double y)) {
                return PairValue.of(roundHalfAway(x), roundHalfAway(y));
            }
            if (subject instanceof MoneyValue || scale.filter(MoneyValue.class::isInstance).isPresent()) {
                return roundAsMoneyRounds(subject, scale, refinements);
            }
            double value = Comparison.asDouble(subject);
            if (scale.isEmpty()) {
                return roundKeepingTheDatatype(subject, roundBy(value, refinements));
            }
            return roundToAMultiple(subject, value, scale.get());
        };
    }

    private Value roundAsMoneyRounds(Value subject, Optional<Value> scale, Set<String> refinements) {
        Deci step = scale.map(this::readStepMoneyRoundsTo).orElse(Deci.ONE);
        Deci rounded = roundBy(MONEY.asDeci(subject), step, refinements);
        if (subject instanceof MoneyValue && scale.isPresent()) {
            return switch (scale.get()) {
                case PercentValue ignored -> PercentValue.of(rounded.toDouble());
                case DecimalValue ignored -> DecimalValue.of(rounded.toDouble());
                case IntegerValue ignored -> IntegerValue.of(rounded.toLong());
                default -> new MoneyValue(rounded);
            };
        }
        return new MoneyValue(rounded);
    }

    private Deci readStepMoneyRoundsTo(Value given) {
        if (!(given instanceof MoneyValue || given instanceof IntegerValue || given instanceof AnyDecimalValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, given);
        }
        Deci step = MONEY.asDeci(given);
        if (step.isZero()) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return step.absolute();
    }

    private Deci roundBy(Deci number, Deci step, Set<String> refinements) {
        if (refinements.contains("even")) {
            return number.roundHalfEvenTo(step);
        }
        if (refinements.contains("down")) {
            return number.truncateTo(step);
        }
        if (refinements.contains("half-down")) {
            return number.roundHalfTowardZeroTo(step);
        }
        if (refinements.contains("floor")) {
            return number.floorTo(step);
        }
        if (refinements.contains("ceiling")) {
            return number.ceilTo(step);
        }
        if (refinements.contains("half-ceiling")) {
            return number.roundHalfTowardCeilingTo(step);
        }
        return number.roundHalfAwayTo(step);
    }

    private static final MoneyCoercion MONEY = new MoneyCoercion();

    private Value roundToAMultiple(Value subject, double value, Value step) {
        double multiple = Comparison.asDouble(step);
        if (multiple == 0) {
            if (step.datatype() == IntegerValue.TYPE && subject.datatype() == IntegerValue.TYPE) {
                throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
            }
            return roundToTheScalesDatatype(step, value);
        }
        return roundToTheScalesDatatype(step, roundHalfAway(value / multiple) * multiple);
    }

    private Value roundTheTime(TimeValue time, Optional<Value> scale) {
        if (scale.isEmpty()) {
            return TimeValue.ofNanoseconds(Math.round(
                    (double) time.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND)
                    * TimeValue.NANOSECONDS_PER_SECOND);
        }
        if (scale.get() instanceof TimeValue(long nanoseconds)) {
            return TimeValue.ofNanoseconds(nanoseconds == 0
                    ? time.nanoseconds()
                    : Math.round((double) time.nanoseconds() / nanoseconds) * nanoseconds);
        }
        double stepSeconds = Comparison.asDouble(scale.get());
        double seconds = (double) time.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND;
        double rounded = stepSeconds == 0
                ? seconds
                : Math.round(seconds / stepSeconds) * stepSeconds;
        return scale.get() instanceof IntegerValue
                ? IntegerValue.of(Math.round(rounded))
                : DecimalValue.of(rounded);
    }

    private Value roundKeepingTheDatatype(Value subject, double rounded) {
        return switch (subject) {
            case MoneyValue amount -> amount.amountTo(BigDecimal.valueOf(rounded));
            case PercentValue ignored -> PercentValue.of(rounded);
            case IntegerValue ignored -> IntegerValue.of((long) rounded);
            default -> DecimalValue.of(rounded);
        };
    }

    private Value roundToTheScalesDatatype(Value scale, double rounded) {
        return switch (scale) {
            case MoneyValue amount -> amount.amountTo(BigDecimal.valueOf(rounded));
            case IntegerValue ignored -> IntegerValue.of((long) rounded);
            case PercentValue ignored -> PercentValue.of(toFifteenDigits(rounded));
            default -> DecimalValue.of(toFifteenDigits(rounded));
        };
    }

    private double toFifteenDigits(double rounded) {
        return new BigDecimal(rounded).round(new MathContext(DIGITS_A_DECIMAL_KEEPS))
                .doubleValue();
    }

    private double roundBy(double value, Set<String> refinements) {
        if (refinements.contains("down")) {
            return value < 0 ? Math.ceil(value) : Math.floor(value);
        }
        if (refinements.contains("floor")) {
            return Math.floor(value);
        }
        if (refinements.contains("ceiling")) {
            return Math.ceil(value);
        }
        if (refinements.contains("even")) {
            return Math.rint(value);
        }
        double fraction = Math.abs(value - (long) value);
        if (refinements.contains("half-down") && fraction == HALFWAY) {
            return value < 0 ? Math.ceil(value) : Math.floor(value);
        }
        if (refinements.contains("half-ceiling") && fraction == HALFWAY) {
            return Math.ceil(value);
        }
        return roundHalfAway(value);
    }

    private double roundHalfAway(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }
}
