package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

final class FileWriting {

    private final String path;
    private final Value data;
    private final Optional<Long> bound;
    private final Optional<Long> position;
    private final boolean atEnd;
    private final boolean oneValuePerLine;

    FileWriting(String path, Value data, PortRequest asked) {
        this.path = path;
        this.data = data;
        this.bound = refuseNegative(asked.part());
        this.position = refuseNegative(asked.seek());
        this.atEnd = asked.refinements().contains("append");
        this.oneValuePerLine = asked.refinements().contains("lines");
    }

    private Optional<Long> refuseNegative(Optional<Value> asked) {
        Optional<Long> magnitude = asked.map(number -> (long) Arithmetic.asMagnitude(number));
        if (magnitude.isPresent() && magnitude.get() < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked.get());
        }
        return magnitude;
    }

    void performThrough(FilePort files) {
        byte[] bytes = encodeBytesToWrite();
        if (position.isPresent()) {
            files.writeAt(path, clipToFileSize(files), bytes);
        } else if (atEnd) {
            files.appendTo(path, bytes);
        } else {
            files.write(path, bytes);
        }
    }

    private long clipToFileSize(FilePort files) {
        long size = files.describe(path)
                .flatMap(FileInformation::size)
                .orElse(0L);
        return Math.min(position.orElseThrow(), size);
    }

    private byte[] encodeBytesToWrite() {
        if (data instanceof BinaryValue binary) {
            return withLineFeedByteLinesAsks(boundOctets(binary.octetsFromHere()));
        }
        if (data instanceof CharacterValue(int codepoint)) {
            return encodeAsUtf8(new String(Character.toChars(codepoint)));
        }
        if (data instanceof AnyBlockValue block && oneValuePerLine) {
            return encodeAsUtf8(formEachValueOnItsOwnLine(block));
        }
        return encodeAsUtf8(withLineFeedLinesAsks(boundText(asTextToWrite())));
    }

    private String asTextToWrite() {
        return data instanceof StringValue text
                ? text.text()
                : Molder.mold(data);
    }

    private String formEachValueOnItsOwnLine(AnyBlockValue block) {
        return block.remaining().stream()
                .map(each -> Molder.form(each) + "\n")
                .collect(Collectors.joining());
    }

    private String withLineFeedLinesAsks(String text) {
        return oneValuePerLine ? text + "\n" : text;
    }

    private byte[] withLineFeedByteLinesAsks(byte[] octets) {
        if (!oneValuePerLine) {
            return octets;
        }
        byte[] fed = Arrays.copyOf(octets, octets.length + 1);
        fed[octets.length] = '\n';
        return fed;
    }

    private String boundText(String text) {
        if (bound.isEmpty()) {
            return text;
        }
        int codePoints = text.codePointCount(0, text.length());
        int kept = (int) Math.min(bound.orElseThrow(), codePoints);
        return text.substring(0, text.offsetByCodePoints(0, kept));
    }

    private byte[] boundOctets(byte[] octets) {
        if (bound.isEmpty() || bound.orElseThrow() >= octets.length) {
            return octets;
        }
        return Arrays.copyOf(octets, (int) (long) bound.orElseThrow());
    }

    private byte[] encodeAsUtf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
