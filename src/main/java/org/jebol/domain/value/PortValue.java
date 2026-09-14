package org.jebol.domain.value;

/**
 * A port: something outside the interpreter that a script reads and writes.
 *
 * <p>An object underneath, and Rebol's is the same. {@code sysobj.reb} builds
 * one from {@code system/standard/port}, which holds eight fields: the spec it
 * was opened from, the scheme it belongs to, its parent, its actor, its awake
 * handler, its private state, the host's own storage, and a data buffer. What
 * makes it a port rather than an object is the datatype, because that is what
 * sends an action to the actor rather than reading a field.
 *
 * <p>The actor is where the work happens. Reading a console port and reading a
 * file port are the same call to READ and two different actors, which is how
 * one verb reaches every kind of thing a script can open.
 *
 * <p>Specified in {@code spec/embed.allium}.
 */
public record PortValue(Context context) implements Value {

    public PortValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("a port needs a real context");
        }
    }

    /** The name of the scheme this port belongs to, or an empty string. */
    public String schemeName() {
        if (!(fieldNamed("scheme") instanceof ObjectValue scheme)) {
            return "";
        }
        return scheme.context().holds("name")
                && scheme.context().ownSlotFor("name").value() instanceof WordValue word
                ? word.canonical()
                : "";
    }

    /** One field of the port, or none when it has no such field. */
    public Value fieldNamed(String name) {
        return context.holds(name)
                ? context.ownSlotFor(name).value()
                : NoneValue.none();
    }

    /** Replaces one field, in place, because a port is a thing and not a value. */
    public void setField(String name, Value replacement) {
        context.set(name, replacement);
    }

    /**
     * Whether this port is open, which is whether its actor has storage in
     * STATE.
     *
     * <p>Kept in the port's own state rather than in the adapter, so that
     * {@code open?} can answer without reaching outside the interpreter. The C
     * keeps the request structure there and asks the same question the same
     * way: {@code Awake_System} reads the field and tests {@code
     * IS_HANDLE(state)}.
     *
     * <p>What a built-in actor leaves there varies by scheme -- a socket, a
     * cipher, a position in a file, or a plain mark where there is nothing to
     * keep. An object is the one thing it never is: STATE is also where an
     * actor written in REBOL keeps its own, and such a port answers OPEN? from
     * its own function and never reaches here.
     *
     * <p>So an object here came from outside, and reading it as "open" is how
     * a connection stopped being made at all. Rebol's TLS hands a TCP port the
     * HTTP protocol's object -- {@code conn/state: port/parent/state} -- and
     * then asks {@code either open? conn [...] [open conn]}.
     */
    public boolean isOpen() {
        Value whatTheActorLeft = fieldNamed("state");
        return !(whatTheActorLeft instanceof ObjectValue)
                && whatTheActorLeft.isTruthy();
    }

    public void markOpen(boolean open) {
        setField("state", LogicValue.of(open));
    }

    @Override
    public Datatype datatype() {
        return Datatype.PORT;
    }

    @Override
    public String toString() {
        String scheme = schemeName();
        return scheme.isEmpty() ? "port" : "port on " + scheme;
    }
}
