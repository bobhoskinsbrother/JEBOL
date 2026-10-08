package org.jebol.domain.value;

import java.util.List;

class DatatypeWithNoValues extends Datatype {

    DatatypeWithNoValues(String spelling, Typeset... declaredTypesets) {
        super(spelling, declaredTypesets);
    }

    @Override
    public Value constructedFrom(List<Value> contents, Construction construction) {
        throw refusingConstruction(contents);
    }
}
