package org.jebol.domain.value;

import java.util.Set;

public record Trimming(Set<String> asked, Set<Integer> unwantedCodePoints) {

    public boolean fromHead() {
        return asked.contains("head") || !asked.contains("tail");
    }

    public boolean fromTail() {
        return asked.contains("tail") || !asked.contains("head");
    }

    public boolean atOneEndOnly() {
        return asked.contains("head") != asked.contains("tail");
    }

    public boolean everywhere() {
        return asked.contains("all");
    }

    public boolean ofGivenCharacters() {
        return asked.contains("with");
    }

    public boolean intoOneLine() {
        return asked.contains("lines");
    }

    public boolean ofCommonIndent() {
        return asked.contains("auto");
    }

    public boolean atBothNamedEnds() {
        return asked.contains("head") && asked.contains("tail");
    }

    public void refuseContradictions() {
        boolean atEnd = asked.contains("head") || asked.contains("tail");
        if (atEnd && (everywhere() || ofGivenCharacters())) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
    }

    public void refuseWhatOnlyTextServes() {
        refuseContradictions();
        if (ofGivenCharacters() || ofCommonIndent() || intoOneLine()) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
    }

    public void refuseEveryRefinementOnFields() {
        if (!asked.isEmpty()) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
    }
}
