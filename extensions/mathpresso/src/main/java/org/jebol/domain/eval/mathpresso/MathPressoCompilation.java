package org.jebol.domain.eval.mathpresso;

import java.util.List;
import java.util.Optional;

public record MathPressoCompilation(Optional<MathPressoExpression> expression, List<MathPressoMessage> messages) {
}
