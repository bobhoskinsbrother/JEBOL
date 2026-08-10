package org.jebol.domain.value;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REBOL 3's error catalogue: which errors exist and what each is numbered.
 *
 * <p>Taken verbatim from a real R3, because a script compares
 * {@code e/code} against arithmetic on this table and compares
 * {@code e/id} against names it did not choose. Both have to be R3's own,
 * and neither can be worked out from what JEBOL happens to raise.
 *
 * <p>Compiled in rather than read from a resource: the domain does no
 * reading and no writing, and the build enforces that.
 *
 * <p>A code is its category's base plus the id's position within that
 * category, so the first id in a category takes the category's own code.
 * Each category in R3 also carries a {@code type} field naming itself;
 * that is not an error id, and counting it would shift every code in the
 * category by one.
 *
 * <p>Lives in the value layer because it is data about errors rather than
 * anything the evaluator does. Putting it beside the natives made the
 * value layer depend on the eval layer, which the dependency rule forbids
 * and the build caught.
 */
public final class ErrorCatalogue {

    private static final Map<String, Integer> CODES = new LinkedHashMap<>();
    private static final Map<String, List<String>> IDS = new LinkedHashMap<>();

    static {
        define("Throw", 0,
                "break return throw continue halt quit");
        define("Note", 100,
                "no-load exited deprecated");
        define("Syntax", 200,
                "invalid missing no-header bad-header bad-checksum malconstruct"
                        + " bad-char needs");
        define("Script", 300,
                "no-value need-value not-defined not-in-context no-arg"
                        + " expect-arg expect-val expect-type cannot-use invalid-arg"
                        + " invalid-type invalid-op no-op-arg invalid-data not-same-type"
                        + " not-same-class not-related bad-func-def bad-func-arg no-refine"
                        + " bad-refines bad-refine invalid-path bad-path-type bad-path-set"
                        + " bad-field-set dup-vars past-end missing-arg out-of-range"
                        + " too-short too-long invalid-chars invalid-compare assert-failed"
                        + " wrong-type invalid-part type-limit size-limit no-return"
                        + " block-lines throw-usage locked-word protected hidden"
                        + " self-protected bad-bad bad-make-arg bad-decode already-used"
                        + " wrong-denom bad-press dialect bad-command parse-rule parse-end"
                        + " parse-variable parse-command parse-series parse-no-collect"
                        + " parse-into-bad parse-into-type invalid-handle"
                        + " invalid-value-for handle-exists vector-not-compatible"
                        + " type-mismatch");
        define("Math", 400,
                "zero-divide overflow positive");
        define("Access", 500,
                "cannot-open not-open already-open no-connect not-connected"
                        + " not-ready no-script no-scheme-name no-scheme invalid-spec"
                        + " invalid-port invalid-actor invalid-port-arg no-port-action"
                        + " protocol invalid-check write-error read-error read-only"
                        + " no-buffer timeout cannot-close no-create no-delete no-rename"
                        + " bad-file-path bad-file-mode security security-level"
                        + " security-error no-codec bad-media no-extension bad-extension"
                        + " extension-init call-fail permission-denied process-not-found"
                        + " invalid-utf invalid-char");
        define("Command", 600,
                "command-fail");
        define("resv700", 700,
                "");
        define("User", 800,
                "message");
        define("Internal", 900,
                "bad-path not-here no-memory stack-overflow globals-full"
                        + " max-natives bad-series limit-hit bad-sys-func feature-na"
                        + " not-done invalid-error");
    }

    private static void define(String category, int baseCode, String ids) {
        CODES.put(category, baseCode);
        IDS.put(category, List.of(ids.split(" ")));
    }

    private ErrorCatalogue() {
    }

    /** The categories, in the order R3 lists them. */
    public static List<String> categories() {
        return List.copyOf(CODES.keySet());
    }

    /** The code a category's first id takes. */
    public static int baseCodeOf(String category) {
        return CODES.getOrDefault(category, 0);
    }

    /** The ids in a category, in the order that decides their codes. */
    public static List<String> idsIn(String category) {
        return IDS.getOrDefault(category, List.of());
    }

    /**
     * The number R3 gives an error of this category and id, or the
     * category's base when the id is not one R3 knows.
     */
    public static int codeFor(String category, String errorId) {
        int at = idsIn(category).indexOf(errorId);
        return at < 0 ? baseCodeOf(category) : baseCodeOf(category) + at;
    }

}
