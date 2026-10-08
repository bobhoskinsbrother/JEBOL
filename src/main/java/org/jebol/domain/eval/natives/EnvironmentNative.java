package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LitWordValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.Set;

public abstract class EnvironmentNative extends HostNative {

    protected EnvironmentNative(GrantedServices granted) {
        super(granted);
    }

    protected Set<Datatype> aVariablesName() {
        return Set.of(StringValue.TYPE, WordValue.TYPE, LitWordValue.TYPE);
    }

    protected String theVariableNamedBy(Value asked) {
        return asked instanceof AnyWordValue word ? word.spelling() : ((AnyStringValue) asked).text();
    }
}
