package org.jebol.adapter.web;

import org.jebol.domain.host.MidiDevice;
import org.jebol.domain.host.MidiDevices;
import org.jebol.domain.host.MidiRoute;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PageMidiDevices implements MidiDevices {

    private static final int UNLISTED_DEVICE = 3;

    private static final int NO_DEVICE = 0;

    private final PageRoundTrip page;
    private final Runnable pullPosted;
    private final Map<Integer, byte[]> arrived = new ConcurrentHashMap<>();

    public PageMidiDevices(PageRoundTrip page, Runnable pullPosted) {
        this.page = page;
        this.pullPosted = pullPosted;
    }

    @Override
    public List<String> inputs() {
        return askForPorts().texts("inputs");
    }

    @Override
    public List<String> outputs() {
        return askForPorts().texts("outputs");
    }

    @Override
    public Connection connect(MidiRoute route) {
        PageAnswer answer = requireGiven(page.ask(new PageAsk("midi-open", false)
                .with("input", route.input().map(MidiDevice::position).orElse(NO_DEVICE))
                .with("output", route.output().map(MidiDevice::position).orElse(NO_DEVICE))));
        int connection = answer.wholeNumber("connection").orElseThrow(() ->
                new Refused(UNLISTED_DEVICE, "the page opened MIDI and named no connection"));
        arrived.put(connection, new byte[0]);
        return new PageConnection(connection);
    }

    public void takeArrival(int connection, byte[] octets) {
        arrived.computeIfPresent(connection, (number, held) -> {
            byte[] grown = Arrays.copyOf(held, held.length + octets.length);
            System.arraycopy(octets, 0, grown, held.length, octets.length);
            return grown;
        });
    }

    private PageAnswer askForPorts() {
        return requireGiven(page.ask(new PageAsk("midi-ports", false)));
    }

    private PageAnswer requireGiven(PageAnswer answer) {
        if (answer.isDeclined()) {
            throw new Declined("the operator did not allow MIDI");
        }
        if (answer.isRefused()) {
            throw new Refused(UNLISTED_DEVICE, answer.why());
        }
        return answer;
    }

    private final class PageConnection implements Connection {

        private final int number;

        private PageConnection(int number) {
            this.number = number;
        }

        @Override
        public byte[] arrivedSinceLastRead() {
            pullPosted.run();
            return Optional.ofNullable(arrived.replace(number, new byte[0])).orElse(new byte[0]);
        }

        @Override
        public void send(byte[] message) {
            requireGiven(page.ask(new PageAsk("midi-send", false)
                    .with("connection", number)
                    .withOctets("octets", message)));
        }

        @Override
        public void close() {
            arrived.remove(number);
            requireGiven(page.ask(new PageAsk("midi-close", false).with("connection", number)));
        }
    }
}
