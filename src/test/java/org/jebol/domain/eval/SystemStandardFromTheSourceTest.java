package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code system/standard} carries what {@code boot/sysobj.reb} declares: the
 * twenty-nine templates a script builds its own values from.
 *
 * <p>The object belongs to the C. {@code sysobj.reb} sits in {@code src/boot/}
 * and the build compiles it into the boot block, so declaring every field is
 * Java's work here. Three fields get their contents from elsewhere and are
 * asserted as the boot leaves them rather than as the declaration writes them:
 * {@code enum} is replaced by {@code mezz-func.reb:110}, and {@code font} and
 * {@code para} by {@code view-funcs.reb:18} and {@code :28} -- borrowed REBOL
 * files, which is the layer rule working as designed, Java declaring the slot
 * and REBOL filling it. {@code bincode} is the C's, set by the base-code block
 * at {@code u-bincode.c:178}, and arrives with the bincode dialect.
 *
 * <p>Written because nothing tested this and seventeen of the twenty-nine were
 * missing. Rebol's own 3721 assertions never ask the SYSTEM object anything,
 * and JEBOL's only coverage was {@code header}, asserted in passing by a test
 * about something else. The single signal was {@code view-funcs.reb} stopping
 * on the word {@code font}, which got recorded as a dialect it was waiting
 * for.
 *
 * <p>So this is one list-driven test rather than twenty-nine hand-written
 * ones. A hand-written set repeats the mistake: the thirtieth field Rebol adds
 * still has nobody watching it.
 */
class SystemStandardFromTheSourceTest {

    /**
     * A template {@code sysobj.reb} declares, and the words it holds.
     *
     * <p>An empty word list means the declaration is {@code none} rather than
     * an object, which is a different assertion and not an object with no
     * fields.
     */
    private record Template(String name, List<String> words) {

        boolean isNone() {
            return words.isEmpty();
        }
    }

    private static Template none(String name) {
        return new Template(name, List.of());
    }

    private static Template holding(String name, String... words) {
        return new Template(name, List.of(words));
    }

    /**
     * What every {@code make port-spec-head [...]} template starts with.
     *
     * <p>{@code port-spec-head} is the prototype the other seven derive from,
     * so each carries these three before its own.
     */
    private static final List<String> PORT_SPEC_HEAD = List.of("title", "scheme", "ref");

    private static Template portSpec(String name, String... ownWords) {
        return new Template(name, Stream.concat(
                PORT_SPEC_HEAD.stream(),
                Stream.of(ownWords).filter(word -> !PORT_SPEC_HEAD.contains(word))).toList());
    }

    /**
     * The twenty-nine templates, in the order {@code sysobj.reb} declares
     * them.
     */
    private static final List<Template> DECLARED = List.of(
            holding("codec",
                    "name", "type", "title", "suffixes", "decode", "encode", "identify"),
            holding("enum", "title*", "assert", "name"),
            holding("error",
                    "code", "type", "id", "arg1", "arg2", "arg3", "near", "where"),
            holding("script", "title", "header", "parent", "path", "args"),
            holding("header",
                    "version", "title", "name", "type", "date", "file", "author",
                    "needs", "options", "checksum"),
            holding("scheme", "name", "title", "spec", "info", "actor", "awake"),
            holding("port",
                    "spec", "scheme", "parent", "actor", "awake", "state", "extra", "data"),
            holding("port-spec-head", "title", "scheme", "ref"),
            portSpec("port-spec-file", "path"),
            portSpec("port-spec-net",
                    "host", "port", "path", "target", "query", "fragment"),
            portSpec("port-spec-checksum", "scheme", "method"),
            portSpec("port-spec-crypt",
                    "scheme", "direction", "algorithm", "init-vector", "key"),
            portSpec("port-spec-midi", "scheme", "device-in", "device-out"),
            portSpec("port-spec-serial",
                    "path", "speed", "data-size", "parity", "stop-bits", "flow-control"),
            portSpec("port-spec-audio",
                    "scheme", "source", "channels", "rate", "bits", "sample-type",
                    "loop-count"),
            holding("file-info",
                    "name", "size", "type", "date", "modified", "accessed", "created"),
            holding("net-info", "local-ip", "local-port", "remote-ip", "remote-port"),
            holding("console-info",
                    "buffer-cols", "buffer-rows", "window-cols", "window-rows", "length"),
            holding("vector-info",
                    "signed", "type", "size", "length", "minimum", "maximum", "range",
                    "sum", "mean", "median", "variance", "sample-variance",
                    "population-deviation", "sample-deviation"),
            holding("date-info",
                    "year", "month", "day", "time", "date", "zone", "hour", "minute",
                    "second", "weekday", "yearday", "timezone", "utc", "julian"),
            holding("handle-info", "type"),
            holding("midi-info", "devices-in", "devices-out"),
            holding("extension",
                    "lib-base", "lib-file", "lib-boot", "command", "cmd-index", "words"),
            holding("stats",
                    "timer", "evals", "eval-natives", "eval-functions", "series-made",
                    "series-freed", "series-expanded", "series-bytes", "series-recycled",
                    "made-blocks", "made-objects", "recycles", "collisions"),
            holding("type-spec", "title", "type"),
            none("bincode"),
            none("utype"),
            holding("font",
                    "name", "style", "size", "color", "offset", "space", "shadow"),
            holding("para",
                    "origin", "margin", "indent", "tabs", "wrap?", "scroll", "align",
                    "valign"));

