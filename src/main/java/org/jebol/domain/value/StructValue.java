package org.jebol.domain.value;

import org.jebol.domain.value.StructSpec.StructField;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A C-shaped record: named fields laid over a run of bytes.
 *
 * <p>Three things, which is what {@code REBSTU} is. The layout says what the
 * bytes mean, the bytes are shared, and the offset says where in them this
 * struct starts. A field of struct type answers a value with the same bytes
 * and a further offset, so {@code s/pos/x: 22} reaches the parent's bytes and
 * {@code a1: s/a/1} keeps seeing what the parent writes.
 *
 * <p>Reading a field that was declared with a count gives a vector for the
 * numeric types and a block for the rest, while reflecting the same field
 * gives a block either way. That asymmetry is Rebol's:
 * {@code Get_Struct_Field_Value} builds a vector and
 * {@code Get_Struct_Reflect} builds a block, from the same bytes.
 */
public record StructValue(StructSpec spec, StructData data, int offset) implements Value {

    @Override
    public boolean isEqualTo(Value other, Sameness how) {
        return other instanceof StructValue theirs && holdsTheSameAs(theirs);
    }


    @Override
    public Value make(Value spec, Maker maker) {
        return maker.makeStructFrom(this, spec);
    }

    public static final Datatype TYPE = new StructDatatype();

    private static final class StructDatatype extends Datatype {

        StructDatatype() {
            super("struct");
        }

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            if (!(spec instanceof AnyBlockValue given)) {
                throw refuse(spec);
            }
            List<Value> written = given.remaining();
            boolean carriesInitialValues = written.size() == 2
                    && written.get(0) instanceof AnyBlockValue
                    && written.get(1) instanceof AnyBlockValue;
            AnyBlockValue layout = carriesInitialValues && written.getFirst() instanceof AnyBlockValue first
                    ? first
                    : given;
            StructValue made = StructValue.of(layOutBy(layout, maker));
            if (carriesInitialValues) {
                made.startWith(written.get(1));
            }
            return made;
        }

        @Override
        public Value convert(Value value, Maker maker) {
            return makeFrom(value, maker);
        }

