package org.jebol.domain.date;

import org.jebol.domain.value.DateValue;

public final class DateOrder {

    private DateOrder() {
    }

    public static int compare(DateValue first, DateValue second) {
        return first.moment().compareTo(second.moment());
    }

    public static boolean same(DateValue first, DateValue second) {
        return first.year() == second.year()
                && first.month() == second.month()
                && first.day() == second.day()
                && first.timeOfDay().equals(second.timeOfDay())
                && zoneMinutesOrZero(first).equals(zoneMinutesOrZero(second));
    }

    private static Integer zoneMinutesOrZero(DateValue date) {
        return date.zoneMinutes().orElse(0);
    }
}
