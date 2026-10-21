package org.jebol.domain.host;

import java.util.List;

public final class NoMidiDevices implements MidiDevices {

    private static final int DEVICE_HOST_DOES_NOT_LIST = 3;

    @Override
    public List<String> inputs() {
        return List.of();
    }

    @Override
    public List<String> outputs() {
        return List.of();
    }

    @Override
    public Connection connect(MidiRoute route) {
        throw new Refused(DEVICE_HOST_DOES_NOT_LIST, "this interpreter was given no MIDI to reach");
    }
}
