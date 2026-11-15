package org.jebol.adapter.cli;

import org.jebol.domain.host.ConsoleKey;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

final class TerminalKeyDecoder {

    sealed interface KeyRead permits Pressed, Ignored, Ended {
    }

    record Pressed(ConsoleKey key) implements KeyRead {
    }

    record Ignored() implements KeyRead {
    }

    record Ended() implements KeyRead {
    }

    private static final int ESC = 0x1B;
    private static final int DEL = 0x7F;
    private static final int BS = 0x08;

    private static final int PAGE_UP = 1;
    private static final int PAGE_DOWN = 2;
    private static final int END = 3;
    private static final int HOME = 4;
    private static final int LEFT = 5;
    private static final int UP = 6;
    private static final int RIGHT = 7;
    private static final int DOWN = 8;
    private static final int INSERT = 9;
    private static final int DELETE = 10;
    private static final int F1 = 11;
    private static final int F2 = 12;
    private static final int F3 = 13;
    private static final int F4 = 14;
    private static final int F5 = 15;
    private static final int F6 = 16;
    private static final int F7 = 17;
    private static final int F8 = 18;
    private static final int F9 = 19;
    private static final int F10 = 20;
    private static final int F11 = 21;
    private static final int F12 = 22;
    private static final int PASTE_START = 23;
    private static final int PASTE_END = 24;
    private static final int ESCAPE = 25;
    private static final int BACKTAB = 31;
    private static final int BACKSPACE = 32;
    private static final int BEGIN = 33;

    private final InputStream typed;
    private final int eraseCharacter;

    private boolean control;
    private boolean shift;
    private boolean alt;

    TerminalKeyDecoder(InputStream typed, int eraseCharacter) {
        this.typed = typed;
        this.eraseCharacter = eraseCharacter;
    }

    Optional<ConsoleKey> readKey() {
        while (true) {
            KeyRead read = readKeyEvent();
            if (read instanceof Pressed(ConsoleKey key)) {
                return Optional.of(key);
            }
            if (read instanceof Ended) {
                return Optional.empty();
            }
        }
    }

    KeyRead readKeyEvent() {
        control = false;
        shift = false;
        alt = false;
        int first = readByte();
        if (first < 0) {
            return new Ended();
        }
        if (first == ESC) {
            return readEscapeSequence();
        }
        if (first < 0x80) {
            if (first == DEL || first == BS) {
                control = first != eraseCharacter;
                return virtualKey(BACKSPACE);
            }
            control = first > 0 && first <= 0x1F;
            return character(first);
        }
        return readUtf8Character(first);
    }

    private KeyRead readUtf8Character(int lead) {
        int extra;
        if ((lead & 0xE0) == 0xC0) {
            extra = 1;
        } else if ((lead & 0xF0) == 0xE0) {
            extra = 2;
        } else if ((lead & 0xF8) == 0xF0) {
            extra = 3;
        } else {
            return new Ignored();
        }
        byte[] bytes = new byte[extra + 1];
        bytes[0] = (byte) lead;
        for (int each = 1; each <= extra; each++) {
            int next = readByte();
            if (next < 0) {
                return new Ended();
            }
            bytes[each] = (byte) next;
        }
        for (int each = 1; each <= extra; each++) {
            if ((bytes[each] & 0xC0) != 0x80) {
                return new Ignored();
            }
        }
        return character(Utf8Lengths.decode(bytes, 0, bytes.length));
    }

    private KeyRead readEscapeSequence() {
        if (nothingWaiting()) {
            return virtualKey(ESCAPE);
        }
        int after = readByte();
        if (after < 0) {
            return new Ended();
        }
        if (after == '[') {
            return readCsiSequence();
        }
        if (after == 'O') {
            return readSs3Sequence();
        }
        if (after == 'b' || after == 'f') {
            control = true;
            return virtualKey(after == 'b' ? LEFT : RIGHT);
        }
        if (after >= 0x20 && after < 0x7F) {
            alt = true;
            return character(after);
        }
        if (after >= 0x01 && after < 0x20) {
            alt = true;
            control = true;
            return virtualKey(after);
        }
        return new Ignored();
    }

    private KeyRead readCsiSequence() {
        int first = readByte();
        if (first < 0) {
            return new Ended();
        }
        switch (first) {
            case 'A': return virtualKey(UP);
            case 'B': return virtualKey(DOWN);
            case 'C': return virtualKey(RIGHT);
            case 'D': return virtualKey(LEFT);
            case 'E': return virtualKey(BEGIN);
            case 'F': return virtualKey(END);
            case 'H': return virtualKey(HOME);
            case 'Z': return virtualKey(BACKTAB);
            default: break;
        }
        int second = readByte();
        if (second < 0) {
            return new Ended();
        }
        return switch (first) {
            case '1' -> readCsiAfterOne(second);
            case '2' -> readCsiAfterTwo(second);
            case '3' -> readCsiAfterThree(second);
            case '4' -> virtualKey(END);
            case '5' -> readShiftableAfter(second, PAGE_UP);
            case '6' -> readShiftableAfter(second, PAGE_DOWN);
            case '7' -> virtualKey(HOME);
            case '8' -> virtualKey(END);
            default -> new Ignored();
        };
    }

