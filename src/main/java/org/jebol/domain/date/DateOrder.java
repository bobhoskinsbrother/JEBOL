package org.jebol.domain.date;

import org.jebol.domain.value.DateValue;

public final class DateOrder {

    private DateOrder() {
    }

    public static int compare(DateValue first, DateValue second) {
        return first.findTheMoment().compareTo(second.findTheMoment());
    }

    public static boolean isTheSameMoment(DateValue first, DateValue second) {
        return compare(first, second) == 0;
    }

    public static boolean isWrittenTheSameWay(DateValue first, DateValue second) {
        return first.year() == second.year()
                && first.month() == second.month()
                && first.day() == second.day()
                && first.timeOfDay().equals(second.timeOfDay())
                && readTheZoneOrNoOffset(first).equals(readTheZoneOrNoOffset(second));
    }

    private static Integer readTheZoneOrNoOffset(DateValue date) {
        return date.zoneMinutes().orElse(0);
    }
}
