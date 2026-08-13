package org.jebol.domain.value;

import java.util.List;
import java.util.Set;

/**
 * A C-shaped record: field names each declaring one scalar type.
 *
 * <p>Construction only, which is what {@code #(struct! [a [uint8!]])} needs
 * to read back as a value {@code struct?} says yes to. The full semantics --
 * reading and writing fields, molding the layout, passing one to a library
 * call -- belong to the FFI this interpreter does not offer.
 */
public record StructValue(BlockValue layout) implements Value {

    /** The scalar type names MT_Struct accepts, {@code type_to_sym}. */
    private static final Set<String> FIELD_TYPES = Set.of(
            "uint8", "int8", "uint16", "int16", "uint32", "int32",
            "uint64", "int64", "float32", "float64", "pointer",
            "word", "rebval");

    /** The struct a layout block declares, or null when it declares none. */
    public static StructValue from(BlockValue layout) {
        List<Value> fields = layout.remaining();
        if (fields.size() % 2 != 0) {
            return null;
        }
        for (int at = 0; at < fields.size(); at += 2) {
            if (!(fields.get(at) instanceof WordValue)
                    || !(fields.get(at + 1) instanceof BlockValue declared)
                    || declared.remaining().size() != 1
                    || !(declared.remaining().getFirst() instanceof WordValue type)
                    || !FIELD_TYPES.contains(withoutTheMark(type.canonical()))) {
                return null;
            }
        }
        return new StructValue(layout);
    }

    private static String withoutTheMark(String spelling) {
        return spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling;
    }

    @Override
    public Datatype datatype() {
        return Datatype.STRUCT;
    }

    @Override
    public String toString() {
        return "make struct! " + layout;
    }
}
