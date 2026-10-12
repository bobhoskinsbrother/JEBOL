package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ServesPortActions {

    Optional<Value> answers(String action, List<Value> arguments,
            Set<String> refinements, Evaluator evaluator);
}
