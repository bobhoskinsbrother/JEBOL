package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every action Rebol's C implements for a datatype, called on that datatype
 * here.
 *
 * <p>The declared surface says APPEND takes a {@code series!}, a {@code port!},
 * a {@code map!}, a {@code gob!}, an {@code object!} and a {@code bitset!}. It
 * does not say which of those the C has an arm for, and it does not say which of
 * them JEBOL has an arm for. Every gap found by hand in the last round was of
 * that shape -- APPEND on a map, CHANGE on a binary, FIND on an object, the walk
 * over a map -- and each was invisible in the declaration.
 *
 * <p>So this reads {@code r3/c-surface.txt}, which
 * {@code scripts/c-surface.py} builds from Rebol's own source: the datatype
 * table in {@code types.reb} says which typeclass serves each datatype, and the
 * {@code REBTYPE} blocks say which actions each typeclass implements. The
 * product of the two is the list of calls that must do something here.
 *
 * <p>A call "does something" when it does not come back as
 * {@code cannot-use}, which is what JEBOL answers for an arm it has not got, or
 * as {@code expect-arg}, which is what it answers when the declared spec refuses
 * the datatype before the arm is reached. Any other outcome counts -- including
 * a different error, because an arm that exists and refuses these particular
 * arguments is a question about the arguments and not about the arm.
 */
class ActionParityTest {

    /**
     * A value of each datatype JEBOL has, written as source.
     *
     * <p>The datatypes JEBOL has not got are simply absent, and the pairs that
     * name them are skipped: those are a datatype backlog rather than a parity
     * gap, and mixing the two would bury the second in the first.
     */
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

    /**
     * How each action is called, with {@code %s} where the value goes.
     *
     * <p>The arguments are the least a call can carry, because what is being
     * asked is whether the arm exists rather than whether it works. MAKE and TO
     * are left out: both take a datatype rather than a value of one, so the
     * matrix row for them says something different from the rest and they have
     * their own tests.
     */
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

    /** What JEBOL answers when the arm is not there. */
    private static final Set<String> MEANS_NO_ARM = Set.of("cannot-use", "expect-arg");

    /**
     * Pairings where the C has the arm and the arm turns this datatype away.
     *
     * <p>This measure multiplies the datatype table by the arms table, and
     * that product says which {@code case} labels exist rather than which of
     * them do anything. Where the first line inside a case is a refusal, the
     * product over-counts, and a faithful port has to look like a gap here or
     * disagree with Rebol.
     *
     * <p>{@code REBTYPE(Block)}'s RANDOM is the whole of the list:
     * {@code if (!IS_BLOCK(value)) Trap_Action(VAL_TYPE(value), action);} is
     * its second line, so every block-like datatype that is not a plain block
     * -- the four paths, a hash and a paren -- reaches the arm and is sent
     * away with {@code cannot-use}. Rebol's own series-test.r3 pins it:
     * {@code all [error? e: try [random 'a/b/c] e/id = 'cannot-use]}.
     *
     * <p>Nothing goes on this list without the line of C that refuses and the
     * assertion that wants it. It is not a place to park work.
     */
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

    /**
     * How many arms are missing today.
     *
     * <p>A ratchet, not a target. Lower it when an arm lands and never raise it:
     * going up means an arm that used to answer does not any more.
     */
    private static final long KNOWN_GAPS = 0;

    /**
     * Datatype to the actions Rebol's C implements for it and lets it reach.
     *
     * <p>Three tables meet here, and all three are needed.
     *
     * <p>The datatype table says which typeclass serves the datatype, and the
     * arms table says which actions that typeclass has a case for. Their
     * product is what the C implements.
     *
     * <p>The declared spec then narrows it, and leaving that out asks for
     * things a real R3 refuses. Every scalar with a position has a POKE arm --
     * `REBTYPE(Pair)` has `case A_POKE` -- and POKE declares
     * {@code series! port! map! gob! bitset!}, so `poke 1x2 1 5` is an error
     * there as it is here. Those arms are reached by writing through a path
     * instead, which is a different question and has its own tests.
     */
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
            String named = typeclass.isEmpty() ? "" : Character.toUpperCase(
                    typeclass.charAt(0)) + typeclass.substring(1);
            Set<String> arms = armsOf.get(named);
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

    /** The datatypes the first argument of a declared spec accepts. */
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
