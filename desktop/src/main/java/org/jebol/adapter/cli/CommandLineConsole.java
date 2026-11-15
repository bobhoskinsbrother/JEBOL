package org.jebol.adapter.cli;

import org.jebol.domain.host.ConsoleKey;
import org.jebol.domain.host.ConsoleMode;
import org.jebol.domain.host.ConsolePort;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class CommandLineConsole implements ConsolePort, AutoCloseable {

    private static final List<String> EDITORS_SETTINGS =
            List.of("-echo", "-icanon", "-icrnl", "-inlcr", "onlcr", "min", "1", "time", "0");

    private static final List<String> RAW_SETTINGS = List.of("-ixon", "-ixoff", "susp", "undef", "quit", "undef");

    private static final String CONCEAL = "\u001b[8m";
    private static final String REVEAL = "\u001b[28m";
    private static final String BRACKETED_PASTE_ON = "\u001b[?2004h";
    private static final String BRACKETED_PASTE_OFF = "\u001b[?2004l";

    private static final List<String> SIGNALS_OFF = List.of("-isig");
    private static final List<String> SIGNALS_ON = List.of("isig");

    private static final int READ_SIZE = 32 * 1024;
    private static final int KEY_POLL_MILLISECONDS = 40;
    private static final int CR = '\r';
    private static final int LF = '\n';
    private static final int ESC = 0x1B;
    private static final int DEL = 0x7F;
    private static final int VALUE_BITS = 0xFFFF;

    private final InputStream typed;
    private final StreamOutput output;
    private final Optional<TerminalSettings> terminal;
    private final boolean honoursModes;
    private final boolean isTerminal;
    private final TerminalLineEditor editor;
    private final TerminalKeyDecoder decoder;
    private final String asFound;
    private final String editorsMode;

    private byte[] pending = new byte[0];
    private boolean lineMode = true;
    private ConsoleMode lastModeSet = ConsoleMode.ECHO;
    private boolean lastSetting;
    private boolean closed;
    private Runnable escape = () -> {
    };
    private BooleanSupplier escapePending = () -> false;

    private CommandLineConsole(InputStream typed, StreamOutput output, OutputStream shown,
                               Optional<TerminalSettings> terminal, boolean honoursModes, boolean isTerminal) {
        this.typed = typed;
        this.output = output;
        this.terminal = terminal;
        this.honoursModes = honoursModes;
        this.isTerminal = isTerminal;
        if (terminal.isPresent()) {
            TerminalSettings settings = terminal.orElseThrow();
            this.asFound = settings.read();
            settings.change(EDITORS_SETTINGS);
            this.editorsMode = settings.read();
            this.editor = new TerminalLineEditor(typed, shown, () -> escape.run());
            this.decoder = new TerminalKeyDecoder(typed, settings.eraseCharacter());
        } else {
            this.asFound = "";
            this.editorsMode = "";
            this.editor = null;
            this.decoder = null;
        }
    }

    static CommandLineConsole atTerminal(InputStream typed, StreamOutput output, OutputStream shown,
                                         TerminalSettings settings) {
        return new CommandLineConsole(typed, output, shown, Optional.of(settings), true, true);
    }

    static CommandLineConsole throughPipes(InputStream typed, StreamOutput output, OutputStream shown) {
        return new CommandLineConsole(typed, output, shown, Optional.empty(), true, false);
    }

    static CommandLineConsole onWindows(InputStream typed, StreamOutput output, boolean isTerminal) {
        return new CommandLineConsole(typed, output, OutputStream.nullOutputStream(), Optional.empty(),
                false, isTerminal);
    }

    static CommandLineConsole forThisProcess(InputStream typed, StreamOutput output, PrintStream shown) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return onWindows(typed, output, System.console() != null);
        }
        return isStandardInputATerminal()
                ? atTerminal(typed, output, shown, new SttyTerminal())
                : throughPipes(typed, output, shown);
    }

    private static boolean isStandardInputATerminal() {
        try {
            Process asking = new ProcessBuilder("sh", "-c", "test -t 0")
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return asking.waitFor() == 0;
        } catch (IOException couldNotAsk) {
            return false;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String readLine() {
        if (editor != null) {
            return withoutFinalLineFeed(withCtrlCAsByte(editor::readLine));
        }
        byte[] line = readLineBytes();
        return line == null ? null : decode(line);
    }

    @Override
    public byte[] readLineBytes() {
        if (editor != null) {
            String line = readLine();
            return line == null ? null : line.getBytes(StandardCharsets.UTF_8);
        }
        if (pending.length > 0) {
            return takePendingLine();
        }
        byte[] read = new byte[READ_SIZE];
        int length = readTyped(read);
        if (length < 0) {
            return null;
        }
        return lineMode ? withoutFinalLineFeed(read, length) : Arrays.copyOf(read, length);
    }

    String nextPromptLine() {
        if (editor != null) {
            return withoutFinalLineFeed(withCtrlCAsByte(editor::readLine));
        }
        while (indexOfLineFeed() < 0) {
            byte[] read = new byte[READ_SIZE];
            int length = readTyped(read);
            if (length < 0) {
                if (pending.length == 0) {
                    return null;
                }
                byte[] last = pending;
                pending = new byte[0];
                return decode(last);
            }
            appendPending(read, length);
        }
        return withoutFinalCarriageReturn(decode(takePendingLine()));
    }

    @Override
    public String readHiddenLine() {
        return withCtrlCAsByte(this::readHiddenBytes);
    }

    private String readHiddenBytes() {
        ByteArrayOutputStream typedSoFar = new ByteArrayOutputStream();
        while (true) {
            int next = nextHiddenByte();
            if (next < 0 || next == CR) {
                break;
            }
            if (next == ESC) {
                return null;
            }
            if (next == ConsoleKey.CTRL_C && terminal.isPresent()) {
                escape.run();
            } else if (next == DEL) {
                byte[] kept = typedSoFar.toByteArray();
                typedSoFar.reset();
                typedSoFar.write(kept, 0, lengthAfterBackingOverCharacter(kept));
            } else {
                typedSoFar.write(next);
            }
        }
        return decode(typedSoFar.toByteArray());
    }

    private static int lengthAfterBackingOverCharacter(byte[] kept) {
        int at = kept.length;
        if (at == 0) {
            return 0;
        }
        do {
            at--;
        } while (at != 0 && (kept[at] & 0xC0) == 0x80);
        return at;
    }

    @Override
    public Optional<ConsoleKey> readKey() {
        if (!honoursModes) {
            return Optional.empty();
        }
        if (lineMode) {
            switchMode(ConsoleMode.LINE, false);
        }
        if (decoder == null) {
            return Optional.of(keyLeftByLastMode());
        }
        return withCtrlCAsByte(this::readKeyUnlessEscaped);
    }

    private Optional<ConsoleKey> readKeyUnlessEscaped() {
        while (true) {
            if (escapePending.getAsBoolean()) {
                return Optional.of(new ConsoleKey(ConsoleKey.CTRL_C, 0, true, false, false));
            }
            try {
                if (typed.available() > 0) {
                    TerminalKeyDecoder.KeyRead read = decoder.readKeyEvent();
                    if (read instanceof TerminalKeyDecoder.Pressed(ConsoleKey key)) {
                        return Optional.of(key);
                    }
                    if (read instanceof TerminalKeyDecoder.Ended) {
                        return Optional.empty();
                    }
                    continue;
                }
            } catch (IOException gone) {
                return Optional.empty();
            }
            if (!waitForKey()) {
                return Optional.empty();
            }
        }
    }

    private boolean waitForKey() {
        try {
            Thread.sleep(KEY_POLL_MILLISECONDS);
            return true;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean isATerminal() {
        return isTerminal;
    }

    @Override
    public void switchMode(ConsoleMode mode, boolean on) {
        lastModeSet = mode;
        lastSetting = on;
        if (!honoursModes) {
            return;
        }
        switch (mode) {
            case ECHO -> output.write(on ? REVEAL : CONCEAL);
            case LINE -> switchLineMode(on);
            case ERROR -> output.useErrorStream(on);
        }
    }

    void whenEscaped(Runnable escaping) {
        this.escape = escaping;
    }

    void askWhetherEscaped(BooleanSupplier pending) {
        this.escapePending = pending;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        terminal.ifPresent(settings -> settings.restore(asFound));
    }

    private void switchLineMode(boolean on) {
        if (on == lineMode) {
            return;
        }
        lineMode = on;
        terminal.ifPresent(settings -> {
            if (on) {
                settings.restore(editorsMode);
            } else {
                settings.change(RAW_SETTINGS);
            }
        });
        output.write(on ? BRACKETED_PASTE_OFF : BRACKETED_PASTE_ON);
    }

    private ConsoleKey keyLeftByLastMode() {
        int value = lastSetting ? 1 : 0;
        return new ConsoleKey(lastModeSet.ordinal(), value & VALUE_BITS, false, false, false);
    }

    private <T> T withCtrlCAsByte(Supplier<T> reading) {
        terminal.ifPresent(settings -> settings.change(SIGNALS_OFF));
        try {
            return reading.get();
        } finally {
            terminal.ifPresent(settings -> settings.change(SIGNALS_ON));
        }
    }

    private int nextHiddenByte() {
        if (pending.length > 0) {
            int next = pending[0] & 0xFF;
            pending = Arrays.copyOfRange(pending, 1, pending.length);
            return next;
        }
        try {
            return typed.read();
        } catch (IOException gone) {
            return -1;
        }
    }

    private int readTyped(byte[] into) {
        try {
            return typed.read(into, 0, into.length);
        } catch (IOException gone) {
            return -1;
        }
    }

    private int indexOfLineFeed() {
        for (int at = 0; at < pending.length; at++) {
            if (pending[at] == LF) {
                return at;
            }
        }
        return -1;
    }

    private void appendPending(byte[] read, int length) {
        byte[] longer = Arrays.copyOf(pending, pending.length + length);
        System.arraycopy(read, 0, longer, pending.length, length);
        pending = longer;
    }

    private byte[] takePendingLine() {
        int lineFeed = indexOfLineFeed();
        int taken = lineFeed < 0 ? pending.length : lineFeed + 1;
        byte[] line = Arrays.copyOf(pending, taken);
        pending = Arrays.copyOfRange(pending, taken, pending.length);
        return withoutFinalLineFeed(line, line.length);
    }

    private static byte[] withoutFinalLineFeed(byte[] read, int length) {
        int kept = length > 0 && read[length - 1] == LF ? length - 1 : length;
        return Arrays.copyOf(read, kept);
    }

    private static String withoutFinalLineFeed(String line) {
        return line != null && line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    }

    private static String withoutFinalCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private static String decode(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
