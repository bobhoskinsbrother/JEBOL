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

    private static final String THE_ARGUMENT_NOTHING_CAN_FILL = "internal";

    private static final int THE_PORT_IS_NOT_OPEN = 12;

    private static final int NO_DEVICE_IS_NAMED = 1;

    private static final int A_DEVICE_THE_HOST_DOES_NOT_LIST = 3;

    private static final long NO_DEVICE_AT_ALL = 0;

    private static final String DEVICES_IN = "devices-in";

    private static final String DEVICES_OUT = "devices-out";

    private final GrantedServices granted;

    public MidiSchemeActor(GrantedServices granted) {
        this.granted = granted;
        declaredBy(BlockValue.block(List.of(NoneValue.none(), WordValue.of(THE_ARGUMENT_NOTHING_CAN_FILL))),
                parametersAsWritten());
    }

    @Override
    public String nativeName() {
        return "the midi actor";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required(THE_ARGUMENT_NOTHING_CAN_FILL, Set.of(EndValue.TYPE)));
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
            case "open" -> opened(port, refinements, evaluator);
            case "close" -> closed(port, evaluator);
            case "read" -> whatHasArrived(port);
            case "write" -> sent(port, arguments.get(1));
            case "query" -> asked(arguments.get(1), evaluator);
            case "open?" -> LogicValue.of(theConnectionBehind(port).isPresent());
            case "update" -> NoneValue.none();
            default -> throw Raised.of(EvaluationFailure.NO_PORT_ACTION, SetWordValue.of(action));
        });
    }

    private Value opened(PortValue port, Set<String> refinements, Evaluator evaluator) {
        granted.require(HostService.MIDI);
        ObjectValue spec = theSpecOf(port);
        if (refinements.contains("write") && !refinements.contains("read")
                && !(spec.fieldValue("device-out") instanceof IntegerValue)) {
            makeTheOneDeviceAnOutput(spec);
        }
        MidiDevices devices = evaluator.midiDevices();
        MidiRoute route = theRouteTheSpecNames(port, spec, devices);
        try {
            port.setField("state", JavaObjectValue.of(devices.connect(route)));
        } catch (MidiDevices.Refused refused) {
            throw refusedToOpen(port, refused.devicesOwnNumber());
        }
        queueWhatHappenedTo(port, "open", evaluator);
        return port;
    }

    private void makeTheOneDeviceAnOutput(ObjectValue spec) {
        long asAnInput = spec.fieldValue("device-in") instanceof IntegerValue(long number)
                ? number
                : NO_DEVICE_AT_ALL;
        spec.context().register("device-in", NoneValue.none());
        spec.context().register("device-out", IntegerValue.of(asAnInput));
    }

    private MidiRoute theRouteTheSpecNames(PortValue port, ObjectValue spec, MidiDevices devices) {
        long input = theNumberIn(spec.fieldValue("device-in"));
        long output = theNumberIn(spec.fieldValue("device-out"));
        if (input == NO_DEVICE_AT_ALL && output == NO_DEVICE_AT_ALL) {
            throw refusedToOpen(port, NO_DEVICE_IS_NAMED);
        }
        Optional<MidiDevice> listedInput = theListedDevice(port, input, devices.inputs());
        Optional<MidiDevice> listedOutput = theListedDevice(port, output, devices.outputs());
        return new MidiRoute(listedInput, listedOutput);
    }

    private long theNumberIn(Value field) {
        return field instanceof IntegerValue(long number) ? number : NO_DEVICE_AT_ALL;
    }

    private Optional<MidiDevice> theListedDevice(PortValue port, long number, List<String> listed) {
        if (number == NO_DEVICE_AT_ALL) {
            return Optional.empty();
        }
        if (number < 0 || number > Integer.MAX_VALUE) {
            throw refusedToOpen(port, A_DEVICE_THE_HOST_DOES_NOT_LIST);
        }
        MidiDevice named = new MidiDevice((int) number);
        if (!named.isListedAmong(listed)) {
            throw refusedToOpen(port, A_DEVICE_THE_HOST_DOES_NOT_LIST);
        }
        return Optional.of(named);
    }

    private Raised refusedToOpen(PortValue port, int devicesOwnNumber) {
        return Raised.of(EvaluationFailure.CANNOT_OPEN, theReferenceOf(port), IntegerValue.of(devicesOwnNumber));
    }

    private Value closed(PortValue port, Evaluator evaluator) {
        MidiDevices.Connection connection = theOpenConnectionBehind(port);
        connection.close();
        port.setField("state", NoneValue.none());
        queueWhatHappenedTo(port, "close", evaluator);
        return port;
    }

    private Value whatHasArrived(PortValue port) {
        granted.require(HostService.MIDI);
        MidiDevices.Connection connection = theOpenConnectionBehind(port);
        try {
            BinaryValue arrived = BinaryValue.ofBytes(connection.arrivedSinceTheLastRead());
            port.setField("data", arrived);
            return arrived;
        } catch (MidiDevices.Refused refused) {
            throw Raised.of(EvaluationFailure.READ_ERROR, theReferenceOf(port),
                    IntegerValue.of(refused.devicesOwnNumber()));
        }
    }

    private Value sent(PortValue port, Value data) {
        granted.require(HostService.MIDI);
        MidiDevices.Connection connection = theOpenConnectionBehind(port);
        if (!(data instanceof BinaryValue message)) {
            return port;
        }
        try {
            connection.send(message.octetsFromHere());
        } catch (MidiDevices.Refused refused) {
            throw Raised.of(EvaluationFailure.WRITE_ERROR, theReferenceOf(port),
                    IntegerValue.of(refused.devicesOwnNumber()));
        }
        return port;
    }

    private Value asked(Value field, Evaluator evaluator) {
        if (!(field instanceof NoneValue)) {
            granted.require(HostService.MIDI);
        }
        return new TheDevicesTheHostLists(evaluator.midiDevices()).answering(field, evaluator);
    }

    private MidiDevices.Connection theOpenConnectionBehind(PortValue port) {
        return theConnectionBehind(port).orElseThrow(() -> Raised.of(EvaluationFailure.NOT_OPEN,
                theReferenceOf(port), IntegerValue.of(THE_PORT_IS_NOT_OPEN)));
    }

    private Optional<MidiDevices.Connection> theConnectionBehind(PortValue port) {
        if (port.fieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof MidiDevices.Connection connection) {
            return Optional.of(connection);
        }
        return Optional.empty();
    }

    private ObjectValue theSpecOf(PortValue port) {
        if (!(port.fieldValue("spec") instanceof ObjectValue spec)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, port);
        }
        return spec;
    }

    private Value theReferenceOf(PortValue port) {
        return theSpecOf(port).fieldValue("ref");
    }

    private void queueWhatHappenedTo(PortValue port, String happened, Evaluator evaluator) {
        evaluator.queueOnTheSystemPort(new EventValue(EventCatalogue.typeIndexOf(happened).orElseThrow(),
                Set.of(), EventValue.Model.PORT, 0, port));
    }

    private static final class TheDevicesTheHostLists extends QueriedFields {

        private final MidiDevices devices;

        TheDevicesTheHostLists(MidiDevices devices) {
            this.devices = devices;
        }

        @Override
        protected List<String> names() {
            return List.of(DEVICES_IN, DEVICES_OUT);
        }

        @Override
        protected Optional<Value> fieldNamed(String asked) {
            return switch (asked) {
                case DEVICES_IN -> Optional.of(theNamesOf(devices.inputs()));
                case DEVICES_OUT -> Optional.of(theNamesOf(devices.outputs()));
                default -> Optional.empty();
            };
        }

        @Override
        protected String theFieldItAsksFor(AnyWordValue asked) {
            return asked.spelling();
        }

        @Override
        protected Value unknownAlone(AnyWordValue asked) {
            throw cannotUse(asked, PortValue.TYPE);
        }

        private Value theNamesOf(List<String> listed) {
            return BlockValue.block(listed.stream().<Value>map(StringValue::of).toList());
        }
    }
}
