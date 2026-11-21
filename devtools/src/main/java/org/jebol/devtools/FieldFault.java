package org.jebol.devtools;

public record FieldFault(String field, Fault fault, String expected) {

    public String describe() {
        return field + ": expected " + expected;
    }
}
