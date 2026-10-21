package org.jebol.domain.value;

public record Sameness(long stepsAllowed, boolean theyWereBroughtTogetherFirst) {

    private static final long STEPS_ALLOWED_INSIDE_A_SERIES = 10;

    private static final long STEPS_ALLOWED_BETWEEN_DECIMALS = 21;

    public static Sameness insideSeries() {
        return new Sameness(STEPS_ALLOWED_INSIDE_A_SERIES, false);
    }

    public static Sameness allowStepsInsideSeries(long stepsAllowed) {
        return new Sameness(stepsAllowed, false);
    }

    public static Sameness bringTogether() {
        return new Sameness(STEPS_ALLOWED_BETWEEN_DECIMALS, true);
    }

    public static Sameness bringTogetherExactly() {
        return new Sameness(0, true);
    }

    public boolean holdsBetween(Value one, Value other) {
        return one.isEqualTo(other, this);
    }
}
