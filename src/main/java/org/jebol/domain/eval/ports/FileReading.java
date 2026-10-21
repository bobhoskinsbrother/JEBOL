package org.jebol.domain.eval.ports;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.eval.Wildcards;
import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class FileReading {

    private final String path;
    private final Optional<Value> bound;
    private final Optional<Value> position;
    private final boolean answersText;
    private final boolean answersLines;

    FileReading(String path, PortRequest asked) {
        this.path = path;
        this.bound = asked.part();
        this.position = refuseNegative(asked.seek());
        this.answersText = asked.refinements().contains("string");
        this.answersLines = asked.refinements().contains("lines");
    }

    private Optional<Value> refuseNegative(Optional<Value> asked) {
        if (asked.isPresent() && wholeMagnitude(asked.get()) < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, asked.get());
        }
        return asked;
    }

    private long wholeMagnitude(Value number) {
        return (long) Arithmetic.asMagnitude(number);
    }

    Value answerThrough(FilePort files) {
        Wildcard wildcard = new Wildcard(path);
        if (wildcard.isPresent()) {
            return wildcard.isInDirectory()
                    ? BlockValue.of(List.of())
                    : listNamesMatchingPattern(wildcard, files);
        }
        if (path.endsWith("/") || files.isDirectory(path)) {
            return asFiles(files.listFileNames(path));
        }
        byte[] chosen = takeBytesAskedFor(files.readBytes(path));
        if (answersLines || answersText) {
            Optional<String> text = new TextBehindItsMark().decode(chosen);
            if (text.isPresent()) {
                return answersLines ? linesOf(text.orElseThrow()) : StringValue.of(text.orElseThrow());
            }
        }
        return new BinaryValue(new BinaryStorage(chosen), 1);
    }

    private Value listNamesMatchingPattern(Wildcard wildcard, FilePort files) {
        try {
            return asFiles(wildcard.listNamesMatchingIn(files));
        } catch (RuntimeException nothingThere) {
            return BlockValue.of(List.of());
        }
    }

    private Value asFiles(List<String> names) {
        return BlockValue.of(names.stream()
                .<Value>map(FileValue::of)
                .toList());
    }

    private byte[] takeBytesAskedFor(byte[] whole) {
        int from = (int) Math.min(position.map(this::wholeMagnitude).orElse(0L), whole.length);
        if (bound.isEmpty()) {
            return Arrays.copyOfRange(whole, from, whole.length);
        }
        long asked = wholeMagnitude(bound.get());
        if (asked >= 0) {
            return Arrays.copyOfRange(whole, from, (int) Math.min(from + asked, whole.length));
        }
        long backwards = -asked;
        if (backwards > from) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, bound.get());
        }
        return Arrays.copyOfRange(whole, (int) (from - backwards), from);
    }

    private Value linesOf(String text) {
        List<Value> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            if (letter == '\n' || letter == '\r') {
                lines.add(StringValue.of(line.toString()));
                line.setLength(0);
            } else {
                line.append(letter);
            }
        }
        if (!line.isEmpty()) {
            lines.add(StringValue.of(line.toString()));
        }
        return BlockValue.of(lines);
    }

    static final class Wildcard {

        private final String directory;
        private final String pattern;
        private final boolean present;

        Wildcard(String path) {
            int lastSeparator = path.lastIndexOf('/');
            this.directory = path.substring(0, lastSeparator + 1);
            this.pattern = path.substring(lastSeparator + 1);
            this.present = holdsWildcard(path);
        }

        boolean isPresent() {
            return present;
        }

        boolean isInDirectory() {
            return holdsWildcard(directory);
        }

        List<String> listNamesMatchingIn(FilePort files) {
            return files.listFileNames(directory.isEmpty() ? "." : directory).stream()
                    .filter(listed -> Wildcards.STARS_AND_QUESTION_MARKS.matchesWhole(
                            withoutItsSlash(listed), pattern))
                    .toList();
        }

        private boolean holdsWildcard(String written) {
            return written.indexOf('*') >= 0 || written.indexOf('?') >= 0;
        }

        private String withoutItsSlash(String name) {
            return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        }
    }

    static final class TextBehindItsMark {

        Optional<String> decode(byte[] bytes) {
            try {
                return Optional.of(StringValue.of(decodeStrictlyByItsMark(bytes))
                        .withOneLineFeedPerEnding().text());
            } catch (CharacterCodingException undecodable) {
                return Optional.empty();
            }
        }

        private String decodeStrictlyByItsMark(byte[] bytes) throws CharacterCodingException {
            if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
                return decodeStrictly(bytes, 3, StandardCharsets.UTF_8);
            }
            if (startsWith(bytes, 0xFF, 0xFE, 0x00, 0x00)) {
                return decodeStrictly(bytes, 4, Charset.forName("UTF-32LE"));
            }
            if (startsWith(bytes, 0x00, 0x00, 0xFE, 0xFF)) {
                return decodeStrictly(bytes, 4, Charset.forName("UTF-32BE"));
            }
            if (startsWith(bytes, 0xFE, 0xFF)) {
                return decodeStrictly(bytes, 2, StandardCharsets.UTF_16BE);
            }
            if (startsWith(bytes, 0xFF, 0xFE)) {
                return decodeStrictly(bytes, 2, StandardCharsets.UTF_16LE);
            }
            return decodeStrictly(bytes, 0, StandardCharsets.UTF_8);
        }

        private String decodeStrictly(byte[] bytes, int from, Charset charset)
                throws CharacterCodingException {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, from, bytes.length - from))
                    .toString();
        }

        private boolean startsWith(byte[] bytes, int... mark) {
            if (bytes.length < mark.length) {
                return false;
            }
            for (int at = 0; at < mark.length; at++) {
                if ((bytes[at] & 0xFF) != mark[at]) {
                    return false;
                }
            }
            return true;
        }
    }
}
