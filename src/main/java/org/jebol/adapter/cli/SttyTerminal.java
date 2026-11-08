package org.jebol.adapter.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SttyTerminal implements TerminalSettings {

    private static final File THE_TERMINAL = new File("/dev/tty");

    private static final int DEL = 0x7F;

    private static final int FIELDS_BEFORE_THE_CONTROL_CHARACTERS = 4;

    private static final int VERASE = 2;

    private static final Pattern NAMED_ERASE = Pattern.compile("(?:^|:)erase=([0-9a-fA-F]+)");

    private String asFirstRead = "";

    @Override
    public String read() {
        String read = run(List.of("-g")).strip();
        if (asFirstRead.isEmpty()) {
            asFirstRead = read;
        }
        return read;
    }

    @Override
    public void change(List<String> settings) {
        run(settings);
    }

    @Override
    public void restore(String saved) {
        if (!saved.isEmpty()) {
            run(List.of(saved));
        }
    }

    @Override
    public int eraseCharacter() {
        Matcher named = NAMED_ERASE.matcher(asFirstRead);
        if (named.find()) {
            return HexFormat.fromHexDigits(named.group(1));
        }
        String[] fields = asFirstRead.split(":");
        int at = FIELDS_BEFORE_THE_CONTROL_CHARACTERS + VERASE;
        return fields.length > at ? HexFormat.fromHexDigits(fields[at]) : DEL;
    }

    private static String run(List<String> settings) {
        List<String> command = new ArrayList<>();
        command.add("stty");
        command.addAll(settings);
        try {
            Process running = new ProcessBuilder(command)
                    .redirectInput(THE_TERMINAL)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String printed = new String(running.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return running.waitFor() == 0 ? printed : "";
        } catch (IOException couldNotRun) {
            return "";
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
