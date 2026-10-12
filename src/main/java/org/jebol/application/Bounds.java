package org.jebol.application;

import org.jebol.domain.host.HostService;

import java.time.Duration;
import java.time.temporal.ChronoUnit;


public record Bounds(
        Duration wallClockLimit,
        int maximumNesting,
        int checkEvery,
        HostAccess hostAccess,
        java.util.Set<HostService> grantedServices,
        java.util.Set<HostService> lackingServices,
        String hostName) {

    private static final Duration DEFAULT_WALL_CLOCK_LIMIT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAXIMUM_NESTING = 10_000;
    private static final int DEFAULT_CHECK_EVERY = 1_000;
    private static final String DEFAULT_HOST_NAME = "this host";

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
        if (grantedServices == null) {
            throw new IllegalArgumentException(
                    "granted services is a decision; use an empty set to grant nothing");
        }
        if (lackingServices == null) {
            throw new IllegalArgumentException(
                    "lacking services is a decision; use an empty set to lack nothing");
        }
        grantedServices = java.util.Set.copyOf(grantedServices);
        lackingServices = java.util.Set.copyOf(lackingServices);
        if (hostAccess == null) {
            throw new IllegalArgumentException(
                    "host access is a decision; use HostAccess.NONE_AT_ALL to decide against");
        }
        if (hostName == null || hostName.isBlank()) {
            throw new IllegalArgumentException(
                    "a host names itself in a refusal; it may not be nameless");
        }
    }

    /** Long enough for ordinary work, short enough not to hold a request open. */
    public static Bounds standard() {
        return new Bounds(
                DEFAULT_WALL_CLOCK_LIMIT, DEFAULT_MAXIMUM_NESTING, DEFAULT_CHECK_EVERY,
                HostAccess.NONE_AT_ALL, java.util.Set.of(), java.util.Set.of(), DEFAULT_HOST_NAME);
    }

    public Bounds withWallClockLimit(Duration limit) {
        return new Bounds(limit, maximumNesting, checkEvery, hostAccess, grantedServices,
                lackingServices, hostName);
    }

    /** No limit on how long a script may run: what a person running their own script expects. */
    public Bounds withoutAWallClockLimit() {
        return withWallClockLimit(ChronoUnit.FOREVER.getDuration());
    }

    /** When a run that starts at this moment, in nanoseconds, must stop. */
    public long deadlineFrom(long startedAtNanos) {
        try {
            return Math.addExact(startedAtNanos, wallClockLimit.toNanos());
        } catch (ArithmeticException beyondAnyClock) {
            return Long.MAX_VALUE;
        }
    }

    public Bounds withMaximumNesting(int nesting) {
        return new Bounds(wallClockLimit, nesting, checkEvery, hostAccess, grantedServices,
                lackingServices, hostName);
    }

    public Bounds withCheckEvery(int steps) {
        return new Bounds(wallClockLimit, maximumNesting, steps, hostAccess, grantedServices,
                lackingServices, hostName);
    }

    /** What the script may reach outside itself. Nothing, by default. */
    public Bounds withHostAccess(HostAccess access) {
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, access, grantedServices,
                lackingServices, hostName);
    }

    /**
     * The same bounds with one more kind of host service granted.
     *
     * <p>One at a time, because a host that can grant everything in one
     * call will, and then the grant says nothing about what the script
     * actually needs. A host cannot grant what it has no way to offer,
     * and saying so here is what keeps the two sets from contradicting
     * each other.
     */
    public Bounds granting(HostService service) {
        if (lackingServices.contains(service)) {
            throw new IllegalArgumentException(hostName + " cannot grant "
                    + service.name().toLowerCase(java.util.Locale.ROOT)
                    + ": it has no way to offer it");
        }
        java.util.Set<HostService> wider =
                java.util.EnumSet.noneOf(HostService.class);
        wider.addAll(grantedServices);
        wider.add(service);
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, hostAccess, wider,
                lackingServices, hostName);
    }

    /**
     * The same bounds with one more kind of host service this host has not
     * got.
     *
     * <p>Lacking a service is the host's own nature rather than a decision
     * about a script, so granting one it lacks is a mistake and refused
     * here. A host that could change its mind should have granted it.
     */
    public Bounds lacking(HostService service) {
        if (grantedServices.contains(service)) {
            throw new IllegalArgumentException(
                    hostName + " cannot lack " + service.name().toLowerCase(java.util.Locale.ROOT)
                            + ": it was granted it");
        }
        java.util.Set<HostService> fewer =
                java.util.EnumSet.noneOf(HostService.class);
        fewer.addAll(lackingServices);
        fewer.add(service);
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, hostAccess, grantedServices,
                fewer, hostName);
    }

    /** What this host calls itself when it refuses. */
    public Bounds called(String name) {
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, hostAccess, grantedServices,
                lackingServices, name);
    }

    /** Whether a script under these bounds may ask for this kind of service. */
    public boolean grants(HostService service) {
        return grantedServices.contains(service);
    }

    /** Whether this host has no way to offer this kind of service at all. */
    public boolean lacks(HostService service) {
        return lackingServices.contains(service);
    }
}
