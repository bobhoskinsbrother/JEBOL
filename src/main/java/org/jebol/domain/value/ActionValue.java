package org.jebol.domain.value;

public non-sealed interface ActionValue extends NativeValue {

    @Override
    default Datatype datatype() {
        return Datatype.ACTION;
    }
}
