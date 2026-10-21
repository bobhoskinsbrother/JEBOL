package org.jebol.domain.value;

import java.util.OptionalLong;

public record HexWidth(OptionalLong asked) {

    private static final int WIDEST_NUMBER_IS_WRITTEN = 16;

    private static final long MOST_ASKABLE_WIDTH = 0xFFFFFFFFL;

    public HexWidth {
        asked.ifPresent(width -> {
            if (width <= 0 || width > MOST_ASKABLE_WIDTH) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, IntegerValue.of(width));
            }
        });
    }

    String keepSixteenDigitsToRight(long number, int whenNoWidthIsAsked) {
        String hex = "%016X".formatted(number);
        int kept = asked.isPresent()
                ? (int) Math.min(asked.getAsLong(), WIDEST_NUMBER_IS_WRITTEN)
                : whenNoWidthIsAsked;
        return hex.substring(Math.max(0, hex.length() - kept));
    }

    String keepFromLeft(String hex, int longestThatCanBeAsked) {
        return asked.isEmpty()
                ? hex
                : hex.substring(0, (int) Math.min(
                        Math.min(asked.getAsLong(), longestThatCanBeAsked), hex.length()));
    }
}
