package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

public abstract class CompressionNative extends EncodingNative {

    protected CompressionNative(Encodings encodings) {
        super(encodings);
    }

    protected String aKnownCompression(Value method) {
        String asked = ((WordValue) method).canonical();
        if (Encodings.COMPRESSIONS_ELSEWHERE.contains(asked)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, method);
        }
        if (!Encodings.COMPRESSIONS.contains(asked)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, method);
        }
        return asked;
    }
}
