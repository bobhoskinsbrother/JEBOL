package org.jebol.domain.value;

public interface Slot {

    Value value();

    void setValue(Value replacement);
}
