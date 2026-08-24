package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The two ways a vector is written down, read back into a vector.
 *
 * <p>{@code Construct_Vector} reads the short form a vector molds as,
 * {@code #(int32! [1 2 3] 3)}, and {@code Make_Vector_Spec} reads the longer
 * one MAKE takes, which still accepts the spelling Rebol used before the kind
 * names existed: {@code [unsigned integer! 32 4 [1 2]]}.
 *
 * <p>Both answer nothing rather than raising when the spec will not do,
 * because what the caller says about it differs: the reader calls it a
 * malformed construct and MAKE calls it a bad argument.
 */
public final class VectorSpec {

    private static final VectorKind WHEN_NOTHING_SAYS_OTHERWISE = VectorKind.INT32;
    private static final VectorKind THE_WIDEST_WHOLE_NUMBER = VectorKind.INT64;
    private static final VectorKind THE_WIDEST_DECIMAL = VectorKind.FLOAT64;

    private VectorSpec() {
    }

    /**
     * The construction form, which names a kind and then optionally data and a
     * position.
     *
     * <p>{@code #(vector! uint8! [1 2 3])} is allowed as well, because MOLD/ALL
     * of a series writes the datatype first and the vector's own molding is the
     * one place that does not.
     */
    public static Optional<VectorValue> readConstruction(List<Value> parts) {
        List<Value> rest = parts;
        if (!rest.isEmpty() && rest.getFirst() instanceof WordValue leading
                && "vector!".equals(leading.canonical())) {
            rest = rest.subList(1, rest.size());
        }
        if (rest.isEmpty() || !(rest.getFirst() instanceof WordValue named)) {
            return Optional.empty();
        }
        Optional<VectorKind> kind = VectorKind.named(named.spelling());
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return assembled(kind.get(), rest.subList(1, rest.size()), UnaryOperator.identity());
    }

    /**
     * The MAKE form, which may name a kind or spell one out.
     *
     * <p>{@code resolveGetWord} is how {@code [uint8! :size :data :index]}
     * works: MAKE looks each get-word up where the spec block was written, and
     * the construction form has no context to look anything up in.
     */
    public static Optional<VectorValue> readMakeSpec(List<Value> parts,
            UnaryOperator<Value> resolveGetWord) {

        if (parts.isEmpty()) {
            return Optional.of(emptyOf(WHEN_NOTHING_SAYS_OTHERWISE));
        }
        Value leading = parts.getFirst();
        if (leading instanceof IntegerValue || leading instanceof DecimalValue) {
            return filled(leading instanceof IntegerValue
                    ? THE_WIDEST_WHOLE_NUMBER
                    : THE_WIDEST_DECIMAL, parts.size(), BlockValue.block(parts), 1);
        }
        if (!(leading instanceof WordValue named)) {
            return Optional.empty();
        }
        Optional<VectorKind> byName = VectorKind.named(named.spelling());
        if (byName.isPresent()) {
            return assembled(byName.get(), parts.subList(1, parts.size()), resolveGetWord);
        }
        return readSpelledOutSpec(parts, resolveGetWord);
    }

    /**
     * The older spelling: an optional sign word, then a datatype word, then a
     * width.
     *
     * <p>All three of those the C insists on in that order, and it refuses
     * outright rather than guessing: {@code unsigned decimal!} is no kind, and
     * eight or sixteen bits is a width only whole numbers have.
     */
    private static Optional<VectorValue> readSpelledOutSpec(List<Value> parts,
            UnaryOperator<Value> resolveGetWord) {

        int at = 0;
        Boolean unsigned = null;
        if (parts.get(at) instanceof WordValue sign) {
            if ("unsigned".equals(sign.canonical())) {
                unsigned = true;
                at++;
            } else if ("signed".equals(sign.canonical())) {
                unsigned = false;
                at++;
            }
        }
        if (at >= parts.size() || !(parts.get(at) instanceof WordValue named)) {
            return Optional.empty();
        }
        boolean wantsDecimals;
        if ("integer!".equals(named.canonical())) {
            wantsDecimals = false;
        } else if ("decimal!".equals(named.canonical())) {
            wantsDecimals = true;
            if (Boolean.TRUE.equals(unsigned)) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
        at++;
        if (at >= parts.size() || !(parts.get(at) instanceof IntegerValue width)) {
            return Optional.empty();
        }
        Optional<VectorKind> kind = VectorKind.of(
                wantsDecimals, Boolean.TRUE.equals(unsigned), (int) width.magnitude());
        if (kind.isEmpty()) {
            return Optional.empty();
        }
        return assembled(kind.get(), parts.subList(at + 1, parts.size()), resolveGetWord);
    }

    /**
     * What follows the kind: a count, then data, then a position, all optional
     * and all in that order.
     */
    private static Optional<VectorValue> assembled(VectorKind kind, List<Value> parts,
            UnaryOperator<Value> resolveGetWord) {

        int at = 0;
        int howMany = 0;
        Value data = null;
        int position = 1;

        Value looking = lookedUp(parts, at, resolveGetWord);
        if (looking instanceof IntegerValue counted) {
            if (counted.magnitude() < 0) {
                return Optional.empty();
            }
            howMany = (int) counted.magnitude();
            looking = lookedUp(parts, ++at, resolveGetWord);
        }
        if (looking instanceof BlockValue || looking instanceof BinaryValue) {
            int offered = countOffered(kind, looking);
            if (howMany == 0) {
                howMany = offered;
            }
            data = looking;
            looking = lookedUp(parts, ++at, resolveGetWord);
        }
        if (looking instanceof IntegerValue where) {
            position = (int) Math.max(1, where.magnitude());
            looking = lookedUp(parts, ++at, resolveGetWord);
        } else if (looking instanceof DecimalValue where) {
            position = (int) Math.max(1, (long) where.quantity());
            looking = lookedUp(parts, ++at, resolveGetWord);
        }
        if (looking != null) {
            return Optional.empty();
        }
        return filled(kind, howMany, data, position);
    }

    private static Value lookedUp(List<Value> parts, int at,
            UnaryOperator<Value> resolveGetWord) {

        if (at >= parts.size()) {
            return null;
        }
        Value written = parts.get(at);
        return written instanceof WordValue word && word.datatype() == Datatype.GET_WORD
                ? resolveGetWord.apply(written)
                : written;
    }

    private static int countOffered(VectorKind kind, Value data) {
        if (data instanceof BlockValue block) {
            return block.lengthFromHere();
        }
        return ((BinaryValue) data).lengthFromHere() / kind.bytes();
    }

    /**
     * A vector of a stated size, filled from data that may be shorter or
     * longer.
     *
     * <p>Shorter leaves zeros behind it and longer is ignored, because the C
     * writes into a series whose tail was already set from the size: the extra
     * values land in slack the vector does not count.
     */
    private static Optional<VectorValue> filled(VectorKind kind, int howMany, Value data,
            int position) {

        VectorStorage storage = new VectorStorage(kind, howMany);
        if (data instanceof BlockValue block) {
            List<Value> numbers = block.remaining();
            for (int at = 0; at < Math.min(numbers.size(), howMany); at++) {
                try {
                    storage.set(at + 1, kind.storedForm(numbers.get(at)));
                } catch (IllegalArgumentException notANumber) {
                    return Optional.empty();
                }
            }
        } else if (data instanceof BinaryValue bytes) {
            byte[] octets = bytes.octetsFromHere();
            int fitting = Math.min(howMany, octets.length / kind.bytes());
            for (int at = 0; at < fitting; at++) {
                storage.set(at + 1, kind.fromOctets(octets, at * kind.bytes()));
            }
        }
        if (position > storage.length() + 1) {
            return Optional.empty();
        }
        return Optional.of(new VectorValue(storage, position));
    }

    private static VectorValue emptyOf(VectorKind kind) {
        return new VectorValue(new VectorStorage(kind, 0), 1);
    }

    /** {@code make vector! 4}, which is four signed 32-bit zeros. */
    public static VectorValue ofSize(int howMany) {
        return new VectorValue(new VectorStorage(WHEN_NOTHING_SAYS_OTHERWISE, howMany), 1);
    }

    /** {@code to vector! #{01FF}}, which reads the bytes as unsigned octets. */
    public static VectorValue ofOctets(BinaryValue bytes) {
        byte[] octets = bytes.octetsFromHere();
        VectorStorage storage = new VectorStorage(VectorKind.UINT8, octets.length);
        for (int at = 0; at < octets.length; at++) {
            storage.set(at + 1, Byte.toUnsignedLong(octets[at]));
        }
        return new VectorValue(storage, 1);
    }
}