        private StructSpec layOutBy(AnyBlockValue layout, Maker maker) {
            try {
                return StructSpec.of(layout, maker.structLayouts());
            } catch (StructLayoutRefused refused) {
                throw Raised.of(refused.malconstructed()
                        ? EvaluationFailure.MALCONSTRUCT
                        : EvaluationFailure.INVALID_ARG, refused.offending());
            }
        }
    }

    public static StructValue of(StructSpec spec) {
        return new StructValue(spec, new StructData(spec.size()), 0);
    }

    /** How many bytes this struct occupies, which is what LENGTH? answers. */
    public int size() {
        return spec.size();
    }

    public byte[] octets() {
        return data.readTheBytesFrom(offset, spec.size());
    }

    public Optional<StructField> findTheFieldCalled(String name) {
        return spec.findTheFieldCalled(name);
    }

    /** Where one element of a field starts in the shared bytes. */
    public int addressOf(StructField field, int element) {
        return offset + field.offset() + element * field.type().size();
    }

    /**
     * One element of one field, as a script sees it.
     *
     * <p>A word field holding zero is none rather than a word with no
     * spelling, and so is a live-value field nothing has been written to.
     * That is {@code get_scalar} testing {@code *(REBINT *)data == 0} before
     * it builds either.
     */
    public Value readTheElementOf(StructField field, int element) {
        int at = addressOf(field, element);
        return switch (field.type()) {
            case StructFieldType.Numeric number ->
                    number.kind().read(data.readTheNumberAt(at, number.kind()));
            case StructFieldType.NamedWord ignored ->
                    data.liveValueAt(at).orElseGet(NoneValue::none);
            case StructFieldType.LiveValue ignored ->
                    data.liveValueAt(at).orElseGet(NoneValue::none);
            case StructFieldType.Nested inside ->
                    new StructValue(inside.spec(), data, at);
        };
    }

    /**
     * A whole field, which is a vector, a block or one value.
     *
     * <p>The C reaches for a vector whenever the field's type has a vector
     * equivalent, and falls back to a block for words, live values and nested
     * structs -- none of which a vector can hold.
     */
    public Value valueOf(StructField field) {
        if (!field.isArray()) {
            return readTheElementOf(field, 0);
        }
        if (field.type() instanceof StructFieldType.Numeric(VectorKind kind)) {
            long[] stored = new long[field.dimension()];
            for (int element = 0; element < stored.length; element++) {
                stored[element] = data.readTheNumberAt(addressOf(field, element), kind);
            }
            return VectorValue.hold(kind, stored);
        }
        return BlockValue.of(readTheElementsOf(field));
    }

    /** Every element of a field, whatever its type, as a plain list. */
    public List<Value> readTheElementsOf(StructField field) {
        List<Value> found = new ArrayList<>(field.dimension());
        for (int element = 0; element < field.dimension(); element++) {
            found.add(readTheElementOf(field, element));
        }
        return found;
    }

    /**
     * What reflection shows for one field: a block when there is more than
     * one of it, and the value itself otherwise.
     *
     * <p>{@code Get_Struct_Reflect} tests {@code field->dimension > 1} rather
     * than the array flag the reader sets, so a field declared
     * {@code [uint8! [1]]} reflects as a bare number while reading it through
     * a path gives a vector of one.
     */
    public Value reflectTheValueOf(StructField field) {
        return field.dimension() > 1
                ? BlockValue.of(readTheElementsOf(field))
                : readTheElementOf(field, 0);
    }

    /**
     * One element of one field, written, which is {@code assign_scalar}.
     *
     * <p>The C checks the written value's datatype against the field's before
     * it touches the bytes: a decimal and an integer are both accepted by any
     * numeric field and converted, a word only by a word field, a struct only
     * by a struct field of the same shape, and a block only by a struct field,
     * where it initialises the inner struct rather than being stored.
     *
     * @throws StructLayoutRefused when the value cannot go in this field
     */
    public void writeElement(StructField field, int element, Value written) {
        int at = addressOf(field, element);
        switch (field.type()) {
            case StructFieldType.LiveValue ignored -> data.putLiveValueAt(at, written);
            case StructFieldType.NamedWord ignored -> {
                if (!(written instanceof WordValue word)) {
                    throw refuseFor(field, written);
                }
                data.putLiveValueAt(at, word);
            }
            case StructFieldType.Numeric number -> {
                if (!(written instanceof IntegerValue) && !(written instanceof AnyDecimalValue)) {
                    throw refuseFor(field, written);
                }
                data.writeNumberAt(at, number.kind(), narrowFor(number.kind(), written));
            }
            case StructFieldType.Nested inside -> writeInnerStruct(inside, at, written);
        }
    }

    private static long narrowFor(VectorKind kind, Value written) {
        return kind.encodeForStorage(written);
    }

    private void writeInnerStruct(StructFieldType.Nested inside, int at, Value written) {
        StructValue there = new StructValue(inside.spec(), data, at);
        if (written instanceof AnyBlockValue initial) {
            there.initialiseFrom(initial);
            return;
        }
        if (written instanceof StructValue given
                && inside.spec().describesTheSameBytesAs(given.spec())) {
            data.write(at, given.octets(), inside.spec().size());
            return;
        }
        throw StructLayoutRefused.refuseTheField(written,
                "an inner struct takes a block or a struct of the same shape, not "
                        + written.datatype().literalSpelling());
    }

    private static StructLayoutRefused refuseFor(StructField field, Value written) {
        return StructLayoutRefused.refuseTheField(written,
                "the field " + field.name() + " holds " + field.type().spelling()
                        + " and cannot take " + written.datatype().literalSpelling());
    }

    /**
     * A whole field written at once, which is {@code init_field}.
     *
     * <p>More than one of something needs a block of exactly that many, and
     * the test is on the count rather than on whether a count was written, so
     * a field declared {@code [uint8! [1]]} takes a bare number here.
     */
    public void writeField(StructField field, Value written) {
        if (field.dimension() <= 1) {
            writeElement(field, 0, written);
            return;
        }
        if (written instanceof VectorValue given) {
            writeWholeArrayFrom(field, given);
            return;
        }
        if (!(written instanceof AnyBlockValue given)
                || given.remaining().size() != field.dimension()) {
            throw StructLayoutRefused.refuseTheField(written,
                    "the field " + field.name() + " holds " + field.dimension()
                            + " values and takes a block of exactly that many");
        }
        List<Value> each = given.remaining();
        for (int element = 0; element < field.dimension(); element++) {
            writeElement(field, element, each.get(element));
        }
    }

    private void writeWholeArrayFrom(StructField field, VectorValue given) {
        if (!(field.type() instanceof StructFieldType.Numeric(VectorKind kind))
                || given.lengthFromHere() != field.dimension()
                || given.storage().kind().bytes() != kind.bytes()) {
            throw StructLayoutRefused.refuseTheField(given,
                    "the field " + field.name() + " takes " + field.dimension()
                            + " values of its own width and this vector is not that");
        }
        data.write(addressOf(field, 0), given.readTheOctetsFromHere(), field.width());
    }

    /**
     * The fields a block sets, which is {@code init_fields}.
     *
     * <p>Two shapes, told apart by whether the block opens with a set-word.
     * Named form takes pairs and refuses a name no field has; positional form
     * walks the fields in order and stops when the block runs out, which is
     * how {@code make proto! [10]} sets the first field and leaves the rest.
     */
    public void startWith(Value given) {
        if (given instanceof BinaryValue octets) {
            changeFrom(octets.readTheBytesFromHere());
            return;
        }
        AnyBlockValue written = (AnyBlockValue) given;
        try {
            initialiseFrom(written);
        } catch (StructLayoutRefused refused) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(written));
        }
    }

    public void initialiseFrom(AnyBlockValue given) {
        List<Value> written = given.remaining();
        if (!written.isEmpty() && written.getFirst() instanceof SetWordValue) {
            for (int at = 0; at + 1 < written.size(); at += 2) {
                if (!(written.get(at) instanceof AnyWordValue name)) {
                    throw StructLayoutRefused.refuseTheField(written.get(at),
                            "a named initialiser is a set-word and then a value");
                }
                writeField(spec.findTheFieldCalled(name.spelling()).orElseThrow(
                        () -> StructLayoutRefused.refuseTheField(name,
                                "this struct has no field called " + name.spelling())),
                        written.get(at + 1));
            }
            return;
        }
        for (int at = 0; at < spec.fields().size() && at < written.size(); at++) {
            writeField(spec.fields().get(at), written.get(at));
        }
    }

    /** CHANGE from a binary: as many of the bytes as both sides have. */
    public void changeFrom(byte[] given) {
        data.write(offset, given, Math.min(given.length, spec.size()));
    }

    /** CLEAR: zero the bytes and drop the values they stood for. */
    public void clear() {
        data.clearFrom(offset, spec.size());
    }

    /**
     * Whether a raw binary may be written over these bytes.
     *
     * <p>No, when anything inside holds a live REBOL value: the C keeps those
     * in the bytes themselves, so overwriting them would leave the interpreter
     * reading a value out of arbitrary data. It raises PROTECTED rather than
     * risking it, and does so for the whole struct when any struct nested
     * inside carries one.
     */
    public boolean acceptsRawBytes() {
        return !spec.holdsALiveValue();
    }

    /** WORDS-OF and KEYS-OF: the field names, in declaration order. */
    public List<Value> fieldNames() {
        return spec.fields().stream()
                .map(field -> (Value) WordValue.of(field.name()))
                .toList();
    }

    /** VALUES-OF: what each field currently holds. */
    public List<Value> fieldValues() {
        return spec.fields().stream().map(this::reflectTheValueOf).toList();
    }

    /** BODY-OF: the field names as set-words, each followed by its value. */
    public List<Value> readTheBody() {
        List<Value> written = new ArrayList<>();
        for (StructField field : spec.fields()) {
            written.add(SetWordValue.of(field.name()));
            written.add(reflectTheValueOf(field));
        }
        return written;
    }

    public StructValue changeBy(Value given) {
        if (given instanceof AnyBlockValue written) {
            startWith(written);
            return this;
        }
        if (!(given instanceof BinaryValue octets)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "change wanted a value, not a " + given.datatype().literalSpelling());
        }
        if (!acceptsRawBytes()) {
            throw Raised.of(EvaluationFailure.PROTECTED);
        }
        changeFrom(octets.readTheBytesFromHere());
        return this;
    }

    @Override
    public Value reflect(AnyWordValue asked) {
        return switch (asked.canonical()) {
            case "spec" -> spec.declaration();
            case "words", "keys" -> BlockValue.of(fieldNames());
            case "values" -> BlockValue.of(fieldValues());
            case "body" -> BlockValue.of(readTheBody());
            default -> throw Raised.cannotUse(asked, "reflect struct!");
        };
    }

    /** COPY: the same layout over bytes of its own. */
    public StructValue separateCopy() {
        return new StructValue(spec, data.copyOf(offset, spec.size()), 0);
    }

    /**
     * Whether another struct holds the same bytes under the same shape.
     *
     * <p>{@code CT_Struct} at the equality modes compares the layouts by their
     * field types and the data byte for byte, so two structs whose fields are
     * spelt differently are still equal.
     */
    public boolean holdsTheSameAs(StructValue other) {
        return spec.describesTheSameBytesAs(other.spec)
                && java.util.Arrays.equals(octets(), other.octets());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "make struct! " + spec.declaration();
    }
}
