package org.jebol.suite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AssertionsRebolFailsTooTest {

    static List<String> everyEntryOnTheList() {
        return RebolSuiteTest.failingOnRebolToo();
    }

    private static Map<String, SuiteFile.Assertion> theSuiteByName() {
        return RebolSuiteTest.everyAssertion().collect(Collectors.toMap(
                SuiteFile.Assertion::toString, Function.identity(), (first, next) -> first));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEntryOnTheList")
    @DisplayName("a real 3.22.5 does not pass this either")
    void itDoesNotHoldHereEither(String entry) {
        SuiteFile.Assertion assertion = theSuiteByName().get(entry);
        assertThat(assertion)
                .as("%s names no assertion in the suite, so nothing can ever take "
                        + "it off the list; delete the entry", entry)
                .isNotNull();
        assertThat(RebolSuiteTest.holds(assertion))
                .as("%s%n  passes here now, and a real 3.22.5 does not pass it. "
                        + "While there are goals left that is a divergence and so "
                        + "a defect: find out why JEBOL answers differently. Only "
                        + "once the goals are done does this become the target, "
                        + "and only then does the entry come off the list.%n  %s",
                        entry, assertion.source())
                .isFalse();
    }

    @Test
    @DisplayName("the list is not empty, so this test is doing something")
    void theListIsNotEmpty() {
        assertThat(everyEntryOnTheList()).isNotEmpty();
    }
}
