package org.jebol.domain.date.part;

import org.jebol.domain.value.DateValue;

interface WritableDateField extends DateField {

    DateValue writeOn(DateValue date, Assigned given);
}
