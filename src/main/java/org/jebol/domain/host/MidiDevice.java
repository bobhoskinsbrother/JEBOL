package org.jebol.domain.host;

import java.util.List;

public record MidiDevice(int position) {

    private static final int FIRST_POSITION = 1;

    public MidiDevice {
        if (position < FIRST_POSITION) {
            throw new IllegalArgumentException(
                    "a MIDI device is counted from one, and " + position + " is not a position");
        }
    }

    public boolean isListedAmong(List<String> listed) {
        return position <= listed.size();
    }
}
