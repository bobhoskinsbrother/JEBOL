package org.jebol.tcp;

import org.jebol.domain.host.NetworkPort;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public final class ACannedPeer implements NetworkPort {

    public static final String ITS_ADDRESS = "203.0.113.1";

    private final Deque<byte[]> replies = new ArrayDeque<>();
    private boolean closesWhenDone;
    private final List<String> namesLookedUp = new ArrayList<>();
    private final List<String> addressesReached = new ArrayList<>();
    private final List<String> whatItWasSent = new ArrayList<>();

    public ACannedPeer willReply(String text) {
        replies.add(text.getBytes(StandardCharsets.ISO_8859_1));
        return this;
    }

    public ACannedPeer closingOnceAllIsSaid() {
        closesWhenDone = true;
        return this;
    }

    public synchronized List<String> namesLookedUp() {
        return List.copyOf(namesLookedUp);
    }

    public synchronized List<String> addressesReached() {
        return List.copyOf(addressesReached);
    }

    public synchronized List<String> whatItWasSent() {
        return List.copyOf(whatItWasSent);
    }

    @Override
    public Optional<String> resolveFirstIpv4Address(String hostName) {
        return Optional.of(ITS_ADDRESS);
    }

    @Override
    public Optional<String> resolveHostName(String ipv4Address) {
        return Optional.empty();
    }

    @Override
    public String hostName() {
        return "a-canned-peer";
    }

    @Override
    public Optional<String> asciiHostName(String hostName) {
        return Optional.of(hostName);
    }

    @Override
    public synchronized void lookUp(String hostName, Lookup answer) {
        namesLookedUp.add(hostName);
        answer.found(ITS_ADDRESS);
    }

    @Override
    public synchronized Connection connect(String address, int portNumber, ConnectionEvents events) {
        addressesReached.add(address + ":" + portNumber);
        events.connected();
        return new Connection() {

            @Override
            public void read() {
                byte[] next = takeNextReply();
                if (next != null) {
                    events.arrived(next);
                } else if (closesWhenDone) {
                    events.closed();
                }
            }

            @Override
            public void write(byte[] bytes) {
                record(bytes);
                events.wrote();
            }

            @Override
            public void close() {
            }

            @Override
            public Endpoints endpoints() {
                return new Endpoints("192.0.2.1", 49152, ITS_ADDRESS, portNumber);
            }
        };
    }

    @Override
    public Listener listen(int portNumber, Accepting accepting) {
        throw new Refused("no-connect", "a canned peer takes no clients", String.valueOf(portNumber));
    }

    @Override
    public Datagrams bindTo(int portNumber) {
        throw new Refused("no-connect", "a canned peer carries no datagrams", String.valueOf(portNumber));
    }

    private synchronized byte[] takeNextReply() {
        return replies.pollFirst();
    }

    private synchronized void record(byte[] bytes) {
        whatItWasSent.add(new String(bytes, StandardCharsets.ISO_8859_1));
    }
}
