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

    /** Whether this context or an ancestor holds the name. */
    public boolean knows(String canonicalName) {
        if (unbound) {
            return false;
        }
        return slotsByCanonicalName.containsKey(canonicalName)
                || (parent != null && parent.knows(canonicalName));
    }

    /** Whether this context itself holds the name, ignoring ancestors. */
    public boolean holds(String canonicalName) {
        return !unbound && slotsByCanonicalName.containsKey(canonicalName);
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

    public int slotCount() {
        return slotsByCanonicalName.size();
    }

    public List<ContextSlot> slots() {
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
