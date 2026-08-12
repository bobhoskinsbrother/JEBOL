package org.jebol.domain.value;

/**
 * An opaque thing the runtime owns and a script can only pass around.
 *
 * <p>{@code c-handle.c} says what it grew into: "initially handles were just user
 * transparent pointer holders. But it seems to be more useful to have it as a
 * more advanced type, with possibility to register a function for releasing
 * resources from GC when handle value is no more needed."
 *
 * <p>So there are two kinds, and almost everything about a handle depends on
 * which it is. A <b>function</b> handle wraps something the runtime can call: a
 * codec dispatcher, an extension entry point. A <b>context</b> handle owns a
 * resource with a lifetime: a cipher's key schedule, a port's device state. Only
 * a context handle publishes anything about itself, and only a context handle can
 * be released.
 *
 * <p>The comparisons are where that split shows most, and they are not what the
 * words suggest.
 *
 * <p>{@code same?} is identity: {@code (VAL_HANDLE_FLAGS(a) ==
 * VAL_HANDLE_FLAGS(b)) && (VAL_HANDLE_DATA(a) == VAL_HANDLE_DATA(b))}.
 *
 * <p>{@code equal?} is <em>type only, and only for context handles</em>:
 * {@code IS_CONTEXT_HANDLE(a) && IS_CONTEXT_HANDLE(b) && (VAL_HANDLE_SYM(a) ==
 * VAL_HANDLE_SYM(b))}. Two ciphers with different keys are equal because both are
 * ciphers of the same kind -- and a function handle is equal to nothing at all,
 * <em>including itself</em>. `equal? h h` is false for a codec.
 *
 * <p>And the ordering puts every context handle before every function handle,
 * sorts context handles of different kinds by name, and everything else by
 * identity. Which is what makes {@code sort} on a block of handles group them by
 * kind.
 */
public record HandleValue(
        String typeName,
        HandleValue.Kind kind,
        int identity,
        Value payload) implements Value {

    /**
     * What the handle is holding.
     *
     * <p>{@code HANDLE_FUNCTION = 0} and {@code HANDLE_CONTEXT = 1 << 2} in
     * sys-value.h, and the flag is read by {@code IS_CONTEXT_HANDLE} in a dozen
     * places to decide whether the handle has anything to say about itself.
     */
    public enum Kind { FUNCTION, CONTEXT }

    /** A handle wrapping something callable, named by kind. */
    public static HandleValue function(String typeName, int identity, Value payload) {
        return new HandleValue(typeName, Kind.FUNCTION, identity, payload);
    }

    @Override
    public Datatype datatype() {
        return Datatype.HANDLE;
    }

    public boolean isContext() {
        return kind == Kind.CONTEXT;
    }

    /**
     * Whether these are the same handle. {@code CT_Handle} with a positive mode.
     *
     * <p>Kind and payload identity, and nothing about the type name -- two handles
     * of different kinds cannot share an identity anyway, because the identity is
     * where the payload lives.
     */
    public boolean isTheSameHandleAs(HandleValue other) {
        return other.kind == kind && other.identity == identity;
    }

    /**
     * Whether these are equal handles. {@code CT_Handle} with mode zero.
     *
     * <p>Both must be context handles and their types must match. So this answers
     * false for every function handle, which reads like a bug and is the C's
     * decision: a function handle has no kind a script can ask about, so there is
     * nothing for equality to compare.
     */
    public boolean isEqualHandleTo(HandleValue other) {
        return isContext() && other.isContext() && other.typeName.equals(typeName);
    }

    /**
     * {@code Cmp_Handle}, which is three cases before it reaches the numbers.
     *
     * <p>A context handle sorts before a function handle -- {@code return -1} and
     * {@code return 1} for the mixed pair. Two context handles of different types
     * sort by their type names, {@code Compare_UTF8(sp, tp, ...) + 2}. Everything
     * else, which is two context handles of one type or two function handles, sorts
     * by the payload's identity.
     */
    public int compareWith(HandleValue other) {
        if (isContext() && !other.isContext()) {
            return -1;
        }
        if (!isContext() && other.isContext()) {
            return 1;
        }
        if (isContext() && !other.typeName.equals(typeName)) {
            return typeName.compareTo(other.typeName) + 2;
        }
        return Integer.compare(identity, other.identity);
    }

    @Override
    public String toString() {
        return Molder.mold(this);
    }
}
