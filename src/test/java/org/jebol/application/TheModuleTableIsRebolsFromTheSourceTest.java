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

/**
 * The addresses in {@code system/modules} are the ones Rebol publishes.
 *
 * <p>JEBOL builds the system object in Java rather than loading
 * {@code sysobj.reb}, so every table in that file is re-expressed here -- the
 * event catalogue, the security policies, the checksum bitsets, the shape of a
 * port. The module addresses are one more of those, and they are the kind that
 * goes stale quietly: a version number in a release url changes upstream and
 * nothing here would ever notice.
 *
 * <p>So this reads the block out of Rebol's own source and holds what this
 * build offers to it: every name offered must be one Rebol publishes, and no
 * compiled extension may be offered at all, because nothing here can load a
 * shared library and an address for one only turns "no such module" into a
 * download that fails afterwards.
 *
 * <p>What it does not hold is the addresses themselves, which differ on
 * purpose. Rebol's send IMPORT to {@code src.rebol.tech}; these name the
 * BUNDLED scheme, so the module is read out of this build and cannot change
 * under a running system.
 */
class TheModuleTableIsRebolsFromTheSourceTest {

    private static final Path SYSOBJ =
            Path.of("rebol3-source", "src", "boot", "sysobj.reb");

    static boolean rebolsOwnSourceIsHere() {
        return Files.exists(SYSOBJ);
    }

    /**
     * Every {@code name: url} pair in the {@code modules:} block, in order.
     *
     * <p>The block ends at the first line that is a closing bracket on its
     * own, which is how that file is written throughout.
     */
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

    /**
     * Every name this build offers is one Rebol publishes, and every address is
     * the BUNDLED scheme rather than an address on the wire.
     *
     * <p>The names have to be Rebol's because IMPORT is asked for them by name
     * and a name nobody else uses reaches nothing. The addresses have to be
     * local because an address on the wire is evaluated as it arrives, with no
     * signature, no checksum and no pinned version between it and the
     * evaluator.
     */
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

    /**
     * And what is offered is what is bundled. An address for a module the build
     * has not got answers cannot-open when IMPORT reads it, which is a worse
     * failure than never offering it.
     */
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

    /**
     * No compiled extension is offered, and none can be. Each is a shared
     * library -- {@code name-platform-arch.rebx} from a release page -- and
     * nothing here can load one, so an address for one only turns "no such
     * module" into a download that fails afterwards. Several name things JEBOL
     * has anyway: BROTLI is the {@code br} compression method.
     */
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
