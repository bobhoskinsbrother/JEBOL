package org.jebol.adapter.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class TerminalLineEditor {

    private static final int TERM_BUF_LEN = 4096;
    private static final int READ_BUF_LEN = 64;
    private static final int MAX_HISTORY = 300;
    private static final int ROOM_PAST_THE_BUFFER = 8;

    private static final int BEL = 7;
    private static final int BS = 8;
    private static final int TAB = 9;
    private static final int LF = 10;
    private static final int CR = 13;
    private static final int ESC = 27;
    private static final int DEL = 127;
    private static final int CTRL_A = 1;
    private static final int CTRL_B = 2;
    private static final int CTRL_D = 4;
    private static final int CTRL_E = 5;
    private static final int CTRL_F = 6;
    private static final int CTRL_K = 11;
    private static final int CTRL_U = 21;
    private static final int SPACE = ' ';
    private static final int SPACES_FOR_A_TAB = 4;

    private static final String ESCAPE_SHOWN = "[ESC]";
    private static final String ESCAPE_LINE = "\n\u0000";

    private static final int CTRL_C = 3;

    private final InputStream typed;
    private final OutputStream shown;
    private final Runnable escape;
    private final byte[] buffer = new byte[TERM_BUF_LEN + ROOM_PAST_THE_BUFFER];
    private final List<byte[]> history = new ArrayList<>(List.of(new byte[0]));
    private byte[] residue = new byte[0];
    private byte[] chunk = new byte[0];
    private byte[] stored;
    private int pos;
    private int end;
    private int hist;

    TerminalLineEditor(InputStream typed, OutputStream shown, Runnable escape) {
        this.typed = typed;
        this.shown = shown;
        this.escape = escape;
    }

    String readLine() {
        pos = 0;
        end = 0;
        hist = history.size();
        stored = null;
        buffer[0] = 0;
        boolean lineEnded = false;
        int cp;
        do {
            if (!readBytes()) {
                flush();
                return null;
            }
            for (cp = 0; at(cp) != 0; ) {
                if (at(cp) == ESC && at(cp + 1) == 0) {
                    writeText(ESCAPE_SHOWN + "\n");
                    flush();
                    return ESCAPE_LINE;
                }
                int started = cp;
                cp = processKey(cp);
                if (at(started) == CR || at(started) == LF) {
                    lineEnded = true;
                    break;
                }
            }
        } while (!lineEnded && stored == null);
        keepWhatIsLeft(cp);
        flush();
        return new String(stored, StandardCharsets.UTF_8) + "\n";
    }

    private void keepWhatIsLeft(int cp) {
        int left = lengthAsString(chunk, cp);
        if (left > 0 && residue.length + left < TERM_BUF_LEN - 1) {
            byte[] longer = Arrays.copyOf(residue, residue.length + left);
            System.arraycopy(chunk, cp, longer, residue.length, left);
            residue = longer;
        }
    }

    private boolean readBytes() {
        int wanted = READ_BUF_LEN - 2;
        byte[] taken;
        if (residue.length > 0) {
            int length = Math.min(wanted, residue.length);
            taken = Arrays.copyOf(residue, length);
            residue = Arrays.copyOfRange(residue, length, residue.length);
        } else {
            flush();
            byte[] read = new byte[wanted];
            int length = readTyped(read, wanted);
            if (length < 0) {
                return false;
            }
            taken = afterLastCtrlC(Arrays.copyOf(read, length));
        }
        chunk = Arrays.copyOf(taken, taken.length + 2);
        return true;
    }

    private byte[] afterLastCtrlC(byte[] read) {
        int after = 0;
        for (int at = 0; at < read.length; at++) {
            if (read[at] == CTRL_C) {
                escape.run();
                after = at + 1;
            }
        }
        return Arrays.copyOfRange(read, after, read.length);
    }

    private int readTyped(byte[] into, int wanted) {
        try {
            return typed.read(into, 0, wanted);
        } catch (IOException terminated) {
            return -1;
        }
    }

    private int at(int index) {
        return index >= 0 && index < chunk.length ? chunk[index] & 0xFF : 0;
    }

    private int processKey(int start) {
        int cp = start;
        if (at(cp) == 0) {
            return cp;
        }
        if (at(cp) == ESC) {
            cp++;
            if (at(cp) == 0) {
                writeText(ESCAPE_SHOWN + "\n");
                buffer[0] = LF;
                buffer[1] = 0;
                pos = 1;
                return cp;
            } else if (at(cp) == '[' || at(cp) == 'O') {
                cp++;
                switch (at(cp)) {
                    case 'A' -> recallHistory(-1);
                    case 'B' -> recallHistory(1);
                    case 'D' -> moveCursor(-1);
                    case 'C' -> moveCursor(1);
                    case '1' -> {
                        homeLine();
                        cp++;
                    }
                    case '4' -> {
                        endLine();
                        cp++;
                    }
                    case '3' -> {
                        deleteCharacter(false);
                        cp++;
                    }
                    case 'H' -> homeLine();
                    case 'F' -> endLine();
                    default -> {
                        writeText(ESCAPE_SHOWN);
                        cp--;
                    }
                }
            } else {
                switch (at(cp)) {
                    case 'H' -> homeLine();
                    case 'F' -> endLine();
                    case 'b' -> moveToWordBoundary(true);
                    case 'f' -> moveToWordBoundary(false);
                    default -> writeRepeated(BEL, 1);
                }
            }
        } else {
            switch (at(cp)) {
                case BS, DEL -> deleteCharacter(true);
                case CR, LF -> {
                    if (at(cp) == CR && at(cp + 1) == LF) {
                        cp++;
                    }
                    writeText("\r\n");
                    storeLine();
                }
                case CTRL_A -> homeLine();
                case CTRL_B -> moveCursor(-1);
                case CTRL_D -> deleteCharacter(false);
                case CTRL_E -> endLine();
                case CTRL_F -> moveCursor(1);
                case CTRL_K -> clearLine(false);
                case CTRL_U -> clearLine(true);
                default -> cp = insertCharacter(cp) - 1;
            }
        }
        return cp + 1;
    }

    private void recallHistory(int step) {
        hist += step;
        int length = end;
        homeLine();
        recallLine();
        length = length <= end ? 0 : end - length;
        showLine(length - 1);
    }

    private void storeLine() {
        buffer[end] = 0;
        stored = Arrays.copyOf(buffer, end);
        if (history.size() >= MAX_HISTORY) {
            history.remove(1);
        }
        history.add(stored);
    }

    private void recallLine() {
        if (hist < 0) {
            hist = 0;
        }
        if (hist == 0) {
            writeRepeated(BEL, 1);
        }
        if (hist >= history.size()) {
            hist = history.size();
            buffer[0] = 0;
            pos = 0;
            end = 0;
        } else {
            byte[] recalled = history.get(hist);
            System.arraycopy(recalled, 0, buffer, 0, recalled.length);
            pos = recalled.length;
            end = recalled.length;
        }
    }

    private void clearLine(boolean back) {
        int bytes = end - pos;
        if (back) {
            writeRepeated(BS, columnsIn(0, pos));
            int columns = columnsIn(0, end);
            writeRepeated(SPACE, columns);
            writeRepeated(BS, columns);
            if (bytes > 0) {
                System.arraycopy(buffer, pos, buffer, 0, bytes);
                writeBuffer(0, bytes);
                writeRepeated(BS, columnsIn(0, bytes));
            }
            end = bytes;
            pos = 0;
        } else {
            int columns = columnsIn(pos, bytes);
            writeRepeated(SPACE, columns);
            writeRepeated(BS, columns);
            buffer[end + 1] = 0;
            end = pos;
        }
    }

    private void homeLine() {
        writeRepeated(BS, columnsIn(0, pos));
        pos = 0;
    }

    private void endLine() {
        int length = end - pos;
        if (length > 0) {
            writeBuffer(pos, length);
            pos = end;
        }
    }

    private void showLine(int blanks) {
        int length;
        if (pos < 0) {
            pos = 0;
        } else if (pos > end) {
            pos = end;
        }
        int spaces = blanks;
        if (spaces >= 0) {
            length = end - pos;
            writeBuffer(pos, length);
        } else {
            writeBuffer(0, end);
            spaces = -spaces;
            length = 0;
        }
        writeRepeated(SPACE, spaces);
        writeRepeated(BS, spaces + columnsIn(pos, length));
    }

    private int insertCharacter(int start) {
        int cp = start;
        if (at(cp) == TAB) {
            int bytes = SPACES_FOR_A_TAB;
            if (end < TERM_BUF_LEN - bytes) {
                moveWithinBuffer(pos, pos + bytes, bytes + end - pos);
            }
            do {
                writeRepeated(SPACE, 1);
                if (pos < TERM_BUF_LEN) {
                    buffer[pos] = SPACE;
                    end++;
                    pos++;
                }
            } while (--bytes > 0);
            return cp + 1;
        } else if (at(cp) < SPACE) {
            writeRepeated(BEL, 1);
            return cp + 1;
        }
        int bytes = Utf8Lengths.encodedLength(at(cp));
        if (end < TERM_BUF_LEN - bytes) {
            if (pos < end) {
                moveWithinBuffer(pos, pos + bytes, bytes + end - pos);
            }
            do {
                writeRepeated(at(cp), 1);
                buffer[pos] = (byte) at(cp);
                end++;
                pos++;
                cp++;
            } while (--bytes > 0);
            showLine(0);
        }
        return cp;
    }

    private void deleteCharacter(boolean back) {
        if (pos == end && !back) {
            return;
        }
        if (pos == 0 && back) {
            return;
        }
        int stepBack = 0;
        if (back) {
            stepBackward();
            stepBack = columnsOfCharacterAt(pos);
        }
        int encoded = Utf8Lengths.encodedLength(buffer[pos] & 0xFF);
        int length = encoded + end - pos;
        if (length > 0) {
            moveWithinBuffer(pos + encoded, pos, length);
            writeRepeated(BS, stepBack);
            end -= encoded;
            showLine(1);
        } else {
            pos = 0;
        }
    }

    private void moveCursor(int count) {
        if (count < 0) {
            if (pos > 0) {
                stepBackward();
                writeRepeated(BS, columnsOfCharacterAt(pos));
            }
        } else if (pos < end) {
            int encoded = Utf8Lengths.encodedLength(buffer[pos] & 0xFF);
            writeBuffer(pos, encoded);
            pos += encoded;
        }
    }

    private void moveToWordBoundary(boolean back) {
        if (back) {
            moveBackToWordBoundary();
        } else {
            moveForwardToWordBoundary();
        }
    }

    private void moveBackToWordBoundary() {
        if (pos == 0) {
            return;
        }
        if (buffer[pos - 1] == SPACE) {
            while (pos > 0) {
                stepBackward();
                writeRepeated(BS, columnsOfCharacterAt(pos));
                if (buffer[pos] != SPACE) {
                    break;
                }
            }
        }
        while (pos > 0) {
            int was = pos;
            stepBackward();
            if (buffer[pos] == SPACE) {
                pos = was;
                break;
            }
            writeRepeated(BS, columnsOfCharacterAt(pos));
        }
    }

    private void moveForwardToWordBoundary() {
        if (pos == end) {
            return;
        }
        if (buffer[pos] == SPACE) {
            while (pos < end) {
                int was = pos;
                stepForward();
                if (was > end) {
                    pos = was;
                    break;
                }
                writeBuffer(was, pos - was);
                if (buffer[pos] != SPACE) {
                    break;
                }
            }
        }
        while (pos < end) {
            int was = pos;
            stepForward();
            if (was > end) {
                pos = was;
                break;
            }
            writeBuffer(was, pos - was);
            if (buffer[pos] == SPACE) {
                break;
            }
        }
    }

    private void stepForward() {
        pos += Utf8Lengths.encodedLength(buffer[pos] & 0xFF);
    }

    private void stepBackward() {
        do {
            pos--;
        } while (pos > 0 && (buffer[pos] & 0xC0) == 0x80);
    }

    private void moveWithinBuffer(int from, int to, int length) {
        int room = buffer.length - Math.max(from, to);
        if (length > 0 && room > 0) {
            System.arraycopy(buffer, from, buffer, to, Math.min(length, room));
        }
    }

    private int columnsIn(int from, int bytes) {
        int columns = 0;
        int at = from;
        int limit = Math.min(from + bytes, buffer.length);
        while (at < limit) {
            int length = Utf8Lengths.decodedLength(buffer, at, limit);
            if (length <= 0) {
                break;
            }
            columns += TerminalCharacterWidth.of(Utf8Lengths.decode(buffer, at, length));
            at += length;
        }
        return columns;
    }

    private int columnsOfCharacterAt(int at) {
        int length = Utf8Lengths.decodedLength(buffer, at, buffer.length);
        return length <= 0 ? 0 : TerminalCharacterWidth.of(Utf8Lengths.decode(buffer, at, length));
    }

    private static int lengthAsString(byte[] bytes, int from) {
        int at = from;
        while (at < bytes.length && bytes[at] != 0) {
            at++;
        }
        return at - from;
    }

    private void writeRepeated(int character, int times) {
        for (int written = 0; written < times; written++) {
            write(new byte[] {(byte) character}, 0, 1);
        }
    }

    private void writeBuffer(int from, int length) {
        if (length > 0) {
            write(buffer, from, Math.min(length, buffer.length - from));
        }
    }

    private void writeText(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        write(bytes, 0, bytes.length);
    }

    private void write(byte[] bytes, int from, int length) {
        try {
            shown.write(bytes, from, length);
        } catch (IOException couldNotShow) {
            throw new UncheckedIOException(couldNotShow);
        }
    }

    private void flush() {
        try {
            shown.flush();
        } catch (IOException couldNotShow) {
            throw new UncheckedIOException(couldNotShow);
        }
    }
}
