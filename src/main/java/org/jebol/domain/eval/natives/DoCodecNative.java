package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Codecs;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class DoCodecNative extends DefaultNative {

    private static final String CODEC = "codec";

    private final Codecs codecs = new Codecs();

    @Override
    public String nativeName() {
        return "do-codec";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("handle", Set.of(HandleValue.TYPE)),
                Parameter.of("action", Set.of(WordValue.TYPE)),
                Parameter.of("data", Set.of(BinaryValue.TYPE, ImageValue.TYPE, StringValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> runCodec(
                (HandleValue) arguments.get(0), (AnyWordValue) arguments.get(1), arguments.get(2));
    }

    private Value runCodec(HandleValue handle, AnyWordValue action, Value data) {
        if (!handle.typeName().equals(CODEC)) {
            throw Raised.of(EvaluationFailure.INVALID_HANDLE);
        }
        Codecs.Action asked = readActionAskedBy(action);
        refuseDataActionCannotTake(asked, data);
        Codecs.Answer answered = codecs.run(((AnyWordValue) handle.payload()).canonical(), asked, data);
        if (answered.error() != 0 && answered.kind() != Codecs.Answer.Kind.CHECK) {
            throw Raised.of(EvaluationFailure.BAD_MEDIA);
        }
        return answered.value();
    }

    private Codecs.Action readActionAskedBy(AnyWordValue action) {
        return switch (action.canonical()) {
            case "identify" -> Codecs.Action.IDENTIFY;
            case "decode" -> Codecs.Action.DECODE;
            case "encode" -> Codecs.Action.ENCODE;
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, action);
        };
    }

    private void refuseDataActionCannotTake(Codecs.Action asked, Value data) {
        boolean encodes = asked == Codecs.Action.ENCODE;
        if (encodes ? !(data instanceof ImageValue) : !(data instanceof BinaryValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, data);
        }
    }
}
