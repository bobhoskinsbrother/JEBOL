package org.jebol.domain.value;

import java.util.OptionalLong;

public record HexWidth(OptionalLong asked) {

    private static final int THE_WIDEST_A_NUMBER_IS_WRITTEN = 16;

    private static final long THE_WIDEST_THAT_CAN_BE_ASKED = 0xFFFFFFFFL;

    public HexWidth {
        asked.ifPresent(width -> {
            if (width <= 0 || width > THE_WIDEST_THAT_CAN_BE_ASKED) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, IntegerValue.of(width));
            }
        });
    }

    String sixteenDigitsKeptToTheRight(long number, int whenNoWidthIsAsked) {
        String hex = "%016X".formatted(number);
        int kept = asked.isPresent()
                ? (int) Math.min(asked.getAsLong(), THE_WIDEST_A_NUMBER_IS_WRITTEN)
                : whenNoWidthIsAsked;
        return hex.substring(Math.max(0, hex.length() - kept));
    }

    String keptFromTheLeft(String hex, int longestThatCanBeAsked) {
        return asked.isEmpty()
                ? hex
                : hex.substring(0, (int) Math.min(
                        Math.min(asked.getAsLong(), longestThatCanBeAsked), hex.length()));
    }
}
