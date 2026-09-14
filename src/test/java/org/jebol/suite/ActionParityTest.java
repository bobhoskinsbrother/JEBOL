package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ActionParityTest {

    private static final Map<String, String> A_VALUE_OF = new LinkedHashMap<>();

    static {
        A_VALUE_OF.put("block!", "[1 2]");
        A_VALUE_OF.put("paren!", "quote (1 2)");
        A_VALUE_OF.put("path!", "quote a/b");
        A_VALUE_OF.put("set-path!", "quote a/b:");
        A_VALUE_OF.put("get-path!", "quote :a/b");
        A_VALUE_OF.put("lit-path!", "quote 'a/b");
        A_VALUE_OF.put("hash!", "make hash! [1 2]");
        A_VALUE_OF.put("string!", "\"ab\"");
        A_VALUE_OF.put("file!", "%ab");
        A_VALUE_OF.put("url!", "http://example.com");
        A_VALUE_OF.put("email!", "a@b");
        A_VALUE_OF.put("tag!", "<a>");
        A_VALUE_OF.put("ref!", "@ab");
        A_VALUE_OF.put("binary!", "#{0102}");
        A_VALUE_OF.put("bitset!", "charset \"ab\"");
        A_VALUE_OF.put("map!", "make map! [a 1]");
        A_VALUE_OF.put("object!", "make object! [a: 1]");
        A_VALUE_OF.put("error!", "try [1 / 0]");
        A_VALUE_OF.put("none!", "none");
        A_VALUE_OF.put("logic!", "true");
        A_VALUE_OF.put("integer!", "1");
        A_VALUE_OF.put("decimal!", "1.5");
        A_VALUE_OF.put("percent!", "50%");
        A_VALUE_OF.put("money!", "$1");
        A_VALUE_OF.put("char!", "#\"a\"");
        A_VALUE_OF.put("pair!", "1x2");
        A_VALUE_OF.put("tuple!", "1.2.3");
        A_VALUE_OF.put("time!", "1:00");
        A_VALUE_OF.put("date!", "1-Jan-2000");
        A_VALUE_OF.put("word!", "quote a");
        A_VALUE_OF.put("set-word!", "quote a:");
        A_VALUE_OF.put("get-word!", "quote :a");
        A_VALUE_OF.put("lit-word!", "quote 'a");
        A_VALUE_OF.put("refinement!", "quote /a");
        A_VALUE_OF.put("issue!", "#ab");
        A_VALUE_OF.put("datatype!", "integer!");
        A_VALUE_OF.put("typeset!", "any-string!");
        A_VALUE_OF.put("function!", "func [] [1]");
        A_VALUE_OF.put("native!", ":append");
        A_VALUE_OF.put("op!", ":+");
    }

    private static final Map<String, String> A_CALL_TO = new LinkedHashMap<>();

    static {
        A_CALL_TO.put("append", "append %s 1");
        A_CALL_TO.put("insert", "insert %s 1");
        A_CALL_TO.put("change", "change %s 1");
        A_CALL_TO.put("clear", "clear %s");
        A_CALL_TO.put("copy", "copy %s");
        A_CALL_TO.put("find", "find %s 1");
        A_CALL_TO.put("select", "select %s 1");
        A_CALL_TO.put("pick", "pick %s 1");
        A_CALL_TO.put("poke", "poke %s 1 1");
        A_CALL_TO.put("put", "put %s 1 2");
        A_CALL_TO.put("remove", "remove %s");
        A_CALL_TO.put("take", "take %s");
        A_CALL_TO.put("trim", "trim %s");
        A_CALL_TO.put("sort", "sort %s");
        A_CALL_TO.put("reverse", "reverse %s");
        A_CALL_TO.put("swap", "swap %s %s");
        A_CALL_TO.put("length?", "length? %s");
        A_CALL_TO.put("head", "head %s");
        A_CALL_TO.put("tail", "tail %s");
        A_CALL_TO.put("head?", "head? %s");
        A_CALL_TO.put("tail?", "tail? %s");
        A_CALL_TO.put("past?", "past? %s");
        A_CALL_TO.put("next", "next %s");
        A_CALL_TO.put("back", "back %s");
        A_CALL_TO.put("skip", "skip %s 1");
        A_CALL_TO.put("at", "at %s 1");
        A_CALL_TO.put("atz", "atz %s 1");
        A_CALL_TO.put("index?", "index? %s");
        A_CALL_TO.put("indexz?", "indexz? %s");
        A_CALL_TO.put("reflect", "reflect %s 'words");
        A_CALL_TO.put("random", "random %s");
        A_CALL_TO.put("complement", "complement %s");
        A_CALL_TO.put("negate", "negate %s");
        A_CALL_TO.put("absolute", "absolute %s");
        A_CALL_TO.put("even?", "even? %s");
        A_CALL_TO.put("odd?", "odd? %s");
        A_CALL_TO.put("add", "add %s 1");
        A_CALL_TO.put("subtract", "subtract %s 1");
        A_CALL_TO.put("multiply", "multiply %s 2");
        A_CALL_TO.put("divide", "divide %s 2");
        A_CALL_TO.put("remainder", "remainder %s 2");
        A_CALL_TO.put("power", "power %s 2");
        A_CALL_TO.put("round", "round %s");
        A_CALL_TO.put("and~", "and~ %s %s");
        A_CALL_TO.put("or~", "or~ %s %s");
        A_CALL_TO.put("xor~", "xor~ %s %s");
        A_CALL_TO.put("query", "query %s");
        A_CALL_TO.put("modify", "modify %s 'a 1");
    }

    private static final Set<String> MEANS_NO_ARM = Set.of("cannot-use", "expect-arg");

    private static final Set<String> REFUSED_BY_THE_C_TOO = Set.of(
            "path! random", "set-path! random", "get-path! random",
            "lit-path! random", "hash! random", "paren! random");

    @Test
    @DisplayName("every action the C implements for a datatype does something here")
    void everyArmIsThere() {
        Map<String, Set<String>> wanted = whatTheCImplements();
        TreeMap<String, List<String>> gaps = new TreeMap<>();
        int probed = 0;

        Interpreter interpreter = Interpreter.create();
        for (var entry : wanted.entrySet()) {
            String datatype = entry.getKey();
            String value = A_VALUE_OF.get(datatype);
            if (value == null) {
                continue;
            }
            for (String action : entry.getValue()) {
                String form = A_CALL_TO.get(action);
                if (form == null) {
                    continue;
                }
                probed++;
                String source = "e: try ["
                        + form.replace("%s", "(" + value + ")")
                        + "] either error? e [e/id] ['worked]";
                String answered;
                try {
                    interpreter.defineFreshWordsIn(source);
                    answered = interpreter.display(interpreter.run(source)).trim();
                } catch (RuntimeException escaped) {
                    answered = "threw " + escaped.getClass().getSimpleName();
                }
                if (REFUSED_BY_THE_C_TOO.contains(datatype + " " + action)) {
                    continue;
                }
                if (MEANS_NO_ARM.contains(answered) || answered.startsWith("threw ")) {
                    gaps.computeIfAbsent(datatype, ignored -> new ArrayList<>())
                            .add(action + " (" + answered + ")");
                }
            }
        }

        System.out.printf("%nACTION PARITY: %d calls the C implements, "
                + "%d datatypes with a gap%n", probed, gaps.size());
        gaps.forEach((datatype, missing) ->
                System.out.printf("  %-12s %s%n", datatype, String.join("  ", missing)));

        long total = gaps.values().stream().mapToLong(List::size).sum();
        assertThat(total)
                .as("an action the C implements for a datatype must not answer "
                        + "cannot-use or expect-arg here; the list above is the work")
                .isLessThanOrEqualTo(KNOWN_GAPS);
    }

    private static final long KNOWN_GAPS = 0;

    private static Map<String, Set<String>> whatTheCImplements() {
        Map<String, String> typeclassOf = new LinkedHashMap<>();
        Map<String, Set<String>> armsOf = new LinkedHashMap<>();
        Map<String, Set<String>> declaredFor = new LinkedHashMap<>();
        Map<String, Set<String>> typesets = new LinkedHashMap<>();
        for (String line : factFile().lines().toList()) {
            if (line.startsWith("TYPESET ")) {
                String[] parts = line.substring("TYPESET ".length()).split("\\|");
                typesets.put(parts[0].trim(),
                        new TreeSet<>(List.of(parts[1].trim().split("\\s+"))));
            }
            if (line.startsWith("DATATYPE ")) {
                String[] parts = line.substring("DATATYPE ".length()).split("\\|");
                typeclassOf.put(parts[0].trim(), parts[1].trim().split("\\s+")[0]);
            }
            if (line.startsWith("ARMS ")) {
                String[] parts = line.substring("ARMS ".length()).split("\\|");
                armsOf.put(parts[0].trim(),
                        new TreeSet<>(List.of(parts[1].trim().split("\\s+"))));
            }
            if (line.startsWith("ACTION ")) {
                String[] parts = line.substring("ACTION ".length()).split("\\|");
                declaredFor.put(parts[0].trim(), firstArgumentsTypes(parts[1]));
            }
        }
        declaredFor.replaceAll((action, declared) -> {
            Set<String> expanded = new TreeSet<>();
            declared.forEach(name -> expanded.addAll(typesets.getOrDefault(
                    name, Set.of(name))));
            return expanded;
        });

        Map<String, Set<String>> wanted = new LinkedHashMap<>();
        typeclassOf.forEach((datatype, typeclass) -> {
            String capitalised = typeclass.isEmpty() ? "" : Character.toUpperCase(
                    typeclass.charAt(0)) + typeclass.substring(1);
            Set<String> arms = armsOf.get(capitalised);
            if (arms == null) {
                return;
            }
            Set<String> reachable = new TreeSet<>();
            for (String action : arms) {
                Set<String> declared = declaredFor.get(action);
                if (declared == null || declared.isEmpty() || declared.contains(datatype)
                        || declared.contains("any-type!")) {
                    reachable.add(action);
                }
            }
            wanted.put(datatype, reachable);
        });
        return wanted;
    }

    private static Set<String> firstArgumentsTypes(String shape) {
        int opens = shape.indexOf('<');
        int firstRefinement = shape.indexOf('/');
        if (opens < 0 || (firstRefinement >= 0 && firstRefinement < opens)) {
            return Set.of();
        }
        return new TreeSet<>(List.of(
                shape.substring(opens + 1, shape.indexOf('>')).trim().split("\\s+")));
    }

    private static String factFile() {
        try (var source = ActionParityTest.class.getResourceAsStream("/r3/c-surface.txt")) {
            if (source == null) {
                throw new IllegalStateException(
                        "r3/c-surface.txt is not on the test path; "
                                + "run scripts/c-surface.py");
            }
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
