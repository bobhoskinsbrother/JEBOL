package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A struct's layout: which fields it has, how wide each is, and where each
 * one sits in the bytes.
 *
 * <p>{@code Prepare_Struct} walks the declaration block and builds this once.
 * Fields are packed with no alignment padding at all -- the C accumulates
 * {@code size * dimension} into a running offset and never rounds it up --
 * which is why {@code [id [uint16!] pos [struct! pair8!]]} is four bytes and
 * not six.
 *
 * <p>A field declared {@code [struct! some-name]} names a layout registered
 * earlier, so building a spec needs a way to look one up. That is the
 * resolver: the catalogue lives in the SYSTEM object and the value layer must
 * not reach for it.
 */
public record StructSpec(BlockValue declaration, List<StructField> fields, int size) {

    /**
     * One field: what it holds, how many of them, and where they start.
     *
     * @param isArray whether the declaration wrote a count at all. The C keeps
     *                {@code array} as its own flag rather than testing the
     *                dimension, and {@code [uint8! [1]]} sets the flag while
     *                leaving the dimension at one. Reading such a field gives
     *                a vector of one rather than a bare number, so the flag
     *                has to be kept apart from the count.
     */
    public record StructField(String name, StructFieldType type, int dimension,
            boolean isArray, int offset) {

        /** How many bytes the whole field takes, all elements together. */
        public int width() {
            return type.size() * dimension;
        }
    }

    /** Where a {@code [struct! name]} field finds the layout it names. */
    public interface LayoutRegistry extends Function<String, Optional<BlockValue>> {
    }

    /**
     * The layout a declaration block describes.
     *
     * @throws StructLayoutRefused when the block declares no struct
     */
    public static StructSpec of(BlockValue declaration, LayoutRegistry registry) {
        List<Value> written = declaration.remaining();
        int at = 0;
        while (at < written.size() && written.get(at) instanceof StringValue) {
            at++;
        }
        if (at < written.size() && written.get(at) instanceof BlockValue) {
            at++;
        }
        List<StructField> fields = new ArrayList<>();
        List<Value> settled = new ArrayList<>();
        int offset = 0;
        while (at < written.size()) {
            if (!(written.get(at) instanceof WordValue name)
                    || name.datatype() != Datatype.WORD
                    || at + 1 >= written.size()
                    || !(written.get(at + 1) instanceof BlockValue declared)) {
                throw StructLayoutRefused.becauseTheShapeIsWrong(
                        "a struct field is a word and then a block, and position "
                                + (at + 1) + " is neither");
            }
            StructField field = fieldNamed(name.spelling(), declared, offset, registry);
            fields.add(field);
            settled.add(name);
            settled.add(withTheTypeNameSettled(declared, field));
            offset += field.width();
            at += 2;
            while (at < written.size() && written.get(at) instanceof StringValue) {
                at++;
            }
        }
        if (fields.isEmpty()) {
            throw StructLayoutRefused.becauseTheShapeIsWrong(
                    "a struct with no fields is not allowed");
        }
        return new StructSpec(BlockValue.block(settled), List.copyOf(fields), offset);
    }

    /**
     * One field's declaration with its type word written the settled way.
     *
     * <p>The C rewrites the word in the spec block itself --
     * {@code VAL_WORD_SYM(val) = Normalize_Vector_Type_Symbol(...)} -- so a
     * struct declared with {@code float!} reports {@code float32!} ever after,
     * and {@code spec-of} shows the settled spelling rather than the one that
     * was typed.
     *
     * <p>An inner struct is left exactly as written, which is why a field
     * declared {@code [struct! pair8!]} still names {@code pair8!} in the
     * spec while the value that field answers molds its whole layout.
     */
    private static BlockValue withTheTypeNameSettled(
            BlockValue declared, StructField field) {
        if (field.type() instanceof StructFieldType.Nested) {
            return declared;
        }
        List<Value> written = new ArrayList<>(declared.remaining());
        written.set(0, WordValue.of(field.type().spelling()));
        return BlockValue.block(written);
    }

