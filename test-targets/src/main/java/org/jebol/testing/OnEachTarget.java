package org.jebol.testing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class OnEachTarget {

    public record Answer<T>(Set<TestTarget> targets, T value) {

        boolean isElsewhere() {
            return targets.isEmpty();
        }
    }

    private OnEachTarget() {
    }

    public static <T> Answer<T> on(TestTarget target, T value) {
        return new Answer<>(EnumSet.of(target), value);
    }

    public static <T> Answer<T> on(TestTarget first, TestTarget second, T value) {
        return new Answer<>(EnumSet.of(first, second), value);
    }

    public static <T> Answer<T> on(TestTarget first, TestTarget second, TestTarget third, T value) {
        return new Answer<>(EnumSet.of(first, second, third), value);
    }

    public static <T> Answer<T> elsewhere(T value) {
        return new Answer<>(EnumSet.noneOf(TestTarget.class), value);
    }

    @SafeVarargs
    public static <T> T expected(Answer<T>... answers) {
        List<Answer<T>> given = new ArrayList<>();
        for (Answer<T> answer : answers) {
            given.add(answer);
        }
        return pickFor(TestTarget.current(), given);
    }

    @SafeVarargs
    static <T> T expectedOn(TestTarget here, Answer<T>... answers) {
        List<Answer<T>> given = new ArrayList<>();
        for (Answer<T> answer : answers) {
            given.add(answer);
        }
        return pickFor(here, given);
    }

    private static <T> T pickFor(TestTarget here, List<Answer<T>> given) {
        checkEachTargetNamedOnce(given);
        return given.stream()
                .filter(answer -> answer.targets().contains(here))
                .findFirst()
                .or(() -> given.stream().filter(Answer::isElsewhere).findFirst())
                .map(Answer::value)
                .orElseThrow(() -> new AssertionError("no answer is expected on " + here + ": name it, or give one for "
                        + "elsewhere"));
    }

    private static <T> void checkEachTargetNamedOnce(List<Answer<T>> given) {
        if (given.stream().filter(Answer::isElsewhere).count() > 1) {
            throw new AssertionError("elsewhere is given more than once");
        }
        Set<TestTarget> named = EnumSet.noneOf(TestTarget.class);
        for (Answer<T> answer : given) {
            for (TestTarget target : answer.targets()) {
                if (!named.add(target)) {
                    throw new AssertionError(target + " is given two answers");
                }
            }
        }
    }
}
