package org.jebol.domain.value;

import java.util.Optional;
import java.util.function.DoublePredicate;

public record PairValue(double x, double y) implements Value {

    public boolean bothHalves(DoublePredicate asked) {
        return asked.test(x) && asked.test(y);
    }

    @Override
    public Value absolute() {
        return PairValue.of(Math.abs(x), Math.abs(y));
    }

    @Override
    public Value negated() {
        return PairValue.of(-x, -y);
    }

    @Override
    public Value heldBetween(Value lowest, Value highest) {
        PairValue floor = (PairValue) lowest;
        PairValue ceiling = (PairValue) highest;
        return PairValue.of(
                Math.max(floor.x, Math.min(ceiling.x, x)),
                Math.max(floor.y, Math.min(ceiling.y, y)));
    }

    @Override
    public Value partWayTo(Value destination, double fraction) {
        if (!(destination instanceof PairValue(double reachedX, double reachedY))) {
            throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(destination));
        }
        return PairValue.of(
                x + (reachedX - x) * fraction,
                y + (reachedY - y) * fraction);
    }

    public Value apartFrom(PairValue other, boolean alongTheStreets) {
        double across = x - other.x;
        double down = y - other.y;
        return DecimalValue.of(alongTheStreets
                ? Math.abs(across) + Math.abs(down)
                : Math.hypot(across, down));
    }

    public Value angleFromTheOrigin(boolean inRadians) {
        double angle = Math.atan2(y, x);
        return DecimalValue.of(inRadians ? angle : Math.toDegrees(angle));
    }


    @Override
    public Value arithmetic(Value right, ArithmeticOperation operation) {
        return new PairActions(this).combinedWith(right, operation);
    }


    @Override
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof PairValue(double theirX, double theirY)
                && x == theirX && y == theirY;
    }

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        PairValue theirs = aPointFrom(right);
        return PairValue.of(
                bitsOf(x, theirs.x, operation),
                bitsOf(y, theirs.y, operation));
    }

    private PairValue aPointFrom(Value right) {
        return switch (right) {
            case PairValue point -> point;
            case IntegerValue(long magnitude) -> PairValue.of(magnitude, magnitude);
            default -> throw Raised.notRelated(this, right);
        };
    }

    private long bitsOf(double ours, double theirs, BitwiseOperation operation) {
        return operation.onWholeElements(roundedHalfUp(ours), roundedHalfUp(theirs));
    }

    private long roundedHalfUp(double half) {
        return (long) Math.floor(half + 0.5);
    }


    private static final String FIRST_HALF = "x";
    private static final String SECOND_HALF = "y";
    private static final String AREA = "area";

    public PairValue {
        x = narrowedToSinglePrecisionGoingInfiniteRatherThanRefusing(x);
        y = narrowedToSinglePrecisionGoingInfiniteRatherThanRefusing(y);
    }

    private static double narrowedToSinglePrecisionGoingInfiniteRatherThanRefusing(
            double half) {
        return (float) half;
    }

    public static PairValue of(double x, double y) {
        return new PairValue(x, y);
    }

    public static PairValue square(double half) {
        return new PairValue(half, half);
    }

    @Override
    public Datatype datatype() {
        return Datatype.PAIR;
    }

    public Optional<Value> half(String name) {
        return switch (name) {
            case FIRST_HALF -> Optional.of(DecimalValue.of(x));
            case SECOND_HALF -> Optional.of(DecimalValue.of(y));
            case AREA -> Optional.of(DecimalValue.of(Math.abs(x * y)));
            default -> Optional.empty();
        };
    }

    public PairValue withHalfAt(int position, double replacement) {
        return position == 1
                ? new PairValue(replacement, y)
                : new PairValue(x, replacement);
    }

    @Override
    public Value picked(int oneBasedPosition) {
        return halfAt(oneBasedPosition).orElseGet(NoneValue::none);
    }

    public Optional<Value> halfAt(int position) {
        return switch (position) {
            case 1 -> Optional.of(DecimalValue.of(x));
            case 2 -> Optional.of(DecimalValue.of(y));
            default -> Optional.empty();
        };
    }

    public PairValue reversed() {
        return new PairValue(y, x);
    }

    @Override
    public String toString() {
        return Molder.mold(this);
    }
}
