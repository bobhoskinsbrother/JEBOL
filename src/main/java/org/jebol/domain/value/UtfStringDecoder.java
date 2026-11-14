package org.jebol.domain.value;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class UtfStringDecoder {

    private UtfStringDecoder() {
    }

    public sealed interface Decoded permits Text, Refused {
    }

    public record Text(String text) implements Decoded {
    }

    public record Refused(int acceptedRunEnd) implements Decoded {
    }

    private static final int PAIR_LENGTH = 6;

    private static final int UTF8_MARK_LENGTH = 3;

    public static Decoded decode(byte[] bytes, int from) {
        int remaining = bytes.length - from;
        if (remaining <= 0) {
            return new Text("");
        }
        if (startsWith(bytes, from, 0xEF, 0xBB, 0xBF)) {
            return decodeUtf8(bytes, from + UTF8_MARK_LENGTH);
        }
        if (startsWith(bytes, from, 0xFE, 0xFF)) {
            return decodeByMark(bytes, from, 2, StandardCharsets.UTF_16BE);
        }
        if (startsWith(bytes, from, 0xFF, 0xFE, 0x00, 0x00)) {
            return decodeByMark(bytes, from, 4, Charset.forName("UTF-32LE"));
        }
        if (startsWith(bytes, from, 0xFF, 0xFE)) {
            return decodeByMark(bytes, from, 2, StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, from, 0x00, 0x00, 0xFE, 0xFF)) {
            return decodeByMark(bytes, from, 4, Charset.forName("UTF-32BE"));
        }
        return decodeUtf8(bytes, from);
    }

    private static Decoded decodeUtf8(byte[] bytes, int from) {
        if (from >= bytes.length) {
            return new Refused(from);
        }
        byte[] stored = new byte[bytes.length - from];
        int storedLength = 0;
        StrictUtf8Steps steps = new StrictUtf8Steps();
        int start = from;
        int accepted = from;
        for (int at = from; at < bytes.length; at++) {
            StrictUtf8Steps.Outcome outcome = steps.step(bytes[at] & 0xFF);
            if (outcome == StrictUtf8Steps.Outcome.ACCEPTED) {
                accepted = at + 1;
            } else if (outcome == StrictUtf8Steps.Outcome.REJECTED) {
                int joined = joinSurrogatePairAt(bytes, from, at - 1);
                if (joined >= 0) {
                    storedLength = copyRun(bytes, start, at - 1, stored, storedLength);
                    storedLength = writeFourByteSequence(joined, stored, storedLength);
                    at += PAIR_LENGTH - 1;
                    start = at;
                    steps.reset();
                }
            }
        }
        if (!steps.isAccepting()) {
            return new Refused(accepted);
        }
        storedLength = copyRun(bytes, start, bytes.length, stored, storedLength);
        return new Text(new String(stored, 0, storedLength, StandardCharsets.UTF_8));
    }

    private static int copyRun(byte[] bytes, int from, int to, byte[] stored, int storedLength) {
        if (from >= to) {
            return storedLength;
        }
        System.arraycopy(bytes, from, stored, storedLength, to - from);
        return storedLength + to - from;
    }

    private static int writeFourByteSequence(int codepoint, byte[] stored, int storedLength) {
        stored[storedLength] = (byte) (0xF0 | codepoint >> 18);
        stored[storedLength + 1] = (byte) (0x80 | codepoint >> 12 & 0x3F);
        stored[storedLength + 2] = (byte) (0x80 | codepoint >> 6 & 0x3F);
        stored[storedLength + 3] = (byte) (0x80 | codepoint & 0x3F);
        return storedLength + 4;
    }

    private static int joinSurrogatePairAt(byte[] bytes, int from, int at) {
        if (at < from) {
            return -1;
        }
        int high = maskAsSurrogateAt(bytes, at);
        int low = maskAsSurrogateAt(bytes, at + 3);
        if (high < 0xD800 || high > 0xDBFF || low < 0xDC00 || low > 0xDFFF) {
            return -1;
        }
        return 0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00);
    }

    private static int maskAsSurrogateAt(byte[] bytes, int at) {
        return (readOrZero(bytes, at) & 0x0F) << 12
                | (readOrZero(bytes, at + 1) & 0x3F) << 6
                | readOrZero(bytes, at + 2) & 0x3F;
    }

    private static int readOrZero(byte[] bytes, int at) {
        return at < bytes.length ? bytes[at] & 0xFF : 0;
    }

    private static Decoded decodeByMark(byte[] bytes, int from, int markLength, Charset charset) {
        try {
            return new Text(charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, from + markLength, bytes.length - from - markLength))
                    .toString());
        } catch (CharacterCodingException notText) {
            return new Refused(from);
        }
    }

    private static boolean startsWith(byte[] bytes, int from, int... mark) {
        if (bytes.length - from < mark.length) {
            return false;
        }
        for (int at = 0; at < mark.length; at++) {
            if ((bytes[from + at] & 0xFF) != mark[at]) {
                return false;
            }
        }
        return true;
    }

    private static final class StrictUtf8Steps {

        enum Outcome { ACCEPTED, REJECTED, INCOMPLETE }

        private boolean rejected;
        private int continuationsDue;
        private int lowest = 0x80;
        private int highest = 0xBF;

        void reset() {
            rejected = false;
            continuationsDue = 0;
            lowest = 0x80;
            highest = 0xBF;
        }

        boolean isAccepting() {
            return !rejected && continuationsDue == 0;
        }

        Outcome step(int octet) {
            if (rejected) {
                return Outcome.REJECTED;
            }
            if (continuationsDue > 0) {
                return continueSequence(octet);
            }
            return beginSequence(octet);
        }

        private Outcome continueSequence(int octet) {
            if (octet < lowest || octet > highest) {
                return reject();
            }
            continuationsDue--;
            lowest = 0x80;
            highest = 0xBF;
            return continuationsDue == 0 ? Outcome.ACCEPTED : Outcome.INCOMPLETE;
        }

        private Outcome beginSequence(int octet) {
            if (octet < 0x80) {
                return Outcome.ACCEPTED;
            }
            if (octet >= 0xC2 && octet <= 0xDF) {
                return expect(1, 0x80, 0xBF);
            }
            if (octet == 0xE0) {
                return expect(2, 0xA0, 0xBF);
            }
            if (octet == 0xED) {
                return expect(2, 0x80, 0x9F);
            }
            if (octet >= 0xE1 && octet <= 0xEF) {
                return expect(2, 0x80, 0xBF);
            }
            if (octet == 0xF0) {
                return expect(3, 0x90, 0xBF);
            }
            if (octet >= 0xF1 && octet <= 0xF3) {
                return expect(3, 0x80, 0xBF);
            }
            if (octet == 0xF4) {
                return expect(3, 0x80, 0x8F);
            }
            return reject();
        }

        private Outcome expect(int continuations, int lowestNext, int highestNext) {
            continuationsDue = continuations;
            lowest = lowestNext;
            highest = highestNext;
            return Outcome.INCOMPLETE;
        }

        private Outcome reject() {
            rejected = true;
            return Outcome.REJECTED;
        }
    }
}
