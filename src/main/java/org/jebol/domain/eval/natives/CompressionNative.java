package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

public abstract class CompressionNative extends EncodingNative {

    protected CompressionNative(Encodings encodings) {
        super(encodings);
    }

    protected String readKnownCompression(Value method, Evaluator evaluator) {
        String asked = ((AnyWordValue) method).canonical();
        if (!evaluator.compressionMethods().isKnown(asked)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, method);
        }
        return asked;
    }

    protected Raised refuseAsBadPress(CompressionRefused refused) {
        return Raised.of(EvaluationFailure.BAD_PRESS, IntegerValue.of(refused.code()));
    }
}
