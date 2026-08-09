package org.jebol.application;

import java.time.Duration;

/**
 * What a host decides before a script runs.
 *
 * <p>Every field is a bound and every bound has a default, because a host
 * that must configure several things before running anything will configure
 * none of them and get whatever happens.
 *
 * @param wallClockLimit how long a script may run before it is stopped
 * @param maximumNesting how deeply it may nest before it is stopped
 * @param checkEvery how many evaluation steps between checks for whether it
 *     should stop. Checking every step is safest and slowest; this is the
 *     compromise, and it is a field rather than a constant so it can be
 *     measured rather than argued about
 * @param hostAccess what the script may reach outside itself
 */
public record Bounds(
        Duration wallClockLimit,
        int maximumNesting,
        int checkEvery,
        HostAccess hostAccess) {

    private static final Duration DEFAULT_WALL_CLOCK_LIMIT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAXIMUM_NESTING = 10_000;
    private static final int DEFAULT_CHECK_EVERY = 1_000;

    public Bounds {
        if (wallClockLimit == null || wallClockLimit.isNegative() || wallClockLimit.isZero()) {
            throw new IllegalArgumentException("a wall clock limit must be a positive duration");
        }
        if (maximumNesting < 1) {
            throw new IllegalArgumentException("nesting must be allowed at least once");
        }
        if (checkEvery < 1) {
            throw new IllegalArgumentException("checks must happen at least every step");
        }
        if (hostAccess == null) {
            throw new IllegalArgumentException(
                    "host access is a decision; use HostAccess.NONE_AT_ALL to decide against");
        }
    }

    /** Long enough for ordinary work, short enough not to hold a request open. */
    public static Bounds standard() {
        return new Bounds(
                DEFAULT_WALL_CLOCK_LIMIT, DEFAULT_MAXIMUM_NESTING, DEFAULT_CHECK_EVERY,
                HostAccess.NONE_AT_ALL);
    }

    public Bounds withWallClockLimit(Duration limit) {
        return new Bounds(limit, maximumNesting, checkEvery, hostAccess);
    }

    public Bounds withMaximumNesting(int nesting) {
        return new Bounds(wallClockLimit, nesting, checkEvery, hostAccess);
    }

    public Bounds withCheckEvery(int steps) {
        return new Bounds(wallClockLimit, maximumNesting, steps, hostAccess);
    }

    /** What the script may reach outside itself. Nothing, by default. */
    public Bounds withHostAccess(HostAccess access) {
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, access);
    }
}
