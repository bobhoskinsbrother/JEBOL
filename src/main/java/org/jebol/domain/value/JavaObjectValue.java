package org.jebol.domain.value;

import java.util.Optional;

/**
 * A value belonging to the host rather than to REBOL.
 *
 * <p>A held Java null is not {@code none}. {@code none} is a REBOL value
 * meaning nothing; a Java null is the host's absence, and conflating them
 * would make a round trip through the host lossy.
 *
 * <p>Has no REBOL spelling, so nothing the reader produces is ever one of
 * these and MOLD cannot print one that reads back.
 */
public record JavaObjectValue(String className, Optional<Object> held) implements Value {

    public JavaObjectValue {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("a host value needs a class name");
        }
        if (held == null) {
            throw new IllegalArgumentException("held is empty for a host null, never null");
        }
    }

    public static JavaObjectValue of(Object held) {
        if (held == null) {
            throw new IllegalArgumentException("use hostNull() for a held null");
        }
        return new JavaObjectValue(held.getClass().getName(), Optional.of(held));
    }

    public static JavaObjectValue hostNull(String className) {
        return new JavaObjectValue(className, Optional.empty());
    }

    public boolean isHostNull() {
        return held.isEmpty();
    }

    @Override
    public Datatype datatype() {
        return Datatype.JAVA_OBJECT;
    }

    @Override
    public String toString() {
        return "java-object " + className + (isHostNull() ? " (null)" : "");
    }
}
