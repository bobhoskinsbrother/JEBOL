package org.jebol.domain.value;

import java.util.List;

/**
 * A module: a context that carries a header saying what it is called, what
 * version it is, and which of its words the rest of the world may see.
 *
 * <p>An object underneath, and Rebol's is the same object with one extra
 * field. {@code types.reb} gives module! its own row, whose mold typeclass
 * and typeset are both {@code object}, so a module molds as an object and
 * answers {@code any-object?} while answering {@code object?} false.
 *
 * <p>What makes it a module rather than an object is the header, because the
 * header is what decides which names escape. A module whose header exports
 * three words puts three words into the library and keeps the rest to
 * itself, and a name it holds privately cannot collide with a library
 * function of the same spelling.
 *
 * <p>That collision is why this datatype exists rather than being deferred
 * again. Rebol's own JSON codec is a module holding a parse rule named
 * {@code exp} and another named {@code stack}. Loaded flat, both replace the
 * library functions of those names, and the failure is silent: the word
 * still answers, it just answers a block.
 *
 * <p>Specified in {@code spec/values.allium}.
 */
public record ModuleValue(Context context, ObjectValue header) implements Value {

    public ModuleValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("a module needs a real context");
        }
        if (header == null) {
            throw new IllegalArgumentException("a module needs a header");
        }
    }

    /**
     * The words this module publishes, in the order the header lists them.
     *
     * <p>Every other word it defines is private to it. A header with no
     * exports field publishes nothing, which is the right answer for a codec
     * whose whole job is to register itself with a side effect.
     */
    public List<String> exportedNames() {
        if (!(headerField("exports") instanceof BlockValue exports)) {
            return List.of();
        }
        return exports.remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .map(WordValue::canonical)
                .toList();
    }

    /** One field of the header, or none when the header has no such field. */
    public Value headerField(String name) {
        Context fields = header.context();
        return fields.holds(name) ? fields.ownSlotFor(name).value() : NoneValue.none();
    }

    /**
     * Two modules are equal when their words and their headers are.
     *
     * <p>The record default compares each context by identity, which would
     * make no two modules equal even when one was built from the other.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleValue module
                && context.fieldsExcludingSelf().equals(
                        module.context.fieldsExcludingSelf())
                && header.equals(module.header);
    }

    @Override
    public int hashCode() {
        return context.fieldsExcludingSelf().hashCode();
    }

    @Override
    public Datatype datatype() {
        return Datatype.MODULE;
    }

    @Override
    public String toString() {
        Value named = headerField("name");
        return named instanceof WordValue word
                ? "module " + word.canonical()
                : "module with " + context.slotCount() + " words";
    }
}
