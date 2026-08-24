package org.jebol.domain.value;

/**
 * What one field of a struct holds, from {@code parse_field_type}.
 *
 * <p>Four shapes. Ten of the C's cases are numbers of a known width and
 * signedness, which is exactly what {@link VectorKind} already describes, so
 * they share it -- including the aliases, because the C runs the field's type
 * word through {@code Normalize_Vector_Type_Symbol} before switching on it and
 * that is the same table {@code vector!} uses.
 *
 * <p>The other three are a word held as its four-byte symbol number, a whole
 * REBOL value held inline, and another struct laid inside this one.
 */
public sealed interface StructFieldType {

    /** How many bytes one element of this field occupies. */
    int size();

    /** The type word this field molds as, after the aliases are settled. */
    String spelling();

    /** A number: the ten widths {@code vector!} also knows. */
    record Numeric(VectorKind kind) implements StructFieldType {

        @Override
        public int size() {
            return kind.bytes();
        }

        @Override
        public String spelling() {
            return kind.spelling();
        }
    }

    /** A word, stored as the four-byte symbol number the C stores. */
    record NamedWord() implements StructFieldType {

        @Override
        public int size() {
            return 4;
        }

        @Override
        public String spelling() {
            return "word!";
        }
    }

    /**
     * A whole REBOL value, held inline.
     *
     * <p>The C copies {@code sizeof(REBVAL)} bytes into the field, so the
     * width is whatever the build's value struct is. Nothing in Rebol's own
     * tests reads those bytes -- a struct carrying one refuses to be changed
     * from a binary at all -- so the number here only has to be the same
     * everywhere it is used.
     */
    record LiveValue() implements StructFieldType {

        @Override
        public int size() {
            return 32;
        }

        @Override
        public String spelling() {
            return "rebval!";
        }
    }

    /** Another struct, laid inside this one at this field's offset. */
    record Nested(StructSpec spec) implements StructFieldType {

        @Override
        public int size() {
            return spec.size();
        }

        @Override
        public String spelling() {
            return "struct!";
        }
    }
}
