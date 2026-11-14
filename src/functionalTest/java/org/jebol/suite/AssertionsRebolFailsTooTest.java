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

    static List<String> listEveryEntry() {
        return SuiteGrading.failingOnRebolToo();
    }

    private static Map<String, SuiteFile.Assertion> indexTheSuiteByName() {
        return SuiteGrading.listEveryAssertion().collect(Collectors.toMap(
                SuiteFile.Assertion::toString, Function.identity(), (first, next) -> first));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listEveryEntry")
    @DisplayName("JEBOL does not pass this, as a real 3.22.5 does not, or on purpose")
    void failsHereToo(String entry) {
        SuiteFile.Assertion assertion = indexTheSuiteByName().get(entry);
        assertThat(assertion)
                .as("%s names no assertion in the suite, so nothing can ever take "
                        + "it off the list; delete the entry", entry)
                .isNotNull();
        assertThat(SuiteGrading.holds(assertion))
                .as("%s%n  passes here now. Above the file's last heading a real "
                        + "3.22.5 does not pass it: while there are goals left that "
                        + "is a divergence and so a defect, so find out why JEBOL "
                        + "answers differently. Under the last heading JEBOL fails "
                        + "it on purpose: passing means a deliberate fix in "
                        + "docs/standing.md has been undone.%n  %s",
                        entry, assertion.source())
                .isFalse();
    }

    @Test
    @DisplayName("the list is not empty, so this test is doing something")
    void holdsEntries() {
        assertThat(listEveryEntry()).isNotEmpty();
    }
}
