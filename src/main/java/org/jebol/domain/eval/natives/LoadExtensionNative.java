package org.jebol.domain.eval.natives;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.HandleValue;
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
                Parameter.required("name", Set.of(FileValue.TYPE, BinaryValue.TYPE)),
                Parameter.belongingTo("dispatch", "function", Set.of(HandleValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("dispatch");
    }
}
