"""Ports Rebol's terminal-width tables from the C into a Java source file.

`UTF8_Width` in `rebol3-source/src/core/s-unicode.c` answers how many columns
one character takes on a terminal, and it does it from four sorted range
tables: two of characters that take none and two of characters that take two.
The tables are themselves generated, from `UnicodeData.txt` and
`EastAsianWidth.txt`, and they are large enough that copying them by hand
would be a transcription exercise with no reader.

So this reads them out of the C and writes
`src/main/java/org/jebol/domain/value/TerminalWidth.java`. Run it when Rebol's
tables change; the generated file is checked in, because `rebol3-source/` is a
symlink to a checkout that is not part of this repository and is not there at
build time.

    python3 scripts/terminal-width-table.py
"""
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(REPO, "rebol3-source", "src", "core", "s-unicode.c")
TARGET = os.path.join(REPO, "src", "main", "java", "org", "jebol", "domain",
                      "value", "TerminalWidth.java")

TABLE = re.compile(
    r"static const struct utf8range_u\d+ (\w+)\[\] = \{(.*?)\n\};", re.S)
RANGE = re.compile(r"\{(0x[0-9A-Fa-f]+),(0x[0-9A-Fa-f]+)\}")


def ranges_named(text):
    """Every table in the C, as {name: [(lower, upper), ...]}."""
    found = {}
    for match in TABLE.finditer(text):
        body = "\n".join(
            line.split("//")[0] for line in match.group(2).splitlines())
        found[match.group(1)] = [
            (int(low, 16), int(high, 16)) for low, high in RANGE.findall(body)]
    return found


def as_java_array(pairs):
    """One flat array of lower, upper, lower, upper, wrapped at four pairs."""
    lines = []
    for at in range(0, len(pairs), 4):
        run = pairs[at:at + 4]
        lines.append("            " + " ".join(
            "0x%04X, 0x%04X," % pair for pair in run))
    return "\n".join(lines).rstrip(",")


def main():
    if not os.path.exists(SOURCE):
        sys.exit("no %s -- is rebol3-source checked out?" % SOURCE)
    text = open(SOURCE, encoding="utf-8", errors="replace").read()
    tables = ranges_named(text)
    zero = sorted(tables["unicode_zero_u16"] + tables["unicode_zero_u32"])
    wide = sorted(tables["unicode_wide_u16"] + tables["unicode_wide_u32"])
    open(TARGET, "w", encoding="utf-8").write(TEMPLATE % {
        "zeroCount": len(zero),
        "wideCount": len(wide),
        "zero": as_java_array(zero),
        "wide": as_java_array(wide),
    })
    print("wrote %s: %d zero-width ranges, %d wide ones"
          % (TARGET, len(zero), len(wide)))


TEMPLATE = '''package org.jebol.domain.value;

/**
 * How many columns a character takes on a terminal.
 *
 * <p>{@code UTF8_Width} in {@code s-unicode.c}: nothing for a combining mark
 * or a formatting character, two for the East Asian wide ones, and one for
 * everything else. It is what {@code s/width} counts, where {@code length?}
 * counts characters and {@code s/size} counts the bytes they encode to -- so
 * a string of three characters can be five bytes long and two columns wide.
 *
 * <p>The two range tables below are Rebol's own, read out of the C by
 * {@code scripts/terminal-width-table.py} rather than copied by hand. Rebol
 * generates them in turn from {@code UnicodeData.txt} and
 * {@code EastAsianWidth.txt}, so this is a port of a port and the script is
 * how it stays one.
 *
 * <p>%(zeroCount)d ranges take no columns and %(wideCount)d take two. Both are sorted, and
 * looked up by halving as the C does.
 */
public final class TerminalWidth {

    private TerminalWidth() {
    }

    /** Below this the answer is one column, which is the common case. */
    private static final int FIRST_PRINTABLE = 0x20;

    private static final int FIRST_DELETE = 0x7F;

    private static final int[] TAKES_NO_COLUMNS = {
%(zero)s
    };

    private static final int[] TAKES_TWO_COLUMNS = {
%(wide)s
    };

    /** How many columns one character takes. */
    public static int of(int codepoint) {
        if (codepoint >= FIRST_PRINTABLE && codepoint < FIRST_DELETE) {
            return 1;
        }
        if (within(TAKES_NO_COLUMNS, codepoint)) {
            return 0;
        }
        return within(TAKES_TWO_COLUMNS, codepoint) ? 2 : 1;
    }

    /** How many columns a whole run of characters takes. */
    public static int of(int[] letters) {
        int columns = 0;
        for (int letter : letters) {
            columns += of(letter);
        }
        return columns;
    }

    /** Whether a code point falls in one of the ranges, found by halving. */
    private static boolean within(int[] ranges, int codepoint) {
        int low = 0;
        int high = ranges.length / 2 - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (codepoint < ranges[middle * 2]) {
                high = middle - 1;
            } else if (codepoint > ranges[middle * 2 + 1]) {
                low = middle + 1;
            } else {
                return true;
            }
        }
        return false;
    }
}
'''

if __name__ == "__main__":
    main()
