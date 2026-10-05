package org.jebol.domain.value;

import java.util.Set;

public record Trimming(Set<String> asked, Set<Integer> unwantedCodePoints) {

    public boolean fromTheHead() {
        return asked.contains("head") || !asked.contains("tail");
    }

    public boolean fromTheTail() {
        return asked.contains("tail") || !asked.contains("head");
    }

    public boolean atOneEndOnly() {
        return asked.contains("head") != asked.contains("tail");
    }

    public boolean everywhere() {
        return asked.contains("all");
    }

    public boolean ofTheGivenCharacters() {
        return asked.contains("with");
    }

    public boolean intoOneLine() {
        return asked.contains("lines");
    }

    public boolean ofTheCommonIndent() {
        return asked.contains("auto");
    }

    public boolean atBothNamedEnds() {
        return asked.contains("head") && asked.contains("tail");
    }

    public void refuseContradictions() {
        boolean atAnEnd = asked.contains("head") || asked.contains("tail");
        if (atAnEnd && (everywhere() || ofTheGivenCharacters())) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
    }

    public void refuseWhatOnlyTextServes() {
        refuseContradictions();
        if (ofTheGivenCharacters() || ofTheCommonIndent() || intoOneLine()) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
    }

    public void refuseEveryRefinementOnFields() {
        if (!asked.isEmpty()) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
    }
}
