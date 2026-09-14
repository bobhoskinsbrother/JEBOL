package org.jebol.domain.date;

import org.jebol.domain.value.DateValue;

public final class DateOrder {

    private DateOrder() {
    }

    public static int comparing(DateValue first, DateValue second) {
        return first.moment().compareTo(second.moment());
    }

    public static boolean theSameMoment(DateValue first, DateValue second) {
        return comparing(first, second) == 0;
    }

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
