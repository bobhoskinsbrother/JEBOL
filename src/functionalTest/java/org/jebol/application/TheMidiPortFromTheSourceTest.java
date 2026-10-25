package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.MidiDevice;
import org.jebol.domain.host.MidiDevices;
import org.jebol.domain.host.MidiRoute;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TheMidiPortFromTheSourceTest {

    private static final int A_DEVICE_THAT_WOULD_NOT_START = 4;

    private static final int A_MESSAGE_THE_DEVICE_CANNOT_SEND = 10;

    private static final class AnAttachedRig implements MidiDevices {

        private final List<String> inputs;
        private final List<String> outputs;
        private final List<MidiRoute> connected = new ArrayList<>();
        private final Deque<byte[]> arriving = new ArrayDeque<>();
        private final List<String> sent = new ArrayList<>();
        private Optional<Integer> refusingToConnectWith = Optional.empty();
        private Optional<Integer> failingInUseWith = Optional.empty();
        private int closings;

        private AnAttachedRig(List<String> inputs, List<String> outputs) {
            this.inputs = inputs;
            this.outputs = outputs;
        }

        private AnAttachedRig addArriving(String... hexMessages) {
            for (String hex : hexMessages) {
                arriving.add(HexFormat.of().parseHex(hex));
            }
            return this;
        }

        private AnAttachedRig refuseToConnect(int devicesOwnNumber) {
            refusingToConnectWith = Optional.of(devicesOwnNumber);
            return this;
        }

        private AnAttachedRig failInUse(int devicesOwnNumber) {
            failingInUseWith = Optional.of(devicesOwnNumber);
            return this;
        }

        @Override
        public List<String> inputs() {
            return inputs;
        }

        @Override
        public List<String> outputs() {
            return outputs;
        }

        @Override
        public Connection connect(MidiRoute route) {
            refusingToConnectWith.ifPresent(number -> {
                throw new Refused(number, "the rig would not start");
            });
            connected.add(route);
            return new Connection() {

                @Override
                public byte[] arrivedSinceLastRead() {
                    failIfAsked();
                    return arriving.isEmpty() ? new byte[0] : arriving.removeFirst();
                }

                @Override
                public void send(byte[] message) {
                    failIfAsked();
                    sent.add(HexFormat.of().formatHex(message));
                }

                @Override
                public void close() {
                    closings++;
                }

                private void failIfAsked() {
                    failingInUseWith.ifPresent(number -> {
                        throw new Refused(number, "the rig stopped answering");
                    });
                }
            };
        }
    }

    private AnAttachedRig attachAKeyboardAndTwoSynths() {
        return new AnAttachedRig(List.of("Keyboard"), List.of("Synth A", "Synth B"));
    }

    private String answerWith(MidiDevices devices, String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withMidiDevices(devices),
                message -> { },
                Bounds.standard().grant(HostService.MIDI));
        return readTheAnswerFrom(interpreter, source);
    }

    private String answerTo(String source) {
        return answerWith(attachAKeyboardAndTwoSynths(), source);
    }

    private String runWithNoMidiGiven(String source) {
        return readTheAnswerFrom(makeAnInterpreterGrantedMidi(), source);
    }

    private String runWithoutTheGrant(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withMidiDevices(attachAKeyboardAndTwoSynths()),
                OutputPort.discard(),
                Bounds.standard());
        return readTheAnswerFrom(interpreter, source);
    }

    private Interpreter makeAnInterpreterGrantedMidi() {
        return AnInterpreter.createWithOutput(message -> { }, Bounds.standard().grant(HostService.MIDI));
    }

    private String readTheAnswerFrom(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String readTheRefusalOf(String expression) {
        return "failure: try [" + expression + "] reduce [failure/id failure/arg1 failure/arg2]";
    }

    private String readTheIdAndNumberOf(String expression) {
        return "failure: try [" + expression + "] reduce [failure/id failure/arg2]";
    }

    private String readTheDevicesOf(String port) {
        return "p: " + port + " reduce [p/spec/device-in p/spec/device-out]";
    }

    @Nested
    @DisplayName("the scheme is Rebol's own declaration, registered where a real 3.22.5 has it")
    class TheScheme {

        @Test
        @DisplayName("midi is a scheme, straight after serial")
        void straightAfterSerial() {
            assertThat(runWithNoMidiGiven("""
                    names: words-of system/schemes
                    reduce [none? find names 'midi  second find names 'serial]"""))
                    .isEqualTo("[#(false) midi]");
        }

        @Test
        @DisplayName("its title is MIDI and it carries no info")
        void hasItsTitleAndNoInfo() {
            assertThat(runWithNoMidiGiven("""
                    reduce [system/schemes/midi/title system/schemes/midi/info]"""))
                    .isEqualTo("""
                            ["MIDI" _]""");
        }

        @Test
        @DisplayName("its spec is the standard MIDI spec")
        void hasTheStandardSpec() {
            assertThat(runWithNoMidiGiven("""
                    words-of system/schemes/midi/spec"""))
                    .isEqualTo("[title scheme ref device-in device-out]");
        }

        @Test
        @DisplayName("and the MIDI file loads whole")
        void loadsTheFileWhole() {
            Interpreter interpreter = AnInterpreter.create();
            assertThat(interpreter.borrowedLoadFailures()).doesNotContainKey("sys-ports-midi.reb");
            assertThat(readTheAnswerFrom(interpreter, """
                    function? :system/schemes/midi/init""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a url names the devices by number, one digit each")
    class AUrlNamesTheDevices {

        @ParameterizedTest(name = "{0} is input {1}, output {2}")
        @CsvSource({
                "midi://1/2,         1,   2",
                "midi:7,             7,   _",
                "midi://device:7/9,  7,   9",
                "midi://abc,         _,   _",
                "midi://12,          1,   _",
                "midi://12345678901, 1,   _",
                "midi://1/x,         1,   _",
                "midi://0/1,         0,   1",
                "midi://,            _,   _",
        })
        void takesEachDeviceFromItsDigit(String url, String input, String output) {
            assertThat(answerTo(readTheDevicesOf("make port! " + url)))
                    .isEqualTo("[" + input + " " + output + "]");
        }
    }

    @Nested
    @DisplayName("a spec block names them by number or by a name the host lists")
    class ASpecBlockNamesTheDevices {

        @ParameterizedTest(name = "device-in: {0} is {1}")
        @CsvSource(delimiter = '|', value = {
                "2           | 2",
                "{Keyboard}  | 1",
                "{key*}      | 1",
                "{*board}    | 1",
                "{Piano}     | _",
                "{}          | _",
        })
        void namesTheInput(String given, String expected) {
            assertThat(answerTo("p: make port! [scheme: 'midi device-in: " + given + "] reduce [p/spec/device-in]"))
                    .isEqualTo("[" + expected + "]");
        }

        @ParameterizedTest(name = "device-out: {0} is {1}")
        @CsvSource(delimiter = '|', value = {
                "{Synth A} | 1",
                "{Synth B} | 2",
                "{synth*}  | 1",
                "{Drums}   | _",
        })
        void namesTheOutput(String given, String expected) {
            assertThat(answerTo("p: make port! [scheme: 'midi device-out: " + given + "] reduce [p/spec/device-out]"))
                    .isEqualTo("[" + expected + "]");
        }
    }

    @Nested
    @DisplayName("QUERY lists the devices the host has")
    class Querying {

        @Test
        @DisplayName("devices-in lists the inputs by name")
        void listsTheInputs() {
            assertThat(answerTo("""
                    query midi:// 'devices-in""")).isEqualTo("""
                    ["Keyboard"]""");
        }

        @Test
        @DisplayName("devices-out lists the outputs by name, in the host's order")
        void listsTheOutputs() {
            assertThat(answerTo("""
                    query midi:// 'devices-out""")).isEqualTo("""
                    ["Synth A" "Synth B"]""");
        }

        @Test
        @DisplayName("a port answers the same as a url")
        void answersTheSameThroughAPort() {
            assertThat(answerTo("""
                    query make port! midi://1 'devices-out""")).isEqualTo("""
                    ["Synth A" "Synth B"]""");
        }

        @Test
        @DisplayName("none names the two fields")
        void namesTheFieldsForNone() {
            assertThat(answerTo("""
                    query midi:// none""")).isEqualTo("[devices-in devices-out]");
        }

        @Test
        @DisplayName("none names the two fields through a port too")
        void namesTheFieldsForNoneThroughAPort() {
            assertThat(answerTo("""
                    query make port! midi://1 none""")).isEqualTo("[devices-in devices-out]");
        }

        @Test
        @DisplayName("a block answers each field as a set-word and its list")
        void answersEachFieldForABlock() {
            assertThat(answerTo("""
                    query midi:// [devices-out devices-in]""")).isEqualTo("""
                    [
                        devices-out: ["Synth A" "Synth B"]
                        devices-in: ["Keyboard"]
                    ]""");
        }

        @Test
        @DisplayName("a get-word in the block answers the list with no name before it")
        void answersTheListAloneForAGetWord() {
            assertThat(answerTo("""
                    query midi:// [devices-in :devices-out]""")).isEqualTo("""
                    [
                        devices-in: ["Keyboard"] ["Synth A" "Synth B"]
                    ]""");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"['devices-in]", "[/devices-in]", "[#devices-in]"})
        void takesEveryKindOfWordAsAField(String asked) {
            assertThat(answerTo("query midi:// " + asked)).isEqualTo("""
                    [
                        devices-in: ["Keyboard"]
                    ]""");
        }

        @Test
        @DisplayName("a field asked twice is answered twice")
        void answersAFieldAskedTwice() {
            assertThat(answerTo("""
                    query midi:// [devices-in devices-in]""")).isEqualTo("""
                    [
                        devices-in: ["Keyboard"]
                        devices-in: ["Keyboard"]
                    ]""");
        }

        @Test
        @DisplayName("an empty block answers an empty block")
        void answersAnEmptyBlock() {
            assertThat(answerTo("""
                    query midi:// []""")).isEqualTo("[]");
        }

        @Test
        @DisplayName("a datatype answers both lists in one object")
        void answersAnObjectForADatatype() {
            assertThat(answerTo("""
                    q: query midi:// integer!
                    reduce [object? q q/devices-in q/devices-out]""")).isEqualTo("""
                    [#(true) ["Keyboard"] ["Synth A" "Synth B"]]""");
        }

        @ParameterizedTest(name = "{0} alone cannot be used on a port")
        @ValueSource(strings = {"foo", "Devices-In", "DEVICES-OUT", "devices"})
        void refusesAnUnknownWordAlone(String asked) {
            assertThat(answerTo(readTheRefusalOf("query midi:// '" + asked)))
                    .isEqualTo("[cannot-use " + asked + " #(port!)]");
        }

        @ParameterizedTest(name = "{0} in a block is an invalid argument")
        @CsvSource(delimiter = '|', value = {
                "[foo]             | foo",
                "[Devices-In]      | Devices-In",
                "[1]               | 1",
                "[devices-in foo]  | foo",
        })
        void refusesAnUnknownEntryInABlock(String asked, String entry) {
            assertThat(answerTo(readTheRefusalOf("query midi:// " + asked)))
                    .isEqualTo("[invalid-arg " + entry + " _]");
        }

        @Test
        @DisplayName("a string in a block is an invalid argument, named as the string")
        void refusesAStringInABlock() {
            assertThat(answerTo(readTheRefusalOf("query midi:// [{devices-in}]"))).isEqualTo("""
                    [invalid-arg "devices-in" _]""");
        }

        @ParameterizedTest(name = "a field given as {0} is refused before the actor")
        @ValueSource(strings = {"{devices-in}", "1", "first [devices-in:]", "first [:devices-in]",
                "first [devices-in/x]"})
        void refusesAFieldOfTheWrongType(String asked) {
            assertThat(answerTo("failure: try [query midi:// " + asked + "] failure/id"))
                    .isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("OPEN connects the devices the spec names")
    class Opening {

        @Test
        @DisplayName("an open answers the port, and the port is open")
        void answersThePort() {
            assertThat(answerTo("""
                    p: make port! midi://1/2
                    reduce [same? p open p open? p]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("the host is asked for exactly the devices named")
        void asksTheHostForTheDevicesNamed() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            answerWith(rig, "open midi://1/2");
            assertThat(rig.connected).containsExactly(
                    new MidiRoute(Optional.of(new MidiDevice(1)), Optional.of(new MidiDevice(2))));
        }

        @Test
        @DisplayName("an input alone is asked for alone")
        void asksForAnInputAlone() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            answerWith(rig, "open midi://1");
            assertThat(rig.connected).containsExactly(
                    new MidiRoute(Optional.of(new MidiDevice(1)), Optional.empty()));
        }

        @Test
        @DisplayName("an output alone, from a spec block, is asked for alone")
        void asksForAnOutputAlone() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            answerWith(rig, "open make port! [scheme: 'midi device-out: 2]");
            assertThat(rig.connected).containsExactly(
                    new MidiRoute(Optional.empty(), Optional.of(new MidiDevice(2))));
        }

        @Test
        @DisplayName("a zero input with a listed output asks for the output alone")
        void asksForTheOutputAloneWithAZeroInput() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            answerWith(rig, "open midi://0/1");
            assertThat(rig.connected).containsExactly(
                    new MidiRoute(Optional.empty(), Optional.of(new MidiDevice(1))));
        }

        @Test
        @DisplayName("an open queues an open event for the port's awake")
        void queuesAnOpenEvent() {
            assertThat(answerTo("""
                    seen: copy []
                    p: open midi://1
                    p/awake: func [event] [append seen event/type false]
                    wait [p 0]
                    seen""")).isEqualTo("[open]");
        }

        @ParameterizedTest(name = "{0} names no device, which is 1")
        @ValueSource(strings = {"midi://", "midi://0", "midi://abc",
                "make port! [scheme: 'midi device-in: 0 device-out: 0]",
                "make port! [scheme: 'midi device-in: 1.5]",
                "make port! [scheme: 'midi device-in: true]",
                "make port! [scheme: 'midi device-in: [1]]",
                "make port! [scheme: 'midi device-in: to char! 49]",
                "make port! [scheme: 'midi device-in: {Piano}]"})
        void refusesWhenNoDeviceIsNamed(String target) {
            assertThat(answerTo(readTheIdAndNumberOf("open " + target))).isEqualTo("[cannot-open 1]");
        }

        @Test
        @DisplayName("the refusal names the url it was given")
        void namesTheUrlInTheRefusal() {
            assertThat(answerTo(readTheRefusalOf("open midi://"))).isEqualTo("[cannot-open midi:// 1]");
        }

        @ParameterizedTest(name = "{0} is beyond what the host lists, which is 3")
        @ValueSource(strings = {"midi://2", "midi://1/3", "midi://0/3", "midi://9/9",
                "make port! [scheme: 'midi device-in: -1]",
                "make port! [scheme: 'midi device-out: 3]"})
        void refusesADeviceBeyondTheList(String target) {
            assertThat(answerTo(readTheIdAndNumberOf("open " + target))).isEqualTo("[cannot-open 3]");
        }

        @Test
        @DisplayName("the last listed device of each type is within the list")
        void acceptsTheLastListedDevices() {
            assertThat(answerTo("""
                    open? open midi://1/2""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a refused spec block is named in the refusal as it was written")
        void namesARefusedSpecBlockAsWritten() {
            assertThat(answerTo(readTheRefusalOf("open make port! [scheme: 'midi device-in: -1]")))
                    .isEqualTo("[cannot-open [scheme: 'midi device-in: -1] 3]");
        }

        @Test
        @DisplayName("a device the host will not start refuses with the device's own number")
        void refusesWithTheDevicesNumber() {
            assertThat(answerWith(attachAKeyboardAndTwoSynths().refuseToConnect(A_DEVICE_THAT_WOULD_NOT_START),
                    readTheRefusalOf("open midi://1"))).isEqualTo("[cannot-open midi://1 4]");
        }

        @Test
        @DisplayName("opening a port that is already open asks the host again, as the C does")
        void asksAgainOnOpeningAnOpenPort() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            assertThat(answerWith(rig, "p: open midi://1 open? open p")).isEqualTo("#(true)");
            assertThat(rig.connected).hasSize(2);
        }

        @Test
        @DisplayName("a refused open leaves the port closed")
        void leavesItClosedAfterARefusedOpen() {
            assertThat(answerTo("""
                    p: make port! midi://9
                    try [open p]
                    open? p""")).isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("OPEN/WRITE with one device makes it an output")
    class OpeningForWriting {

        @Test
        @DisplayName("the number moves from device-in to device-out")
        void movesTheNumber() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            assertThat(answerWith(rig, readTheDevicesOf("open/write midi://2"))).isEqualTo("[_ 2]");
            assertThat(rig.connected).containsExactly(
                    new MidiRoute(Optional.empty(), Optional.of(new MidiDevice(2))));
        }

        @Test
        @DisplayName("and stays moved when the open fails")
        void staysMovedWhenTheOpenFails() {
            assertThat(answerTo("""
                    p: make port! midi://9
                    try [open/write p]
                    reduce [p/spec/device-in p/spec/device-out]""")).isEqualTo("[_ 9]");
        }

        @Test
        @DisplayName("OPEN/READ/WRITE moves nothing")
        void readAndWriteMovesNothing() {
            assertThat(answerTo("""
                    p: make port! midi://1
                    try [open/read/write p]
                    reduce [p/spec/device-in p/spec/device-out]""")).isEqualTo("[1 _]");
        }

        @Test
        @DisplayName("and with both devices named, nothing moves")
        void movesNothingWithBothNamed() {
            assertThat(answerTo("""
                    p: make port! [scheme: 'midi device-in: 1 device-out: 2]
                    open/write p
                    reduce [p/spec/device-in p/spec/device-out]""")).isEqualTo("[1 2]");
        }
    }

    @Nested
    @DisplayName("READ, WRITE and CLOSE need an open port")
    class NeedingAnOpenPort {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"read midi://1", "write midi://1 #{90}", "write midi:// #{90}",
                "read make port! midi://1", "write make port! midi://1 {abc}",
                "write make port! midi://1 42", "close make port! midi://1"})
        void refusesAsNotOpen(String expression) {
            assertThat(answerTo(readTheIdAndNumberOf(expression))).isEqualTo("[not-open 12]");
        }

        @Test
        @DisplayName("the refusal names the port's url")
        void namesTheUrlInTheRefusal() {
            assertThat(answerTo(readTheRefusalOf("read midi://1"))).isEqualTo("[not-open midi://1 12]");
        }

        @Test
        @DisplayName("a port closed after being open is refused the same")
        void refusesAPortClosedAgain() {
            assertThat(answerTo(readTheRefusalOf("p: open midi://1 close p read p")))
                    .isEqualTo("[not-open midi://1 12]");
        }

        @Test
        @DisplayName("closing twice refuses the second")
        void refusesTheSecondClose() {
            assertThat(answerTo(readTheRefusalOf("p: open midi://1 close p close p")))
                    .isEqualTo("[not-open midi://1 12]");
        }
    }

    @Nested
    @DisplayName("READ answers what has arrived since the last read")
    class Reading {

        @Test
        @DisplayName("one message that arrived")
        void readsOneMessage() {
            assertThat(answerWith(attachAKeyboardAndTwoSynths().addArriving("903c40"), """
                    p: open midi://1
                    read p""")).isEqualTo("#{903C40}");
        }

        @Test
        @DisplayName("nothing arrived is an empty binary")
        void readsAnEmptyBinaryWhenNothingArrived() {
            assertThat(answerTo("""
                    p: open midi://1
                    read p""")).isEqualTo("#{}");
        }

        @Test
        @DisplayName("each read takes what arrived after the one before")
        void takesTheNextOnEachRead() {
            assertThat(answerWith(attachAKeyboardAndTwoSynths().addArriving("903c40", "803c00"), """
                    p: open midi://1
                    reduce [read p read p read p]""")).isEqualTo("[#{903C40} #{803C00} #{}]");
        }

        @Test
        @DisplayName("a device that fails while reading refuses with its own number")
        void refusesWithAFailingDevicesNumber() {
            assertThat(answerWith(attachAKeyboardAndTwoSynths().failInUse(A_MESSAGE_THE_DEVICE_CANNOT_SEND),
                    readTheRefusalOf("p: open midi://1 read p"))).isEqualTo("[read-error midi://1 10]");
        }
    }

    @Nested
    @DisplayName("WRITE sends a binary and answers the port")
    class Writing {

        @Test
        @DisplayName("a binary is sent as it is")
        void sendsABinary() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            assertThat(answerWith(rig, """
                    p: open midi://0/1
                    same? p write p #{903C4000}""")).isEqualTo("#(true)");
            assertThat(rig.sent).containsExactly("903c4000");
        }

        @Test
        @DisplayName("an empty binary is sent as nothing")
        void sendsAnEmptyBinaryAsNothing() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            answerWith(rig, "p: open midi://0/1 write p #{}");
            assertThat(rig.sent).containsExactly("");
        }

        @Test
        @DisplayName("a binary is sent from where it stands")
        void sendsFromWhereItStands() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            answerWith(rig, "p: open midi://0/1 write p next #{FF903C4000}");
            assertThat(rig.sent).containsExactly("903c4000");
        }

        @ParameterizedTest(name = "{0} is not sent, and the answer is still the port")
        @ValueSource(strings = {"{903C4000}", "42", "1.5", "none", "[#{90}]", "true"})
        void sendsNothingElse(String written) {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            assertThat(answerWith(rig, "p: open midi://0/1 same? p write p " + written))
                    .isEqualTo("#(true)");
            assertThat(rig.sent).isEmpty();
        }

        @Test
        @DisplayName("a device that fails while writing refuses with its own number")
        void refusesWithAFailingDevicesNumber() {
            assertThat(answerWith(attachAKeyboardAndTwoSynths().failInUse(A_MESSAGE_THE_DEVICE_CANNOT_SEND),
                    readTheRefusalOf("p: open midi://0/1 write p #{F0}"))).isEqualTo("[write-error midi://0/1 10]");
        }
    }

    @Nested
    @DisplayName("CLOSE lets the devices go")
    class Closing {

        @Test
        @DisplayName("a close answers the port, and the port is closed")
        void answersThePort() {
            assertThat(answerTo("""
                    p: open midi://1
                    reduce [same? p close p open? p]""")).isEqualTo("[#(true) #(false)]");
        }

        @Test
        @DisplayName("the host's connection is closed once")
        void closesTheConnectionOnce() {
            AnAttachedRig rig = attachAKeyboardAndTwoSynths();
            answerWith(rig, "p: open midi://1 close p try [close p]");
            assertThat(rig.closings).isOne();
        }

        @Test
        @DisplayName("a close queues a close event after the open one")
        void queuesACloseEvent() {
            assertThat(answerTo("""
                    seen: copy []
                    p: open midi://1
                    p/awake: func [event] [append seen event/type false]
                    close p
                    wait [p 0]
                    seen""")).isEqualTo("[open close]");
        }

        @Test
        @DisplayName("a port opened again after a close is open")
        void opensAgain() {
            assertThat(answerTo("""
                    p: open midi://1
                    close p
                    open p
                    open? p""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("OPEN? and UPDATE")
    class OpenAndUpdate {

        @Test
        @DisplayName("a port never opened is not open")
        void neverOpened() {
            assertThat(answerTo("""
                    open? make port! midi://1""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("a refused read does not leave the port open")
        void opensNothingOnARefusedRead() {
            assertThat(answerTo("""
                    p: make port! midi://1
                    try [read p]
                    open? p""")).isEqualTo("#(false)");
        }

        @ParameterizedTest(name = "UPDATE on {0} answers none")
        @ValueSource(strings = {"make port! midi://1", "open midi://1"})
        void updateAnswersNone(String port) {
            assertThat(answerTo("update " + port)).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("seven verbs and no more")
    class TheVerbsItServes {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "pick p 1          | pick:",
                "poke p 1 1        | poke:",
                "put p 1 1         | put:",
                "select p 1        | select:",
                "length? p         | length?:",
                "copy p            | copy:",
                "remove p          | remove:",
                "modify p 'x 1     | modify:",
                "insert p 1        | insert:",
                "append p 1        | append:",
                "change p 1        | change:",
                "clear p           | clear:",
                "find p 1          | find:",
                "take p            | take:",
                "head p            | head:",
                "tail p            | tail:",
                "next p            | next:",
                "index? p          | index?:",
                "flush p           | flush:",
                "create p          | create:",
                "delete p          | delete:",
                "rename p %x       | rename:",
        })
        void refuseEveryOtherVerbByName(String expression, String verb) {
            assertThat(answerTo("p: make port! midi://1 failure: try [" + expression
                    + "] reduce [failure/id failure/arg1]"))
                    .isEqualTo("[no-port-action " + verb + "]");
        }
    }

    @Nested
    @DisplayName("MIDI needs its own grant")
    class TheGrant {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"open midi://1", "read midi://1", "write midi://1 #{90}",
                "query midi:// 'devices-in", "query midi:// [devices-out]", "query midi:// integer!",
                "read make port! midi://1"})
        void asksNothingOfTheHostWithoutIt(String expression) {
            assertThat(runWithoutTheGrant("failure: try [" + expression + "] failure/id"))
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("the refusal names midi as the service")
        void namesMidiInTheRefusal() {
            assertThat(runWithoutTheGrant("failure: try [open midi://1] reduce [form failure/arg1]"))
                    .isEqualTo("""
                            ["midi is not granted"]""");
        }

        @Test
        @DisplayName("naming the fields asks nothing of the host and needs no grant")
        void needsNoneToNameTheFields() {
            assertThat(runWithoutTheGrant("""
                    query midi:// none""")).isEqualTo("[devices-in devices-out]");
        }

        @Test
        @DisplayName("OPEN? and UPDATE need no grant")
        void openAndUpdate() {
            assertThat(runWithoutTheGrant("""
                    p: make port! midi://1
                    reduce [open? p update p]""")).isEqualTo("[#(false) _]");
        }

        @Test
        @DisplayName("nor does closing a port that was never open, which is still not open")
        void needsNoneToCloseAClosedPort() {
            assertThat(runWithoutTheGrant("failure: try [close make port! midi://1] failure/id"))
                    .isEqualTo("not-open");
        }

        @Test
        @DisplayName("and the scheme is registered whether or not it is granted")
        void registersTheSchemeEitherWay() {
            assertThat(runWithoutTheGrant("""
                    object? system/schemes/midi""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a host that gives no MIDI lists nothing, as a real 3.22.5 with nothing attached does")
    class AHostWithNoMidi {

        @ParameterizedTest(name = "query midi:// ''{0}")
        @ValueSource(strings = {"devices-in", "devices-out"})
        void listsNothing(String field) {
            assertThat(runWithNoMidiGiven("query midi:// '" + field)).isEqualTo("[]");
        }

        @ParameterizedTest(name = "open {0} refuses with {1}")
        @CsvSource({
                "midi://,     1",
                "midi://0,    1",
                "midi://1,    3",
                "midi://1/2,  3",
                "midi://0/1,  3",
        })
        void refusesEveryOpen(String target, String number) {
            assertThat(runWithNoMidiGiven(readTheRefusalOf("open " + target)))
                    .isEqualTo("[cannot-open " + target + " " + number + "]");
        }

        @Test
        @DisplayName("and a name finds nothing to match")
        void findsNothingForAName() {
            assertThat(runWithNoMidiGiven(readTheDevicesOf("make port! [scheme: 'midi device-in: {x*} device-out: 3]")))
                    .isEqualTo("[_ 3]");
        }
    }
}
