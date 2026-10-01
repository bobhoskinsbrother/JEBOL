package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class DefaultFunction implements FunctionDefinition {

    protected List<Parameter> acceptsAllNumbers(String... names) {
        Set<Datatype> numbers = Typeset.NUMBER.membersAnd(
                Datatype.MONEY,
                Datatype.PAIR,
                Datatype.TUPLE,
                Datatype.TIME,
                Datatype.DATE,
                Datatype.CHAR,
                Datatype.VECTOR
        );
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, numbers));
        }
        return parameters;
    }


}
