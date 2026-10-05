package org.jebol.domain.eval;

import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.Optional;

public record Wildcards(char anyRun, char oneCharacter) {

    public static final Wildcards STARS_AND_QUESTION_MARKS = new Wildcards('*', '?');

    public Wildcards orThoseChosenBy(Optional<Value> given) {
        return given.filter(StringValue.class::isInstance)
                .map(chosen -> chosenFrom(((StringValue) chosen).text()))
                .orElse(this);
    }

    public boolean matchTheWholeOf(String within, String pattern) {
        return patternEnd(within, 0, within.length(), pattern, true) == within.length();
    }

    public int patternEnd(
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
        if (first != oneCharacter && !sameCharacter(within.charAt(from), first, mindingCase)) {
            return -1;
        }
        return patternEnd(within, from + 1, upTo, pattern.substring(1), mindingCase);
    }

    private Wildcards chosenFrom(String characters) {
        return new Wildcards(
                characters.isEmpty() ? anyRun : characters.charAt(0),
                characters.length() < 2 ? oneCharacter : characters.charAt(1));
    }

    private int endAfterTheShortestRun(
            String within, int from, int upTo, String pattern, boolean mindingCase) {
        for (int taken = from; taken <= upTo; taken++) {
            int end = patternEnd(within, taken, upTo, pattern.substring(1), mindingCase);
            if (end >= 0) {
                return end;
            }
        }
        return -1;
    }

    private boolean sameCharacter(char left, char right, boolean mindingCase) {
        return mindingCase
                ? left == right
                : Character.toLowerCase(left) == Character.toLowerCase(right);
    }
}
