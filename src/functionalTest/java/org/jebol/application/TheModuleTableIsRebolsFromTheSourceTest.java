package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TheModuleTableIsRebolsFromTheSourceTest {

    private static final Path SYSOBJ =
            Path.of("rebol3-source", "src", "boot", "sysobj.reb");

    static boolean rebolsOwnSourceIsHere() {
        return Files.exists(SYSOBJ);
    }

    private static Map<String, String> whatRebolPublishes() {
        String source;
        try {
            source = Files.readString(SYSOBJ, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        String block = source.substring(source.indexOf("modules: object ["));
        block = block.substring(0, block.indexOf("\n]"));
        Map<String, String> published = new LinkedHashMap<>();
        Matcher pair = A_NAME_AND_ITS_ADDRESS.matcher(block);
        while (pair.find()) {
            published.put(pair.group(1), pair.group(2));
        }
        return published;
    }

    private static final Pattern A_NAME_AND_ITS_ADDRESS =
            Pattern.compile("(?m)^\\s*([a-z0-9-]+):\\s+(https://\\S+)\\s*$");

    private static String whatJebolHolds(String name) {
        Interpreter interpreter = Interpreter.create();
        String asking = "mold select system/modules '" + name;
        interpreter.defineFreshWordsIn(asking);
        return interpreter.display(interpreter.run(asking));
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("the block read out of Rebol's own source is not empty")
    void theBlockReadOutOfRebolsOwnSourceIsNotEmpty() {
        assertThat(whatRebolPublishes())
                .as("if this is empty the checks below prove nothing, "
                        + "and the shape of sysobj.reb has changed")
                .hasSizeGreaterThan(40)
                .containsKey("thru-cache")
                .containsKey("brotli");
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("and every name offered is Rebol's, read out of this build")
    void everyNameOfferedIsRebolsReadOutOfThisBuild() {
        Map<String, String> published = whatRebolPublishes();
        Map<String, String> wrong = new LinkedHashMap<>();
        for (String name : whatThisBuildOffers()) {
            String held = whatJebolHolds(name);
            if (!published.containsKey(name)) {
                wrong.put(name, "not a name Rebol publishes");
            } else if (!held.equals("\"bundled://" + name + ".reb\"")) {
                wrong.put(name, held);
            }
        }

        assertThat(wrong)
                .as("each of these should be bundled://<name>.reb and read out "
                        + "of this build, and each name should be one Rebol "
                        + "publishes:%n  %s", wrong)
                .isEmpty();
    }

    @Test
    @DisplayName("and every one of them is a module this build can read")
    void everyOneOfThemIsAModuleThisBuildCanRead() {
        Interpreter interpreter = Interpreter.create();
        for (String name : whatThisBuildOffers()) {
            String asking = "binary? read bundled://" + name + ".reb";
            interpreter.defineFreshWordsIn(asking);
            assertThat(interpreter.display(interpreter.run(asking)))
                    .as("bundled://%s.reb", name)
                    .isEqualTo("#(true)");
        }
    }

    private static List<String> whatThisBuildOffers() {
        Interpreter interpreter = Interpreter.create();
        String asking = """
                collect [
                    foreach name words-of system/modules [
                        if url? select system/modules name [keep name]
                    ]
                ]""";
        interpreter.defineFreshWordsIn(asking);
        String held = interpreter.display(interpreter.run(asking));
        return List.of(held.substring(1, held.length() - 1).trim().split("\\s+"));
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("and no compiled extension is offered, because none can be loaded")
    void noCompiledExtensionIsOffered() {
        List<String> offered = whatThisBuildOffers();
        List<String> shouldNotBe = whatRebolPublishes().entrySet().stream()
                .filter(one -> one.getValue().endsWith("/"))
                .map(Map.Entry::getKey)
                .filter(offered::contains)
                .toList();

        assertThat(shouldNotBe)
                .as("an address here sends IMPORT to fetch a shared library "
                        + "this build cannot load:%n  %s", shouldNotBe)
                .isEmpty();
    }
}
