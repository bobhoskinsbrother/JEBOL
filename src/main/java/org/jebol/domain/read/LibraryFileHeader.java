package org.jebol.domain.read;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.Locale;

/**
 * The {@code REBOL [...]} block at the top of a library file, read as data.
 *
 * <p>Two fields of it decide where the file's words go. {@code Type: module}
 * asks for a context of the file's own, and {@code Exports:} names the words
 * that escape from it. Everything else in a header is copyright text.
 *
 * <p>Read here rather than in the loader because a header arrives from a
 * file, so nothing has checked it. A field of the wrong datatype, a missing
 * field and an export list holding something that is not a word are all
 * shapes a header can really have, and each one has to answer something
 * rather than throwing at start-up. A file with a header JEBOL cannot make
 * sense of loads into the library, which is what a file with no header at all
 * does, and is the answer that changes least.
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
     * Reads a header block, keeping the two fields that matter.
     *
     * <p>The block is a run of set-words each followed by a value, which is
     * the shape CONSTRUCT reads. Anything that is not that shape is skipped
     * rather than refused, because a header is data and a loader that raised
     * on one would take the whole library with it.
     */
    public static LibraryFileHeader readFrom(Value header) {
        if (!(header instanceof BlockValue fields)) {
            return none();
        }
        String declaredType = "";
        String moduleName = "";
        List<String> exported = List.of();
        List<Value> items = fields.remaining();
        for (int at = 0; at + 1 < items.size(); at++) {
            if (!(items.get(at) instanceof WordValue named)
                    || named.datatype() != Datatype.SET_WORD) {
                continue;
            }
            Value given = items.get(at + 1);
            switch (named.canonical()) {
                case "type" -> declaredType = plainTextOf(given);
                case "name" -> moduleName = plainTextOf(given);
                case "exports" -> exported = wordsIn(given);
                default -> { }
            }
        }
        return new LibraryFileHeader(declaredType, moduleName, exported);
    }

    /**
     * Whether this file asks for a context of its own.
     *
     * <p>The comparison is on the canonical spelling because REBOL word
     * equality ignores case, and the two files that need this disagree:
     * codec-json.reb writes {@code Type: module} and mezz-logger.reb writes
     * {@code type: module}. A loader that matched the spelling would load one
     * and not the other.
     */
    public boolean declaresAModule() {
        return MODULE.equals(declaredType);
    }

    /** The text of a word or a string, and nothing for anything else. */
    private static String plainTextOf(Value given) {
        return switch (given) {
            case WordValue word -> word.canonical();
            case StringValue text -> text.text().toLowerCase(Locale.ROOT);
            default -> "";
        };
    }

    /**
     * The words of an export list.
     *
     * <p>An item that is not a word is dropped. An export list is written by
     * hand and a stray value in one names nothing that could be published.
     */
    private static List<String> wordsIn(Value given) {
        if (!(given instanceof BlockValue listed)) {
            return List.of();
        }
        return listed.remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .filter(word -> word.datatype() == Datatype.WORD)
                .map(WordValue::canonical)
                .toList();
    }
}
