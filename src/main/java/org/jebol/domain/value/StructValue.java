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

    /**
     * The scalar type names MT_Struct accepts, {@code type_to_sym}.
     *
     * <p>{@code float!} and {@code double!} are the same two widths written
     * the other way and Rebol takes all four spellings. Leaving them out made
     * {@code #(struct! [a [float!]] ...)} a malconstruct, and struct-test.r3
     * uses both on the way to its hundred and eighty-eight assertions.
     */
    private static final Set<String> FIELD_TYPES = Set.of(
            "uint8", "int8", "uint16", "int16", "uint32", "int32",
            "uint64", "int64", "float32", "float64", "float", "double",
            "pointer", "word", "rebval");

    /**
     * Whether a layout block declares a struct.
     *
     * <p>Asked separately from building one, so that nothing has to hold a
     * null to find out. Returning null for "no struct here" put absence into
     * a variable, and it travelled: it reached a block read out of
     * struct-test.r3 and came back as a NullPointerException from a copy in
     * the test harness, one file and several layers away.
     */
    public static boolean declaresAStruct(BlockValue layout) {
        List<Value> fields = layout.remaining();
        if (fields.size() % 2 != 0) {
            return false;
        }
        for (int at = 0; at < fields.size(); at += 2) {
            if (!(fields.get(at) instanceof WordValue)
                    || !(fields.get(at + 1) instanceof BlockValue declared)
                    || !declaresOneField(declared.remaining())) {
                return false;
            }
        }
        return true;
    }

    /**
     * One field's type, which may carry how many of them there are.
     *
     * <p>{@code a [int8!]} is a field and {@code d [uint8! [2]]} is two of
     * them side by side. Taking only the first shape made
     * {@code #(struct! [... d [uint8! [2]]] ...)} a malconstruct, and that is
     * line 435 of struct-test.r3 -- one array field standing between the
     * reader and the other hundred and eighty-seven assertions.
     */
    private static boolean declaresOneField(List<Value> declared) {
        if (declared.isEmpty() || declared.size() > 2
                || !(declared.getFirst() instanceof WordValue type)
                || !FIELD_TYPES.contains(withoutTheMark(type.canonical()))) {
            return false;
        }
        return declared.size() == 1
                || (declared.get(1) instanceof BlockValue howMany
                        && howMany.remaining().size() == 1
                        && howMany.remaining().getFirst() instanceof IntegerValue);
    }

    /** The struct a layout block declares. Ask {@link #declaresAStruct} first. */
    public static StructValue from(BlockValue layout) {
        if (!declaresAStruct(layout)) {
            throw new IllegalArgumentException(
                    "this block declares no struct: " + layout);
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