    private KeyRead readCsiAfterOne(int second) {
        int third = readByte();
        if (third < 0) {
            return new Ended();
        }
        if (second == ';') {
            readModifier(third);
            int last = readByte();
            if (last < 0) {
                return new Ended();
            }
            return switch (last) {
                case 'A' -> virtualKey(UP);
                case 'B' -> virtualKey(DOWN);
                case 'C' -> virtualKey(RIGHT);
                case 'D' -> virtualKey(LEFT);
                case 'F' -> virtualKey(END);
                case 'H' -> virtualKey(HOME);
                case 'P' -> virtualKey(F1);
                case 'Q' -> virtualKey(F2);
                case 'R' -> virtualKey(F3);
                case 'S' -> virtualKey(F4);
                default -> new Ignored();
            };
        }
        if (third == '~' || third == '^') {
            control = control || third == '^';
            return switch (second) {
                case '1' -> virtualKey(F1);
                case '2' -> virtualKey(F2);
                case '3' -> virtualKey(F3);
                case '4' -> virtualKey(F4);
                case '5' -> virtualKey(F5);
                case '7' -> virtualKey(F6);
                case '8' -> virtualKey(F7);
                case '9' -> virtualKey(F8);
                default -> new Ignored();
            };
        }
        return new Ignored();
    }

    private void readModifier(int modifier) {
        switch (modifier) {
            case '2' -> shift = true;
            case '3' -> alt = true;
            case '4' -> {
                alt = true;
                shift = true;
            }
            case '5' -> control = true;
            case '6' -> {
                control = true;
                shift = true;
            }
            case '7' -> {
                control = true;
                alt = true;
            }
            case '8' -> {
                control = true;
                alt = true;
                shift = true;
            }
            default -> {
            }
        }
    }

    private KeyRead readCsiAfterTwo(int second) {
        if (second == '~') {
            return virtualKey(INSERT);
        }
        int third = readByte();
        if (third < 0) {
            return new Ended();
        }
        if (third == '~' || third == '^') {
            control = control || third == '^';
            switch (second) {
                case '0': return virtualKey(F9);
                case '1': return virtualKey(F10);
                case '3': return virtualKey(F11);
                case '4': return virtualKey(F12);
                default: break;
            }
            shift = true;
            return switch (second) {
                case '5' -> virtualKey(F5);
                case '6' -> virtualKey(F6);
                case '8' -> virtualKey(F7);
                case '9' -> virtualKey(F8);
                default -> new Ignored();
            };
        }
        if (second == '0') {
            int fourth = readByte();
            if (fourth < 0) {
                return new Ended();
            }
            if (fourth == '~' && third == '0') {
                return virtualKey(PASTE_START);
            }
            if (fourth == '~' && third == '1') {
                return virtualKey(PASTE_END);
            }
        }
        return new Ignored();
    }

    private KeyRead readCsiAfterThree(int second) {
        if (second == '~') {
            return virtualKey(DELETE);
        }
        if (second == '^') {
            control = true;
            return virtualKey(DELETE);
        }
        int third = readByte();
        if (third < 0) {
            return new Ended();
        }
        if (second == ';' && third == '5') {
            int fourth = readByte();
            if (fourth < 0) {
                return new Ended();
            }
            if (fourth == '~') {
                control = true;
                return virtualKey(DELETE);
            }
        }
        if (third == '~' || third == '^') {
            if (third == '~') {
                shift = true;
            } else {
                control = true;
            }
            return switch (second) {
                case '1' -> virtualKey(F9);
                case '2' -> virtualKey(F10);
                case '3' -> virtualKey(F11);
                case '4' -> virtualKey(F12);
                default -> new Ignored();
            };
        }
        return new Ignored();
    }

    private KeyRead readShiftableAfter(int second, int key) {
        if (second == '~') {
            return virtualKey(key);
        }
        int third = readByte();
        if (third < 0) {
            return new Ended();
        }
        if (second == ';' && third == '2') {
            int fourth = readByte();
            if (fourth < 0) {
                return new Ended();
            }
            if (fourth == '~') {
                shift = true;
                return virtualKey(key);
            }
        }
        return new Ignored();
    }

    private KeyRead readSs3Sequence() {
        int letter = readByte();
        if (letter < 0) {
            return new Ended();
        }
        return switch (letter) {
            case 'A' -> virtualKey(UP);
            case 'B' -> virtualKey(DOWN);
            case 'C' -> virtualKey(RIGHT);
            case 'D' -> virtualKey(LEFT);
            case 'H' -> virtualKey(HOME);
            case 'F' -> virtualKey(END);
            case 'P' -> virtualKey(F1);
            case 'Q' -> virtualKey(F2);
            case 'R' -> virtualKey(F3);
            case 'S' -> virtualKey(F4);
            case 'M' -> character('\r');
            case 'X' -> character('=');
            case 'j' -> character('*');
            case 'k' -> character('+');
            case 'l' -> character(',');
            case 'm' -> character('-');
            case 'n' -> character('.');
            case 'o' -> character('/');
            case 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y' -> character('0' + letter - 'p');
            default -> new Ignored();
        };
    }

    private KeyRead virtualKey(int number) {
        return new Pressed(new ConsoleKey(0, number, control, shift, alt));
    }

    private KeyRead character(int codepoint) {
        return new Pressed(new ConsoleKey(codepoint, 0, control, shift, alt));
    }

    private boolean nothingWaiting() {
        try {
            return typed.available() <= 0;
        } catch (IOException gone) {
            return true;
        }
    }

    private int readByte() {
        try {
            return typed.read();
        } catch (IOException gone) {
            return -1;
        }
    }
}
