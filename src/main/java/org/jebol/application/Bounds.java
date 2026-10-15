package org.jebol.application;

import org.jebol.domain.host.HostService;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


public record Bounds(
        Duration wallClockLimit,
        int maximumNesting,
        int checkEvery,
        Set<HostService> grantedServices,
        Set<HostService> lackingServices,
        String hostName,
        List<Extension> extensions) {

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
        grantedServices = Set.copyOf(grantedServices);
        lackingServices = Set.copyOf(lackingServices);
        if (hostName == null || hostName.isBlank()) {
            throw new IllegalArgumentException(
                    "a host names itself in a refusal; it may not be nameless");
        }
        if (extensions == null) {
            throw new IllegalArgumentException(
                    "extensions is a decision; use an empty list to add none");
        }
        extensions = List.copyOf(extensions);
    }

    /** Long enough for ordinary work, short enough not to hold a request open. */
    public static Bounds standard() {
        return new Bounds(
                DEFAULT_WALL_CLOCK_LIMIT, DEFAULT_MAXIMUM_NESTING, DEFAULT_CHECK_EVERY,
                Set.of(), Set.of(), DEFAULT_HOST_NAME, List.of());
    }

    public Bounds withWallClockLimit(Duration limit) {
        return new Bounds(limit, maximumNesting, checkEvery, grantedServices,
                lackingServices, hostName, extensions);
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
        return new Bounds(wallClockLimit, nesting, checkEvery, grantedServices,
                lackingServices, hostName, extensions);
    }

    public Bounds withCheckEvery(int steps) {
        return new Bounds(wallClockLimit, maximumNesting, steps, grantedServices,
                lackingServices, hostName, extensions);
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
                    + service.name().toLowerCase(Locale.ROOT)
                    + ": it has no way to offer it");
        }
        Set<HostService> wider =
                EnumSet.noneOf(HostService.class);
        wider.addAll(grantedServices);
        wider.add(service);
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, wider,
                lackingServices, hostName, extensions);
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
                    hostName + " cannot lack " + service.name().toLowerCase(Locale.ROOT)
                            + ": it was granted it");
        }
        Set<HostService> fewer =
                EnumSet.noneOf(HostService.class);
        fewer.addAll(lackingServices);
        fewer.add(service);
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, grantedServices,
                fewer, hostName, extensions);
    }

    /** What this host calls itself when it refuses. */
    public Bounds called(String name) {
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, grantedServices,
                lackingServices, name, extensions);
    }

    public Bounds adding(Extension extension) {
        List<Extension> more = new ArrayList<>(extensions);
        more.add(extension);
        return new Bounds(wallClockLimit, maximumNesting, checkEvery, grantedServices,
                lackingServices, hostName, more);
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
