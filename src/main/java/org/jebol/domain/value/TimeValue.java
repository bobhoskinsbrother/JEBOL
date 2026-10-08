package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

public record TimeValue(long nanoseconds) implements Value {

    @Override
    public Value randomised(RandomDraw draw) {
        return TimeValue.ofNanoseconds(draw.upTo(nanoseconds));
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return nanoseconds;
    }

    @Override
    public Value absolute() {
        return TimeValue.ofNanoseconds(Math.abs(nanoseconds));
    }


    @Override
    public Value arithmetic(Value right, ArithmeticOperation operation) {
        return right instanceof DateValue moment
                && operation.isCommutative() && !operation.multiplies()
                ? new DateArithmetic(moment).takenBy(this, operation)
                : new TimeActions(this).combinedWith(right, operation);
    }


    @Override
    public Value pickedBy(Value selector) {
        long seconds = Math.abs(nanoseconds) / NANOSECONDS_PER_SECOND;
        long fraction = Math.abs(nanoseconds) % NANOSECONDS_PER_SECOND;
        return switch (thePartNamedBy(selector)) {
            case "hour" -> IntegerValue.of(seconds / 3600);
            case "minute" -> IntegerValue.of(seconds / 60 % 60);
            case "second" -> fraction == 0
                    ? IntegerValue.of(seconds % 60)
                    : DecimalValue.of(seconds % 60 + (double) fraction / NANOSECONDS_PER_SECOND);
            default -> NoneValue.none();
        };
    }

    private String thePartNamedBy(Value selector) {
        return switch (selector) {
            case AnyWordValue asked -> asked.canonical();
            case IntegerValue(long magnitude) when magnitude == 1 -> "hour";
            case IntegerValue(long magnitude) when magnitude == 2 -> "minute";
            case IntegerValue(long magnitude) when magnitude == 3 -> "second";
            default -> "";
        };
    }

    public AnyDecimalValue asSeconds() {
        return DecimalValue.of((double) nanoseconds / NANOSECONDS_PER_SECOND);
    }

    @Override
    public Optional<AnyDecimalValue> asDecimalNumber() {
        return Optional.of(asSeconds());
    }


    public static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;

    public static final long NANOSECONDS_PER_HOUR =
            MINUTES_PER_HOUR * SECONDS_PER_MINUTE * NANOSECONDS_PER_SECOND;

    private static final long HOURS_PER_DAY = 24L;

    public static final long NANOSECONDS_PER_DAY = HOURS_PER_DAY * NANOSECONDS_PER_HOUR;

    public static final long LONGEST =
            (Long.MAX_VALUE / NANOSECONDS_PER_HOUR) * NANOSECONDS_PER_HOUR;

    public static TimeValue ofNanoseconds(long nanoseconds) {
        return new TimeValue(nanoseconds);
    }

    public static TimeValue of(long hours, long minutes, long seconds, long nanoseconds) {
        long total = ((hours * MINUTES_PER_HOUR + minutes) * SECONDS_PER_MINUTE + seconds)
                * NANOSECONDS_PER_SECOND + nanoseconds;
        return new TimeValue(total);
    }

    public boolean isNegative() {
        return nanoseconds < 0;
    }

    public long hours() {
        return Math.abs(nanoseconds) / NANOSECONDS_PER_SECOND
                / SECONDS_PER_MINUTE / MINUTES_PER_HOUR;
    }

    public long minutes() {
        return Math.abs(nanoseconds) / NANOSECONDS_PER_SECOND
                / SECONDS_PER_MINUTE % MINUTES_PER_HOUR;
    }

    public long seconds() {
        return Math.abs(nanoseconds) / NANOSECONDS_PER_SECOND % SECONDS_PER_MINUTE;
    }

    public long subsecondNanoseconds() {
        return Math.abs(nanoseconds) % NANOSECONDS_PER_SECOND;
    }

    private static final long NANOSECONDS_PER_MINUTE =
            SECONDS_PER_MINUTE * NANOSECONDS_PER_SECOND;

    private long hoursCountedWithTheirSign() {
        return nanoseconds / NANOSECONDS_PER_HOUR;
    }

    private long minutesCountedWithTheirSign() {
        return nanoseconds % NANOSECONDS_PER_HOUR / NANOSECONDS_PER_MINUTE;
    }

    public TimeValue withTheHour(long hours) {
        return ofNanoseconds(nanoseconds
                - hoursCountedWithTheirSign() * NANOSECONDS_PER_HOUR
                + hours * NANOSECONDS_PER_HOUR);
    }

    public TimeValue withTheMinute(long minutes) {
        return ofNanoseconds(nanoseconds
                - minutesCountedWithTheirSign() * NANOSECONDS_PER_MINUTE
                + minutes * NANOSECONDS_PER_MINUTE);
    }

    public TimeValue withTheSecondOf(long secondsInNanoseconds) {
        long secondsPart = nanoseconds
                - hoursCountedWithTheirSign() * NANOSECONDS_PER_HOUR
                - minutesCountedWithTheirSign() * NANOSECONDS_PER_MINUTE;
        return ofNanoseconds(nanoseconds - secondsPart + secondsInNanoseconds);
    }

    @Override
    public Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(inHundredths(
                wanted, (double) nanoseconds / NANOSECONDS_PER_SECOND));
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new TimeDatatype();

