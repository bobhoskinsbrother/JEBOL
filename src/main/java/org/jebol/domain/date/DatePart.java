package org.jebol.domain.date;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum DatePart implements DateField {

    YEAR {
        @Override
        public Value readFrom(DateValue date) {
            return IntegerValue.of(date.year());
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.onTheDay(wholeNumberIn(given), date.month(), date.day());
        }
    },

    MONTH {
        @Override
        public Value readFrom(DateValue date) {
            return IntegerValue.of(date.month());
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.onTheDay(date.year(), wholeNumberIn(given), date.day());
        }
    },

    DAY {
        @Override
        public Value readFrom(DateValue date) {
            return IntegerValue.of(date.day());
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.onTheDay(date.year(), date.month(), wholeNumberIn(given));
        }
    },

    TIME {
        @Override
        public Value readFrom(DateValue date) {
            return date.clock();
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return switch (given) {
                case NoneValue _ -> date.asJustTheDay();
                case TimeValue clock -> date.atTheTime(clock);
                case DateValue other -> date.atTheTime(other.clock());
                case IntegerValue seconds -> date.atTheTime(
                        TimeValue.ofNanoseconds(secondsInNanoseconds(seconds)));
                case DecimalValue seconds -> date.atTheTime(
                        TimeValue.ofNanoseconds(secondsInNanoseconds(seconds)));
                default -> throw refusing(given);
            };
        }
    },

    DATE {
        @Override
        public Value readFrom(DateValue date) {
            return date.asJustTheDay();
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.onTheDayOf(aDateIn(given));
        }
    },

    ZONE {
        @Override
        public Value readFrom(DateValue date) {
            return date.zoneAsATime();
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return given instanceof NoneValue
                    ? date.withTheZoneDropped()
                    : date.atMidnightIfItHasNoClock()
                            .withTheSameClockIn(offsetAskedFor(given));
        }
    },

    HOUR {
        @Override
        public Value readFrom(DateValue date) {
            return IntegerValue.of(date.hourOfTheDay());
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.atTheTime(date.clock().withTheHour(wholeNumberIn(given)));
        }
    },

    MINUTE {
        @Override
        public Value readFrom(DateValue date) {
            return IntegerValue.of(date.minuteOfTheHour());
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.atTheTime(date.clock().withTheMinute(wholeNumberIn(given)));
        }
    },

    SECOND {
        @Override
        public Value readFrom(DateValue date) {
            return date.secondOfTheMinute();
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.atTheTime(
                    date.clock().withTheSecondOf(secondsInNanoseconds(given)));
        }
    },

    WEEKDAY {
        @Override
        public Value readFrom(DateValue date) {
            return IntegerValue.of(date.asLocalDate().getDayOfWeek().getValue());
        }
    },

    YEARDAY {
        @Override
        public Value readFrom(DateValue date) {
            return IntegerValue.of(date.asLocalDate().getDayOfYear());
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.onTheDayOfTheYear(wholeNumberIn(given));
        }
    },

    TIMEZONE {
        @Override
        public Value readFrom(DateValue date) {
            return date.zoneAsATime();
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            return date.atMidnightIfItHasNoClock()
                    .atTheSameInstantIn(offsetAskedFor(given));
        }
    },

    UTC {
        @Override
        public Value readFrom(DateValue date) {
            return date.asStoredInUtc();
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            DateValue other = aDateIn(given);
            return other.zoneMinutes().orElse(0) == 0
                    ? other.withTheZoneForgotten()
                    : other.atTheSameInstantIn(0).withTheZoneForgotten();
        }
    },

    JULIAN {
        @Override
        public Value readFrom(DateValue date) {
            return DecimalValue.of(JulianDay.countedFromNoon(date));
        }

        @Override
        public DateValue writtenOn(DateValue date, Value given) {
            if (!(given instanceof DecimalValue counted)) {
                throw refusing(given);
            }
            return JulianDay.asADate(counted.quantity());
        }
    };

    @Override
    public abstract Value readFrom(DateValue date);

    @Override
    public String spelling() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Value readFrom(DateValue date, Value selector) {
        return named(selector)
                .filter(part -> part.isThereToRead(date))
                .map(part -> part.readFrom(date))
                .orElseGet(NoneValue::none);
    }

    public static DateValue writtenOn(DateValue date, Value selector, Value given) {
        return named(selector)
                .orElseThrow(() -> noPartGoesByThatName(selector))
                .writtenOn(date, given);
    }

    public static List<String> partNames() {
        return Arrays.stream(values()).map(DatePart::spelling).toList();
    }

    private static Optional<DatePart> named(Value selector) {
        if (selector instanceof WordValue asked) {
            return Arrays.stream(values())
                    .filter(part -> part.spelling().equals(asked.canonical()))
                    .findFirst();
        }
        if (!(selector instanceof IntegerValue position)) {
            return Optional.empty();
        }
        long counted = position.magnitude();
        return counted >= 1 && counted <= values().length
                ? Optional.of(values()[(int) counted - 1])
                : Optional.empty();
    }

    private static final Set<DatePart> THERE_ONLY_WHEN_A_CLOCK_IS =
            EnumSet.of(TIME, ZONE, TIMEZONE, HOUR, MINUTE, SECOND);

    private boolean isThereToRead(DateValue date) {
        return date.timeOfDay().isPresent()
                || !THERE_ONLY_WHEN_A_CLOCK_IS.contains(this);
    }

    private static Raised noPartGoesByThatName(Value selector) {
        return Raised.of(EvaluationFailure.INVALID_PATH,
                selector instanceof WordValue word ? word.spelling() : "date");
    }

    private static int wholeNumberIn(Value given) {
        return switch (given) {
            case IntegerValue number -> Math.toIntExact(number.magnitude());
            case DecimalValue number -> (int) number.quantity();
            case NoneValue _ -> 0;
            default -> throw refusing(given);
        };
    }

    private static long secondsInNanoseconds(Value given) {
        return given instanceof DecimalValue fraction
                ? (long) (fraction.quantity() * TimeValue.NANOSECONDS_PER_SECOND)
                : (long) wholeNumberIn(given) * TimeValue.NANOSECONDS_PER_SECOND;
    }

    private static DateValue aDateIn(Value given) {
        if (given instanceof DateValue other) {
            return other;
        }
        throw refusing(given);
    }

    private static final int MOST_A_ZONE_MAY_BE = 15 * 60 + 45;

    private static int offsetAskedFor(Value given) {
        return switch (given) {
            case IntegerValue aBareNumberMeansHours -> withinReach(
                    Math.toIntExact(aBareNumberMeansHours.magnitude()) * 60);
            case DecimalValue aBareNumberMeansHours -> withinReach(
                    (int) aBareNumberMeansHours.quantity() * 60);
            case TimeValue clock -> withinReach((int) (clock.nanoseconds()
                    / (60L * TimeValue.NANOSECONDS_PER_SECOND)));
            default -> throw Raised.of(
                    EvaluationFailure.BAD_FIELD_SET, Molder.mold(given));
        };
    }

    private static int withinReach(int offsetMinutes) {
        if (Math.abs(offsetMinutes) > MOST_A_ZONE_MAY_BE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    IntegerValue.of(offsetMinutes));
        }
        return offsetMinutes;
    }

    private static Raised refusing(Value given) {
        return Raised.of(EvaluationFailure.BAD_FIELD_SET, given);
    }

    static Raised noDateReachesThatYear() {
        return Raised.of(EvaluationFailure.TYPE_LIMIT, DatatypeValue.of(Datatype.DATE));
    }
}
