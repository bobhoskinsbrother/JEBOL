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

    abstract static class DefinedFunctionDatatype extends AnyFunctionDatatype {

        DefinedFunctionDatatype(String spelling) {
            super(spelling);
        }

        abstract Value fromTheFunction(FunctionValue made);

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            if (!(spec instanceof AnyBlockValue parts)) {
                throw refuse(spec);
            }
            List<Value> items = parts.remaining();
            if (items.size() < 2
                    || !(items.get(0) instanceof AnyBlockValue functionSpec)
                    || !(items.get(1) instanceof AnyBlockValue body)) {
                throw refuse(spec);
            }
            return fromTheFunction(maker.bindAFunction(functionSpec, body));
        }

        @Override
        public Value convert(Value value, Maker maker) {
            return makeFrom(value, maker);
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            if (contents.size() != 1
                    || !(contents.getFirst() instanceof AnyBlockValue definition)
                    || definition.remaining().size() != 2
                    || !(definition.remaining().get(0) instanceof BlockValue functionSpec)
                    || !(definition.remaining().get(1) instanceof BlockValue body)) {
                throw refuseConstruction(contents);
            }
            return construction.makeFunction(functionSpec, body);
        }
    }

    public abstract DefinedFunctionValue makeSameKindRunning(AnyBlockValue anotherBody, Context closedOverInstead);

    public abstract Context makeFreshCallFrame();

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
    public Value reflect(AnyWordValue field) {
        return switch (field.canonical()) {
            case "spec" -> spec;
            case "body" -> body.copy(true);
            case "words" -> spec.declaredParameters();
            case "types" -> listTheTypesets();
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
