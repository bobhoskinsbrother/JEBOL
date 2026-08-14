package org.jebol.corpus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One entry from a {@code .corpus} file: a piece of real REBOL with what it is
 * expected to do.
 *
 * @param id unique, {@code area/name}
 * @param origin where the example came from, precisely enough to find again
 * @param requires capabilities needed beyond the milestone 1 core; empty means
 *     it should run today
 * @param notes free text from the entry
 * @param code the REBOL source, self-contained
 * @param expectedResult the molded result of the last expression, if asserted
 * @param expectedPrints expected standard output, if asserted
 * @param expectedError expected failure as {@code category id}, if asserted
 * @param expectedTypes datatype of each value the code loads to, if asserted
 */
public record CorpusEntry(
        String id,
        String origin,
        Set<String> requires,
        List<String> notes,
        String code,
        Optional<String> expectedResult,
        Optional<String> expectedPrints,
        Optional<String> expectedError,
        Optional<List<String>> expectedTypes) {

    public CorpusEntry {
        requires = Set.copyOf(requires);
        notes = List.copyOf(notes);
    }

    /**
     * Capabilities that genuinely do not exist yet. Everything else a
     * {@code requires} tag names has since been built, so the tag is now a
     * record of when the entry was written rather than a reason to skip it.
     */
    private static final Set<String> NOT_BUILT_YET =
            Set.of("clock", "random", "file", "network", "gui", "r2-only", "mutable-strings");

    /** Whether this entry can run against what exists today. */
    public boolean isRunnableNow() {
        return requires.stream().noneMatch(NOT_BUILT_YET::contains);
    }

    public boolean needs(String capability) {
        return requires.contains(capability);
    }

    /** An entry asserting nothing is a mistake, not a passing test. */
    public boolean assertsSomething() {
        return expectedResult.isPresent()
                || expectedPrints.isPresent()
                || expectedError.isPresent()
                || expectedTypes.isPresent();
    }

    @Override
    public String toString() {
        return id;
    }
}
