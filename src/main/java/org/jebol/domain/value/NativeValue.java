package org.jebol.domain.value;

import java.util.List;

/**
 * A built-in function.
 *
 * <p>Carries its interface but not its behaviour: the behaviour is looked up
 * by name where natives are implemented, so this package stays data and the
 * value model does not need to know how anything runs.
 */
public record NativeValue(String nativeName, List<Parameter> parameters) implements Value {

    public NativeValue {
        if (nativeName == null || nativeName.isEmpty()) {
            throw new IllegalArgumentException("a native needs a name");
        }
        parameters = List.copyOf(parameters);
    }

    /** How many values this native takes from the block. */
    public int arity() {
        return (int) parameters.stream().filter(Parameter::consumesAnArgument).count();
    }

    @Override
    public Datatype datatype() {
        return Datatype.NATIVE;
    }

    @Override
    public String toString() {
        return "native " + nativeName + "/" + arity();
    }
}
