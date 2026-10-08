package org.jebol.domain.eval.definition;

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
        return List.of(Parameter.required("value"),
                Parameter.belongingTo("to", "multiple", Set.of()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("to", "down", "even", "half-down", "floor", "ceiling", "half-ceiling");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value subject = arguments.getFirst();
            Optional<Value> scale = argumentOf("to", 0, arguments, refinements);
            if (subject instanceof TimeValue time) {
                return roundedTime(time, scale);
            }
            if (subject instanceof PairValue(double x, double y)) {
                return PairValue.of(roundedHalfAway(x), roundedHalfAway(y));
            }
            if (subject instanceof MoneyValue || scale.filter(MoneyValue.class::isInstance).isPresent()) {
                return roundedAsMoneyRounds(subject, scale, refinements);
            }
            double value = Comparison.asDouble(subject);
            if (scale.isEmpty()) {
                return roundedKeepingTheDatatype(subject, roundedBy(value, refinements));
            }
            return roundedToAMultiple(subject, value, scale.get());
        };
    }

    private Value roundedAsMoneyRounds(Value subject, Optional<Value> scale, Set<String> refinements) {
        Deci step = scale.map(this::aStepMoneyRoundsTo).orElse(Deci.ONE);
        Deci rounded = roundedBy(MONEY.asDeci(subject), step, refinements);
        if (subject instanceof MoneyValue && scale.isPresent()) {
            return switch (scale.get()) {
                case DecimalValue quantity when quantity.datatype() == Datatype.PERCENT ->
                        DecimalValue.percent(rounded.toDouble());
                case DecimalValue ignored -> DecimalValue.of(rounded.toDouble());
                case IntegerValue ignored -> IntegerValue.of(rounded.toLong());
                default -> new MoneyValue(rounded);
            };
        }
        return new MoneyValue(rounded);
    }

    private Deci aStepMoneyRoundsTo(Value given) {
        if (!(given instanceof MoneyValue || given instanceof IntegerValue || given instanceof DecimalValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, given);
        }
        Deci step = MONEY.asDeci(given);
        if (step.isZero()) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return step.absolute();
    }

    private Deci roundedBy(Deci number, Deci step, Set<String> refinements) {
        if (refinements.contains("even")) {
            return number.halfEvenTo(step);
        }
        if (refinements.contains("down")) {
            return number.truncatedTo(step);
        }
        if (refinements.contains("half-down")) {
            return number.halfTruncatedTo(step);
        }
        if (refinements.contains("floor")) {
            return number.flooredTo(step);
        }
        if (refinements.contains("ceiling")) {
            return number.ceiledTo(step);
        }
        if (refinements.contains("half-ceiling")) {
            return number.halfCeiledTo(step);
        }
        return number.halfAwayTo(step);
    }

    private static final MoneyCoercion MONEY = new MoneyCoercion();

    private Value roundedToAMultiple(Value subject, double value, Value step) {
        double multiple = Comparison.asDouble(step);
        if (multiple == 0) {
            if (step.datatype() == Datatype.INTEGER && subject.datatype() == Datatype.INTEGER) {
                throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
            }
            return roundedToTheScalesDatatype(step, value);
        }
        return roundedToTheScalesDatatype(step, roundedHalfAway(value / multiple) * multiple);
    }

    private Value roundedTime(TimeValue time, Optional<Value> scale) {
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

    private Value roundedKeepingTheDatatype(Value subject, double rounded) {
        return switch (subject) {
            case MoneyValue amount -> amount.amounting(BigDecimal.valueOf(rounded));
            case DecimalValue quantity when quantity.datatype() == Datatype.PERCENT ->
                    DecimalValue.percent(rounded);
            case IntegerValue ignored -> IntegerValue.of((long) rounded);
            default -> DecimalValue.of(rounded);
        };
    }

    private Value roundedToTheScalesDatatype(Value scale, double rounded) {
        return switch (scale) {
            case MoneyValue amount -> amount.amounting(BigDecimal.valueOf(rounded));
            case IntegerValue ignored -> IntegerValue.of((long) rounded);
            case DecimalValue quantity when quantity.datatype() == Datatype.PERCENT ->
                    DecimalValue.percent(toFifteenDigits(rounded));
            default -> DecimalValue.of(toFifteenDigits(rounded));
        };
    }

    private double toFifteenDigits(double rounded) {
        return new BigDecimal(rounded).round(new MathContext(DIGITS_A_DECIMAL_KEEPS))
                .doubleValue();
    }

    private double roundedBy(double value, Set<String> refinements) {
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
        return roundedHalfAway(value);
    }

    private double roundedHalfAway(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).doubleValue();
    }
}
