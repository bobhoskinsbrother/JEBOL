package org.jebol.adapter.cli;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RebolArguments {

    enum Flag {
        SCRIPT("script"), ARGS("args"), DO("do"), IMPORT("import"), VERSION("version"),
        DEBUG("debug"), SECURE("secure"), HELP("help"), VERS("vers"), QUIET("quiet"),
        VERBOSE("verbose"), SECURE_MIN("secure-min"), SECURE_MAX("secure-max"),
        TRACE("trace"), HALT("halt"), CGI("cgi"), BOOT("boot-level"),
        NO_WINDOW("no-window"), NO_COLOR("no-color"), LEGACY_REPL("legacy-repl");

        private final String spelling;

        Flag(String spelling) {
            this.spelling = spelling;
        }

        String spelling() {
            return spelling;
        }
    }

    private record Option(Set<Flag> flags, boolean takesAValue) {
    }

    private static final Map<String, Option> OPTION_WORDS = Map.ofEntries(
            Map.entry("args", new Option(EnumSet.of(Flag.ARGS), true)),
            Map.entry("boot", new Option(EnumSet.of(Flag.BOOT), true)),
            Map.entry("cgi", new Option(EnumSet.of(Flag.CGI, Flag.QUIET), false)),
            Map.entry("debug", new Option(EnumSet.of(Flag.DEBUG), true)),
            Map.entry("do", new Option(EnumSet.of(Flag.DO), true)),
            Map.entry("halt", new Option(EnumSet.of(Flag.HALT), false)),
            Map.entry("help", new Option(EnumSet.of(Flag.HELP), false)),
            Map.entry("import", new Option(EnumSet.of(Flag.IMPORT), true)),
            Map.entry("legacy-repl", new Option(EnumSet.of(Flag.LEGACY_REPL), false)),
            Map.entry("no-color", new Option(EnumSet.of(Flag.NO_COLOR), false)),
            Map.entry("quiet", new Option(EnumSet.of(Flag.QUIET), false)),
            Map.entry("script", new Option(EnumSet.of(Flag.SCRIPT), true)),
            Map.entry("secure", new Option(EnumSet.of(Flag.SECURE), true)),
            Map.entry("trace", new Option(EnumSet.of(Flag.TRACE), false)),
            Map.entry("verbose", new Option(EnumSet.of(Flag.VERBOSE), false)),
            Map.entry("version", new Option(EnumSet.of(Flag.VERSION), true)));

    private static final Map<Character, Option> OPTION_CHARACTERS = Map.ofEntries(
            Map.entry('?', new Option(EnumSet.of(Flag.HELP), false)),
            Map.entry('V', new Option(EnumSet.of(Flag.VERS), false)),
            Map.entry('b', new Option(EnumSet.of(Flag.BOOT), true)),
            Map.entry('c', new Option(EnumSet.of(Flag.CGI, Flag.QUIET), false)),
            Map.entry('h', new Option(EnumSet.of(Flag.HALT), false)),
            Map.entry('q', new Option(EnumSet.of(Flag.QUIET), false)),
            Map.entry('s', new Option(EnumSet.of(Flag.SECURE_MIN), false)),
            Map.entry('t', new Option(EnumSet.of(Flag.TRACE), false)),
            Map.entry('v', new Option(EnumSet.of(Flag.VERS), false)),
            Map.entry('w', new Option(EnumSet.of(Flag.NO_WINDOW), false)));

    private static final Map<Character, Option> PLUS_CHARACTERS = Map.of(
            's', new Option(EnumSet.of(Flag.SECURE_MAX), false));

    private static final Option UNKNOWN = new Option(EnumSet.of(Flag.HELP), false);

    private static final Option IGNORED = new Option(EnumSet.noneOf(Flag.class), false);

    private static final String STOPS_THE_OPTIONS = "--";

    private final Set<Flag> flags = EnumSet.noneOf(Flag.class);
    private final Map<Flag, String> values = new EnumMap<>(Flag.class);
    private Optional<String> script = Optional.empty();
    private final List<String> remaining = new ArrayList<>();

    RebolArguments(List<String> arguments) {
        int at = 0;
        while (at < arguments.size()) {
            String argument = arguments.get(at);
            if (argument.equals(STOPS_THE_OPTIONS)) {
                break;
            }
            if (argument.startsWith("--")) {
                at = took(OPTION_WORDS.getOrDefault(argument.substring(2),
                        startsALine(argument.substring(2)) ? IGNORED : UNKNOWN), arguments, at);
            } else if (argument.startsWith("-")) {
                at = tookEachCharacter(argument, OPTION_CHARACTERS, arguments, at);
            } else if (argument.startsWith("+")) {
                at = tookEachCharacter(argument, PLUS_CHARACTERS, arguments, at);
            } else {
                if (script.isPresent()) {
                    at--;
                } else {
                    script = Optional.of(argument);
                }
                break;
            }
            at++;
        }
        for (int rest = at + 1; rest < arguments.size(); rest++) {
            remaining.add(arguments.get(rest));
        }
    }

    private boolean startsALine(String word) {
        return word.startsWith("\r") || word.startsWith("\n");
    }

    private int tookEachCharacter(String argument, Map<Character, Option> options,
            List<String> arguments, int at) {

        int reached = at;
        for (char each : argument.substring(1).toCharArray()) {
            Option option = each == '\r' || each == '\n'
                    ? IGNORED
                    : options.getOrDefault(each, UNKNOWN);
            reached = took(option, arguments, reached);
        }
        return reached;
    }

    private int took(Option option, List<String> arguments, int at) {
        if (!option.takesAValue()) {
            flags.addAll(option.flags());
            return at;
        }
        if (at + 1 >= arguments.size()) {
            flags.add(Flag.HELP);
            return at + 1;
        }
        String value = arguments.get(at + 1);
        flags.addAll(option.flags());
        if (secondCharacterIsADash(value)) {
            return at;
        }
        option.flags().forEach(flag -> values.put(flag, value));
        if (option.flags().contains(Flag.SCRIPT)) {
            script = Optional.of(value);
        }
        return at + 1;
    }

    private boolean secondCharacterIsADash(String value) {
        return value.length() > 1 && value.charAt(1) == '-';
    }

    Set<Flag> flags() {
        return flags;
    }

    boolean has(Flag flag) {
        return flags.contains(flag);
    }

    Optional<String> valueOf(Flag flag) {
        return Optional.ofNullable(values.get(flag));
    }

    Optional<String> script() {
        return script;
    }

    List<String> argumentsForTheScript() {
        List<String> handedOn = new ArrayList<>();
        valueOf(Flag.ARGS).ifPresent(handedOn::add);
        handedOn.addAll(remaining);
        return handedOn;
    }
}
