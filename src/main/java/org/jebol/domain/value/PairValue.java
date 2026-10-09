package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.function.DoublePredicate;
import java.util.function.ToLongFunction;

public record PairValue(double x, double y) implements Value, PathTarget {

    private static final int BITS_IN_A_HALF = 32;

    @Override
    public Value randomised(RandomDraw draw) {
        return PairValue.of(halfRandomised(x, draw), halfRandomised(y, draw));
    }

    private double halfRandomised(double half, RandomDraw draw) {
        long bound = (long) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, half));
        return bound == 0 ? 0 : draw.upTo(bound);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return (bitsOfTheHalf(y) << BITS_IN_A_HALF) | bitsOfTheHalf(x);
    }

    private long bitsOfTheHalf(double half) {
        return Integer.toUnsignedLong(Float.floatToRawIntBits((float) half));
    }

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
        return TYPE;
    }

    public static final Datatype TYPE = new ScalarDatatype("pair") {

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            return built(Conversion.MAKE, spec, maker);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case PairValue pair -> pair;
                case IntegerValue whole -> PairValue.square(whole.magnitude());
                case DecimalValue quantity -> PairValue.square(quantity.quantity());
                case AnyStringValue text -> readPair(text, maker);
                case BlockValue block -> pairOf(block);
                default -> throw refusing(from);
            };
        }

        private Value pairOf(BlockValue block) {
            List<Value> halves = block.remaining();
            if (halves.size() != 2) {
                throw refusing(block);
            }
            return PairValue.of(aHalfIn(halves.get(0), block), aHalfIn(halves.get(1), block));
        }

        private double aHalfIn(Value half, BlockValue block) {
            return switch (half) {
                case IntegerValue(long magnitude) -> magnitude;
                case AnyDecimalValue number -> number.quantity();
                default -> throw refusing(block);
            };
        }

        private Value readPair(AnyStringValue text, Maker maker) {
            List<Value> read = maker.valuesReadFrom(text.text()).orElse(List.of());
            if (read.size() != 1 || !(read.getFirst() instanceof PairValue pair)) {
                throw refusing(StringValue.of(text.text()));
            }
            return pair;
        }
    };

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
    public Value steppedIntoBy(Value selector) {
        Optional<Value> half = switch (selector) {
            case IntegerValue position -> halfAt((int) position.magnitude());
            case AnyWordValue name -> half(name.canonical());
            default -> Optional.empty();
        };
        return half.orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                "a pair has an x half, a y half and an area, and nothing else"));
    }

    @Override
    public void writeThrough(Slot place, Value selector, Value written) {
        place.setValue(withHalfWritten(selector, written));
    }

    public PairValue withHalfWritten(Value selector, Value written) {
        int half = theHalfNamedBy(selector);
        double replacement = switch (written) {
            case IntegerValue whole -> whole.magnitude();
            case AnyDecimalValue quantity -> quantity.quantity();
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        };
        return withHalfAt(half, replacement);
    }

    private int theHalfNamedBy(Value selector) {
        return switch (selector) {
            case AnyWordValue name when name.canonical().equals(FIRST_HALF) -> 1;
            case AnyWordValue name when name.canonical().equals(SECOND_HALF) -> 2;
            case AnyWordValue name when name.canonical().equals(AREA) ->
                    throw Raised.of(EvaluationFailure.BAD_PATH_SET);
            case IntegerValue position when position.magnitude() == 1
                    || position.magnitude() == 2 -> (int) position.magnitude();
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH);
        };
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