    private static final class TimeDatatype extends Datatype {

        private static final double MOST_SECONDS_A_DURATION_HOLDS = 9_223_372_036.0;

        private static final long MOST_SECONDS_A_TIME_HOLDS = 9_223_372_036L;

        private static final long SECONDS_AN_HOUR = 3600L;

        TimeDatatype() {
            super("time", Typeset.SCALAR);
        }

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            return spec instanceof AnyBlockValue parts
                    ? timeFromParts(parts.remaining())
                    : super.madeFrom(spec, maker);
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            return construction.madeOf(this, contents.getFirst());
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case TimeValue already -> already;
                case StringValue written -> theTimeScannedFrom(written);
                case BlockValue parts -> aTimeOfHoursMinutesAndSeconds(parts);
                case ParenValue parts -> aTimeOfHoursMinutesAndSeconds(parts);
                case IntegerValue number -> aDurationOfSeconds(number, number.magnitude());
                case DecimalValue number -> aDurationOfSeconds(number, number.quantity());
                default -> throw refusing(from);
            };
        }

        private Value timeFromParts(List<Value> parts) {
            if (parts.isEmpty() || parts.size() > 3
                    || !(parts.get(0) instanceof IntegerValue(long hours))) {
                throw refusing(BlockValue.block(parts));
            }
            boolean negative = hours < 0;
            long seconds = Math.abs(hours) * SECONDS_AN_HOUR;
            long fraction = 0;
            if (parts.size() > 1) {
                if (!(parts.get(1) instanceof IntegerValue(long minutes)) || minutes < 0) {
                    throw refusing(BlockValue.block(parts));
                }
                seconds += minutes * 60;
            }
            if (parts.size() > 2) {
                switch (parts.get(2)) {
                    case IntegerValue whole when whole.magnitude() >= 0 ->
                            seconds += whole.magnitude();
                    case AnyDecimalValue part -> {
                        seconds += (long) part.quantity();
                        fraction = Math.round(
                                (part.quantity() - (long) part.quantity()) * NANOSECONDS_PER_SECOND);
                    }
                    default -> throw refusing(BlockValue.block(parts));
                }
            }
            long total = seconds * NANOSECONDS_PER_SECOND + fraction;
            return ofNanoseconds(negative ? -total : total);
        }

        private Value aDurationOfSeconds(Value value, double seconds) {
            if (seconds < -MOST_SECONDS_A_DURATION_HOLDS || seconds > MOST_SECONDS_A_DURATION_HOLDS) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, value);
            }
            return ofNanoseconds(TimeActions.wholeNanosecondsOf(value));
        }

        private Value aTimeOfHoursMinutesAndSeconds(AnyBlockValue parts) {
            List<Value> given = parts.remaining();
            if (given.isEmpty() || given.size() > 3
                    || !(given.getFirst() instanceof IntegerValue(long hours))) {
                throw refusing(parts);
            }
            boolean negated = hours < 0;
            long seconds = whatFitsInThirtyTwoBits(Math.abs(hours), parts) * SECONDS_AN_HOUR;
            double fraction = 0.0;
            for (int at = 1; at < given.size(); at++) {
                if (seconds > MOST_SECONDS_A_TIME_HOLDS) {
                    throw refusing(parts);
                }
                Value part = given.get(at);
                if (at == 2 && part instanceof DecimalValue fractional) {
                    fraction = fractional.quantity();
                    if (seconds + (long) fraction + 1 > MOST_SECONDS_A_TIME_HOLDS) {
                        throw refusing(parts);
                    }
                    break;
                }
                if (!(part instanceof IntegerValue(long magnitude)) || magnitude < 0) {
                    throw refusing(parts);
                }
                seconds += whatFitsInThirtyTwoBits(magnitude, parts) * (at == 1 ? 60L : 1L);
            }
            if (seconds > MOST_SECONDS_A_TIME_HOLDS) {
                throw refusing(parts);
            }
            long nanoseconds = seconds * NANOSECONDS_PER_SECOND
                    + Math.round(fraction * NANOSECONDS_PER_SECOND);
            return ofNanoseconds(negated ? -nanoseconds : nanoseconds);
        }

        private long whatFitsInThirtyTwoBits(long magnitude, Value about) {
            if (magnitude > Integer.MAX_VALUE) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, about);
            }
            return magnitude;
        }

        private Value theTimeScannedFrom(StringValue given) {
            ScanningATime scanning = new ScanningATime(new WrittenText(given.text()).theOneTimeIn());
            if (!scanning.readsATime()) {
                throw refusing(given);
            }
            return ofNanoseconds(scanning.nanoseconds());
        }
    }

    @Override
    public String toString() {
        String written = (isNegative() ? "-" : "")
                + hours() + ":" + String.format("%02d", minutes());
        if (seconds() == 0 && subsecondNanoseconds() == 0) {
            return written;
        }
        return written + ":" + String.format("%02d", seconds()) + writtenFraction();
    }

    private String writtenFraction() {
        long fraction = subsecondNanoseconds();
        if (fraction == 0) {
            return "";
        }
        String digits = String.format("%09d", fraction);
        return "." + digits.replaceFirst("0+$", "");
    }
}
