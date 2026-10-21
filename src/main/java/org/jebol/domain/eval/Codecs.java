package org.jebol.domain.eval;

import org.jebol.domain.host.DigestPort;
import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Codecs {

    private final Encodings encodings;

    public Codecs(DigestPort digests) {
        this.encodings = new Encodings(digests);
    }

    public enum Action { IDENTIFY, DECODE, ENCODE }

    public record Answer(Kind kind, Value value, int error) {

        public enum Kind { ERROR, CHECK, BINARY, TEXT, IMAGE, SOUND, BLOCK, STRING }

        static Answer checkWhoseErrorCodeIsTheInvertedResult(int error) {
            return new Answer(Kind.CHECK, LogicValue.of(error == 0), error);
        }

        static Answer string(Value text) {
            return new Answer(Kind.STRING, text, 0);
        }

        static Answer answerWithBlock(Value items) {
            return new Answer(Kind.BLOCK, items, 0);
        }

        static Answer answerWithBinary(Value bytes) {
            return new Answer(Kind.BINARY, bytes, 0);
        }

        static Answer answerNotAvailable() {
            return new Answer(Kind.ERROR, LogicValue.answerNo(), NOT_AVAILABLE);
        }
    }

    private static final int NOT_AVAILABLE = 1;

    static final List<String> REGISTERED = List.of("text", "markup", "qoi");

    public Answer run(String codec, Action action, Value data) {
        return switch (codec) {
            case "text" -> text(action, data);
            case "markup" -> handleMarkup(action, data);
            case "qoi" -> handleQoi(action, data);
            default -> Answer.answerNotAvailable();
        };
    }

    private Answer handleQoi(Action action, Value data) {
        return switch (action) {
            case IDENTIFY -> Answer.checkWhoseErrorCodeIsTheInvertedResult(
                    Qoi.identifies(readTheBytesOf(data)) ? YES : NO);
            case DECODE -> decodeImageIn(readTheBytesOf(data));
            case ENCODE -> Answer.answerWithBinary(writeBytesBlueFirst((ImageValue) data));
        };
    }

    private static final int YES = 0;

    private static final int NO = 1;

    private Answer decodeImageIn(byte[] bytes) {
        Qoi.Decoded read = Qoi.decode(bytes);
        if (read == null) {
            return Answer.answerNotAvailable();
        }
        ImageValue picture = ImageValue.of(read.wide(), read.high());
        byte[] pixels = read.pixels();
        for (int pixel = 1; pixel <= read.wide() * read.high(); pixel++) {
            int at = (pixel - 1) * 4;
            picture.storage().setColourAt(pixel,
                    pixels[at + 2] & 0xFF, pixels[at + 1] & 0xFF, pixels[at] & 0xFF);
            picture.storage().setAlphaAt(pixel, pixels[at + 3] & 0xFF);
        }
        return new Answer(Answer.Kind.IMAGE, picture, 0);
    }

    private Value writeBytesBlueFirst(ImageValue picture) {
        int wide = picture.storage().wide();
        int high = picture.storage().high();
        byte[] pixels = new byte[wide * high * 4];
        for (int pixel = 1; pixel <= wide * high; pixel++) {
            int[] channels = picture.storage().readPixelAt(pixel);
            int at = (pixel - 1) * 4;
            pixels[at] = (byte) channels[2];
            pixels[at + 1] = (byte) channels[1];
            pixels[at + 2] = (byte) channels[0];
            pixels[at + 3] = (byte) channels[3];
        }
        byte[] written = Qoi.encode(wide, high, pixels);
        int[] octets = new int[written.length];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = written[at] & 0xFF;
        }
        return BinaryValue.of(octets);
    }

    private Answer text(Action action, Value data) {
        return switch (action) {
            case IDENTIFY -> Answer.checkWhoseErrorCodeIsTheInvertedResult(YES);
            case DECODE -> Answer.string(StringValue.of(
                    encodings.textBehindAnyMark(readTheBytesOf(data))));
            case ENCODE -> Answer.answerWithBinary(BinaryValue.of());
        };
    }

    private Answer handleMarkup(Action action, Value data) {
        return switch (action) {
            case IDENTIFY -> Answer.checkWhoseErrorCodeIsTheInvertedResult(NO);
            case DECODE -> Answer.answerWithBlock(BlockValue.of(
                    parseTheMarkupOf(new String(readTheBytesOf(data), StandardCharsets.UTF_8))));
            case ENCODE -> Answer.answerNotAvailable();
        };
    }

    private byte[] readTheBytesOf(Value data) {
        BinaryValue binary = (BinaryValue) data;
        byte[] bytes = new byte[binary.lengthFromHere()];
        for (int at = 0; at < bytes.length; at++) {
            bytes[at] = (byte) binary.storage().at(binary.index() + at);
        }
        return bytes;
    }

    private List<Value> parseTheMarkupOf(String source) {
        List<Value> parts = new ArrayList<>();
        int textFrom = 0;
        int at = 0;
        while (at < source.length()) {
            int opened = source.indexOf('<', at);
            if (opened < 0) {
                break;
            }
            if (!canStartTag(source, opened + 1)) {
                at = opened + 1;
                continue;
            }
            if (opened > textFrom) {
                parts.add(StringValue.of(source.substring(textFrom, opened)));
            }
            int closed = endOfTag(source, opened + 1);
            if (closed < 0) {
                textFrom = treatANeverClosedTagAsText(opened);
                at = source.length();
                break;
            }
            parts.add(TagValue.of(source.substring(opened + 1, closed)));
            textFrom = closed + 1;
            at = textFrom;
        }
        if (textFrom < source.length()) {
            parts.add(StringValue.of(source.substring(textFrom)));
        }
        return parts;
    }

    private int treatANeverClosedTagAsText(int opened) {
        return opened;
    }

    private boolean canStartTag(String source, int at) {
        if (at >= source.length()) {
            return false;
        }
        char next = source.charAt(at);
        return Character.isLetter(next) || next == '/' || next == '?' || next == '!';
    }

    private int endOfTag(String source, int from) {
        if (source.startsWith("!--", from)) {
            return endOfComment(source, from);
        }
        for (int at = from; at < source.length(); at++) {
            char here = source.charAt(at);
            if (here == '>') {
                return at;
            }
            if (here == '"' || here == '\'') {
                int closed = skipAQuotedRun(source, here, at);
                if (closed < 0) {
                    return NOWHERE;
                }
                at = closed;
            }
        }
        return NOWHERE;
    }

    private int endOfComment(String source, int from) {
        int ended = source.indexOf("-->", from + 3);
        return ended < 0 ? NOWHERE : ended + 2;
    }

    private int skipAQuotedRun(
            String source, char quote, int at) {
        return source.indexOf(quote, at + 1);
    }

    private static final int NOWHERE = -1;
}
