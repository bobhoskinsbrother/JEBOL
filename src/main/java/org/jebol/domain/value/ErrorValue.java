package org.jebol.domain.value;

import java.util.*;

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
        Optional<Value> whereChain,
        Map<String, Value> writtenFields) implements Value {

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
                || near == null || whereChain == null) {
            throw new IllegalArgumentException("optional fields are empty, never null");
        }
    }

    public static ErrorValue of(ErrorCategory category, String errorId, String message) {
        return new ErrorValue(category, errorId, message,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), new LinkedHashMap<>());
    }

    /** The same, naming what the failure was about. */
    public static ErrorValue about(
            ErrorCategory category, String errorId, String message, Value subject) {
        return new ErrorValue(category, errorId, message,
                Optional.of(subject), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), new LinkedHashMap<>());
    }

    /**
     * The same, naming two things rather than one.
     *
     * <p>Several of Rebol's catalogue entries word a failure with exactly two:
     * {@code expect-val: [{expected} :arg1 {not} :arg2]} and
     * {@code bad-field-set: [{cannot set} :arg1 {field to} :arg2 {datatype}]}.
     * Building those through the three-argument form would put a none in ARG3
     * where the entry has nothing at all.
     */
    public static ErrorValue about(
            ErrorCategory category, String errorId, String message,
            Value first, Value second) {

        return new ErrorValue(category, errorId, message,
                Optional.of(first), Optional.of(second), Optional.empty(),
                Optional.empty(), Optional.empty(), new LinkedHashMap<>());
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
                Optional.empty(), Optional.empty(), new LinkedHashMap<>());
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
     * A field written over the top of the derived one.
     *
     * <p>An error is an object and an object is shared, so this writes through
     * every name holding it. The map is the shared part, held the way a block
     * holds its storage: the record is a value and what it points at is not.
     *
     * <p>Refuses a field the frame has not got, which is {@code PE_BAD_SELECT}
     * in {@code PD_Object} and reads as {@code invalid-path} to a script.
     */
    public void write(String name, Value value) {
        if (!FIELDS.contains(name)) {
            throw new IllegalArgumentException(
                    "an error has no field called " + name);
        }
        writtenFields.put(name, value);
    }

    /**
     * What one field holds, or empty when the error has no such field.
     *
     * <p>What was written over the top comes first, because an error is an
     * object and a field somebody set is the field.
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
        if (writtenFields.containsKey(name)) {
            return Optional.of(writtenFields.get(name));
        }
        return switch (name) {
            case "code" ->
                    Optional.of(IntegerValue.of(codeNumberedInHundredsByCategory()));
            case "type" ->
                    Optional.of(WordValue.of(categoryWordCapitalisedAsR3WritesIt()));
            case "id" -> Optional.of(WordValue.of(errorId));
            case "arg1" -> Optional.of(subject.orElseGet(NoneValue::none));
            case "arg2" -> Optional.of(secondArgument.orElseGet(NoneValue::none));
            case "arg3" -> Optional.of(thirdArgument.orElseGet(NoneValue::none));
            case "near" -> Optional.of(near.orElseGet(NoneValue::none));
            case "where" -> Optional.of(whereChain.orElseGet(NoneValue::none));
            default -> Optional.empty();
        };
    }

    private long codeNumberedInHundredsByCategory() {
        int fromCatalogue = ErrorCatalogue.codeFor(
                category.name().charAt(0) + category.name().substring(1).toLowerCase(
                        Locale.ROOT),
                errorId);
        if (fromCatalogue > 0) {
            return fromCatalogue;
        }
        return switch (category) {
            case NOTE -> 100;
            case SYNTAX -> 200;
            case SCRIPT -> 300;
            case MATH -> 400;
            case ACCESS -> 500;
            case COMMAND -> 600;
            case USER -> 800;
            case INTERNAL -> 900;
            case THROW -> 0;
        };
    }

    private String categoryWordCapitalisedAsR3WritesIt() {
        String spelling = category.spelling();
        return Character.toUpperCase(spelling.charAt(0)) + spelling.substring(1);
    }

    private static final int MOST_OF_THE_LOCATION_THAT_IS_SHOWN = 60;

    /**
     * The four lines FORM writes: the type and the message, then the chain of
     * calls and the location when there are any.
     *
     * <p>{@code Mold_Error} writes them in that order, each followed by a
     * newline, and leaves out a line whose field is none. The words come from
     * the catalogue rather than from here, so a script that changes an
     * argument changes the message.
     */
    public String formedAsRebolFormsIt() {
        StringBuilder written = new StringBuilder("\n** ")
                .append(categoryWordCapitalisedAsR3WritesIt())
                .append(" error: ")
                .append(theMessageTheCatalogueGives())
                .append('\n');
        field("where").filter(said -> !(said instanceof NoneValue)).ifPresent(chain ->
                written.append("** Where: ").append(Molder.form(chain)).append('\n'));
        field("near").filter(said -> !(said instanceof NoneValue)).ifPresent(where ->
                written.append("** Near: ").append(theLocationShown(where)).append('\n'));
        return written.toString();
    }

    private String theMessageTheCatalogueGives() {
        Value said = ErrorWording.forTheId(
                categoryWordCapitalisedAsR3WritesIt(), errorId).orElse(null);
        if (said == null) {
            return ErrorWording.NOTHING_IN_THE_CATALOGUE;
        }
        if (!(said instanceof BlockValue words)) {
            return Molder.form(said);
        }
        StringBuilder built = new StringBuilder();
        for (Value item : words.remaining()) {
            if (!built.isEmpty() && built.charAt(built.length() - 1) != '\n') {
                built.append(' ');
            }
            built.append(theFieldItNamesOrTheItemItself(item));
        }
        return built.toString();
    }

    private String theFieldItNamesOrTheItemItself(Value item) {
        if (item instanceof WordValue named && named.datatype().isAnyWord()) {
            Optional<Value> held = field(named.canonical());
            if (held.isPresent()) {
                return Molder.mold(held.get());
            }
        }
        return Molder.form(item);
    }

    private static String theLocationShown(Value where) {
        if (where instanceof StringValue text && where.datatype() == Datatype.STRING) {
            return text.text();
        }
        if (!(where instanceof BlockValue fragment)) {
            return Molder.mold(where);
        }
        StringBuilder built = new StringBuilder();
        for (Value item : fragment.remaining()) {
            if (built.length() > MOST_OF_THE_LOCATION_THAT_IS_SHOWN) {
                break;
            }
            if (!built.isEmpty()) {
                built.append(' ');
            }
            built.append(Molder.mold(item));
        }
        return built.length() > MOST_OF_THE_LOCATION_THAT_IS_SHOWN
                ? built.substring(0, MOST_OF_THE_LOCATION_THAT_IS_SHOWN) + "..."
                : built.toString();
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

    /**
     * The same error, carrying the block fragment it came from.
     *
     * <p>The written fields carry over by reference rather than being copied,
     * because this is the same error rather than another like it: an error
     * that picked up its NEAR on the way out must not lose an id somebody set.
     */
    public ErrorValue near(Value fragment) {
        return new ErrorValue(category, errorId, message, subject,
                secondArgument, thirdArgument, Optional.of(fragment), whereChain,
                writtenFields);
    }

    /**
     * The same error, carrying the chain of names it was raised through.
     *
     * <p>A block, innermost first, as R3 answers: {@code 1 / 0} inside a
     * function F gives {@code [/ f try ...]}. The tail differs from R3's,
     * whose frames run down into the console's own evaluation; what matches
     * is the part that is about the script.
     */
    public ErrorValue raisedThrough(Value chain) {
        return new ErrorValue(category, errorId, message, subject,
                secondArgument, thirdArgument, near, Optional.of(chain),
                writtenFields);
    }

    /** Whether anything has already said where this came from. */
    public boolean saysWhereItCameFrom() {
        return near.isPresent() || whereChain.isPresent();
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
