package org.jebol.domain.eval.natives;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class LoadExtensionNative extends ExtensionPointNative {

    @Override
    public String nativeName() {
        return "load-extension";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("name", Set.of(Datatype.FILE, Datatype.BINARY)),
                Parameter.belongingTo("dispatch", "function", Set.of(Datatype.HANDLE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("dispatch");
    }
}
