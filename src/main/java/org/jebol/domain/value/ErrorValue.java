package org.jebol.domain.value;

import java.util.Optional;

/**
 * An error, which in REBOL is a value like any other.
 *
 * <p>Being a value is what lets {@code try} hand one back instead of raising
 * it, and it is what makes the guarantee that no failure escapes as a host
 * exception keepable.
 *
 * <p>Identified by category and id rather than by message. R3-Alpha and
 * REBOL 2 word the same failure differently, and the wording is not the
 * behaviour, so nothing should ever match on {@link #message()}.
 */
public record ErrorValue(
        ErrorCategory category,
        String errorId,
        String message,
        Optional<Value> near,
        Optional<String> whereWord) implements Value {

    public ErrorValue {
        if (category == null) {
            throw new IllegalArgumentException("an error needs a category");
        }
        if (errorId == null || errorId.isEmpty()) {
            throw new IllegalArgumentException("an error needs an id");
        }
        if (message == null) {
            throw new IllegalArgumentException("an error needs a message, even an empty one");
        }
        if (near == null || whereWord == null) {
            throw new IllegalArgumentException("optional fields are empty, never null");
        }
    }

    public static ErrorValue of(ErrorCategory category, String errorId, String message) {
        return new ErrorValue(category, errorId, message, Optional.empty(), Optional.empty());
    }

    public static ErrorValue script(String errorId, String message) {
        return of(ErrorCategory.SCRIPT, errorId, message);
    }

    public static ErrorValue math(String errorId, String message) {
        return of(ErrorCategory.MATH, errorId, message);
    }

    public static ErrorValue syntax(String errorId, String message) {
        return of(ErrorCategory.SYNTAX, errorId, message);
    }

    /** The same error, carrying the block fragment it came from. */
    public ErrorValue near(Value fragment) {
        return new ErrorValue(category, errorId, message, Optional.of(fragment), whereWord);
    }

    /** The same error, naming the function it was raised in. */
    public ErrorValue raisedIn(String functionName) {
        return new ErrorValue(category, errorId, message, near, Optional.of(functionName));
    }

    @Override
    public Datatype datatype() {
        return Datatype.ERROR;
    }

    @Override
    public String toString() {
        return "** " + category.spelling() + " error: " + message;
    }
}
