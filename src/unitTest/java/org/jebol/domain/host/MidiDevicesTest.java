package org.jebol.domain.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MidiDevicesTest {

    private static final int A_DEVICE_THE_HOST_DOES_NOT_LIST = 3;

    @Nested
    @DisplayName("a device is a position counted from one")
    class ADevice {

        @ParameterizedTest(name = "{0} is a position")
        @ValueSource(ints = {1, 2, Integer.MAX_VALUE})
        void fromOneUp(int position) {
            assertThat(new MidiDevice(position).position()).isEqualTo(position);
        }

        @ParameterizedTest(name = "{0} is not")
        @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
        void belowOneIsRefused(int position) {
            assertThatThrownBy(() -> new MidiDevice(position))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(position));
        }

        @ParameterizedTest(name = "position {0} among {1} listed: {2}")
        @CsvSource({
                "1, 1, true",
                "1, 2, true",
                "2, 2, true",
                "3, 2, false",
                "1, 0, false",
        })
        void isListedUpToTheLength(int position, int howManyListed, boolean listed) {
            List<String> names = Collections.nCopies(howManyListed, "a device");
            assertThat(new MidiDevice(position).isListedAmong(names)).isEqualTo(listed);
        }
    }

    @Nested
    @DisplayName("a route names an input, an output, or both")
    class ARoute {

        @Test
        @DisplayName("an input alone")
        void anInputAlone() {
            assertThat(new MidiRoute(Optional.of(new MidiDevice(1)), Optional.empty()).input())
                    .contains(new MidiDevice(1));
        }

        @Test
        @DisplayName("an output alone")
        void anOutputAlone() {
            assertThat(new MidiRoute(Optional.empty(), Optional.of(new MidiDevice(2))).output())
                    .contains(new MidiDevice(2));
        }

        @Test
        @DisplayName("and naming neither is refused")
        void neitherIsRefused() {
            assertThatThrownBy(() -> new MidiRoute(Optional.empty(), Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("a host with no MIDI lists nothing and connects nothing")
    class NoDevices {

        @Test
        @DisplayName("no inputs")
        void noInputs() {
            assertThat(new NoMidiDevices().inputs()).isEmpty();
        }

        @Test
        @DisplayName("no outputs")
        void noOutputs() {
            assertThat(new NoMidiDevices().outputs()).isEmpty();
        }

        @Test
        @DisplayName("and a connection is refused as a device it does not list")
        void aConnectionIsRefused() {
            assertThatThrownBy(() -> new NoMidiDevices().connect(
                    new MidiRoute(Optional.of(new MidiDevice(1)), Optional.empty())))
                    .isInstanceOfSatisfying(MidiDevices.Refused.class, refused ->
                            assertThat(refused.devicesOwnNumber()).isEqualTo(A_DEVICE_THE_HOST_DOES_NOT_LIST));
        }
    }
}
