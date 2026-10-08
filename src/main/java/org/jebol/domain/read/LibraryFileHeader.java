package org.jebol.domain.read;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.Locale;

/**
 * The {@code REBOL [...]} block at the top of a library file, read as data.
 *
 * <p>Two fields of it decide where the file's words go: {@code Type: module}
 * asks for a context of the file's own, and {@code Exports:} names the words
 * that escape from it.
 *
 * <p>Specified in {@code spec/load.allium} as LibraryFileLoad.
 */
public record LibraryFileHeader(
        String declaredType, String moduleName, List<String> exportedNames) {

    private static final String MODULE = "module";

    public LibraryFileHeader {
        exportedNames = List.copyOf(exportedNames);
    }

    /** The header of a file that has not got one. */
    public static LibraryFileHeader none() {
        return new LibraryFileHeader("", "", List.of());
    }

    /**
     * Reads a header block, keeping the two fields that matter. Anything that
     * is not a set-word followed by a value is skipped rather than refused,
     * because a header is data and a loader that raised on one would take the
     * whole library with it.
     */
    public static LibraryFileHeader readFrom(Value header) {
        if (!(header instanceof AnyBlockValue fields)) {
            return none();
        }
        String declaredType = "";
        String moduleName = "";
        List<String> exported = List.of();
        List<Value> items = fields.remaining();
        for (int at = 0; at + 1 < items.size(); at++) {
            if (!(items.get(at) instanceof SetWordValue field)) {
                continue;
            }
            Value given = items.get(at + 1);
            switch (field.canonical()) {
                case "type" -> declaredType = plainTextOf(given);
                case "name" -> moduleName = plainTextOf(given);
                case "exports" -> exported = wordsIn(given);
                default -> { }
            }
        }
        return new LibraryFileHeader(declaredType, moduleName, exported);
    }

    /** Whether this file asks for a context of its own. */
    public boolean declaresAModule() {
        return MODULE.equals(declaredType);
    }

    private static String plainTextOf(Value given) {
        return switch (given) {
            case AnyWordValue word -> word.canonical();
            case AnyStringValue text -> text.text().toLowerCase(Locale.ROOT);
            default -> "";
        };
    }

    private static List<String> wordsIn(Value given) {
        if (!(given instanceof AnyBlockValue listed)) {
            return List.of();
        }
        return listed.remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .map(AnyWordValue::canonical)
                .toList();
    }
}
