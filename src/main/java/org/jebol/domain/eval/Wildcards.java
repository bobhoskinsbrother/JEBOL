package org.jebol.domain.eval;

import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;

import java.util.Optional;

public record Wildcards(char anyRun, char oneCharacter) {

    public static final Wildcards STARS_AND_QUESTION_MARKS = new Wildcards('*', '?');

    public Wildcards overrideWith(Optional<Value> given) {
        return given.filter(AnyStringValue.class::isInstance)
                .map(chosen -> chooseFrom(((AnyStringValue) chosen).text()))
                .orElse(this);
    }

    public boolean matchTheWholeOf(String within, String pattern) {
        return findPatternEnd(within, 0, within.length(), pattern, true) == within.length();
    }

    public int findPatternEnd(
            String within, int from, int upTo, String pattern, boolean mindingCase) {
        if (pattern.isEmpty()) {
            return from;
        }
        char first = pattern.charAt(0);
        if (first == anyRun) {
            return pattern.length() == 1
                    ? upTo
                    : endAfterTheShortestRun(within, from, upTo, pattern, mindingCase);
        }
        if (from >= upTo) {
            return -1;
        }
        if (first != oneCharacter && !isTheSameCharacter(within.charAt(from), first, mindingCase)) {
            return -1;
        }
        return findPatternEnd(within, from + 1, upTo, pattern.substring(1), mindingCase);
    }

    private Wildcards chooseFrom(String characters) {
        return new Wildcards(
                characters.isEmpty() ? anyRun : characters.charAt(0),
                characters.length() < 2 ? oneCharacter : characters.charAt(1));
    }

    private int endAfterTheShortestRun(
            String within, int from, int upTo, String pattern, boolean mindingCase) {
        for (int taken = from; taken <= upTo; taken++) {
            int end = findPatternEnd(within, taken, upTo, pattern.substring(1), mindingCase);
            if (end >= 0) {
                return end;
            }
        }
        return -1;
    }

    private boolean isTheSameCharacter(char left, char right, boolean mindingCase) {
        return mindingCase
                ? left == right
                : Character.toLowerCase(left) == Character.toLowerCase(right);
    }
}
