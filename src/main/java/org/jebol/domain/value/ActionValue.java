package org.jebol.domain.value;

public non-sealed interface ActionValue extends NativeValue {

    Datatype TYPE = new Datatype("action", Typeset.ANY_FUNCTION) {
    };

    @Override
    default Datatype datatype() {
        return TYPE;
    }
}
