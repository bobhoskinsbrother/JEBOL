package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.*;
import org.jebol.domain.host.ClipboardPort;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.NetworkPort;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.EventCatalogue;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.JavaObjectValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.Supplier;

public final class Ports {

    private static final String THE_CONSOLE_MODE_HEADING = "*console-modes*";

    private static final int OPEN_FAILED = 3;

    private static final int WHATEVER_NUMBER_THE_MACHINE_HAS_FREE = 0;

    private static final Set<String> SCHEMES_WHOSE_ACTOR_TAKES_AN_UPDATE = Set.of(
            "console", "dns", "clipboard", "tcp", "udp", "event", "system", "callback");

    private final GrantedServices granted;

    private final ChecksumPort checksumPort = new ChecksumPort(new Encodings());

    private final CryptPort cryptPort = new CryptPort();

    private final FileSystemCalls fileSystem = new FileSystemCalls();

    private final BootDeclarations declarations = new BootDeclarations();

    private String modeTableSource = "";

    private SequencedSet<String> consoleModes = new LinkedHashSet<>();

    public Ports(GrantedServices granted) {
        this.granted = granted;
    }

    public CryptPort cryptPort() {
        return cryptPort;
    }

    public void useModeTable(String source) {
        this.modeTableSource = source;
        this.consoleModes = theConsoleModesInTheModeTable();
    }

    public SequencedSet<String> consoleModes() {
        return consoleModes;
    }

    private SequencedSet<String> theConsoleModesInTheModeTable() {
        List<Value> rows = declarations.theRowsBelowTheHeaderOf(modeTableSource);
        for (int at = 0; at + 1 < rows.size(); at++) {
            if (rows.get(at) instanceof WordValue heading
                    && heading.canonical().equals(THE_CONSOLE_MODE_HEADING)
                    && rows.get(at + 1) instanceof BlockValue listed) {
                return theWordsIn(listed);
            }
        }
        return new LinkedHashSet<>();
    }

    private SequencedSet<String> theWordsIn(BlockValue listed) {
        SequencedSet<String> named = new LinkedHashSet<>();
        for (Value each : listed.remaining()) {
            if (each instanceof WordValue word) {
                named.add(word.canonical());
            }
        }
        return named;
    }

    public Value throughTheFileSystem(Supplier<Value> operation) {
        return fileSystem.answeredOrRaised(operation);
    }

    public boolean routesToAScheme(Value source) {
        return source.datatype() == Datatype.URL
                || source.datatype() == Datatype.BLOCK
                || source instanceof WordValue;
    }

