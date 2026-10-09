package org.jebol.domain.value;

public non-sealed interface ActionValue extends NativeValue {

    Datatype TYPE = new AnyFunctionDatatype("action") {
    };

    @Override
    default Datatype datatype() {
        return TYPE;
    }
}
