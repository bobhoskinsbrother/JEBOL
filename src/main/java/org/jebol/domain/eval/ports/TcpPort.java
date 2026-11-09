package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.Postbox;
import org.jebol.domain.host.NetworkPort;
import org.jebol.domain.host.PausePort;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.GetWordValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TcpPort {

    private static final int CLIENT_DEFAULT_PORT = 80;
    private static final int LISTENER_DEFAULT_PORT = 8000;
    private static final int OPEN_FAILED = -12;

    public PortValue open(PortValue port, Evaluator evaluator) {
        Optional<Connecting> held = findConnectingBehind(port);
        if (held.isPresent() && held.get().isOpen()) {
            return connectLookedUp(port, held.get(), evaluator);
        }
        Connecting connecting = new Connecting();
        port.setField("state", JavaObjectValue.of(connecting));
        Value host = readSpecField(port, "host");
        NetworkPort network = evaluator.ports().network();
        switch (host) {
            case AnyStringValue named when !named.text().isEmpty() -> lookUp(port, connecting, named.text(), evaluator);
            case TupleValue address -> {
                connecting.markOpen();
                connecting.address = writeDotted(address);
                connectLookedUp(port, connecting, evaluator);
            }
            default -> {
                int number = readPortNumber(port, LISTENER_DEFAULT_PORT);
                connecting.listener = runThroughNetwork(() -> network.listen(number, client -> {
                    PortEvents events = new PortEvents(evaluator);
                    connecting.accepted.add(new Accepted(client, events));
                    events.post(port, "accept");
                    return events;
                }));
                connecting.markOpen();
            }
        }
        return port;
    }

    private void lookUp(PortValue port, Connecting connecting, String written, Evaluator evaluator) {
        NetworkPort network = evaluator.ports().network();
        connecting.markOpen();
        Optional<String> ascii = runThroughNetwork(() -> network.asciiHostName(written));
        PortEvents events = new PortEvents(evaluator);
        if (ascii.isEmpty()) {
            events.post(port, "error");
            return;
        }
        runThroughNetwork(() -> {
            network.lookUp(ascii.get(), new NetworkPort.Lookup() {

                @Override
                public void found(String ipv4Address) {
                    events.post(port, "lookup", () -> connecting.address = ipv4Address);
                }

                @Override
                public void missing(String why) {
                    events.post(port, "error");
                }
            });
            return Boolean.TRUE;
        });
    }

    private PortValue connectLookedUp(PortValue port, Connecting connecting, Evaluator evaluator) {
        if (connecting.address == null || connecting.connection != null || connecting.listener != null) {
            return port;
        }
        PortEvents events = new PortEvents(evaluator);
        events.owner = port;
        int number = readPortNumber(port, CLIENT_DEFAULT_PORT);
        connecting.connection = runThroughNetwork(() -> evaluator.ports().network().connect(
                connecting.address, number, events.reportingConnectionTo(connecting)));
        return port;
    }

    public Value read(PortValue port) {
        Connecting connecting = requireConnected(port);
        connecting.connection.read();
        return port;
    }

    public Value write(PortValue port, Value data) {
        Connecting connecting = requireConnected(port);
        port.setField("data", data);
        connecting.connection.write(data.asOctets());
        return port;
    }

    public Value close(PortValue port) {
        findConnectingBehind(port).ifPresent(Connecting::close);
        return port;
    }

    public boolean isConnectedOrListening(PortValue port) {
        return findConnectingBehind(port).map(Connecting::isConnectedOrListening).orElse(false);
    }

    public static boolean isTcp(Value value) {
        return value instanceof PortValue port && port.schemeName().equals("tcp");
    }

    public static Value takeAccepted(PortValue listening, Value position) {
        if (!(position instanceof IntegerValue(long index)) || index != 1) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, position);
        }
        return takeAccepted(listening);
    }

    private static Value takeAccepted(PortValue listening) {
        Connecting connecting = findConnectingBehind(listening)
                .filter(found -> found.listener != null)
                .orElseThrow(() -> Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(1)));
        Accepted next = connecting.accepted.poll();
        if (next == null) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(1));
        }
        PortValue client = copyPort(listening);
        client.setField("data", NoneValue.none());
        Connecting clientsState = new Connecting();
        clientsState.markOpen();
        clientsState.connection = next.connection();
        clientsState.connected = true;
        client.setField("state", JavaObjectValue.of(clientsState));
        next.events().owner = client;
        next.events().reportTo(clientsState);
        return client;
    }

    public Value query(PortValue port, Value field) {
        Connecting connecting = findConnectingBehind(port)
                .orElseThrow(() -> Raised.of(EvaluationFailure.NOT_OPEN, StringValue.of(port.schemeName())));
        NetworkPort.Endpoints endpoints = connecting.endpoints();
        if (field instanceof AnyWordValue word) {
            return answerField(endpoints, word.canonical(), field);
        }
        if (field instanceof AnyBlockValue asked) {
            List<Value> answers = new ArrayList<>();
            for (Value each : asked.remaining()) {
                if (!(each instanceof AnyWordValue word)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, each);
                }
                if (!(each instanceof GetWordValue)) {
                    answers.add(SetWordValue.of(word.canonical()));
                }
                answers.add(answerField(endpoints, word.canonical(), each));
            }
            return BlockValue.of(answers);
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, field);
    }

    private Value answerField(NetworkPort.Endpoints endpoints, String name, Value asked) {
        return switch (name) {
            case "remote-ip" -> readDotted(endpoints.remoteAddress());
            case "remote-port" -> IntegerValue.of(endpoints.remotePort());
            case "local-ip" -> readDotted(endpoints.localAddress());
            case "local-port" -> IntegerValue.of(endpoints.localPort());
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        };
    }

    private Connecting requireConnected(PortValue port) {
        Connecting connecting = findConnectingBehind(port)
                .filter(Connecting::isOpen)
                .orElseThrow(() -> Raised.of(EvaluationFailure.NOT_OPEN, StringValue.of(port.schemeName())));
        if (!connecting.connected) {
            throw Raised.of(EvaluationFailure.NOT_CONNECTED, port);
        }
        return connecting;
    }

    private static Optional<Connecting> findConnectingBehind(PortValue port) {
        return port.readFieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof Connecting connecting
                ? Optional.of(connecting)
                : Optional.empty();
    }

    private static PortValue copyPort(PortValue port) {
        return new PortValue(((ObjectValue) new ObjectValue(port.context()).copy(false, Set.of())).context());
    }

    private static Value readSpecField(PortValue port, String name) {
        if (port.readFieldValue("spec") instanceof ObjectValue(Context context) && context.holds(name)) {
            return context.ownSlotFor(name).value();
        }
        return NoneValue.none();
    }

    private static int readPortNumber(PortValue port, int otherwise) {
        return readSpecField(port, "port") instanceof IntegerValue(long magnitude) ? (int) magnitude : otherwise;
    }

    private static String writeDotted(TupleValue address) {
        return String.join(".", Arrays.stream(address.segments()).mapToObj(String::valueOf).toList());
    }

    private static Value readDotted(String address) {
        try {
            return TupleValue.of(Arrays.stream(address.split("\\.")).mapToInt(Integer::parseInt).toArray());
        } catch (NumberFormatException notIpv4) {
            return TupleValue.of(0, 0, 0, 0);
        }
    }

    private static <T> T runThroughNetwork(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (NetworkPort.Refused refused) {
            throw new Raised(ErrorValue.of(ErrorCategory.ACCESS,
                    refused.errorId(), refused.getMessage(),
                    StringValue.of(refused.subject()),
                    IntegerValue.of(OPEN_FAILED)));
        }
    }

    private static void appendToData(PortValue port, byte[] bytes) {
        if (!(port.readFieldValue("data") instanceof BinaryValue held)) {
            port.setField("data", BinaryValue.ofBytes(bytes));
            return;
        }
        BinaryValue arrived = BinaryValue.ofBytes(bytes);
        for (int at = arrived.index(); at <= arrived.storageLength(); at++) {
            held.storage().append(arrived.storage().at(at));
        }
    }

    private record Accepted(NetworkPort.Connection connection, PortEvents events) {
    }

    private static final class Connecting {

        private volatile String address;
        private volatile NetworkPort.Connection connection;
        private volatile NetworkPort.Listener listener;
        private final Queue<Accepted> accepted = new ConcurrentLinkedQueue<>();
        private boolean open;
        private boolean connected;

        private void markOpen() {
            open = true;
        }

        private boolean isOpen() {
            return open;
        }

        private boolean isConnectedOrListening() {
            return open && (connected || listener != null);
        }

        private void close() {
            if (!open) {
                return;
            }
            open = false;
            connected = false;
            if (connection != null) {
                connection.close();
            }
            if (listener != null) {
                listener.close();
            }
        }

        private NetworkPort.Endpoints endpoints() {
            if (listener != null) {
                return new NetworkPort.Endpoints("0.0.0.0", listener.localPort(), "0.0.0.0", 0);
            }
            if (connection != null) {
                return connection.endpoints();
            }
            return new NetworkPort.Endpoints("0.0.0.0", 0, address == null ? "0.0.0.0" : address, 0);
        }
    }

    private static final class PortEvents implements NetworkPort.ConnectionEvents {

        private final Postbox postbox;
        private final PausePort pause;
        private volatile PortValue owner;
        private volatile Connecting state;

        private PortEvents(Evaluator evaluator) {
            this.postbox = evaluator.postbox();
            this.pause = evaluator.ports().pause();
        }

        private NetworkPort.ConnectionEvents reportingConnectionTo(Connecting connecting) {
            state = connecting;
            return this;
        }

        private void reportTo(Connecting connecting) {
            state = connecting;
        }

        private void post(PortValue port, String eventType) {
            post(port, eventType, () -> {
            });
        }

        private void post(PortValue port, String eventType, Runnable beforeQueueing) {
            postbox.post(port, eventType, beforeQueueing);
            pause.wake();
        }

        @Override
        public void connected() {
            post(owner, "connect", () -> state.connected = true);
        }

        @Override
        public void arrived(byte[] bytes) {
            PortValue port = owner;
            post(port, "read", () -> appendToData(port, bytes));
        }

        @Override
        public void wrote() {
            PortValue port = owner;
            post(port, "wrote", () -> port.setField("data", NoneValue.none()));
        }

        @Override
        public void closed() {
            post(owner, "close");
        }

        @Override
        public void failed(String why) {
            post(owner, "error");
        }
    }
}
