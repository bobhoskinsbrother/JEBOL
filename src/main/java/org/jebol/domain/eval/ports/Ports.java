package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.*;
import org.jebol.domain.host.ClipboardPort;
import org.jebol.domain.host.DigestPort;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.NetworkPort;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
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
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TextLines;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.UrlValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.SetWordValue;

import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.eval.security.PolicyClass;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class Ports {

    private static final String THE_CONSOLE_MODE_HEADING = "*console-modes*";

    private static final int OPEN_FAILED = 3;

    private static final int WHATEVER_NUMBER_THE_MACHINE_HAS_FREE = 0;

    private static final Set<String> SCHEMES_CHECKED_AGAINST_THE_NET_POLICY = Set.of("tcp", "udp");

    private static final Pattern WRITTEN_AS_AN_ADDRESS = Pattern.compile("\\d+(\\.\\d+){2,}");

    private static final int IPV4_PARTS = 4;

    private static final Set<String> SCHEMES_WHOSE_ACTOR_TAKES_AN_UPDATE = Set.of(
            "console", "dns", "clipboard", "tcp", "udp", "event", "system", "callback");

    private final GrantedServices granted;

    private final ChecksumPort checksumPort;

    private final CryptPort cryptPort = new CryptPort();

    private final FileSystemCalls fileSystem = new FileSystemCalls();

    private final BootDeclarations declarations = new BootDeclarations();

    private String modeTableSource = "";

    private SequencedSet<String> consoleModes = new LinkedHashSet<>();

    public Ports(GrantedServices granted, DigestPort digests) {
        this.granted = granted;
        this.checksumPort = new ChecksumPort(new Encodings(digests));
    }

    public CryptPort cryptPort() {
        return cryptPort;
    }

    public void useModeTable(String source) {
        this.modeTableSource = source;
        this.consoleModes = listTheConsoleModesInTheModeTable();
    }

    public SequencedSet<String> consoleModes() {
        return consoleModes;
    }

    private SequencedSet<String> listTheConsoleModesInTheModeTable() {
        List<Value> rows = declarations.readTheRowsBelowTheHeaderOf(modeTableSource);
        for (int at = 0; at + 1 < rows.size(); at++) {
            if (rows.get(at) instanceof AnyWordValue heading
                    && heading.canonical().equals(THE_CONSOLE_MODE_HEADING)
                    && rows.get(at + 1) instanceof AnyBlockValue listed) {
                return listWordsIn(listed);
            }
        }
        return new LinkedHashSet<>();
    }

    private SequencedSet<String> listWordsIn(AnyBlockValue listed) {
        SequencedSet<String> named = new LinkedHashSet<>();
        for (Value each : listed.remaining()) {
            if (each instanceof AnyWordValue word) {
                named.add(word.canonical());
            }
        }
        return named;
    }

    public Value runThroughTheFileSystem(Supplier<Value> operation) {
        return fileSystem.answerOrRaise(operation);
    }

    public void requireTheFilePolicy(String path, Evaluator evaluator, Set<PolicyAccess> accesses) {
        Value subject = runThroughTheFileSystem(() -> FileValue.of(evaluator.ports().files().locationFor(path)));
        for (PolicyAccess access : PolicyAccess.values()) {
            if (accesses.contains(access)) {
                evaluator.policies().require(PolicyClass.FILE, access, Optional.of(subject));
            }
        }
    }

    public boolean namesADirectory(String path, Evaluator evaluator) {
        return path.endsWith("/") || runThroughTheFileSystem(() ->
                LogicValue.of(evaluator.ports().files().isDirectory(path))).isTruthy();
    }

    public boolean routesToAScheme(Value source) {
        return source instanceof UrlValue
                || source instanceof BlockValue
                || source instanceof AnyWordValue;
    }

    public PortValue makePortFor(Value target, Evaluator evaluator, Context context) {
        if (target instanceof PortValue already) {
            return already;
        }
        Value built = evaluator.applyFunction(
                context.findSystemFunctionNamed("make-port*"), List.of(target));
        if (!(built instanceof PortValue port)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, "nothing knows how to open that");
        }
        return port;
    }

    public PortValue openAPortFor(Value address, Evaluator evaluator, Context context) {
        PortValue port = makePortFor(address, evaluator, context);
        if (port.actorWrittenInRebol().isPresent()) {
            return port;
        }
        requireServiceForScheme(port.schemeName());
        port.markOpen(true);
        return port;
    }

    public Optional<String> findTheFileNamedByAUrl(Value target, Evaluator evaluator, Context context) {
        if (!(target instanceof UrlValue)) {
            return Optional.empty();
        }
        PortValue routed = openAPortFor(target, evaluator, context);
        return routed.isAFile() ? Optional.of(readThePathOf(routed)) : Optional.empty();
    }

    public String readThePathOf(PortValue port) {
        return new SeekableFilePort(port).path();
    }

    public Raised refuseWithNoActionFor(String verb) {
        return Raised.of(EvaluationFailure.NO_PORT_ACTION, SetWordValue.of(verb));
    }

    public void requireServiceForScheme(String scheme) {
        switch (scheme) {
            case "console", "system", "callback", "bundled", "checksum", "crypt" ->
                    refuseToReachOutside();
            case "file", "dir" -> granted.require(HostService.FILES);
            case "tcp", "dns", "udp" -> granted.require(HostService.NETWORK);
            case "http", "https" -> granted.require(HostService.WEB);
            case "event" -> granted.require(HostService.WINDOWS);
            case "clipboard" -> granted.require(HostService.CLIPBOARD);
            case "midi" -> granted.require(HostService.MIDI);
            default -> throw Raised.of(EvaluationFailure.NO_SERVICE,
                    scheme.isEmpty()
                            ? "that port has no scheme"
                            : "nothing here serves the " + scheme + " scheme");
        }
    }

    private void refuseToReachOutside() {
    }

    public Value readFrom(PortValue port, Evaluator evaluator, List<Value> arguments, PortRequest asked) {
        Optional<Value> itsOwn = evaluator.askTheRebolActor(
                "read", withThePortInFront(port, arguments), asked.refinements());
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> lineReadFromTheConsole(evaluator);
            case "clipboard" -> readWhatTheOperatorLastCopied(port, evaluator, asked);
            case "bundled" -> readTheSourceOfABundledModule(port, evaluator);
            case "tcp" -> readBytesFromConnection(port, evaluator);
            case "udp" -> readOneDatagramInto(port, evaluator);
            case "dns" -> answerWhatTheDnsUrlAsks(port, evaluator);
            case "checksum" -> checksumPort.digestSoFarLeftInDataFieldAsWell(port);
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
        return runThroughTheFileSystem(() -> {
            String line = evaluator.ports().console().readLine();
            return line == null ? NoneValue.none() : StringValue.of(line);
        });
    }

    private Value readWhatTheOperatorLastCopied(PortValue port, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.CLIPBOARD);
        String whole = askClipboard(evaluator);
        String copied = asked.part()
                .filter(IntegerValue.class::isInstance)
                .map(wanted -> whole.substring(0, Math.clamp(
                        ((IntegerValue) wanted).magnitude(), 0, whole.length())))
                .orElse(whole);
        port.setField("data", StringValue.of(copied));
        if (asked.refinements().contains("lines")) {
            return BlockValue.of(TextLines.of(copied).stream().<Value>map(StringValue::of).toList());
        }
        return StringValue.of(copied);
    }

    private String askClipboard(Evaluator evaluator) {
        try {
            return evaluator.ports().clipboard().read();
        } catch (ClipboardPort.Unreachable unreachable) {
            throw Raised.of(EvaluationFailure.READ_ERROR, unreachable.getMessage());
        }
    }

    private Value readTheSourceOfABundledModule(PortValue port, Evaluator evaluator) {
        return readTheNameABundledUrlAsksFor(port)
                .flatMap(name -> evaluator.bundledModules().readTheSourceOf(name))
                .map(source -> (Value) BinaryValue.ofBytes(source))
                .orElseThrow(() -> Raised.of(EvaluationFailure.CANNOT_OPEN, readTheReferenceOf(port)));
    }

    private Value readTheReferenceOf(PortValue port) {
        return port.readFieldValue("spec") instanceof ObjectValue spec
                ? spec.readFieldValue("ref")
                : NoneValue.none();
    }

    private Optional<String> readTheNameABundledUrlAsksFor(PortValue port) {
        if (!(port.readFieldValue("spec") instanceof ObjectValue spec)
                || !(spec.readFieldValue("host") instanceof AnyStringValue host)
                || !(spec.readFieldValue("path") instanceof NoneValue)
                || !(spec.readFieldValue("target") instanceof NoneValue)) {
            return Optional.empty();
        }
        return host.text().isEmpty() ? Optional.empty() : Optional.of(host.text());
    }

    private Value readBytesFromConnection(PortValue port, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        NetworkPort.Connection connection = findTheConnectionBehind(port);
        return runThroughTheNetwork(() -> {
            BinaryValue arrived = BinaryValue.ofBytes(connection.read());
            addToPortsData(port, arrived);
            queueWhatHappenedTo(port, arrived.lengthFromHere() == 0 ? "close" : "read", evaluator);
            return arrived;
        });
    }

    private void addToPortsData(PortValue port, BinaryValue arrived) {
        if (!(port.readFieldValue("data") instanceof BinaryValue held)) {
            port.setField("data", arrived);
            return;
        }
        for (int at = arrived.index(); at <= arrived.storageLength(); at++) {
            held.storage().append(arrived.storage().at(at));
        }
    }

    private Value answerWhatTheDnsUrlAsks(PortValue port, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        String host = readHostNamedBy(port);
        NetworkPort network = evaluator.ports().network();
        return runThroughTheNetwork(() -> {
            if (host.isEmpty()) {
                return StringValue.of(network.hostName());
            }
            if (WRITTEN_AS_AN_ADDRESS.matcher(host).matches()) {
                return lookUpTheNameAtAnAddressOfFourParts(host, network);
            }
            return network.resolveTheFirstIpv4Address(host)
                    .<Value>map(this::asTuple)
                    .orElseGet(NoneValue::none);
        });
    }

    private Value lookUpTheNameAtAnAddressOfFourParts(String address, NetworkPort network) {
        if (address.split("\\.").length != IPV4_PARTS) {
            return NoneValue.none();
        }
        return network.resolveHostName(address).<Value>map(StringValue::of).orElseGet(NoneValue::none);
    }

    private Value asTuple(String dotted) {
        return TupleValue.of(Arrays.stream(dotted.split("\\.")).mapToInt(Integer::parseInt).toArray());
    }

    private void queueWhatHappenedTo(PortValue port, String happened, Evaluator evaluator) {
        evaluator.queueOnTheSystemPort(new EventValue(EventCatalogue.findTheTypeIndexOf(happened).orElseThrow(),
                Set.of(), EventValue.Model.PORT, 0, port));
    }

    public Value readFromFileBehind(PortValue port, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.FILES);
        SeekableFilePort seekable = new SeekableFilePort(port);
        if (port.schemeName().equals("dir")) {
            requireTheFilePolicy(seekable.path(), evaluator, Set.of(PolicyAccess.READ));
            return runThroughTheFileSystem(() -> listNamesIn(evaluator.ports().files(), seekable.path()));
        }
        boolean wasClosed = !port.isOpen();
        if (wasClosed) {
            requireTheFilePolicy(seekable.path(), evaluator, Set.of(PolicyAccess.READ));
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
        Value read = runThroughTheFileSystem(() -> seekable.readFrom(evaluator.ports().files(), howMany));
        if (wasClosed) {
            port.markOpen(false);
        }
        return asTextWhereAskedFor(read, asked.refinements());
    }

    private Value listNamesIn(FilePort files, String path) {
        return BlockValue.of(files.listFileNames(path).stream()
                .<Value>map(FileValue::of)
                .toList());
    }

    private Value asTextWhereAskedFor(Value read, Set<String> refinements) {
        if (!(read instanceof BinaryValue bytes)
                || !(refinements.contains("string") || refinements.contains("lines"))) {
            return read;
        }
        Optional<String> text = new FileReading.TextBehindItsMark().decode(bytes.octetsFromHere());
        if (text.isEmpty()) {
            return read;
        }
        return refinements.contains("lines")
                ? BlockValue.of(StringValue.of(text.orElseThrow()).linesDroppingOneTrailingEmptyLine())
                : StringValue.of(text.orElseThrow());
    }

    public Value readFile(String path, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.FILES);
        requireTheFilePolicy(path, evaluator, Set.of(PolicyAccess.READ));
        return runThroughTheFileSystem(() -> new FileReading(path, asked).answerThrough(evaluator.ports().files()));
    }

    public Value writeFile(Value destination, Value data, Evaluator evaluator, PortRequest asked) {
        granted.require(HostService.FILES);
        requireTheFilePolicy(((AnyStringValue) destination).text(), evaluator, Set.of(PolicyAccess.WRITE));
        return runThroughTheFileSystem(() -> {
            new FileWriting(((AnyStringValue) destination).text(), data, asked)
                    .performThrough(evaluator.ports().files());
            return destination;
        });
    }

    public Value writeTo(PortValue port, Value data, Evaluator evaluator, PortRequest asked) {
        Optional<Value> itsOwn = evaluator.askTheRebolActor(
                "write", List.of(port, data), asked.refinements());
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> writeToTheConsole(port, data, evaluator);
            case "clipboard" -> putOnTheClipboard(port, data, evaluator);
            case "tcp" -> sendDownTheConnection(port, data, evaluator);
            case "udp" -> sendOneDatagramFrom(port, data, evaluator);
            case "checksum" -> sumIntoThePort(port, data, asked);
            case "crypt" -> encipherIntoPort(port, data);
            case "file" -> {
                if (!port.isOpen()) {
                    requireTheFilePolicy(readThePathOf(port), evaluator, Set.of(PolicyAccess.WRITE));
                }
                yield new OpenFile(port, evaluator.ports().files(), granted)
                        .write(data, asked.seek(), asked.part(), asked.refinements().contains("append"));
            }
            default -> throw refuseWithNoActionFor("write");
        };
    }

    private Value writeToTheConsole(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.CONSOLE);
        evaluator.findOutput().write(Molder.form(data));
        return port;
    }

    private Value putOnTheClipboard(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.CLIPBOARD);
        String text = switch (data) {
            case AnyStringValue written -> written.text();
            case BinaryValue bytes -> new String(bytes.octetsFromHere(), StandardCharsets.UTF_8);
            default -> throw Raised.of(EvaluationFailure.INVALID_PORT_ARG, data);
        };
        try {
            evaluator.ports().clipboard().write(text);
        } catch (ClipboardPort.Unreachable unreachable) {
            throw Raised.of(EvaluationFailure.WRITE_ERROR, unreachable.getMessage());
        }
        return port;
    }

    private Value sendDownTheConnection(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        NetworkPort.Connection connection = findTheConnectionBehind(port);
        return runThroughTheNetwork(() -> {
            connection.write(data.asOctets());
            queueWhatHappenedTo(port, "wrote", evaluator);
            return port;
        });
    }

    private Value sumIntoThePort(PortValue port, Value data, PortRequest asked) {
        if (!(data instanceof BinaryValue || data instanceof AnyStringValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(data));
        }
        if (!port.isOpen()) {
            port.markOpen(true);
            checksumPort.startEvenOnAnAlreadyOpenPort(port, checksumPort.readTheMethodOf(port));
        }
        RebolSeries written = (RebolSeries) data;
        byte[] whole = written.head().asOctets();
        long from = checksumPort.seekFrom(whole, written.index() - 1,
                readWholeNumberIn(asked.seek()).orElse(0L));
        readWholeNumberIn(asked.part()).ifPresentOrElse(
                wanted -> checksumPort.addPart(port, whole, from, wanted),
                () -> checksumPort.add(port, whole, from));
        return port;
    }

    private Optional<Long> readWholeNumberIn(Optional<Value> asked) {
        return asked.filter(Comparison::isNumeric).map(number -> (long) Comparison.asDouble(number));
    }

    private Value encipherIntoPort(PortValue port, Value data) {
        cryptPort.refuseWhenClosed(port);
        if (!(data instanceof BinaryValue octets)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, readTheReferenceOf(port));
        }
        cryptPort.write(port, octets.octetsFromHere());
        return port;
    }

    public Value open(PortValue port, Evaluator evaluator, Set<String> refinements) {
        requireServiceForScheme(port.schemeName());
        if (SCHEMES_CHECKED_AGAINST_THE_NET_POLICY.contains(port.schemeName())) {
            evaluator.policies().require(PolicyClass.NET, PolicyAccess.EXECUTE, Optional.of(readTheReferenceOf(port)));
        }
        if (port.schemeName().equals("tcp")) {
            return connectOrLeaveClosed(port, evaluator);
        }
        switch (port.schemeName()) {
            case "udp" -> bindDatagramPort(port, evaluator);
            case "crypt" -> startTheCipherBehindBlankingTheKeyInTheSpec(port);
            case "checksum" -> checksumPort.startEvenOnAnAlreadyOpenPort(port, checksumPort.readTheMethodOf(port));
            default -> refuseToReachOutside();
        }
        if (port.isAFile()) {
            openTheFileBehind(port, evaluator, refinements);
        }
        return markOpen(port);
    }

    private PortValue markOpen(PortValue port) {
        port.findTheEventQueue();
        if (!port.isOpen()) {
            port.markOpen(true);
        }
        return port;
    }

    private PortValue connectOrLeaveClosed(PortValue port, Evaluator evaluator) {
        try {
            NetworkPort.Connection made = evaluator.ports().network().connectTo(readHostNamedBy(port), readThePortNumberOf(port));
            port.setField("state", JavaObjectValue.of(made));
            queueWhatHappenedTo(port, "connect", evaluator);
            return markOpen(port);
        } catch (NetworkPort.Refused refused) {
            port.findTheEventQueue();
            queueWhatHappenedTo(port, "error", evaluator);
            return port;
        }
    }

    private void openTheFileBehind(PortValue port, Evaluator evaluator, Set<String> refinements) {
        SeekableFilePort seekable = new SeekableFilePort(port);
        String path = seekable.path();
        if (port.schemeName().equals("dir")) {
            requireTheFilePolicy(path, evaluator, Set.of(PolicyAccess.READ));
            if (!runThroughTheFileSystem(() -> LogicValue.of(
                    isSomethingThereFor(path, evaluator.ports().files()))).isTruthy()) {
                throw Raised.of(EvaluationFailure.CANNOT_OPEN,
                        FileValue.of(path), IntegerValue.of(OPEN_FAILED));
            }
            seekable.moveTo(0);
            return;
        }
        OpeningAFile asked = new OpeningAFile(refinements);
        asked.refuseNewFileNobodyMayWriteTo(path);
        requireTheFilePolicy(path, evaluator, asked.listTheAccessesItAsksFor());
        boolean alreadyThere = runThroughTheFileSystem(() ->
                LogicValue.of(evaluator.ports().files().exists(path))).isTruthy();
        if (!asked.mayWrite()) {
            if (!alreadyThere) {
                throw Raised.of(EvaluationFailure.CANNOT_OPEN, FileValue.of(path));
            }
        } else if (!alreadyThere || asked.emptiesWhatIsThere()) {
            runThroughTheFileSystem(() -> {
                evaluator.ports().files().write(path, new byte[0]);
                return NoneValue.none();
            });
        }
        seekable.openAt(0, asked.mayWrite());
    }

    private boolean isSomethingThereFor(String path, FilePort files) {
        FileReading.Wildcard wildcard = new FileReading.Wildcard(path);
        if (!wildcard.isPresent()) {
            return files.exists(path);
        }
        if (wildcard.isInTheDirectory()) {
            return false;
        }
        try {
            return !wildcard.listTheNamesMatchingIn(files).isEmpty();
        } catch (RuntimeException nothingThere) {
            return false;
        }
    }

    private void startTheCipherBehindBlankingTheKeyInTheSpec(PortValue port) {
        if (cryptPort.isWorking(port)) {
            throw Raised.of(EvaluationFailure.ALREADY_OPEN, readTheReferenceOf(port));
        }
        if (!(port.readFieldValue("spec") instanceof ObjectValue spec)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, port);
        }
        String algorithm = spec.readFieldValue("algorithm") instanceof AnyWordValue word
                ? word.canonical()
                : "";
        if (!cryptPort.serves(algorithm)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, spec);
        }
        cryptPort.start(port, algorithm,
                spec.readFieldValue("direction") instanceof AnyWordValue wanted
                        && wanted.canonical().equals("decrypt"),
                readOctetsInSpec(spec, "key"), readOctetsInSpec(spec, "init-vector"));
        spec.context().register("key", NoneValue.none());
        spec.context().register("init-vector", NoneValue.none());
    }

    private byte[] readOctetsInSpec(ObjectValue spec, String field) {
        return switch (spec.readFieldValue(field)) {
            case BinaryValue octets -> octets.octetsFromHere();
            case AnyStringValue text -> text.text().getBytes(StandardCharsets.UTF_8);
            default -> new byte[0];
        };
    }

    public Value update(PortValue port) {
        if (port.schemeName().equals("checksum")) {
            checksumPort.digestSoFarLeftInDataFieldAsWell(port);
            return port;
        }
        if (port.schemeName().equals("crypt")) {
            cryptPort.refuseWhenClosed(port);
            cryptPort.update(port);
            return port;
        }
        if (!SCHEMES_WHOSE_ACTOR_TAKES_AN_UPDATE.contains(port.schemeName())) {
            throw refuseWithNoActionFor("update");
        }
        return NoneValue.none();
    }

    public Value isOpen(PortValue port) {
        if (port.schemeName().equals("crypt")) {
            cryptPort.refuseWhenClosed(port);
        }
        return LogicValue.of(port.isOpen());
    }

    public Value close(PortValue port) {
        if (port.schemeName().equals("crypt")) {
            cryptPort.refuseWhenClosed(port);
            cryptPort.stop(port);
        }
        handBackConnectionBehind(port);
        port.markOpen(false);
        if (port.schemeName().equals("checksum")) {
            checksumPort.stop(port);
        }
        return port;
    }

    private void bindDatagramPort(PortValue port, Evaluator evaluator) {
        int number = findHostToSendTo(port).isEmpty()
                ? readThePortNumberOf(port)
                : WHATEVER_NUMBER_THE_MACHINE_HAS_FREE;
        runThroughTheNetwork(() -> {
            NetworkPort.Datagrams bound = evaluator.ports().network().bindTo(number);
            port.setField("state", JavaObjectValue.of(bound));
            return port;
        });
    }

    private String findHostToSendTo(PortValue port) {
        if (port.readFieldValue("spec") instanceof ObjectValue(Context context)
                && context.holds("host")
                && context.ownSlotFor("host").value() instanceof AnyStringValue host) {
            return host.text();
        }
        return "";
    }

    private NetworkPort.Datagrams findTheDatagramsBehind(PortValue port) {
        if (port.readFieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Datagrams bound) {
            return bound;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    private Value readOneDatagramInto(PortValue port, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        NetworkPort.Datagrams bound = findTheDatagramsBehind(port);
        return runThroughTheNetwork(() -> {
            BinaryValue arrived = BinaryValue.ofBytes(bound.receive());
            addToPortsData(port, arrived);
            queueWhatHappenedTo(port, "read", evaluator);
            return port;
        });
    }

    private Value sendOneDatagramFrom(PortValue port, Value data, Evaluator evaluator) {
        granted.require(HostService.NETWORK);
        byte[] bytes = switch (data) {
            case AnyStringValue written -> written.text().getBytes(StandardCharsets.UTF_8);
            case BinaryValue carried -> carried.octetsFromHere();
            default -> throw Raised.of(EvaluationFailure.INVALID_PORT_ARG, data);
        };
        NetworkPort.Datagrams bound = findTheDatagramsBehind(port);
        return runThroughTheNetwork(() -> {
            bound.sendTo(findHostToSendTo(port), readThePortNumberOf(port), bytes);
            port.setField("data", NoneValue.none());
            queueWhatHappenedTo(port, "wrote", evaluator);
            return port;
        });
    }

    private int readThePortNumberOf(PortValue port) {
        if (port.readFieldValue("spec") instanceof ObjectValue(Context context)
                && context.holds("port")
                && context.ownSlotFor("port").value() instanceof IntegerValue(long magnitude)) {
            return (int) magnitude;
        }
        return NetworkPort.findTheWellKnownPortFor(port.schemeName()).orElse(0);
    }

    private Value runThroughTheNetwork(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (NetworkPort.Refused refused) {
            throw new Raised(ErrorValue.of(ErrorCategory.ACCESS,
                    refused.errorId(), refused.getMessage(),
                    StringValue.of(refused.subject()),
                    IntegerValue.of(OPEN_FAILED)));
        }
    }

    private void handBackConnectionBehind(PortValue port) {
        if (port.readFieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            open.close();
        }
    }

    private NetworkPort.Connection findTheConnectionBehind(PortValue port) {
        if (port.readFieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            return open;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    private String readHostNamedBy(PortValue port) {
        if (port.readFieldValue("spec") instanceof ObjectValue(Context context)) {
            if (context.holds("host") && context.ownSlotFor("host").value() instanceof AnyStringValue host) {
                return host.text();
            }
            if (context.holds("ref") && context.ownSlotFor("ref").value() instanceof AnyStringValue reference) {
                String written = reference.text();
                int afterScheme = written.indexOf("://");
                return afterScheme < 0 ? written : written.substring(afterScheme + 3);
            }
        }
        return "";
    }
}
