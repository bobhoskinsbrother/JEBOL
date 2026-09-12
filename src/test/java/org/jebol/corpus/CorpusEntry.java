package org.jebol.corpus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    public boolean isRunnableNow() {
        return true;
    }

    public boolean needs(String capability) {
        return requires.contains(capability);
    }

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