    public PortValue portMadeFor(Value target, Evaluator evaluator, Context context) {
        if (target instanceof PortValue already) {
            return already;
        }
        Value built = evaluator.applyFunction(
                context.systemFunctionNamed("make-port*"), List.of(target));
        if (!(built instanceof PortValue port)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, "nothing knows how to open that");
        }
        return port;
    }

    public PortValue portOpenedFor(Value address, Evaluator evaluator, Context context) {
        PortValue port = portMadeFor(address, evaluator, context);
        if (port.actorWrittenInRebol().isPresent()) {
            return port;
        }
        requireServiceForScheme(port.schemeName());
        port.markOpen(true);
        return port;
    }

    public Optional<String> theFileNamedByAUrl(Value target, Evaluator evaluator, Context context) {
        if (target.datatype() != Datatype.URL) {
            return Optional.empty();
        }
        PortValue routed = portOpenedFor(target, evaluator, context);
        return routed.isAFile() ? Optional.of(pathOf(routed)) : Optional.empty();
    }

    public String pathOf(PortValue port) {
        return new SeekableFilePort(port).path();
    }

    public Raised noActionFor(String verb) {
        return Raised.of(EvaluationFailure.NO_PORT_ACTION, WordValue.of(verb).as(Datatype.SET_WORD));
    }

    public void requireServiceForScheme(String scheme) {
        switch (scheme) {
            case "console", "system", "callback", "bundled", "checksum", "crypt" ->
                    theSchemeReachesNothingOutside();
            case "file", "dir" -> granted.require(HostService.FILES);
            case "tcp", "dns", "udp" -> granted.require(HostService.NETWORK);
            case "event" -> granted.require(HostService.WINDOWS);
            case "clipboard" -> granted.require(HostService.CLIPBOARD);
            default -> throw Raised.of(EvaluationFailure.NO_SERVICE,
                    scheme.isEmpty()
                            ? "that port has no scheme"
                            : "nothing here serves the " + scheme + " scheme");
        }
    }

    private void theSchemeReachesNothingOutside() {
    }

    public Value readFrom(PortValue port, Evaluator evaluator, List<Value> arguments, PortRequest asked) {
        Optional<Value> itsOwn = evaluator.theRebolActorsAnswer(
                "read", withThePortInFront(port, arguments), asked.refinements());
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> lineReadFromTheConsole(evaluator);
            case "clipboard" -> whatTheOperatorLastCopied(port, evaluator, asked);
            case "bundled" -> theSourceOfABundledModule(port, evaluator);
            case "tcp" -> bytesReadFromTheConnection(port, evaluator);
            case "udp" -> oneDatagramReadInto(port, evaluator);
            case "dns" -> addressesOfTheNameThePortNames(port, evaluator);
            case "checksum" -> checksumPort.digestSoFarLeftInTheDataFieldAsWell(port);
            case "crypt" -> {
                cryptPort.refuseWhenClosed(port);
                yield cryptPort.read(port);
            }
            default -> throw Raised.of(EvaluationFailure.NO_SERVICE,
                    "nothing here reads the " + port.schemeName() + " scheme");
        };
    }

    private List<Value> withThePortInFront(PortValue port, List<Value> arguments) {
        List<Value> asTheActorTakesThem = new ArrayList<>(arguments);
        asTheActorTakesThem.set(0, port);
        return asTheActorTakesThem;
    }

    private Value lineReadFromTheConsole(Evaluator evaluator) {
        granted.require(HostService.CONSOLE);
        return throughTheFileSystem(() -> {
            String line = evaluator.console().readLine();
            return line == null ? NoneValue.none() : StringValue.of(line);
        });
    }

    private Value whatTheOperatorLastCopied(PortValue port, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.CLIPBOARD);
        String whole = theClipboardsAnswer(evaluator);
        String copied = asked.part()
                .filter(IntegerValue.class::isInstance)
                .map(wanted -> whole.substring(0, Math.clamp(
                        ((IntegerValue) wanted).magnitude(), 0, whole.length())))
                .orElse(whole);
        port.setField("data", StringValue.of(copied));
        if (asked.refinements().contains("lines")) {
            return BlockValue.block(copied.lines().<Value>map(StringValue::of).toList());
        }
        return StringValue.of(copied);
    }

    private String theClipboardsAnswer(Evaluator evaluator) {
        try {
            return evaluator.clipboard().read();
        } catch (ClipboardPort.Unreachable unreachable) {
            throw Raised.of(EvaluationFailure.READ_ERROR, unreachable.getMessage());
        }
    }

    private Value theSourceOfABundledModule(PortValue port, Evaluator evaluator) {
        return theNameABundledUrlAsksFor(port)
                .flatMap(name -> evaluator.bundledModules().sourceOf(name))
                .map(source -> (Value) BinaryValue.ofBytes(source))
                .orElseThrow(() -> Raised.of(EvaluationFailure.CANNOT_OPEN, theReferenceOf(port)));
    }

    private Value theReferenceOf(PortValue port) {
        return port.fieldValue("spec") instanceof ObjectValue spec
                ? spec.fieldValue("ref")
                : NoneValue.none();
    }

    private Optional<String> theNameABundledUrlAsksFor(PortValue port) {
        if (!(port.fieldValue("spec") instanceof ObjectValue spec)
                || !(spec.fieldValue("host") instanceof StringValue host)
                || !(spec.fieldValue("path") instanceof NoneValue)
                || !(spec.fieldValue("target") instanceof NoneValue)) {
            return Optional.empty();
        }
        return host.text().isEmpty() ? Optional.empty() : Optional.of(host.text());
    }

    private Value bytesReadFromTheConnection(PortValue port, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughTheNetwork(() -> {
            BinaryValue arrived = BinaryValue.ofBytes(connection.read());
            addToThePortsData(port, arrived);
            queueWhatHappenedTo(port, arrived.lengthFromHere() == 0 ? "close" : "read", evaluator);
            return arrived;
        });
    }

    private void addToThePortsData(PortValue port, BinaryValue arrived) {
        if (!(port.fieldValue("data") instanceof BinaryValue held)) {
            port.setField("data", arrived);
            return;
        }
        for (int at = arrived.index(); at <= arrived.storageLength(); at++) {
            held.storage().append(arrived.storage().at(at));
        }
    }

    private Value addressesOfTheNameThePortNames(PortValue port, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        String hostName = hostNamedBy(port);
        return throughTheNetwork(() -> {
            List<String> found = evaluator.network().addressesFor(hostName);
            return found.isEmpty()
                    ? NoneValue.none()
                    : BlockValue.block(found.stream().<Value>map(StringValue::of).toList());
        });
    }

    private void queueWhatHappenedTo(PortValue port, String happened, Evaluator evaluator) {
        if (!(evaluator.hostPort("system") instanceof PortValue queue)) {
            return;
        }
        queue.eventQueue().ifPresent(onIt -> onIt.storage().insertAt(
                onIt.storage().length() + 1,
                new EventValue(EventCatalogue.typeIndexOf(happened).orElseThrow(),
                        Set.of(), EventValue.Model.PORT, 0, port)));
    }

    public Value readFromTheFileBehind(PortValue port, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.FILES);
        SeekableFilePort seekable = new SeekableFilePort(port);
        if (port.schemeName().equals("dir")) {
            return throughTheFileSystem(() -> namesIn(evaluator.files(), seekable.path()));
        }
        boolean wasClosed = !port.isOpen();
        if (wasClosed) {
            port.markOpen(true);
            seekable.moveTo(0);
        }
        asked.seek().filter(IntegerValue.class::isInstance)
                .ifPresent(at -> seekable.moveTo(((IntegerValue) at).magnitude()));
        Optional<Long> howMany = asked.part()
                .filter(IntegerValue.class::isInstance)
                .map(wanted -> ((IntegerValue) wanted).magnitude());
        if (howMany.isPresent() && howMany.get() < 0 && -howMany.get() > seekable.position()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked.part().orElseThrow());
        }
        Value read = throughTheFileSystem(() -> seekable.readFrom(evaluator.files(), howMany));
        if (wasClosed) {
            port.markOpen(false);
        }
        return asTextWhereAskedFor(read, asked.refinements());
    }

    private Value namesIn(FilePort files, String path) {
        return BlockValue.block(files.namesIn(path).stream()
                .<Value>map(name -> StringValue.of(name, Datatype.FILE))
                .toList());
    }

    private Value asTextWhereAskedFor(Value read, Set<String> refinements) {
        if (!(read instanceof BinaryValue bytes)
                || !(refinements.contains("string") || refinements.contains("lines"))) {
            return read;
        }
        Optional<String> text = new FileReading.TextBehindItsMark().decoded(bytes.octetsFromHere());
        if (text.isEmpty()) {
            return read;
        }
        return refinements.contains("lines")
                ? BlockValue.block(StringValue.of(text.orElseThrow()).linesDroppingOneTrailingEmptyLine())
                : StringValue.of(text.orElseThrow());
    }

    public Value readTheFile(String path, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.FILES);
        return throughTheFileSystem(() -> new FileReading(path, asked).answerThrough(evaluator.files()));
    }

    public Value writeTheFile(Value destination, Value data, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.FILES);
        return throughTheFileSystem(() -> {
            new FileWriting(((StringValue) destination).text(), data, asked)
                    .performThrough(evaluator.files());
            return destination;
        });
    }

    public Value writeTo(PortValue port, Value data, Evaluator evaluator, PortRequest asked) {
        Optional<Value> itsOwn = evaluator.theRebolActorsAnswer(
                "write", List.of(port, data), asked.refinements());
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> writtenToTheConsole(port, data, evaluator);
            case "clipboard" -> putOnTheClipboard(port, data, evaluator);
            case "tcp" -> sentDownTheConnection(port, data, evaluator);
            case "udp" -> oneDatagramSentFrom(port, data, evaluator);
            case "checksum" -> summedIntoThePort(port, data, asked);
            case "crypt" -> encipheredIntoThePort(port, data);
            case "file" -> new OpenFile(port, evaluator.files(), granted)
                    .written(data, asked.seek(), asked.part(), asked.refinements().contains("append"));
            default -> throw noActionFor("write");
        };
    }

    private Value writtenToTheConsole(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.CONSOLE);
        evaluator.output().write(Molder.form(data));
        return port;
    }

    private Value putOnTheClipboard(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.CLIPBOARD);
        String text = switch (data) {
            case StringValue written -> written.text();
            case BinaryValue bytes -> new String(bytes.octetsFromHere(), StandardCharsets.UTF_8);
            default -> throw Raised.of(EvaluationFailure.INVALID_PORT_ARG, data);
        };
        try {
            evaluator.clipboard().write(text);
        } catch (ClipboardPort.Unreachable unreachable) {
            throw Raised.of(EvaluationFailure.WRITE_ERROR, unreachable.getMessage());
        }
        return port;
    }

    private Value sentDownTheConnection(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughTheNetwork(() -> {
            connection.write(data.asOctets());
            queueWhatHappenedTo(port, "wrote", evaluator);
            return port;
        });
    }

    private Value summedIntoThePort(PortValue port, Value data, PortRequest asked) {
        if (!(data instanceof BinaryValue || data instanceof StringValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(data));
        }
        if (!port.isOpen()) {
            port.markOpen(true);
            checksumPort.startEvenOnAnAlreadyOpenPort(port, checksumPort.methodOf(port));
        }
        RebolSeries written = (RebolSeries) data;
        byte[] whole = written.head().asOctets();
        long from = checksumPort.seekedFrom(whole, written.index() - 1,
                wholeNumberIn(asked.seek()).orElse(0L));
        wholeNumberIn(asked.part()).ifPresentOrElse(
                wanted -> checksumPort.addPart(port, whole, from, wanted),
                () -> checksumPort.add(port, whole, from));
        return port;
    }

    private Optional<Long> wholeNumberIn(Optional<Value> asked) {
        return asked.filter(Comparison::isNumeric).map(number -> (long) Comparison.asDouble(number));
    }

    private Value encipheredIntoThePort(PortValue port, Value data) {
        cryptPort.refuseWhenClosed(port);
        if (!(data instanceof BinaryValue octets)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, theReferenceOf(port));
        }
        cryptPort.write(port, octets.octetsFromHere());
        return port;
    }

    public Value opened(PortValue port, Evaluator evaluator, Set<String> refinements) {
        requireServiceForScheme(port.schemeName());
        if (port.schemeName().equals("tcp")) {
            return connectedOrLeftClosed(port, evaluator);
        }
        switch (port.schemeName()) {
            case "udp" -> bindTheDatagramPort(port, evaluator);
            case "crypt" -> startTheCipherBehindBlankingTheKeyInTheSpec(port);
            case "checksum" -> checksumPort.startEvenOnAnAlreadyOpenPort(port, checksumPort.methodOf(port));
            default -> theSchemeReachesNothingOutside();
        }
        if (port.isAFile()) {
            openTheFileBehind(port, evaluator, refinements);
        }
        return markedOpen(port);
    }

    private PortValue markedOpen(PortValue port) {
        port.eventQueue();
        if (!port.isOpen()) {
            port.markOpen(true);
        }
        return port;
    }

    private PortValue connectedOrLeftClosed(PortValue port, Evaluator evaluator) {
        try {
            NetworkPort.Connection made = evaluator.network().connectTo(hostNamedBy(port), portNumberOf(port));
            port.setField("state", JavaObjectValue.of(made));
            queueWhatHappenedTo(port, "connect", evaluator);
            return markedOpen(port);
        } catch (NetworkPort.Refused refused) {
            port.eventQueue();
            queueWhatHappenedTo(port, "error", evaluator);
            return port;
        }
    }

    private void openTheFileBehind(PortValue port, Evaluator evaluator, Set<String> refinements) {
        SeekableFilePort seekable = new SeekableFilePort(port);
        String path = seekable.path();
        if (port.schemeName().equals("dir")) {
            if (!throughTheFileSystem(() -> LogicValue.of(
                    somethingIsThereFor(path, evaluator.files()))).isTruthy()) {
                throw Raised.of(EvaluationFailure.CANNOT_OPEN,
                        StringValue.of(path, Datatype.FILE), IntegerValue.of(OPEN_FAILED));
            }
            seekable.moveTo(0);
            return;
        }
        OpeningAFile asked = new OpeningAFile(refinements);
        asked.refuseANewFileNobodyMayWriteTo(path);
        boolean alreadyThere = throughTheFileSystem(() ->
                LogicValue.of(evaluator.files().exists(path))).isTruthy();
        if (!asked.mayWrite()) {
            if (!alreadyThere) {
                throw Raised.of(EvaluationFailure.CANNOT_OPEN, StringValue.of(path, Datatype.FILE));
            }
        } else if (!alreadyThere || asked.emptiesWhatIsThere()) {
            throughTheFileSystem(() -> {
                evaluator.files().write(path, new byte[0]);
                return NoneValue.none();
            });
        }
        seekable.openedAt(0, asked.mayWrite());
    }

    private boolean somethingIsThereFor(String path, FilePort files) {
        FileReading.Wildcard wildcard = new FileReading.Wildcard(path);
        if (!wildcard.isPresent()) {
            return files.exists(path);
        }
        if (wildcard.inTheDirectory()) {
            return false;
        }
        try {
            return !wildcard.namesMatchingIn(files).isEmpty();
        } catch (RuntimeException nothingThere) {
            return false;
        }
    }

    private void startTheCipherBehindBlankingTheKeyInTheSpec(PortValue port) {
        if (cryptPort.isWorking(port)) {
            throw Raised.of(EvaluationFailure.ALREADY_OPEN, theReferenceOf(port));
        }
        if (!(port.fieldValue("spec") instanceof ObjectValue spec)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, port);
        }
        String algorithm = spec.fieldValue("algorithm") instanceof WordValue word
                ? word.canonical()
                : "";
        if (!cryptPort.serves(algorithm)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, spec);
        }
        cryptPort.start(port, algorithm,
                spec.fieldValue("direction") instanceof WordValue wanted
                        && wanted.canonical().equals("decrypt"),
                octetsInSpec(spec, "key"), octetsInSpec(spec, "init-vector"));
        spec.context().register("key", NoneValue.none());
        spec.context().register("init-vector", NoneValue.none());
    }

    private byte[] octetsInSpec(ObjectValue spec, String field) {
        return switch (spec.fieldValue(field)) {
            case BinaryValue octets -> octets.octetsFromHere();
            case StringValue text -> text.text().getBytes(StandardCharsets.UTF_8);
            default -> new byte[0];
        };
    }

    public Value updated(PortValue port) {
        if (port.schemeName().equals("checksum")) {
            checksumPort.digestSoFarLeftInTheDataFieldAsWell(port);
            return port;
        }
        if (port.schemeName().equals("crypt")) {
            cryptPort.refuseWhenClosed(port);
            cryptPort.update(port);
            return port;
        }
        if (!SCHEMES_WHOSE_ACTOR_TAKES_AN_UPDATE.contains(port.schemeName())) {
            throw noActionFor("update");
        }
        return NoneValue.none();
    }

    public Value isOpen(PortValue port) {
        if (port.schemeName().equals("crypt")) {
            cryptPort.refuseWhenClosed(port);
        }
        return LogicValue.of(port.isOpen());
    }

    public Value closed(PortValue port) {
        if (port.schemeName().equals("crypt")) {
            cryptPort.refuseWhenClosed(port);
            cryptPort.stop(port);
        }
        handBackTheConnectionBehind(port);
        port.markOpen(false);
        if (port.schemeName().equals("checksum")) {
            checksumPort.stop(port);
        }
        return port;
    }

    private void bindTheDatagramPort(PortValue port, Evaluator evaluator) {
        int number = theHostToSendTo(port).isEmpty()
                ? portNumberOf(port)
                : WHATEVER_NUMBER_THE_MACHINE_HAS_FREE;
        throughTheNetwork(() -> {
            NetworkPort.Datagrams bound = evaluator.network().bindTo(number);
            port.setField("state", JavaObjectValue.of(bound));
            return port;
        });
    }

    private String theHostToSendTo(PortValue port) {
        if (port.fieldValue("spec") instanceof ObjectValue(Context context)
                && context.holds("host")
                && context.ownSlotFor("host").value() instanceof StringValue host) {
            return host.text();
        }
        return "";
    }

    private NetworkPort.Datagrams datagramsBehind(PortValue port) {
        if (port.fieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Datagrams bound) {
            return bound;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    private Value oneDatagramReadInto(PortValue port, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        NetworkPort.Datagrams bound = datagramsBehind(port);
        return throughTheNetwork(() -> {
            BinaryValue arrived = BinaryValue.ofBytes(bound.receive());
            addToThePortsData(port, arrived);
            queueWhatHappenedTo(port, "read", evaluator);
            return port;
        });
    }

    private Value oneDatagramSentFrom(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        byte[] bytes = switch (data) {
            case StringValue written -> written.text().getBytes(StandardCharsets.UTF_8);
            case BinaryValue carried -> carried.octetsFromHere();
            default -> throw Raised.of(EvaluationFailure.INVALID_PORT_ARG, data);
        };
        NetworkPort.Datagrams bound = datagramsBehind(port);
        return throughTheNetwork(() -> {
            bound.sendTo(theHostToSendTo(port), portNumberOf(port), bytes);
            port.setField("data", NoneValue.none());
            queueWhatHappenedTo(port, "wrote", evaluator);
            return port;
        });
    }

    private int portNumberOf(PortValue port) {
        if (port.fieldValue("spec") instanceof ObjectValue(Context context)
                && context.holds("port")
                && context.ownSlotFor("port").value() instanceof IntegerValue(long magnitude)) {
            return (int) magnitude;
        }
        return NetworkPort.wellKnownPortFor(port.schemeName()).orElse(0);
    }

    private Value throughTheNetwork(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (NetworkPort.Refused refused) {
            throw new Raised(ErrorValue.about(ErrorCategory.ACCESS,
                    refused.errorId(), refused.getMessage(),
                    StringValue.of(refused.subject()),
                    IntegerValue.of(OPEN_FAILED)));
        }
    }

    private void handBackTheConnectionBehind(PortValue port) {
        if (port.fieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            open.close();
        }
    }

    private NetworkPort.Connection connectionBehind(PortValue port) {
        if (port.fieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            return open;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    private String hostNamedBy(PortValue port) {
        if (port.fieldValue("spec") instanceof ObjectValue(Context context)) {
            if (context.holds("host") && context.ownSlotFor("host").value() instanceof StringValue host) {
                return host.text();
            }
            if (context.holds("ref") && context.ownSlotFor("ref").value() instanceof StringValue reference) {
                String written = reference.text();
                int afterScheme = written.indexOf("://");
                return afterScheme < 0 ? written : written.substring(afterScheme + 3);
            }
        }
        return "";
    }
}
