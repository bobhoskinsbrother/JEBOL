package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.QueriedFields;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.MidiDevice;
import org.jebol.domain.host.MidiDevices;
import org.jebol.domain.host.MidiRoute;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.EndValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.EventCatalogue;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MidiSchemeActor extends DefaultNative implements ServesPortActions {

    private static final String ARGUMENT_NOTHING_CAN_FILL = "internal";

    private static final int PORT_IS_NOT_OPEN = 12;

    private static final int NO_DEVICE_IS_NAMED = 1;

    private static final int DEVICE_HOST_DOES_NOT_LIST = 3;

    private static final long NO_DEVICE_AT_ALL = 0;

    private static final String DEVICES_IN = "devices-in";

    private static final String DEVICES_OUT = "devices-out";

    private final GrantedServices granted;

    public MidiSchemeActor(GrantedServices granted) {
        this.granted = granted;
        declare(BlockValue.of(List.of(NoneValue.none(), WordValue.of(ARGUMENT_NOTHING_CAN_FILL))),
                parametersAsWritten());
    }

    @Override
    public String nativeName() {
        return "the midi actor";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of(ARGUMENT_NOTHING_CAN_FILL, Set.of(EndValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw Raised.of(EvaluationFailure.CANNOT_USE, nativeName());
        };
    }

    @Override
    public Optional<Value> answers(String action, List<Value> arguments,
            Set<String> refinements, Evaluator evaluator) {

        if (!(arguments.getFirst() instanceof PortValue port)) {
            return Optional.empty();
        }
        return Optional.of(switch (action) {
            case "open" -> open(port, refinements, evaluator);
            case "close" -> close(port, evaluator);
            case "read" -> collectWhatHasArrived(port);
            case "write" -> send(port, arguments.get(1));
            case "query" -> answerQuery(arguments.get(1), evaluator);
            case "open?" -> LogicValue.of(findTheConnectionBehind(port).isPresent());
            case "update" -> NoneValue.none();
            default -> throw Raised.of(EvaluationFailure.NO_PORT_ACTION, SetWordValue.of(action));
        });
    }

    private Value open(PortValue port, Set<String> refinements, Evaluator evaluator) {
        granted.require(HostService.MIDI);
        ObjectValue spec = readTheSpecOf(port);
        if (refinements.contains("write") && !refinements.contains("read")
                && !(spec.readFieldValue("device-out") instanceof IntegerValue)) {
            makeOneDeviceOutput(spec);
        }
        MidiDevices devices = evaluator.ports().midiDevices();
        MidiRoute route = findRouteSpecNames(port, spec, devices);
        try {
            port.setField("state", JavaObjectValue.of(devices.connect(route)));
        } catch (MidiDevices.Refused refused) {
            throw refuseToOpen(port, refused.devicesOwnNumber());
        }
        queueWhatHappenedTo(port, "open", evaluator);
        return port;
    }

    private void makeOneDeviceOutput(ObjectValue spec) {
        long asAnInput = spec.readFieldValue("device-in") instanceof IntegerValue(long number)
                ? number
                : NO_DEVICE_AT_ALL;
        spec.context().register("device-in", NoneValue.none());
        spec.context().register("device-out", IntegerValue.of(asAnInput));
    }

    private MidiRoute findRouteSpecNames(PortValue port, ObjectValue spec, MidiDevices devices) {
        long input = readNumberIn(spec.readFieldValue("device-in"));
        long output = readNumberIn(spec.readFieldValue("device-out"));
        if (input == NO_DEVICE_AT_ALL && output == NO_DEVICE_AT_ALL) {
            throw refuseToOpen(port, NO_DEVICE_IS_NAMED);
        }
        Optional<MidiDevice> listedInput = findTheListedDevice(port, input, devices.inputs());
        Optional<MidiDevice> listedOutput = findTheListedDevice(port, output, devices.outputs());
        return new MidiRoute(listedInput, listedOutput);
    }

    private long readNumberIn(Value field) {
        return field instanceof IntegerValue(long number) ? number : NO_DEVICE_AT_ALL;
    }

    private Optional<MidiDevice> findTheListedDevice(PortValue port, long number, List<String> listed) {
        if (number == NO_DEVICE_AT_ALL) {
            return Optional.empty();
        }
        if (number < 0 || number > Integer.MAX_VALUE) {
            throw refuseToOpen(port, DEVICE_HOST_DOES_NOT_LIST);
        }
        MidiDevice named = new MidiDevice((int) number);
        if (!named.isListedAmong(listed)) {
            throw refuseToOpen(port, DEVICE_HOST_DOES_NOT_LIST);
        }
        return Optional.of(named);
    }

    private Raised refuseToOpen(PortValue port, int devicesOwnNumber) {
        return Raised.of(EvaluationFailure.CANNOT_OPEN, readTheReferenceOf(port), IntegerValue.of(devicesOwnNumber));
    }

    private Value close(PortValue port, Evaluator evaluator) {
        MidiDevices.Connection connection = findTheOpenConnectionBehind(port);
        connection.close();
        port.setField("state", NoneValue.none());
        queueWhatHappenedTo(port, "close", evaluator);
        return port;
    }

    private Value collectWhatHasArrived(PortValue port) {
        granted.require(HostService.MIDI);
        MidiDevices.Connection connection = findTheOpenConnectionBehind(port);
        try {
            BinaryValue arrived = BinaryValue.ofBytes(connection.arrivedSinceLastRead());
            port.setField("data", arrived);
            return arrived;
        } catch (MidiDevices.Refused refused) {
            throw Raised.of(EvaluationFailure.READ_ERROR, readTheReferenceOf(port),
                    IntegerValue.of(refused.devicesOwnNumber()));
        }
    }

    private Value send(PortValue port, Value data) {
        granted.require(HostService.MIDI);
        MidiDevices.Connection connection = findTheOpenConnectionBehind(port);
        if (!(data instanceof BinaryValue message)) {
            return port;
        }
        try {
            connection.send(message.octetsFromHere());
        } catch (MidiDevices.Refused refused) {
            throw Raised.of(EvaluationFailure.WRITE_ERROR, readTheReferenceOf(port),
                    IntegerValue.of(refused.devicesOwnNumber()));
        }
        return port;
    }

    private Value answerQuery(Value field, Evaluator evaluator) {
        if (!(field instanceof NoneValue)) {
            granted.require(HostService.MIDI);
        }
        return new DevicesHostLists(evaluator.ports().midiDevices()).answer(field, evaluator);
    }

    private MidiDevices.Connection findTheOpenConnectionBehind(PortValue port) {
        return findTheConnectionBehind(port).orElseThrow(() -> Raised.of(EvaluationFailure.NOT_OPEN,
                readTheReferenceOf(port), IntegerValue.of(PORT_IS_NOT_OPEN)));
    }

    private Optional<MidiDevices.Connection> findTheConnectionBehind(PortValue port) {
        if (port.readFieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof MidiDevices.Connection connection) {
            return Optional.of(connection);
        }
        return Optional.empty();
    }

    private ObjectValue readTheSpecOf(PortValue port) {
        if (!(port.readFieldValue("spec") instanceof ObjectValue spec)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, port);
        }
        return spec;
    }

    private Value readTheReferenceOf(PortValue port) {
        return readTheSpecOf(port).readFieldValue("ref");
    }

    private void queueWhatHappenedTo(PortValue port, String happened, Evaluator evaluator) {
        evaluator.queueOnSystemPort(new EventValue(EventCatalogue.findTheTypeIndexOf(happened).orElseThrow(),
                Set.of(), EventValue.Model.PORT, 0, port));
    }

    private static final class DevicesHostLists extends QueriedFields {

        private final MidiDevices devices;

        DevicesHostLists(MidiDevices devices) {
            this.devices = devices;
        }

        @Override
        protected List<String> names() {
            return List.of(DEVICES_IN, DEVICES_OUT);
        }

        @Override
        protected Optional<Value> findFieldNamed(String asked) {
            return switch (asked) {
                case DEVICES_IN -> Optional.of(listTheNamesOf(devices.inputs()));
                case DEVICES_OUT -> Optional.of(listTheNamesOf(devices.outputs()));
                default -> Optional.empty();
            };
        }

        @Override
        protected String findFieldItAsksFor(AnyWordValue asked) {
            return asked.spelling();
        }

        @Override
        protected Value answerUnknownAlone(AnyWordValue asked) {
            throw cannotUse(asked, PortValue.TYPE);
        }

        private Value listTheNamesOf(List<String> listed) {
            return BlockValue.of(listed.stream().<Value>map(StringValue::of).toList());
        }
    }
}
