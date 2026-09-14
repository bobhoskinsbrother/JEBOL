package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;

interface WritableDateField extends DateField {

    DateValue writtenOn(DateValue date, Assigned given);
}
