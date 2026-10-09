package org.jebol.domain.value;

import java.util.List;

/**
 * An infix operator: always two arguments, the first of which comes from the
 * value already produced to its left rather than from the position after it.
 *
 * <p>Every operator has a prefix twin doing the same work, so {@code 1 + 2}
 * and {@code add 1 2} are one behaviour reached two ways.
 */
public record OperatorValue(String operatorName, AnyFunctionValue underlying) implements AnyFunctionValue {

    public OperatorValue {
        if (operatorName == null || operatorName.isEmpty()) {
            throw new IllegalArgumentException("an operator needs a name");
        }
        if (underlying == null) {
            throw new IllegalArgumentException("an operator needs something to dispatch to");
        }
    }

    /** Always two, by definition. */
    public int arity() {
        return 2;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new OperatorDatatype();

    private static final class OperatorDatatype extends AnyFunctionDatatype {

        private static final String AN_OPERATOR_NOBODY_HAS_NAMED = "?";

        private static final int THE_ARGUMENTS_AN_OPERATOR_TAKES = 2;

        OperatorDatatype() {
            super("op");
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            throw refusingConstruction(contents);
        }

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            Value offered = spec instanceof AnyBlockValue parts
                    ? aFunctionMadeFrom(parts, maker)
                    : spec;
            if (!(offered instanceof AnyFunctionValue dispatching)
                    || howManyArgumentsBeforeAnyRefinement(dispatching)
                            != THE_ARGUMENTS_AN_OPERATOR_TAKES) {
                throw refusing(spec);
            }
            return new OperatorValue(AN_OPERATOR_NOBODY_HAS_NAMED, dispatching);
        }

        private Value aFunctionMadeFrom(AnyBlockValue parts, Maker maker) {
            List<Value> items = parts.remaining();
            if (items.size() < 2
                    || !(items.get(0) instanceof AnyBlockValue functionSpec)
                    || !(items.get(1) instanceof AnyBlockValue body)) {
                throw refusing(parts);
            }
            return maker.functionBoundFrom(functionSpec, body);
        }

        private int howManyArgumentsBeforeAnyRefinement(AnyFunctionValue dispatching) {
            List<Parameter> declared = switch (dispatching) {
                case DeclaresParameters function -> function.parameters();
                case OperatorValue operator ->
                        List.of(Parameter.required("a"), Parameter.required("b"));
            };
            int counted = 0;
            for (Parameter parameter : declared) {
                if (parameter.kind() == ParameterKind.REFINEMENT) {
                    return counted;
                }
                if (parameter.kind() != ParameterKind.RETURN_TYPE) {
                    counted++;
                }
            }
            return counted;
        }
    }

    @Override
    public String toString() {
        return "op " + operatorName;
    }
}
