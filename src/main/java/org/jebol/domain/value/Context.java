package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A set of named slots that words resolve through. The global environment and
 * every {@code object!} share this representation.
 *
 * <p>Lookup is by canonical (lowercased) name, because REBOL words compare
 * without regard to case while printing as they were written.
 *
 * <p>{@link #unbound()} is a null object rather than a null reference: an
 * unbound word carries a context that knows nothing and says so, so no code
 * downstream has to branch on null to find out.
 */
public final class Context {

    private static final Context UNBOUND = new Context(null, true);

    private final Map<String, ContextSlot> slotsByCanonicalName = new LinkedHashMap<>();
    private final Context parent;
    private final boolean unbound;

    /**
     * Whether this is a loop's own frame. The C marks one as an internal
     * series -- {@code INT_SERIES(frame)} -- so it is not reachable as an
     * object: {@code foreach x [1] [context? 'x]} answers none.
     */
    private boolean loopFrame;

    private Context(Context parent, boolean unbound) {
        this.parent = parent;
        this.unbound = unbound;
    }

    /** A fresh context with no parent. */
    public static Context root() {
        return new Context(null, false);
    }

    /** A fresh context that falls back to another for names it lacks. */
    public static Context childOf(Context parent) {
        if (parent == null) {
            throw new IllegalArgumentException("a child context needs a parent");
        }
        return new Context(parent, false);
    }

    /** A loop's own frame, hidden from CONTEXT?. */
    public static Context loopFrameOf(Context parent) {
        Context frame = childOf(parent);
        frame.loopFrame = true;
        return frame;
    }

    public boolean isALoopFrame() {
        return loopFrame;
    }

    /**
     * The function whose call this frame belongs to, or null. CONTEXT? of a
     * word bound into a call frame answers the function itself, which is
     * what lets a body reach its own SPEC-OF.
     */
    private Value ownedByFunction;

    /**
     * Whether the call this frame belonged to has returned. The C reuses a
     * returned function's stack frame, so a word still bound into one
     * answers whatever call took the frame over -- under DO, that is DO.
     */
    private boolean callEnded;

    public void markAsCallFrameOf(Value function) {
        this.ownedByFunction = function;
    }

    public Value functionOwningThisFrame() {
        return ownedByFunction;
    }

    public void markCallEnded() {
        this.callEnded = true;
    }

    public boolean callHasEnded() {
        return callEnded;
    }

    /**
     * The context an unbound word carries. It holds nothing, accepts nothing,
     * and reports itself as unbound.
     */
    public static Context unbound() {
        return UNBOUND;
    }

    public boolean isUnbound() {
        return unbound;
    }

    /**
     * Whether new names may be added.
     *
     * <p>The third thing protection covers on an object, and separate
     * from the other two. PROTECT/DEEP guards the object itself, the
     * words already in it, and the values those words hold, and the
     * UNPROTECT refinements release different subsets: plain releases the
     * object and its words but not their values, /WORDS releases only the
     * words, /DEEP releases all three, /WORDS/DEEP releases the words and
     * their values but not the object.
     *
     * <p>Without this as its own flag, "can a word be added" and "can a
     * word be reassigned" are the same question, and no combination of
     * refinements can tell them apart.
     */
    private boolean closedToNewNames;

    /** Whether a new name may be added to this context. */
    public boolean isClosedToNewNames() {
        return closedToNewNames;
    }

    /** Closes or reopens this context to new names. */
    public void closeToNewNames(boolean closed) {
        this.closedToNewNames = closed;
    }

    /** Whether this context or an ancestor holds the name. */
    public boolean knows(String canonicalName) {
        if (unbound) {
            return false;
        }
        return slotsByCanonicalName.containsKey(canonicalName)
                || (parent != null && parent.knows(canonicalName));
    }

    /**
     * Whether this context itself holds the name, ignoring ancestors.
     *
     * <p>A hidden field is not held, as far as anything outside the object
     * is concerned. This is the question field selection asks, so
     * `o/f` on a hidden f fails as though there were no such field --
     * while {@link #knows}, which is how a word inside the object
     * resolves, still finds it. That split is the whole of PROTECT/HIDE.
     */
    public boolean holds(String canonicalName) {
        ContextSlot slot = unbound ? null : slotsByCanonicalName.get(canonicalName);
        return slot != null && !slot.isHidden();
    }

    /**
     * The context that actually holds the name, which may be an ancestor.
     *
     * <p>What a bound word must point at. Pointing at a descendant that only
     * reaches the slot through its parent would be true enough for reading,
     * but a caller that asks a word where it lives and then defines a name
     * there would write into a scope that is about to be thrown away. Ask
     * {@link #knows} first.
     */
    public Context holderOf(String canonicalName) {
        if (holds(canonicalName)) {
            return this;
        }
        if (!unbound && parent != null) {
            return parent.holderOf(canonicalName);
        }
        throw new IllegalStateException(
                "no context holds \"" + canonicalName + "\"; ask knows() first");
    }

    /**
     * The slot for a name, searching ancestors. Absent rather than null, so a
     * caller must decide what an unknown word means rather than tripping over
     * it later.
     */
    public ContextSlot slotFor(String canonicalName) {
        if (unbound) {
            throw new IllegalStateException(
                    "the unbound context holds no slots; ask knows() first");
        }
        ContextSlot slot = slotsByCanonicalName.get(canonicalName);
        if (slot != null) {
            return slot;
        }
        if (parent != null) {
            return parent.slotFor(canonicalName);
        }
        throw new IllegalStateException(
                "no slot for \"" + canonicalName + "\"; ask knows() first");
    }

    /**
     * Adds a slot holding {@code unset}, or returns the existing one. A word
     * that has been named but not assigned is exactly what {@code unset!} is
     * for.
     */
    public ContextSlot define(String spelling) {
        if (unbound) {
            throw new IllegalStateException("the unbound context cannot be extended");
        }
        String canonical = canonicalise(spelling);
        ContextSlot existing = slotsByCanonicalName.get(canonical);
        if (existing != null) {
            return existing;
        }
        ContextSlot created = new ContextSlot(this, spelling, canonical);
        slotsByCanonicalName.put(canonical, created);
        return created;
    }

    /** Defines the name if needed, then sets its value. */
    public ContextSlot set(String spelling, Value value) {
        ContextSlot slot = define(spelling);
        slot.setValue(value);
        return slot;
    }

    /**
     * The slot this context itself holds, ignoring ancestors.
     *
     * <p>Field selection uses this rather than {@link #slotFor}. An object's
     * context hangs beneath where it was written, so a word inside its body
     * can still reach the enclosing script; but {@code account/balance} must
     * find a field of the account, not a global that happens to share the
     * name, or every object would appear to have every word ever defined.
     */
    public ContextSlot ownSlotFor(String canonicalName) {
        ContextSlot slot = slotsByCanonicalName.get(canonicalName);
        if (slot == null) {
            throw new IllegalStateException(
                    "no field \"" + canonicalName + "\" here; ask holds() first");
        }
        return slot;
    }

    /**
     * This context's own fields by name, without {@code self}.
     *
     * <p>What object equality compares. {@code self} is left out because
     * every object has one and it points back at the object, so counting
     * it would make the comparison recurse for ever.
     */
    public Map<String, Value> fieldsExcludingSelf() {
        Map<String, Value> fields = new LinkedHashMap<>();
        slotsByCanonicalName.forEach((name, slot) -> {
            if (!name.equals("self") && !slot.isHidden()) {
                fields.put(name, slot.value());
            }
        });
        return fields;
    }

    /**
     * How many fields there are, hidden ones counted and SELF not.
     *
     * <p>Not {@link #slotCount}: an object built one way carries SELF and
     * one built another does not, so counting slots makes two objects
     * with the same fields unequal for a reason that has nothing to do
     * with their fields.
     */
    public int fieldCount() {
        return (int) slotsByCanonicalName.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("self"))
                .count();
    }

    public int slotCount() {
        return slotsByCanonicalName.size();
    }

    /**
     * This context's slots, without the hidden ones.
     *
     * <p>What WORDS-OF, VALUES-OF, BODY-OF and MOLD all walk, so hiding a
     * field takes it out of every one of them at once. Anything that has
     * to see a hidden field asks {@link #everySlot} instead, and there is
     * only one such caller: the code that hides them.
     */
    public List<ContextSlot> slots() {
        return slotsByCanonicalName.values().stream()
                .filter(slot -> !slot.isHidden())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** Every slot, hidden ones included. */
    public List<ContextSlot> everySlot() {
        return new ArrayList<>(slotsByCanonicalName.values());
    }

    /** The form a word compares by: lowercased. */
    public static String canonicalise(String spelling) {
        return spelling.toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return unbound ? "Context(unbound)" : "Context(" + slotCount() + " slots)";
    }
}
