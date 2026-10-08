package org.jebol.domain.value;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract sealed class DefinedFunctionValue implements DeclaresParameters
        permits FunctionValue, ClosureValue {

    private final AnyBlockValue spec;
    private final AnyBlockValue body;
    private final List<Parameter> parameters;
    private final List<String> localNames;
    private final Context closedOver;
    private final Context declaredWords;

    DefinedFunctionValue(
            AnyBlockValue spec, AnyBlockValue body, List<Parameter> parameters,
            List<String> localNames, Context closedOver, Context declaredWords) {
        if (spec == null || body == null || closedOver == null) {
            throw new IllegalArgumentException("a function needs a spec, a body and a context");
        }
        this.spec = spec;
        this.body = body;
        this.parameters = List.copyOf(parameters);
        this.localNames = List.copyOf(localNames);
        this.closedOver = closedOver;
        this.declaredWords = declaredWords;
    }

    @Override
    public abstract Datatype datatype();

    public abstract DefinedFunctionValue sameKindRunning(AnyBlockValue anotherBody, Context closedOverInstead);

    public abstract Context aFreshCallFrame();

    public AnyBlockValue spec() {
        return spec;
    }

    public AnyBlockValue body() {
        return body;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    public List<String> localNames() {
        return localNames;
    }

    public Context closedOver() {
        return closedOver;
    }

    public Context declaredWords() {
        return declaredWords;
    }

    public int arity() {
        return (int) parameters.stream().filter(Parameter::consumesAnArgument).count();
    }

    @Override
    public Value reflected(AnyWordValue field) {
        return switch (field.canonical()) {
            case "spec" -> spec;
            case "body" -> body.copied(true);
            case "words" -> spec.declaredParameters();
            case "types" -> typesets();
            default -> NoneValue.none();
        };
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DefinedFunctionValue function
                && function.datatype() == datatype()
                && function.spec.equals(spec)
                && function.body.equals(body)
                && function.parameters.equals(parameters)
                && function.localNames.equals(localNames)
                && function.closedOver.equals(closedOver)
                && function.declaredWords.equals(declaredWords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datatype(), spec, body, parameters, localNames, closedOver, declaredWords);
    }

    @Override
    public String toString() {
        return "function/" + arity();
    }
}
