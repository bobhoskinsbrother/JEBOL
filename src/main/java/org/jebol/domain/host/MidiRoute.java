package org.jebol.domain.host;

import java.util.Optional;

public record MidiRoute(Optional<MidiDevice> input, Optional<MidiDevice> output) {

    public MidiRoute {
        if (input.isEmpty() && output.isEmpty()) {
            throw new IllegalArgumentException("a MIDI route names an input, an output, or both");
        }
    }
}
