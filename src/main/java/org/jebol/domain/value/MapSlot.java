package org.jebol.domain.value;

record MapSlot(MapValue map, Value key, Value held) implements Slot {

    @Override
    public Value value() {
        return held;
    }

    @Override
    public void setValue(Value replacement) {
        map.putUnlessKeyIsNone(key, replacement);
    }
}
