package org.jebol.testing;

import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public final class RunsOnFilter implements PostDiscoveryFilter {

    private final TestTarget fixedTarget;
    private final List<RequirementCheck> fixedChecks;

    public RunsOnFilter() {
        this(null, null);
    }

    RunsOnFilter(TestTarget target, List<RequirementCheck> checks) {
        this.fixedTarget = target;
        this.fixedChecks = checks;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        Optional<MethodSource> method = descriptor.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast);
        if (method.isEmpty()) {
            return FilterResult.included("judged by its tests");
        }
        TestTarget here = fixedTarget == null ? TestTarget.current() : fixedTarget;
        Optional<RunsOn> runsOn = innermostRunsOn(method.get());
        if (runsOn.isEmpty()) {
            return FilterResult.included("runs wherever its source set runs");
        }
        if (Arrays.stream(runsOn.get().value()).noneMatch(here::equals)) {
            return FilterResult.excluded("runs on " + Arrays.toString(runsOn.get().value()) + ", not " + here
                    + ", because " + runsOn.get().because());
        }
        List<String> shortfalls = findShortfalls(runsOn.get().requires());
        if (shortfalls.isEmpty()) {
            return FilterResult.included("runs on " + here + ", and this device meets what it requires");
        }
        String said = method.get().getClassName() + "#" + method.get().getMethodName() + " requires what this device "
                + "lacks: " + String.join("; ", shortfalls);
        if (checks().stream().anyMatch(RequirementCheck::requirementsMustBeMet)) {
            throw new IllegalStateException(said + ". Requirements must be met in this run.");
        }
        return FilterResult.excluded(said);
    }

    private List<String> findShortfalls(Needs[] required) {
        List<String> shortfalls = new ArrayList<>();
        for (Needs need : required) {
            RequirementCheck answering = checks().stream()
                    .filter(check -> check.answers().contains(need))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("nothing on this platform can say whether " + need
                            + " is met: a RequirementCheck in META-INF/services answers it"));
            answering.shortfall(need).ifPresent(has -> shortfalls.add(need.reason() + ", and " + has));
        }
        return shortfalls;
    }

    private List<RequirementCheck> checks() {
        return fixedChecks != null ? fixedChecks : TheChecksFound.CHECKS;
    }

    private static Optional<RunsOn> innermostRunsOn(MethodSource source) {
        Optional<RunsOn> onTheMethod = AnnotationSupport.findAnnotation(source.getJavaMethod(), RunsOn.class);
        if (onTheMethod.isPresent()) {
            return onTheMethod;
        }
        for (Class<?> each = source.getJavaClass(); each != null; each = each.getEnclosingClass()) {
            Optional<RunsOn> onTheClass = AnnotationSupport.findAnnotation(each, RunsOn.class);
            if (onTheClass.isPresent()) {
                return onTheClass;
            }
        }
        return Optional.empty();
    }

    private static final class TheChecksFound {

        private static final List<RequirementCheck> CHECKS = ServiceLoader.load(RequirementCheck.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();

        private TheChecksFound() {
        }
    }
}