    /**
     * The templates a borrowed REBOL file fills after the declaration, and the
     * file that fills each.
     *
     * <p>Their presence is Java's obligation and their contents are the
     * borrowed file's, so a failure here is read differently: the slot is
     * missing, or the file that writes it stopped before reaching the line.
     *
     * <p>{@code font} and {@code para} are a fork rather than a match.
     * {@code make/pre-make.r3} puts {@code view-funcs.reb} in
     * {@code vid-files} and includes it only when {@code include-vid} is set,
     * so a stock 3.22.1 answers none for both -- checked against the binary,
     * which has no VIEW at all. JEBOL loads the file unconditionally and so
     * gets the objects. Asserted as JEBOL has them, with the divergence
     * recorded here rather than hidden.
     */
    private static final Map<String, String> FILLED_BY_A_BORROWED_FILE = Map.of(
            "enum", "mezz-func.reb:110",
            "font", "view-funcs.reb:18",
            "para", "view-funcs.reb:28");

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    private static Stream<String> everyTemplateName() {
        return DECLARED.stream().map(Template::name);
    }

    private static Stream<Arguments> everyObjectTemplate() {
        return DECLARED.stream()
                .filter(template -> !template.isNone())
                .map(template -> Arguments.of(template.name(), template));
    }

    private static Stream<String> everyObjectTemplateName() {
        return DECLARED.stream().filter(template -> !template.isNone()).map(Template::name);
    }

    private static Stream<Arguments> everyPortSpec() {
        return DECLARED.stream()
                .filter(template -> template.name().startsWith("port-spec-"))
                .map(template -> Arguments.of(template.name(), template));
    }

    @Nested
    @DisplayName("every template sysobj.reb declares is there")
    class TheFieldsExist {

