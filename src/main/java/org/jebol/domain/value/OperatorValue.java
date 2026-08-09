package org.jebol.domain.value;

/**
 * An infix operator: always two arguments, the first of which comes from the
 * value already produced to its left rather than from the position after it.
 *
 * <p>Every operator has a prefix twin doing the same work, so {@code 1 + 2}
 * and {@code add 1 2} are one behaviour reached two ways.
 */
public record OperatorValue(String operatorName, Value underlying) implements Value {

    public OperatorValue {
        if (operatorName == null || operatorName.isEmpty()) {
            throw new IllegalArgumentException("an operator needs a name");
        }
        if (underlying == null) {
            throw new IllegalArgumentException("an operator needs something to dispatch to");
        }
        if (!underlying.datatype().isAnyFunction()) {
            throw new IllegalArgumentException(
                    "an operator dispatches to a function, not "
                            + underlying.datatype().literalSpelling());
        }
    }

    /** Always two, by definition. */
    public int arity() {
        return 2;
    }

    @Override
    public Datatype datatype() {
        return Datatype.OP;
    }

    @Override
    public String toString() {
        return "op " + operatorName;
    }
}
