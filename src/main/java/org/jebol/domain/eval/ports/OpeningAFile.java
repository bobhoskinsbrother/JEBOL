package org.jebol.domain.eval.ports;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;

import java.util.Set;

record OpeningAFile(Set<String> refinements) {

    void refuseANewFileNobodyMayWriteTo(String path) {
        if (refinements.contains("new") && !mayWrite()) {
            throw Raised.of(EvaluationFailure.BAD_FILE_MODE, StringValue.of(path, Datatype.FILE));
        }
    }

    boolean mayWrite() {
        return refinements.contains("write") || namesNeitherWay();
    }

    private boolean mayRead() {
        return refinements.contains("read") || namesNeitherWay();
    }

    private boolean namesNeitherWay() {
        return !refinements.contains("read") && !refinements.contains("write");
    }

    boolean emptiesWhatIsThere() {
        return refinements.contains("new") || !(mayRead() || refinements.contains("seek"));
    }
}