    private static StructField fieldNamed(String name, BlockValue declared,
            int offset, LayoutRegistry registry) {
        List<Value> written = declared.remaining();
        if (written.isEmpty() || !(written.getFirst() instanceof WordValue typeWord)) {
            throw StructLayoutRefused.becauseTheFieldIsWrong(
                    "the field " + name + " names no type");
        }
        int at = 1;
        StructFieldType type;
        if (typeWord.canonical().equals("struct!")) {
            type = new StructFieldType.Nested(
                    layoutInsideOf(name, written, registry));
            at = 2;
        } else {
            type = scalarNamed(name, typeWord.canonical());
        }
        int dimension = 1;
        boolean declaredAsAnArray = false;
        if (at < written.size() && written.get(at) instanceof BlockValue howMany) {
            List<Value> counted = howMany.remaining();
            if (counted.size() != 1 || !(counted.getFirst() instanceof IntegerValue count)) {
                throw StructLayoutRefused.becauseTheFieldIsWrong(
                        "the field " + name + " says how many of itself there are "
                                + "with something that is not a whole number");
            }
            dimension = (int) count.magnitude();
            declaredAsAnArray = true;
            at++;
        }
        if (at != written.size()) {
            throw StructLayoutRefused.becauseTheFieldIsWrong(
                    "the field " + name + " carries something after its type");
        }
        return new StructField(name, type, dimension, declaredAsAnArray, offset);
    }

    private static StructSpec layoutInsideOf(String name, List<Value> written,
            LayoutRegistry registry) {
        if (written.size() < 2) {
            throw StructLayoutRefused.becauseTheFieldIsWrong(
                    "the field " + name + " says struct! and then says which one");
        }
        Value which = written.get(1);
        if (which instanceof BlockValue inline) {
            return of(inline, registry);
        }
        if (which instanceof WordValue registered) {
            return of(registry.apply(registered.spelling())
                    .orElseThrow(() -> StructLayoutRefused.becauseTheFieldIsWrong(
                            "no struct is registered as " + registered.spelling())),
                    registry);
        }
        throw StructLayoutRefused.becauseTheFieldIsWrong(
                "the field " + name + " names an inner struct by neither a layout "
                        + "nor a registered name");
    }

    private static StructFieldType scalarNamed(String name, String typeWord) {
        return switch (typeWord) {
            case "word!" -> new StructFieldType.NamedWord();
            case "rebval!" -> new StructFieldType.LiveValue();
            default -> new StructFieldType.Numeric(VectorKind.named(typeWord)
                    .orElseThrow(() -> StructLayoutRefused.becauseTheFieldIsWrong(
                            "the field " + name + " names the type " + typeWord
                                    + ", which no struct field can be")));
        };
    }

    /** The field of this name, if there is one. */
    public Optional<StructField> fieldCalled(String name) {
        return fields.stream()
                .filter(field -> field.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Whether anything in here holds a live REBOL value, however deep.
     *
     * <p>The C accumulates the flag upward -- {@code STRUCT_FLAGS(stu) |=
     * VAL_STRUCT_FLAGS(inner)} -- so a struct is protected from a raw binary
     * change when any struct inside it carries one.
     */
    public boolean holdsALiveValue() {
        return fields.stream().anyMatch(field -> switch (field.type()) {
            case StructFieldType.LiveValue ignored -> true;
            case StructFieldType.Nested inside -> inside.spec().holdsALiveValue();
            default -> false;
        });
    }

    /**
     * Whether two layouts describe the same bytes.
     *
     * <p>{@code same_fields} compares a hash built from each field's type and
     * dimension and nothing else, so two structs whose fields are named
     * differently are still equal. That is what makes
     * {@code (make struct! [a [u8!] b [u8!]]) = (make struct! [a [uint8!] b [uint8!]])}
     * true while {@code ==} on the same pair is false.
     */
    public boolean describesTheSameBytesAs(StructSpec other) {
        if (fields.size() != other.fields.size() || size != other.size) {
            return false;
        }
        for (int at = 0; at < fields.size(); at++) {
            StructField mine = fields.get(at);
            StructField theirs = other.fields.get(at);
            if (mine.dimension() != theirs.dimension()
                    || !sameType(mine.type(), theirs.type())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameType(StructFieldType mine, StructFieldType theirs) {
        if (mine instanceof StructFieldType.Nested inside
                && theirs instanceof StructFieldType.Nested alongside) {
            return inside.spec().describesTheSameBytesAs(alongside.spec());
        }
        return mine.equals(theirs);
    }
}
