package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public interface FunctionDefinition {

    String name();

    List<Parameter> parameters();

    RefinedCallable behaviour();

    Set<String> refinements();

}
