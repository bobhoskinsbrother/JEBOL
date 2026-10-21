package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HttpPort;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.EndValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class HttpSchemeActor extends DefaultNative implements ServesPortActions {

    private static final String ARGUMENT_NOTHING_CAN_FILL = "internal";

    private final String scheme;
    private final HttpPort host;

    public HttpSchemeActor(String scheme, HttpPort host) {
        this.scheme = scheme;
        this.host = host;
        declare(BlockValue.of(List.of(NoneValue.none(), WordValue.of(ARGUMENT_NOTHING_CAN_FILL))),
                parametersAsWritten());
    }

    @Override
    public String nativeName() {
        return "the " + scheme + " actor";
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
        return switch (action) {
            case "read" -> Optional.of(fetchThroughTheHost(port, arguments, refinements, evaluator));
            case "open" -> Optional.of(open(port));
            case "close" -> Optional.of(close(port));
            default -> Optional.empty();
        };
    }

    private Value open(PortValue port) {
        port.findTheEventQueue();
        port.markOpen(true);
        return port;
    }

    private Value close(PortValue port) {
        port.markOpen(false);
        return port;
    }

    private Value fetchThroughTheHost(
            PortValue port, List<Value> arguments, Set<String> refinements, Evaluator evaluator) {

        WhatWasAsked asked = WhatWasAsked.readFrom(arguments, refinements);
        HttpPort.Answer answer = fetch(port, asked);
        port.markOpen(true);
        port.setField("data", BinaryValue.ofBytes(answer.body()));
        if (asked.all()) {
            return BlockValue.of(List.of(
                    IntegerValue.of(answer.status()),
                    headersAsBlock(answer),
                    answerBody(answer, asked)));
        }
        requireAnswerRatherThanErrorPage(answer, evaluator);
        return answerBody(answer, asked);
    }

    private HttpPort.Answer fetch(PortValue port, WhatWasAsked asked) {
        HttpPort.Request request = new HttpPort.Request(
                readTheUrlOf(port),
                readTheMethodOf(port),
                listTheHeaderNamesOf(port, asked),
                listTheHeaderValuesOf(port, asked),
                contentBytes(port),
                followsRedirects(port),
                readTheTimeoutOf(port));
        try {
            return host.fetch(request);
        } catch (HttpPort.Refused refused) {
            throw new Raised(ErrorValue.of(ErrorCategory.ACCESS,
                    refused.errorId(), refused.getMessage(),
                    StringValue.of(refused.subject())));
        }
    }

    private void requireAnswerRatherThanErrorPage(HttpPort.Answer answer, Evaluator evaluator) {
        if (answer.status() < 400 && answer.status() != 300) {
            return;
        }
        String phrase = findThePhraseFor(answer.status(), evaluator).orElse("Unknown error response");
        throw new Raised(ErrorValue.of(ErrorCategory.ACCESS,
                "protocol", phrase, StringValue.of(phrase)));
    }

    private Value answerBody(HttpPort.Answer answer, WhatWasAsked asked) {
        if (asked.binary()) {
            return trimOf(BinaryValue.ofBytes(answer.body()), asked, answer);
        }
        Value body = readTheTextOf(answer)
                .<Value>map(StringValue::of)
                .orElseGet(() -> BinaryValue.ofBytes(answer.body()));
        if (asked.string() || asked.lines()) {
            body = StringValue.of(asTextOf(body));
        }
        if (asked.lines()) {
            return BlockValue.of(((StringValue) body).linesDroppingOneTrailingEmptyLine());
        }
        return trimOf(body, asked, answer);
    }

    private Optional<String> readTheTextOf(HttpPort.Answer answer) {
        Optional<String> contentType = answer.readTheHeaderNamed("Content-Type");
        if (contentType.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> charset = findTheCharsetIn(contentType.orElseThrow());
        if (charset.isPresent()) {
            return decodeAs(charset.orElseThrow(), answer.body());
        }
        String type = contentType.orElseThrow().trim().toLowerCase(Locale.ROOT);
        return type.startsWith("text/") || type.startsWith("application/json")
                ? Optional.of(asTextOf(BinaryValue.ofBytes(answer.body())))
                : Optional.empty();
    }

    private static Optional<String> findTheCharsetIn(String contentType) {
        int named = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (named < 0) {
            return Optional.empty();
        }
        String after = contentType.substring(named + "charset=".length()).trim();
        if (after.startsWith("\"")) {
            int closed = after.indexOf('"', 1);
            return closed < 0 ? Optional.empty() : Optional.of(after.substring(1, closed));
        }
        int ended = after.indexOf(';');
        String name = (ended < 0 ? after : after.substring(0, ended)).trim();
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    private static Optional<String> decodeAs(String charset, byte[] body) {
        try {
            return Optional.of(Charset.forName(charset).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString());
        } catch (IllegalArgumentException | CharacterCodingException undecodable) {
            return Optional.empty();
        }
    }

    private static String asTextOf(Value body) {
        if (body instanceof AnyStringValue text) {
            return text.text();
        }
        byte[] bytes = ((BinaryValue) body).octetsFromHere();
        return new FileReading.TextBehindItsMark().decode(bytes)
                .orElseGet(() -> new String(bytes, StandardCharsets.UTF_8));
    }

    private Value trimOf(Value body, WhatWasAsked asked, HttpPort.Answer answer) {
        if (answer.status() == 206) {
            return body;
        }
        RebolSeries octetsOrText = (RebolSeries) body;
        RebolSeries from = asked.index() == 0
                ? octetsOrText
                : octetsOrText.atIndex((int) Math.min(asked.index() + 1, octetsOrText.storageLength() + 1));
        if (asked.length().isEmpty()) {
            return from;
        }
        return from.copyFront((int) Math.min(asked.length().orElseThrow(), from.lengthFromHere()),
                false, Set.of());
    }

    private Value headersAsBlock(HttpPort.Answer answer) {
        List<Value> flat = new ArrayList<>();
        for (int at = 0; at < answer.headerNames().size(); at++) {
            flat.add(StringValue.of(answer.headerNames().get(at)));
            flat.add(StringValue.of(answer.headerValues().get(at)));
        }
        return BlockValue.of(flat);
    }

    private Optional<String> findThePhraseFor(int status, Evaluator evaluator) {
        Value system = evaluator.systemContext().findSlotFor("system").value();
        Value codes = system.readFieldValue("catalog").readFieldValue("http-status-codes");
        if (!(codes instanceof MapValue phrases)) {
            return Optional.empty();
        }
        return Optional.ofNullable(phrases.select(IntegerValue.of(status)))
                .filter(AnyStringValue.class::isInstance)
                .map(phrase -> ((AnyStringValue) phrase).text());
    }

    private String readTheUrlOf(PortValue port) {
        Value spec = port.readFieldValue("spec");
        Value reference = spec.readFieldValue("ref");
        if (reference instanceof AnyStringValue written && !(reference instanceof FileValue)) {
            return written.text();
        }
        String hostName = readAnyStringIn(spec, "host");
        long portNumber = readAnIntegerIn(spec, "port").orElse(0L);
        String path = readAnyStringIn(spec, "path");
        String namedPort = portNumber == 0 ? "" : ":" + portNumber;
        return scheme + "://" + hostName + namedPort + (path.isEmpty() ? "/" : path);
    }

    private String readTheMethodOf(PortValue port) {
        Value method = port.readFieldValue("spec").readFieldValue("method");
        return method instanceof AnyWordValue word ? word.spelling() : "GET";
    }

    private List<String> listTheHeaderNamesOf(PortValue port, WhatWasAsked asked) {
        List<Value> flat = flattenTheHeadersOf(port);
        List<String> names = new ArrayList<>();
        for (int at = 0; at < flat.size(); at += 2) {
            names.add(((AnyStringValue) flat.get(at)).text());
        }
        asked.range().ifPresent(range -> names.add("Range"));
        return names;
    }

    private List<String> listTheHeaderValuesOf(PortValue port, WhatWasAsked asked) {
        List<Value> flat = flattenTheHeadersOf(port);
        List<String> values = new ArrayList<>();
        for (int at = 1; at < flat.size(); at += 2) {
            values.add(((AnyStringValue) flat.get(at)).text());
        }
        asked.range().ifPresent(range -> values.add(range));
        return values;
    }

    private List<Value> flattenTheHeadersOf(PortValue port) {
        Value headers = port.readFieldValue("spec").readFieldValue("headers");
        if (!(headers instanceof AnyBlockValue written)) {
            return List.of();
        }
        List<Value> flat = new ArrayList<>();
        for (Value each : written.remaining()) {
            if (each instanceof AnyWordValue word) {
                flat.add(StringValue.of(word.spelling()));
            } else if (each instanceof AnyStringValue text) {
                flat.add(StringValue.of(text.text()));
            }
        }
        return flat;
    }

    private byte[] contentBytes(PortValue port) {
        Value content = port.readFieldValue("spec").readFieldValue("content");
        return switch (content) {
            case AnyStringValue written -> written.asOctets();
            case BinaryValue carried -> carried.octetsFromHere();
            default -> new byte[0];
        };
    }

    private boolean followsRedirects(PortValue port) {
        Value redirects = port.readFieldValue("spec").readFieldValue("redirect?");
        return !(redirects instanceof NoneValue) && redirects.isTruthy();
    }

    private Optional<Duration> readTheTimeoutOf(PortValue port) {
        return readAnIntegerIn(port.readFieldValue("spec"), "timeout")
                .filter(seconds -> seconds > 0)
                .map(Duration::ofSeconds);
    }

    private static String readAnyStringIn(Value spec, String field) {
        Value held = spec.readFieldValue(field);
        return held instanceof AnyStringValue written ? written.text() : "";
    }

    private static Optional<Long> readAnIntegerIn(Value spec, String field) {
        Value held = spec.readFieldValue(field);
        return held instanceof IntegerValue(long magnitude)
                ? Optional.of(magnitude)
                : Optional.empty();
    }

    private record WhatWasAsked(
            boolean binary, boolean string, boolean lines, boolean all, boolean partial,
            long index, Optional<Long> length) {

        static WhatWasAsked readFrom(List<Value> arguments, Set<String> refinements) {
            if (refinements.contains("lines") && refinements.contains("binary")) {
                throw Raised.of(EvaluationFailure.BAD_REFINE, WordValue.of("binary"));
            }
            if (refinements.contains("string") && refinements.contains("binary")) {
                throw Raised.of(EvaluationFailure.BAD_REFINES);
            }
            boolean string = refinements.contains("string");
            boolean lines = refinements.contains("lines");
            boolean partial = !lines && (refinements.contains("part") || refinements.contains("seek"));
            long index = partial
                    ? readTheNumberAt(arguments, refinements.contains("part") ? 2 : 1).orElse(0L)
                    : 0L;
            Optional<Long> part = partial ? readTheNumberAt(arguments, 1) : Optional.empty();
            boolean binary = refinements.contains("binary") || (partial && !string);
            return new WhatWasAsked(binary, string, lines, refinements.contains("all"), partial,
                    index, part.map(count -> count + index));
        }

        private static Optional<Long> readTheNumberAt(List<Value> arguments, int position) {
            return position < arguments.size() && arguments.get(position) instanceof IntegerValue(long magnitude)
                    ? Optional.of(magnitude)
                    : Optional.empty();
        }

        Optional<String> range() {
            if (!partial) {
                return Optional.empty();
            }
            return Optional.of("bytes=" + index + "-"
                    + length.map(wanted -> String.valueOf(wanted - 1)).orElse(""));
        }
    }
}
