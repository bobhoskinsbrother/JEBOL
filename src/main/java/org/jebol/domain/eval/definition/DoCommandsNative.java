package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DoCommandsNative extends ExtensionPointNative {

    @Override
    public String name() {
        return "do-commands";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("commands", Set.of(Datatype.BLOCK)));
    }
}
