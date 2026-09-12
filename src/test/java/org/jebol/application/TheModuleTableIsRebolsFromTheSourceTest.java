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
 * <p>So this reads the block out of Rebol's own source and holds the copy to
 * it, the same way the vendored suite files are held to theirs. A name whose
 * value is a module rather than a url is one this build loaded -- LOAD-MODULE
 * ends with {@code repend system/modules [name module]} and the address is
 * replaced -- and that is checked as the replacement it is rather than skipped.
 *
 * <p>Rebol's table opens with fourteen compiled extensions and those are left
 * out here, which the third test holds to. Nothing in this build can load a
 * shared library, so an address for one only turns "no such module" into a
 * download that fails afterwards.
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

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("and every address in it is the address this build holds")
    void everyAddressIsTheAddressThisBuildHolds() {
        Map<String, String> wrong = new LinkedHashMap<>();
        whatRebolPublishes().forEach((name, address) -> {
            if (isACompiledExtension(address)) {
                return;
            }
            String held = whatJebolHolds(name);
            boolean rightOrReplaced = held.equals("\"" + address + "\"")
                    || isAModule(held);
            if (!rightOrReplaced) {
                wrong.put(name, held);
            }
        });

        assertThat(wrong)
                .as("these names do not hold the address Rebol publishes, and "
                        + "are not modules this build loaded either. A copy of "
                        + "somebody else's table goes stale without a word:%n  %s",
                        wrong)
                .isEmpty();
    }

    /**
     * The compiled extensions are left out, and this is what says so.
     *
     * <p>Each is a shared library -- {@code name-platform-arch.rebx} fetched
     * from a release page -- and nothing here can load one. An address for one
     * turns "no such module" into a download that fails after a round trip to
     * github, which is slower, more confusing, and one more way for a run to
     * fail. Several are things JEBOL has anyway: BROTLI is the {@code br}
     * compression method, and DEFLATE, BZIP2 and ZLIB-NG are in
     * {@code system/catalog/compressions}.
     */
    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("but no compiled extension is offered, because none can be loaded")
    void noCompiledExtensionIsOffered() {
        Map<String, String> offered = new LinkedHashMap<>();
        whatRebolPublishes().forEach((name, address) -> {
            if (isACompiledExtension(address) && whatJebolHolds(name).contains("://")) {
                offered.put(name, address);
            }
        });

        assertThat(offered)
                .as("an address here sends IMPORT to fetch a shared library "
                        + "this build cannot load, after a round trip:%n  %s",
                        offered)
                .isEmpty();
    }

    /**
     * A release page rather than a file: the module loader tells the two apart
     * with {@code either dir? source}, and builds a platform-specific
     * {@code .rebx} name for the first.
     */
    private static boolean isACompiledExtension(String address) {
        return address.endsWith("/");
    }

    /**
     * Whether what came back is a molded module, in either delimiter.
     *
     * <p>REBOL molds a string of more than fifty characters in braces and a
     * shorter one in quotes, and a module with nothing exported is short --
     * {@code "make module! [^/]"}. Accepting only braces read five of these as
     * the wrong address when they were modules this build had loaded.
     */
    private static boolean isAModule(String held) {
        return held.startsWith("{make module!") || held.startsWith("\"make module!");
    }

    /**
     * And nothing extra. A name here that Rebol does not publish would send
     * IMPORT somewhere Rebol never would.
     */
    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("and no address is here that Rebol does not publish")
    void noAddressIsHereThatRebolDoesNotPublish() {
        Interpreter interpreter = Interpreter.create();
        String asking = """
                collect [
                    foreach name words-of system/modules [
                        if url? select system/modules name [keep name]
                    ]
                ]""";
        interpreter.defineFreshWordsIn(asking);
        String held = interpreter.display(interpreter.run(asking));

        assertThat(held.substring(1, held.length() - 1).split("\\s+"))
                .allMatch(name -> whatRebolPublishes().containsKey(name),
                        "published by Rebol");
    }
}
