package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Codecs;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class DoCodecNative extends DefaultNative {

    private static final String A_CODEC = "codec";

    private final Codecs codecs = new Codecs();

    @Override
    public String name() {
        return "do-codec";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("handle", Set.of(Datatype.HANDLE)),
                Parameter.required("action", Set.of(Datatype.WORD)),
                Parameter.required("data", Set.of(Datatype.BINARY, Datatype.IMAGE, Datatype.STRING)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> ranCodec(
                (HandleValue) arguments.get(0), (WordValue) arguments.get(1), arguments.get(2));
    }

    private Value ranCodec(HandleValue handle, WordValue action, Value data) {
        if (!handle.typeName().equals(A_CODEC)) {
            throw Raised.of(EvaluationFailure.INVALID_HANDLE,
                    "a codec was wanted, not a " + handle.typeName() + " handle");
        }
        Codecs.Action asked = theActionAskedBy(action);
        refuseDataTheActionCannotTake(asked, data);
        Codecs.Answer answered = codecs.run(((WordValue) handle.payload()).canonical(), asked, data);
        if (answered.error() != 0 && answered.kind() != Codecs.Answer.Kind.CHECK) {
            throw Raised.of(EvaluationFailure.BAD_MEDIA,
                    action.spelling() + " is not something this codec does");
        }
        return answered.value();
    }

    private Codecs.Action theActionAskedBy(WordValue action) {
        return switch (action.canonical()) {
            case "identify" -> Codecs.Action.IDENTIFY;
            case "decode" -> Codecs.Action.DECODE;
            case "encode" -> Codecs.Action.ENCODE;
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, action.spelling());
        };
    }

    private void refuseDataTheActionCannotTake(Codecs.Action asked, Value data) {
        if (asked == Codecs.Action.ENCODE && !(data instanceof ImageValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "encoding takes an image, not a " + data.datatype().literalSpelling());
        }
        if (asked != Codecs.Action.ENCODE && !(data instanceof BinaryValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "decoding takes a binary, not a " + data.datatype().literalSpelling());
        }
    }
}
