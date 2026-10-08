package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ChecksumNative extends EncodingNative {

    private static final String HASH_INTO_A_TABLE = "hash";

    private static final String FILE_CHECKSUM = "file-checksum";

    public ChecksumNative(Encodings encodings) {
        super(encodings);
    }

    @Override
    public String nativeName() {
        return "checksum";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("data", Set.of(Datatype.BINARY, Datatype.STRING, Datatype.FILE)),
                Parameter.required("method", Set.of(Datatype.WORD)),
                Parameter.belongingTo("with", "spec", anyStringOr(Datatype.BINARY, Datatype.INTEGER)),
                Parameter.belongingTo("part", "length", aPartLimit()));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("with", "part");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value data = arguments.getFirst();
            AnyWordValue method = (AnyWordValue) arguments.get(1);
            if (data instanceof FileValue) {
                return theContentsOfThatFileSummed(data, method, evaluator, refinements);
            }
            return summed(data, octetsWithinAnyPart(data, arguments, refinements), method,
                    arguments, refinements);
        };
    }

    private Value summed(Value data, byte[] octets, AnyWordValue method,
            List<Value> arguments, Set<String> refinements) {

        String asked = method.canonical();
        Optional<Value> spec = argumentOf("with", 0, arguments, refinements);
        if (Encodings.DIGESTS.containsKey(asked)) {
            return BinaryValue.ofBytes(spec
                    .map(key -> keyedDigestOf(octets, asked, key))
                    .orElseGet(() -> encodings.digestOf(octets, asked)));
        }
        if (Encodings.CYCLIC.contains(asked)) {
            if (spec.isPresent()) {
                throw Raised.of(EvaluationFailure.BAD_REFINES);
            }
            return IntegerValue.of(encodings.cyclicOf(octets, asked));
        }
        if (HASH_INTO_A_TABLE.equals(asked)) {
            return IntegerValue.of(hashedIntoATable(data,
                    spec.orElseThrow(() -> Raised.of(EvaluationFailure.MISSING_ARG))));
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, method);
    }

    private byte[] keyedDigestOf(byte[] octets, String method, Value key) {
        if (key instanceof IntegerValue) {
            throw Raised.of(EvaluationFailure.BAD_REFINE, key);
        }
        return encodings.keyedDigestOf(octets, method, key.asOctets());
    }

    private long hashedIntoATable(Value value, Value size) {
        if (!(size instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.BAD_REFINE, size);
        }
        long slots = Math.max(1, magnitude) & 0xFFFFFFFFL;
        long hash = Integer.toUnsignedLong(hashOf(value));
        return slots == 0 ? hash : hash % slots;
    }

    private int hashOf(Value value) {
        return value instanceof BinaryValue bytes
                ? encodings.murmurOf(bytes.octetsFromHere())
                : encodings.caseFoldedHashOf(value.asOctets()) ^ value.datatype().ordinal();
    }

    private Value theContentsOfThatFileSummed(
            Value file, AnyWordValue method, Evaluator evaluator, Set<String> refinements) {

        if (!Encodings.DIGESTS.containsKey(method.canonical())) {
            throw Raised.of(EvaluationFailure.FEATURE_NA);
        }
        if (refinements.contains("part") || refinements.contains("with")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
        Context library = evaluator.systemContext();
        if (!library.knows(FILE_CHECKSUM)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA);
        }
        return evaluator.applyFunction(library.slotFor(FILE_CHECKSUM).value(),
                List.of(file, WordValue.of(method.canonical())));
    }
}
