package org.jebol.domain.value;

public non-sealed interface ActionValue extends NativeValue {

    Datatype TYPE = new AnyFunctionDatatype("action") {
    };

    @Override
    default Datatype datatype() {
        return TYPE;
    }

    /**
     * Every action reaches the actor, except the three that work on the port
     * object itself rather than on the thing it stands for -- MAKE, TO and
     * REFLECT -- and the datatype tests, which look at a value instead of
     * sending it anything.
     */
    @Override
    default boolean reachesPortActor() {
        return true;
    }
}
