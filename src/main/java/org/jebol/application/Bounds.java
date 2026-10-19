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
        Duration timeLimit,
        int maximumNesting,
        int checkEvery,
        Set<HostService> grantedServices,
        Set<HostService> unavailableServices,
        String hostName,
        List<Extension> extensions) {

    private static final Duration DEFAULT_TIME_LIMIT = Duration.ofSeconds(5);
    private static final int DEFAULT_MAXIMUM_NESTING = 10_000;
    private static final int DEFAULT_CHECK_EVERY = 1_000;
    private static final String DEFAULT_HOST_NAME = "this host";

    public Bounds {
        if (timeLimit == null || timeLimit.isNegative() || timeLimit.isZero()) {
            throw new IllegalArgumentException("a time limit must be a positive duration");
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
        if (unavailableServices == null) {
            throw new IllegalArgumentException(
                    "unavailable services is a decision; use an empty set to mark nothing unavailable");
        }
        grantedServices = Set.copyOf(grantedServices);
        unavailableServices = Set.copyOf(unavailableServices);
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
                DEFAULT_TIME_LIMIT, DEFAULT_MAXIMUM_NESTING, DEFAULT_CHECK_EVERY,
                Set.of(), Set.of(), DEFAULT_HOST_NAME, List.of());
    }

    public Bounds withTimeLimit(Duration limit) {
        return new Bounds(limit, maximumNesting, checkEvery, grantedServices,
                unavailableServices, hostName, extensions);
    }

    public Bounds withoutATimeLimit() {
        return withTimeLimit(ChronoUnit.FOREVER.getDuration());
    }

    public long computeDeadline(long startedAtNanos) {
        try {
            return Math.addExact(startedAtNanos, timeLimit.toNanos());
        } catch (ArithmeticException beyondAnyClock) {
            return Long.MAX_VALUE;
        }
    }

    public Bounds withMaximumNesting(int nesting) {
        return new Bounds(timeLimit, nesting, checkEvery, grantedServices,
                unavailableServices, hostName, extensions);
    }

    public Bounds withCheckEvery(int steps) {
        return new Bounds(timeLimit, maximumNesting, steps, grantedServices,
                unavailableServices, hostName, extensions);
    }

    public Bounds grant(HostService service) {
        if (unavailableServices.contains(service)) {
            throw new IllegalArgumentException(hostName + " cannot grant "
                    + service.name().toLowerCase(Locale.ROOT)
                    + ": it has no way to offer it");
        }
        Set<HostService> wider =
                EnumSet.noneOf(HostService.class);
        wider.addAll(grantedServices);
        wider.add(service);
        return new Bounds(timeLimit, maximumNesting, checkEvery, wider,
                unavailableServices, hostName, extensions);
    }

    public Bounds markUnavailable(HostService service) {
        if (grantedServices.contains(service)) {
            throw new IllegalArgumentException(
                    hostName + " cannot mark " + service.name().toLowerCase(Locale.ROOT)
                            + " unavailable"
                            + ": it was granted it");
        }
        Set<HostService> fewer =
                EnumSet.noneOf(HostService.class);
        fewer.addAll(unavailableServices);
        fewer.add(service);
        return new Bounds(timeLimit, maximumNesting, checkEvery, grantedServices,
                fewer, hostName, extensions);
    }

    public Bounds calls(String name) {
        return new Bounds(timeLimit, maximumNesting, checkEvery, grantedServices,
                unavailableServices, name, extensions);
    }

    public Bounds add(Extension extension) {
        List<Extension> more = new ArrayList<>(extensions);
        more.add(extension);
        return new Bounds(timeLimit, maximumNesting, checkEvery, grantedServices,
                unavailableServices, hostName, more);
    }

    public boolean grants(HostService service) {
        return grantedServices.contains(service);
    }

    public boolean isUnavailable(HostService service) {
        return unavailableServices.contains(service);
    }

}
