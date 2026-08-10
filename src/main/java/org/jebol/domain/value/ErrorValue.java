package org.jebol.domain.value;

import java.util.List;
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
        Optional<Value> subject,
        Optional<Value> secondArgument,
        Optional<Value> thirdArgument,
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
        if (subject == null || secondArgument == null || thirdArgument == null
                || near == null || whereWord == null) {
            throw new IllegalArgumentException("optional fields are empty, never null");
        }
    }

    public static ErrorValue of(ErrorCategory category, String errorId, String message) {
        return new ErrorValue(category, errorId, message,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /** The same, naming what the failure was about. */
    public static ErrorValue about(
            ErrorCategory category, String errorId, String message, Value subject) {
        return new ErrorValue(category, errorId, message,
                Optional.of(subject), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /**
     * An error carrying all three of the arguments its catalogue entry names.
     *
     * <p>Rebol's catalogue words each failure with up to three of them, and a
     * script reads them by name: EXPECT-ARG is
     * {@code [:arg1 {does not allow} :arg3 {for its} :arg2 {argument}]}, so
     * arg1 is the function, arg2 the parameter and arg3 the datatype. Rebol's
     * own suite asserts on arg3 directly, which is why the three cannot all
     * live in the message.
     */
    public static ErrorValue about(
            ErrorCategory category, String errorId, String message,
            Value first, Value second, Value third) {

        return new ErrorValue(category, errorId, message,
                Optional.of(first), Optional.of(second), Optional.of(third),
                Optional.empty(), Optional.empty());
    }

    /**
     * The fields WORDS-OF reports, in the order it reports them.
     *
     * <p>Fixed and ordered because code walks the result. TYPE and ID are
     * words rather than strings, so they compare with a lit-word --
     * {@code e/id = 'expect-arg} is the idiom Rebol's own suite is
     * written in, and it fails silently against strings.
     */
    public static final List<String> FIELDS = List.of(
            "code", "type", "id", "arg1", "arg2", "arg3", "near", "where");

    /**
     * What one field holds, or empty when the error has no such field.
     *
     * <p>ARG1 carries whatever the failure was about -- the word that had
     * no value, the function that refused an argument -- and is none when
     * the failure had nothing to name. ARG2 and ARG3 carry the rest of what
     * the catalogue entry words, and are none for the failures that name only
     * one thing.
     *
     * <p>All three used to be read out of the message, with ARG2 and ARG3
     * always none. That made `e/arg3 = integer!` false for every expect-arg,
     * which is an assertion Rebol's own suite makes.
     */
    public Optional<Value> field(String name) {
        return switch (name) {
            case "code" -> Optional.of(IntegerValue.of(codeNumber()));
            case "type" -> Optional.of(WordValue.of(categoryWord()));
            case "id" -> Optional.of(WordValue.of(errorId));
            case "arg1" -> Optional.of(subject.orElseGet(NoneValue::none));
            case "arg2" -> Optional.of(secondArgument.orElseGet(NoneValue::none));
            case "arg3" -> Optional.of(thirdArgument.orElseGet(NoneValue::none));
            case "near" -> Optional.of(near.orElseGet(NoneValue::none));
            case "where" -> Optional.of(whereWord
                    .<Value>map(WordValue::of).orElseGet(NoneValue::none));
            default -> Optional.empty();
        };
    }

    /**
     * R3 numbers its failures in hundreds by category, and code 400 for
     * a maths error is what a script compares against.
     */
    private long codeNumber() {
        // R3 numbers each id within its category, so the number depends on
        // the id and not only on the family it belongs to. The catalogue
        // that says which is R3's own, held in the eval layer.
        int fromCatalogue = ErrorCatalogue.codeFor(
                category.name().charAt(0) + category.name().substring(1).toLowerCase(
                        java.util.Locale.ROOT),
                errorId);
        if (fromCatalogue > 0) {
            return fromCatalogue;
        }
        return switch (category) {
            case SYNTAX -> 200;
            case SCRIPT -> 300;
            case MATH -> 400;
            case ACCESS -> 500;
            case USER -> 800;
            case INTERNAL -> 900;
            // The one category not numbered in hundreds. A real R3 gives
            // `try/all [throw 5]` the code 2, so this family counts from
            // zero. Which id sits at which number is part of the wider
            // error-catalogue reconciliation, not settled here.
            case THROW -> 0;
        };
    }

    /** Capitalised, as R3 writes it: Math, Script, Access. */
    private String categoryWord() {
        String spelling = category.spelling();
        return Character.toUpperCase(spelling.charAt(0)) + spelling.substring(1);
    }

    /**
     * What the failure was about, when the message names it.
     *
     * <p>The raiser writes the offending word or value at the front of
     * the message, so this reads it back. A structured field carrying the
     * value itself would be better; this is what can be had without
     * changing every raise site.
     */

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
        return new ErrorValue(category, errorId, message, subject,
                secondArgument, thirdArgument, Optional.of(fragment), whereWord);
    }

    /** The same error, naming the function it was raised in. */
    public ErrorValue raisedIn(String functionName) {
        return new ErrorValue(category, errorId, message, subject,
                secondArgument, thirdArgument, near, Optional.of(functionName));
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
