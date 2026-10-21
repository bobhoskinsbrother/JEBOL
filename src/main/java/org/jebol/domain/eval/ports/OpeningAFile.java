package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.FileValue;

import java.util.EnumSet;
import java.util.Set;

record OpeningAFile(Set<String> refinements) {

    void refuseNewFileNobodyMayWriteTo(String path) {
        if (refinements.contains("new") && !canWrite()) {
            throw Raised.of(EvaluationFailure.BAD_FILE_MODE, FileValue.of(path));
        }
    }

    boolean canWrite() {
        return refinements.contains("write") || namesNeitherWay();
    }

    private boolean canRead() {
        return refinements.contains("read") || namesNeitherWay();
    }

    private boolean namesNeitherWay() {
        return !refinements.contains("read") && !refinements.contains("write");
    }

    Set<PolicyAccess> listTheAccessesItAsksFor() {
        Set<PolicyAccess> asked = EnumSet.noneOf(PolicyAccess.class);
        if (canRead()) {
            asked.add(PolicyAccess.READ);
        }
        if (canWrite()) {
            asked.add(PolicyAccess.WRITE);
        }
        return asked;
    }

    boolean emptiesWhatIsThere() {
        return refinements.contains("new") || !(canRead() || refinements.contains("seek"));
    }
}
