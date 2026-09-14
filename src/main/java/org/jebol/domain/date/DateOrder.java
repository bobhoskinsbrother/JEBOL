package org.jebol.domain.date;

import org.jebol.domain.value.DateValue;

/**
 * When two dates count as the same and which of them comes first --
 * {@code CT_Date} and {@code Cmp_Date} in {@code t-date.c}.
 *
 * <p>The two questions are not the same one. Loosely, two dates are equal
 * when they name the same instant, so noon in London and one o'clock in
 * Paris are one moment and compare equal. Strictly, they must also have been
 * written the same way, because {@code ==} asks whether two values are
 * interchangeable and those two are not -- printing them gives different
 * text.
 *
 * <p>A date with no zone is read as having no offset rather than as having
 * none to compare, which is why a bare {@code 1-Jan-2026} and the same date
 * written {@code +0:00} are strictly equal.
 */
public final class DateOrder {

    private DateOrder() {
    }

    /** Which comes first, by the instant each names once its zone is applied. */
    public static int comparing(DateValue first, DateValue second) {
        return first.moment().compareTo(second.moment());
    }

    /** Whether two dates name the same instant, however each was written. */
    public static boolean theSameMoment(DateValue first, DateValue second) {
        return comparing(first, second) == 0;
    }

    /**
     * Whether two dates were also written the same way, which is what
     * {@code ==} asks: the same day, the same clock, and the same offset.
     */
    public static boolean writtenTheSameWay(DateValue first, DateValue second) {
        return first.year() == second.year()
                && first.month() == second.month()
                && first.day() == second.day()
                && first.timeOfDay().equals(second.timeOfDay())
                && zoneOrNoOffset(first).equals(zoneOrNoOffset(second));
    }

    private static Integer zoneOrNoOffset(DateValue date) {
        return date.zoneMinutes().orElse(0);
    }
}
