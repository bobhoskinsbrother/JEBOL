package org.jebol.domain.date;

import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.TimeValue;

import java.time.LocalDate;
import java.util.Optional;

final class JulianDay {

    private JulianDay() {
    }

    private static final long NOON_GIVEN_TO_A_DATE_WITH_NO_CLOCK =
            12L * 3600 * TimeValue.NANOSECONDS_PER_SECOND;

    private static final int WIDEST_YEAR_A_DATE_HOLDS = 0x3fff;

    static double countedFromNoon(DateValue date) {
        long nanoseconds = date.timeOfDay().isEmpty()
                ? NOON_GIVEN_TO_A_DATE_WITH_NO_CLOCK
                : date.nanosecondsInUniversalTime();
        long seconds = Math.abs(nanoseconds) / TimeValue.NANOSECONDS_PER_SECOND;
        long hours = seconds / 3600;
        long minutes = seconds / 60 % 60;
        long wholeSeconds = seconds % 60;
        LocalDate day = date.asLocalDate();
        if (hours <= 12) {
            day = day.minusDays(1);
            hours += 12;
        } else {
            hours -= 12;
        }
        long year = day.getYear() + 8000L;
        long month = day.getMonthValue();
        long dayOfMonth = day.getDayOfMonth() - 1L;
        if (month < 3) {
            year--;
            month += 12;
        }
        long julian = year * 365 + year / 4 - year / 100 + year / 400 - 1200820;
        julian += (month * 153 + 3) / 5 - 92;
        julian += dayOfMonth;
        return julian + hours / 24.0 + minutes / 1440.0 + wholeSeconds / 86400.0;
    }

    static DateValue asADate(double julian) {
        double fraction = julian - Math.floor(julian);
        int wholeDays = (int) Math.floor(julian);
        int leapYearsSince4713bc = (int) ((wholeDays - 1867216.25) / 36524.25);
        int fourYearCycles = leapYearsSince4713bc / 4;
        int adjusted = wholeDays + 1 + leapYearsSince4713bc - fourYearCycles + 1524;
        int estimatedYear = (int) ((adjusted - 122.1) / 365.25);
        int daysBeforeThisMonth = (int) (365.25 * estimatedYear);
        int monthNumber = (int) ((adjusted - daysBeforeThisMonth) / 30.6001);
        int daysBeforeThisDay = (int) (30.6001 * monthNumber);
        int day = (int) (adjusted - daysBeforeThisDay - daysBeforeThisMonth + fraction);
        int month = monthNumber < 14 ? monthNumber - 1 : monthNumber - 13;
        int year = month > 2 ? estimatedYear - 4716 : estimatedYear - 4715;
        return aDayWithTheClockAFractionNames(year, month, day, fraction);
    }

    private static DateValue aDayWithTheClockAFractionNames(
            int year, int month, int day, double fraction) {

        double hoursAndOver = fraction * 24;
        long hours = (long) hoursAndOver;
        double minutesAndOver = (hoursAndOver - hours) * 60;
        long minutes = (long) minutesAndOver;
        long seconds = Math.round((minutesAndOver - minutes) * 60);
        long nanoseconds = (hours + 12) * TimeValue.NANOSECONDS_PER_HOUR
                + minutes * 60 * TimeValue.NANOSECONDS_PER_SECOND
                + seconds * TimeValue.NANOSECONDS_PER_SECOND;
        LocalDate landedOn = aDayThatMayHaveRolledOver(year, month, day)
                .plusDays(nanoseconds / TimeValue.NANOSECONDS_PER_DAY);
        return new DateValue(landedOn.getYear(), landedOn.getMonthValue(),
                landedOn.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(
                        nanoseconds % TimeValue.NANOSECONDS_PER_DAY)),
                Optional.of(0));
    }

    private static LocalDate aDayThatMayHaveRolledOver(int year, int month, int day) {
        if (year < 0 || year > WIDEST_YEAR_A_DATE_HOLDS) {
            throw DatePart.noDateReachesThatYear();
        }
        try {
            return LocalDate.of(year, 1, 1)
                    .plusMonths(month - 1L)
                    .plusDays(day - 1L);
        } catch (java.time.DateTimeException | ArithmeticException unreachable) {
            throw DatePart.noDateReachesThatYear();
        }
    }
}
