package org.jebol.domain.value;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each error id says, read from Rebol's own catalogue.
 *
 * <p>FORM of an error looks its words up rather than carrying them:
 * {@code Find_Error_Info} reads {@code system/catalog/errors/<type>/<id>}, so
 * what a script sees is what {@code errors.reb} says and not a sentence the
 * implementation composed. A script that changes an argument changes the
 * message with it.
 *
 * <p>Held statically because the catalogue is a file this build carries rather
 * than anything a host supplies, so every interpreter reads the same one. The
 * words arrive from the evaluator, which is where the reader lives.
 */
public final class ErrorWording {

    private ErrorWording() {
    }

    /** What FORM says when the catalogue has nothing for the id. */
    public static final String NOTHING_IN_THE_CATALOGUE =
            "(improperly formatted error)";

    private static final Map<String, Value> SAID = new ConcurrentHashMap<>();

    /** Tells the catalogue what one id says. Called once per id at boot. */
    public static void say(String category, String errorId, Value words) {
        SAID.put(keyFor(category, errorId), words);
    }

    /** What an id says, or empty when the catalogue does not name it. */
    public static Optional<Value> forTheId(String category, String errorId) {
        return Optional.ofNullable(SAID.get(keyFor(category, errorId)));
    }

    private static String keyFor(String category, String errorId) {
        return category.toLowerCase(Locale.ROOT) + "/"
                + errorId.toLowerCase(Locale.ROOT);
    }
}
