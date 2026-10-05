package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public abstract class CloakNative extends EncodingNative {

    protected CloakNative(Encodings encodings) {
        super(encodings);
    }

    abstract boolean decodes();

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("data", Set.of(Datatype.BINARY)),
                Parameter.required("key", Set.of(Datatype.STRING, Datatype.BINARY, Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BinaryValue data = (BinaryValue) arguments.getFirst();
            if (data.isProtected()) {
                throw Raised.of(EvaluationFailure.PROTECTED);
            }
            Value key = arguments.get(1);
            byte[] octets = data.octetsFromHere();
            if (!encodings.cloak(decodes(), octets, keyBytesFor(key, refinements.contains("with")))) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, key);
            }
            for (int at = 0; at < octets.length; at++) {
                data.storage().set(data.index() + at, octets[at] & 0xFF);
            }
            return data;
        };
    }

    private byte[] keyBytesFor(Value key, boolean asItStands) {
        return switch (key) {
            case IntegerValue(long magnitude) ->
                    encodings.hashedKey(Long.toString(magnitude).getBytes(StandardCharsets.UTF_8));
            case BinaryValue octets -> hashedUnless(asItStands, octets.octetsFromHere());
            default -> hashedUnless(asItStands, ((StringValue) key).text().getBytes(StandardCharsets.UTF_8));
        };
    }

    private byte[] hashedUnless(boolean asItStands, byte[] key) {
        return asItStands ? key : encodings.hashedKey(key);
    }
}
