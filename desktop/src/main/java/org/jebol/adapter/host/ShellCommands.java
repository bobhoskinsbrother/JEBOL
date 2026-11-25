package org.jebol.adapter.host;

import java.util.ArrayList;
import java.util.List;

final class ShellCommands {

    private static final String QUOTE = "\"";
    private static final char QUOTE_MARK = '"';
    private static final char BACKSLASH = '\\';

    private ShellCommands() {
    }

    static List<String> asShellCommand(List<String> command, boolean throughShell, boolean onWindows) {
        if (!throughShell) {
            return onWindows && command.size() == 1 ? splitAsWindowsCommandLine(command.getFirst()) : command;
        }
        return onWindows ? wrapForCmd(command) : wrapForSh(command);
    }

    private static List<String> wrapForCmd(List<String> command) {
        return List.of("cmd.exe", "/C", QUOTE + String.join(" ", command) + QUOTE);
    }

    private static List<String> wrapForSh(List<String> command) {
        List<String> withShell = new ArrayList<>(List.of("/bin/sh", "-c"));
        withShell.addAll(command);
        return withShell;
    }

    private static List<String> splitAsWindowsCommandLine(String line) {
        List<String> words = new ArrayList<>();
        int at = skipSpaces(line, 0);
        if (at >= line.length()) {
            return List.of(line);
        }
        at = readProgramName(line, at, words);
        while ((at = skipSpaces(line, at)) < line.length()) {
            at = readArgument(line, at, words);
        }
        return words;
    }

    private static int readProgramName(String line, int at, List<String> words) {
        if (line.charAt(at) == QUOTE_MARK) {
            int closing = line.indexOf(QUOTE_MARK, at + 1);
            int end = closing < 0 ? line.length() : closing;
            words.add(line.substring(at + 1, end));
            return closing < 0 ? end : end + 1;
        }
        int end = at;
        while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
            end++;
        }
        words.add(line.substring(at, end));
        return end;
    }

    private static int readArgument(String line, int at, List<String> words) {
        StringBuilder word = new StringBuilder();
        boolean quoted = false;
        int position = at;
        while (position < line.length()) {
            char letter = line.charAt(position);
            if (letter == BACKSLASH) {
                int run = countBackslashes(line, position);
                position += run;
                boolean beforeQuote = position < line.length() && line.charAt(position) == QUOTE_MARK;
                word.append(String.valueOf(BACKSLASH).repeat(beforeQuote ? run / 2 : run));
                if (beforeQuote && run % 2 == 1) {
                    word.append(QUOTE_MARK);
                    position++;
                }
            } else if (letter == QUOTE_MARK) {
                quoted = !quoted;
                position++;
            } else if (Character.isWhitespace(letter) && !quoted) {
                break;
            } else {
                word.append(letter);
                position++;
            }
        }
        words.add(word.toString());
        return position;
    }

    private static int countBackslashes(String line, int from) {
        int end = from;
        while (end < line.length() && line.charAt(end) == BACKSLASH) {
            end++;
        }
        return end - from;
    }

    private static int skipSpaces(String line, int from) {
        int at = from;
        while (at < line.length() && Character.isWhitespace(line.charAt(at))) {
            at++;
        }
        return at;
    }
}
