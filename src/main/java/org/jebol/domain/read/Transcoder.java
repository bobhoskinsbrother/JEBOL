package org.jebol.domain.read;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Transcoder {

    public static final int MAXIMUM_NESTING = 1_000;

    public record SourceSpan(String text, int from, int to) {
    }

    public static String textOf(String source, List<SourceSpan> spans, int from, int count) {
        if (count <= 0 || from >= spans.size()) {
            return "";
        }
        int last = Math.min(from + count, spans.size()) - 1;
        int[] codepoints = source.codePoints().toArray();
        int begins = spans.get(from).from();
        return new String(codepoints, begins, spans.get(last).to() - begins);
    }

    public static List<SourceSpan> topLevelSpans(String source) {
        if (source == null) {
            throw new IllegalArgumentException("nothing to read: source was null");
        }
        Transcoder reader = new Transcoder(source, new OnlyFindingWhereValuesAre(), 1);
        try {
            reader.scanner.scanTheWholeSource();
        } catch (ScanFailure unreadable) {
            return List.of();
        }
        List<SourceSpan> spans = new ArrayList<>();
        int[] codepoints = source.codePoints().toArray();
        for (int at = 0; at < reader.scanner.topStarts().size(); at++) {
            int from = reader.codepointAt(reader.scanner.topStarts().get(at));
            int to = reader.codepointAt(reader.scanner.topEnds().get(at));
            spans.add(new SourceSpan(new String(codepoints, from, to - from), from, to));
        }
        return List.copyOf(spans);
    }

    public static Reading read(String source, long firstLine, Extent extent, Construction construction) {
        return read(source, firstLine, extent, construction, Errors.STOP_THE_READ);
    }

    public static Reading read(String source, long firstLine, Extent extent, Construction construction,
            Errors errors) {
        if (source == null) {
            throw new IllegalArgumentException("nothing to read: source was null");
        }
        Transcoder reader = new Transcoder(source, construction, (int) firstLine);
        if (extent != Extent.THE_WHOLE_SOURCE) {
            reader.scanner.stopAfterOneValue();
        }
        if (extent == Extent.THE_FIRST_VALUE_AT_EVERY_DEPTH) {
            reader.scanner.stopAtEveryDepth();
        }
        if (errors == Errors.STAND_IN_FOR_THE_VALUE) {
            reader.scanner.relax();
        }
        return reader.reading();
    }

    public enum Errors {
        STOP_THE_READ,
        STAND_IN_FOR_THE_VALUE
    }

    public enum Extent {
        THE_WHOLE_SOURCE,
        THE_FIRST_VALUE,
        THE_FIRST_VALUE_AT_EVERY_DEPTH
    }

    public record Reading(
            List<Value> valuesReadBeforeStopping,
            Optional<TranscodeResult.Failure> whyItStopped,
            int endedAtCodePoint,
            int lineEndedOn,
            Set<Integer> positionsThatBeginALine) {

        public AnyBlockValue asABlock() {
            AnyBlockValue block = BlockValue.block(valuesReadBeforeStopping);
            for (int position : positionsThatBeginALine) {
                block.storage().setLineBreakAt(position, true);
            }
            return block;
        }
    }

    public static TranscodeResult transcode(String source) {
        return transcode(source, Construction.refused());
    }

    public static TranscodeResult transcode(String source, Construction construction) {
        return transcode(source, 1, construction);
    }

    public static TranscodeResult transcode(String source, long firstLine, Construction construction) {
        Reading reading = read(source, firstLine, Extent.THE_WHOLE_SOURCE, construction);
        return reading.whyItStopped()
                .<TranscodeResult>map(failure -> failure)
                .orElseGet(() -> new TranscodeResult.Success(reading.asABlock()));
    }

    private final SourceScanner scanner;

    private final int[] codepointAtByte;

    private Transcoder(String source, Construction construction, int firstLine) {
        byte[] encoded = source.getBytes(StandardCharsets.UTF_8);
        this.scanner = new SourceScanner(encoded, firstLine, construction, MAXIMUM_NESTING);
        this.codepointAtByte = new int[encoded.length + 1];
        int codepoint = 0;
        for (int at = 0; at < encoded.length; at++) {
            codepointAtByte[at] = codepoint;
            if ((encoded[at] & 0xC0) != 0x80) {
                codepoint++;
            }
        }
        codepointAtByte[encoded.length] = codepoint;
    }

    private int codepointAt(int byteOffset) {
        return codepointAtByte[Math.max(0, Math.min(byteOffset, codepointAtByte.length - 1))];
    }

    private Reading reading() {
        try {
            SourceScanner.ScannedBlock whole = scanner.scanTheWholeSource();
            return new Reading(whole.values(), Optional.empty(), codepointAt(scanner.end()),
                    scanner.lineCount(), scanner.topLineStarts());
        } catch (ScanFailure malformed) {
            return new Reading(List.copyOf(scanner.topValues()), Optional.of(malformed.asAFailure()),
                    codepointAt(scanner.end()), scanner.lineCount(), scanner.topLineStarts());
        }
    }

    private static final class OnlyFindingWhereValuesAre implements Construction {

        @Override
        public Value madeOf(Datatype datatype, Value specification) {
            return NoneValue.none();
        }

        @Override
        public Value functionMadeFrom(AnyBlockValue spec, AnyBlockValue body) {
            return NoneValue.none();
        }
    }
}
