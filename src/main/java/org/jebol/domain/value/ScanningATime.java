package org.jebol.domain.value;

final class ScanningATime {

    private static final int NOT_GIVEN = -1;
    private static final int DIGITS_OF_A_FRACTION = 9;
    private static final long NANOSECONDS_SECOND = 1_000_000_000L;
    private static final long NANOSECONDS_MINUTE = 60L * NANOSECONDS_SECOND;
    private static final long NANOSECONDS_HOUR = 60L * NANOSECONDS_MINUTE;
    private static final long MOST_HOURS_TIME_HOLDS = 9_223_372_036L / 3600L;
    private static final int NOON = 12;

    private final String text;

    private int at;
    private boolean negated;
    private int firstNumber;
    private int secondNumber;
    private int thirdNumber = NOT_GIVEN;
    private int fractionInNanoseconds = NOT_GIVEN;
    private char afternoonOrMorning;

    ScanningATime(String text) {
        this.text = text;
    }

    boolean readsTime() {
        readAnyLeadingSign();
        if (hasSignAfterSign()) {
            return false;
        }
        firstNumber = readWholeNumberFromHere();
        if (firstNumber > MOST_HOURS_TIME_HOLDS || !stepOverColon()) {
            return false;
        }
        if (!readsSecondNumber()) {
            return false;
        }
        if (!readsAnyThirdNumber()) {
            return false;
        }
        readAnyFraction();
        return readsAnyMeridian();
    }

    long readNanoseconds() {
        long counted = itCountsFirstNumberAsHours()
                ? readHoursMinutesAndSeconds()
                : minutesAndSeconds();
        if (fractionInNanoseconds > 0) {
            counted += fractionInNanoseconds;
        }
        return negated ? -counted : counted;
    }

    private boolean itCountsFirstNumberAsHours() {
        return thirdNumber >= 0 || fractionInNanoseconds < 0;
    }

    private long readHoursMinutesAndSeconds() {
        int hours = firstNumber;
        if (afternoonOrMorning != 0) {
            hours = hours == NOON ? 0 : hours;
            hours += afternoonOrMorning == 'P' ? NOON : 0;
        }
        return hours * NANOSECONDS_HOUR
                + secondNumber * NANOSECONDS_MINUTE
                + Math.max(thirdNumber, 0) * NANOSECONDS_SECOND;
    }

    private long minutesAndSeconds() {
        return firstNumber * NANOSECONDS_MINUTE
                + secondNumber * NANOSECONDS_SECOND;
    }

    private void readAnyLeadingSign() {
        if (here() == '-') {
            negated = true;
            at++;
        } else if (here() == '+') {
            at++;
        }
    }

    private boolean hasSignAfterSign() {
        return here() == '-' || here() == '+';
    }

    private boolean stepOverColon() {
        boolean there = here() == ':';
        at++;
        return there;
    }

    private boolean readsSecondNumber() {
        int began = at;
        secondNumber = readWholeNumberFromHere();
        return secondNumber >= 0 && at != began;
    }

    private boolean readsAnyThirdNumber() {
        if (here() != ':') {
            return true;
        }
        at++;
        int began = at;
        thirdNumber = readWholeNumberFromHere();
        return thirdNumber >= 0 && at != began;
    }

    private void readAnyFraction() {
        if (here() != '.' && here() != ',') {
            return;
        }
        at++;
        fractionInNanoseconds = readScaledNumberFromHere();
        if (fractionInNanoseconds == 0) {
            fractionInNanoseconds = NOT_GIVEN;
        }
    }

    private boolean readsAnyMeridian() {
        char letter = Character.toUpperCase(here());
        if ((letter != 'A' && letter != 'P') || Character.toUpperCase(next()) != 'M') {
            return true;
        }
        afternoonOrMorning = letter;
        at += 2;
        if (itCountsFirstNumberAsHours()) {
            return firstNumber <= NOON;
        }
        return false;
    }

    private int readWholeNumberFromHere() {
        int counted = 0;
        boolean below = here() == '-';
        if (below || here() == '+') {
            at++;
        }
        while (isAnAsciiDigit(here())) {
            counted = counted * 10 + (here() - '0');
            at++;
        }
        return below ? -counted : counted;
    }

    private int readScaledNumberFromHere() {
        long counted = 0;
        int placesLeft = DIGITS_OF_A_FRACTION;
        while (placesLeft > 0 && isAnAsciiDigit(here())) {
            counted = counted * 10 + (here() - '0');
            at++;
            placesLeft--;
        }
        if (here() >= '5' && here() <= '9') {
            counted++;
        }
        while (isAnAsciiDigit(here())) {
            at++;
        }
        for (; placesLeft > 0; placesLeft--) {
            counted *= 10;
        }
        return (int) counted;
    }

    private static boolean isAnAsciiDigit(char letter) {
        return letter >= '0' && letter <= '9';
    }

    private char here() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private char next() {
        return at + 1 < text.length() ? text.charAt(at + 1) : '\0';
    }
}
