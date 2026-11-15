package org.jebol.adapter.web;

import org.jebol.domain.host.MidiDevice;
import org.jebol.domain.host.MidiDevices;
import org.jebol.domain.host.MidiRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageMidiDevicesTest {

    private final AScriptedPage page = new AScriptedPage();
    private final AtomicInteger pulls = new AtomicInteger();
    private final PageMidiDevices devices = new PageMidiDevices(page, pulls::incrementAndGet);

    @Test
    @DisplayName("the instruments are the ones the page's MIDI access lists, inputs and outputs apart")
    void listsWhatThePageReaches() {
        page.answering("""
                {"ask":1,"answer":"given","inputs":["Keys"],"outputs":["Synth","Drums"]}""", """
                {"ask":2,"answer":"given","inputs":["Keys"],"outputs":["Synth","Drums"]}""");

        assertThat(devices.inputs()).containsExactly("Keys");
        assertThat(devices.outputs()).containsExactly("Synth", "Drums");
        assertThat(page.writeAsk(1)).isEqualTo("""
                {"type":"ask","ask":1,"kind":"midi-ports","gesture":false}""");
    }

    @Test
    @DisplayName("nothing plugged in is two empty lists, which is not the same answer as being denied")
    void listsNothingPluggedIn() {
        page.answering("""
                {"ask":1,"answer":"given","inputs":[],"outputs":[]}""");

        assertThat(devices.inputs()).isEmpty();
    }

    @Test
    @DisplayName("a denied permission is the operator declining")
    void aDenialIsDeclining() {
        page.answering("""
                {"ask":1,"answer":"declined"}""");

        assertThatThrownBy(devices::inputs).isInstanceOf(MidiDevices.Declined.class);
    }

    @Test
    @DisplayName("a browser with no Web MIDI refuses, naming why")
    void aBrowserWithoutMidiRefuses() {
        page.answering("""
                {"ask":1,"answer":"refused","why":"this browser has no Web MIDI"}""");

        assertThatThrownBy(devices::outputs)
                .isInstanceOf(MidiDevices.Refused.class)
                .hasMessage("this browser has no Web MIDI");
    }

    @Test
    @DisplayName("opening a route names the positions the script chose, and nought for the side it left out")
    void opensARoute() {
        page.answering("""
                {"ask":1,"answer":"given","connection":3}""");

        devices.connect(new MidiRoute(Optional.empty(), Optional.of(new MidiDevice(2))));

        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"midi-open","gesture":false,"input":0,"output":2}""");
    }

    @Test
    @DisplayName("a message sent goes to the connection the page opened, as bytes")
    void sendsAMessage() {
        page.answering("""
                {"ask":1,"answer":"given","connection":3}""", """
                {"ask":2,"answer":"given"}""");
        MidiDevices.Connection connection =
                devices.connect(new MidiRoute(Optional.empty(), Optional.of(new MidiDevice(1))));

        connection.send(new byte[] {(byte) 0x90, 60, 127});

        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":2,"kind":"midi-send","gesture":false,"connection":3,"octets":"kDx/"}""");
    }

    @Test
    @DisplayName("what arrived for a connection is read once, after the page's postings are taken in")
    void readsWhatArrivedOnce() {
        page.answering("""
                {"ask":1,"answer":"given","connection":3}""");
        MidiDevices.Connection connection =
                devices.connect(new MidiRoute(Optional.of(new MidiDevice(1)), Optional.empty()));
        devices.takeArrival(3, new byte[] {(byte) 0x90, 60, 1});
        devices.takeArrival(4, new byte[] {9});
        devices.takeArrival(3, new byte[] {(byte) 0x80, 60, 0});

        assertThat(connection.arrivedSinceLastRead()).containsExactly(0x90, 60, 1, 0x80, 60, 0);
        assertThat(connection.arrivedSinceLastRead()).isEmpty();
        assertThat(pulls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("closing tells the page to close what it opened")
    void closes() {
        page.answering("""
                {"ask":1,"answer":"given","connection":5}""", """
                {"ask":2,"answer":"given"}""");
        MidiDevices.Connection connection =
                devices.connect(new MidiRoute(Optional.of(new MidiDevice(1)), Optional.empty()));

        connection.close();

        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":2,"kind":"midi-close","gesture":false,"connection":5}""");
    }

    @Test
    @DisplayName("an open the page refused is refused with the device number the actor reports, and a denial declines")
    void refusedAndDeclinedOpens() {
        page.answering("""
                {"ask":1,"answer":"refused","why":"no such port"}""", """
                {"ask":2,"answer":"declined"}""", """
                {"ask":3,"answer":"given"}""");
        MidiRoute route = new MidiRoute(Optional.of(new MidiDevice(1)), Optional.empty());

        assertThatThrownBy(() -> devices.connect(route))
                .isInstanceOf(MidiDevices.Refused.class)
                .satisfies(refused -> assertThat(((MidiDevices.Refused) refused).devicesOwnNumber()).isEqualTo(3));
        assertThatThrownBy(() -> devices.connect(route)).isInstanceOf(MidiDevices.Declined.class);
        assertThatThrownBy(() -> devices.connect(route)).isInstanceOf(MidiDevices.Refused.class);
    }
}