        @Test
        @DisplayName("all twenty-nine of them, named together so the gap is one failure")
        void everyDeclaredFieldIsPresent() {
            String present = answerTo("mold words-of system/standard");

            SoftAssertions.assertSoftly(all -> DECLARED.forEach(template ->
                    all.assertThat(present)
                            .as("system/standard is missing %s", template.name())
                            .contains(template.name())));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.SystemStandardFromTheSourceTest#everyTemplateName")
        @DisplayName("and each is reachable through a path")
        void eachIsReachableThroughAPath(String name) {
            assertThat(answerTo("error? try [system/standard/" + name + "]"))
                    .as("system/standard/%s does not resolve", name)
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and there are exactly twenty-nine, so nothing was invented either")
        void thereAreNoExtraFields() {
            assertThat(answerTo("length? words-of system/standard"))
                    .isEqualTo(String.valueOf(DECLARED.size()));
        }
    }

    @Nested
    @DisplayName("each template holds the words its declaration gives it")
    class TheFieldsHaveTheirShape {

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.SystemStandardFromTheSourceTest#everyObjectTemplate")
        @DisplayName("an object template's words match sysobj.reb exactly")
        void theWordsMatch(String name, Template template) {
            String expected = "[" + String.join(" ", template.words()) + "]";

            assertThat(answerTo(
                    "(mold words-of system/standard/" + name + ") = {" + expected + "}"))
                    .as("system/standard/%s has the wrong fields; it has %s",
                            name, answerTo("mold words-of system/standard/" + name))
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.SystemStandardFromTheSourceTest#everyObjectTemplate")
        @DisplayName("and it is an object, not a value that merely exists")
        void eachIsAnObject(String name, Template template) {
            assertThat(answerTo("object? system/standard/" + name))
                    .as("system/standard/%s is not an object", name)
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a template declared none stays none")
        void theNoneTemplatesAreNone() {
            SoftAssertions.assertSoftly(all -> DECLARED.stream()
                    .filter(Template::isNone)
                    .forEach(template -> all.assertThat(
                                    answerTo("none? system/standard/" + template.name()))
                            .as("system/standard/%s should be none", template.name())
                            .isEqualTo(TRUE)));
        }
    }

    @Nested
    @DisplayName("the port spec family derives from port-spec-head")
    class ThePortSpecs {

        @ParameterizedTest(name = "{0}")
        @MethodSource(
                "org.jebol.domain.eval.SystemStandardFromTheSourceTest#everyObjectTemplate")
        @DisplayName("each carries title, scheme and ref before its own words")
        void eachStartsWithTheHeadsWords(String name, Template template) {
            if (!name.startsWith("port-spec-")) {
                return;
            }
            assertThat(template.words().subList(0, PORT_SPEC_HEAD.size()))
                    .as("the declaration table itself is wrong for %s", name)
                    .isEqualTo(PORT_SPEC_HEAD);

            SoftAssertions.assertSoftly(all -> PORT_SPEC_HEAD.forEach(inherited ->
                    all.assertThat(answerTo(
                                    "true? find words-of system/standard/" + name
                                            + " '" + inherited))
                            .as("%s does not carry the head's %s", name, inherited)
                            .isEqualTo(TRUE)));
        }

        @Test
        @DisplayName("and a derived spec does not share the head's storage")
        void aDerivedSpecIsItsOwnObject() {
            assertThat(answerTo("""
                    same? system/standard/port-spec-head
                          system/standard/port-spec-file"""))
                    .isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("the templates a borrowed REBOL file fills")
    class TheBorrowedFills {

        @Test
        @DisplayName("each is filled, so the file that writes it ran that far")
        void eachIsFilled() {
            SoftAssertions.assertSoftly(all -> FILLED_BY_A_BORROWED_FILE.forEach(
                    (name, where) -> all.assertThat(
                                    answerTo("object? system/standard/" + name))
                            .as("%s should have been filled by %s", name, where)
                            .isEqualTo(TRUE)));
        }

        @Test
        @DisplayName("ENUM is the one that already worked, and proves the mechanism")
        void enumIsTheWorkingExample() {
            assertThat(answerTo("object? system/standard/enum")).isEqualTo(TRUE);
            assertThat(answerTo("mold words-of system/standard/enum"))
                    .isEqualTo("\"[title* assert name]\"");
        }
    }

    @Nested
    @DisplayName("what the templates are for: a script builds its own from them")
    class TheyAreUsable {

        @Test
        @DisplayName("MAKE over a template answers an object carrying its words")
        void makeOverATemplateWorks() {
            assertThat(answerTo("""
                    s: make system/standard/script [title: "t"]
                    all [object? s  s/title = "t"  none? s/args]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("which is what Rebol's own DO does with the script template")
        void doBuildsTheScriptObjectFromIt() {
            assertThat(answerTo("""
                    s: make system/standard/script compose [
                        title: "a title" header: none parent: none
                        path: none args: none
                    ]
                    mold words-of s""")).isEqualTo("\"[title header parent path args]\"");
        }

        @Test
        @DisplayName("and MAKE leaves the template itself alone")
        void theTemplateIsNotMutated() {
            assertThat(answerTo("""
                    make system/standard/script [title: "changed"]
                    none? system/standard/script/title""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an error template carries the eight fields an error! has")
        void theErrorTemplateMatchesARaisedError() {
            assertThat(answerTo("""
                    e: try [1 / 0]
                    all [
                        true? find words-of system/standard/error 'code
                        true? find words-of system/standard/error 'id
                        true? find words-of system/standard/error 'near
                        true? find words-of system/standard/error 'where
                        e/id = 'zero-divide
                    ]""")).isEqualTo(TRUE);
        }
    }
}
