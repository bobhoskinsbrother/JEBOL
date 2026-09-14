package org.jebol.domain.date;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.Value;

public interface DateField {

    String spelling();

    Value readFrom(DateValue date);

    default DateValue writtenOn(DateValue date, Value given) {
        throw Raised.of(EvaluationFailure.BAD_PATH_SET, spelling());
    }
}
