package org.jebol.domain.render;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class CarriedFont {

    private static final int SFNT_VERSION_TRUETYPE = 0x00010000;

    private final int unitsPerEm;
    private final int ascender;
    private final int descender;
    private final int[] advances;
    private final ReadingOfCharacters characters;
    private final String family;

    private CarriedFont(int unitsPerEm, int ascender, int descender,
            int[] advances, ReadingOfCharacters characters, String family) {
        this.unitsPerEm = unitsPerEm;
        this.ascender = ascender;
        this.descender = descender;
        this.advances = advances;
        this.characters = characters;
        this.family = family;
    }

    public static CarriedFont readFrom(byte[] font) {
        Tables tables = Tables.readFrom(font);
        int unitsPerEm = tables.readUint16("head", 18);
        int ascender = tables.readInt16("hhea", 4);
        int descender = tables.readInt16("hhea", 6);
        int metrics = tables.readUint16("hhea", 34);
        int glyphs = tables.readUint16("maxp", 4);
        int[] advances = advancesOf(tables, metrics, glyphs);
        return new CarriedFont(unitsPerEm, ascender, descender, advances,
                ReadingOfCharacters.readFrom(tables), readTheFamilyOf(tables));
    }

    public String familyName() {
        return family;
    }

    public TextMeasure measure() {
        return this::measureTheExtentOf;
    }

    private TextExtent measureTheExtentOf(TextRun run) {
        double scale = run.size() / unitsPerEm;
        double wide = 0;
        String text = run.text();
        for (int at = 0; at < text.length(); ) {
            int character = text.codePointAt(at);
            wide += advances[findTheGlyphOf(character)];
            at += Character.charCount(character);
        }
        return new TextExtent(wide * scale, ascender * scale, -descender * scale);
    }

    private int findTheGlyphOf(int character) {
        int glyph = characters.findTheGlyphFor(character);
        return glyph >= 0 && glyph < advances.length ? glyph : 0;
    }

    private static int[] advancesOf(Tables tables, int metrics, int glyphs) {
        int[] advances = new int[Math.max(1, glyphs)];
        int at = tables.findTheOffset("hmtx");
        int last = 0;
        for (int glyph = 0; glyph < advances.length; glyph++) {
            if (glyph < metrics) {
                last = tables.readUint16(at, glyph * 4);
            }
            advances[glyph] = last;
        }
        return advances;
    }

    private static String readTheFamilyOf(Tables tables) {
        int at = tables.findTheOffset("name");
        int count = tables.readUint16(at, 2);
        int storage = at + tables.readUint16(at, 4);
        String found = "";
        for (int record = 0; record < count; record++) {
            int here = at + 6 + record * 12;
            int platform = tables.readUint16(here, 0);
            int named = tables.readUint16(here, 6);
            boolean theFamily = named == 1 || named == 16;
            if (!theFamily || platform != 3) {
                continue;
            }
            int length = tables.readUint16(here, 8);
            int offset = storage + tables.readUint16(here, 10);
            found = new String(tables.bytes, offset, length, StandardCharsets.UTF_16BE);
            if (named == 1) {
                return found;
            }
        }
        return found;
    }

    private interface ReadingOfCharacters {

        int findTheGlyphFor(int character);

        static ReadingOfCharacters readFrom(Tables tables) {
            int at = tables.findTheOffset("cmap");
            int count = tables.readUint16(at, 2);
            for (int wanted : new int[] {12, 4}) {
                for (int record = 0; record < count; record++) {
                    int here = at + 4 + record * 8;
                    int platform = tables.readUint16(here, 0);
                    int encoding = tables.readUint16(here, 2);
                    int subtable = at + tables.readUint32(here, 4);
                    boolean theBmpOfWindows = platform == 3 && encoding == 1;
                    boolean theWholeOfWindows = platform == 3 && encoding == 10;
                    boolean theWholeOfUnicode = platform == 0 && encoding >= 4;
                    if (tables.readUint16(subtable, 0) != wanted) {
                        continue;
                    }
                    if (wanted == 4 && !theBmpOfWindows) {
                        continue;
                    }
                    if (wanted == 12 && !(theWholeOfWindows || theWholeOfUnicode)) {
                        continue;
                    }
                    return wanted == 12
                            ? Grouped.readFrom(tables, subtable)
                            : Segmented.readFrom(tables, subtable);
                }
            }
            return character -> 0;
        }
    }

    private record Grouped(int[] firsts, int[] lasts, int[] glyphs) implements ReadingOfCharacters {

        static Grouped readFrom(Tables tables, int at) {
            int count = tables.readUint32(at, 12);
            int[] firsts = new int[count];
            int[] lasts = new int[count];
            int[] glyphs = new int[count];
            for (int group = 0; group < count; group++) {
                int here = at + 16 + group * 12;
                firsts[group] = tables.readUint32(here, 0);
                lasts[group] = tables.readUint32(here, 4);
                glyphs[group] = tables.readUint32(here, 8);
            }
            return new Grouped(firsts, lasts, glyphs);
        }

        @Override
        public int findTheGlyphFor(int character) {
            int low = 0;
            int high = firsts.length - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                if (character < firsts[middle]) {
                    high = middle - 1;
                } else if (character > lasts[middle]) {
                    low = middle + 1;
                } else {
                    return glyphs[middle] + character - firsts[middle];
                }
            }
            return 0;
        }
    }

    private record Segmented(int[] ends, int[] starts, int[] deltas, int[] ranges, int[] glyphIds)
            implements ReadingOfCharacters {

        static Segmented readFrom(Tables tables, int at) {
            int segments = tables.readUint16(at, 6) / 2;
            int[] ends = new int[segments];
            int[] starts = new int[segments];
            int[] deltas = new int[segments];
            int[] ranges = new int[segments];
            for (int segment = 0; segment < segments; segment++) {
                ends[segment] = tables.readUint16(at + 14, segment * 2);
            }
            int afterTheEnds = at + 14 + segments * 2 + 2;
            for (int segment = 0; segment < segments; segment++) {
                starts[segment] = tables.readUint16(afterTheEnds, segment * 2);
                deltas[segment] = tables.readInt16(afterTheEnds + segments * 2, segment * 2);
                ranges[segment] = tables.readUint16(afterTheEnds + segments * 4, segment * 2);
            }
            int glyphsAt = afterTheEnds + segments * 6;
            int left = Math.max(0, at + tables.readUint16(at, 2) - glyphsAt);
            int[] glyphIds = new int[left / 2];
            for (int glyph = 0; glyph < glyphIds.length; glyph++) {
                glyphIds[glyph] = tables.readUint16(glyphsAt, glyph * 2);
            }
            return new Segmented(ends, starts, deltas, ranges, glyphIds);
        }

        @Override
        public int findTheGlyphFor(int character) {
            int segment = 0;
            while (segment < ends.length && ends[segment] < character) {
                segment++;
            }
            if (segment == ends.length || starts[segment] > character) {
                return 0;
            }
            if (ranges[segment] == 0) {
                return (character + deltas[segment]) & 0xFFFF;
            }
            int within = ranges[segment] / 2 + (character - starts[segment])
                    - (ends.length - segment);
            if (within < 0 || within >= glyphIds.length) {
                return 0;
            }
            int glyph = glyphIds[within];
            return glyph == 0 ? 0 : (glyph + deltas[segment]) & 0xFFFF;
        }
    }

    private record Tables(byte[] bytes, Map<String, int[]> where) {

        static Tables readFrom(byte[] font) {
            if (font.length < 12 || readUint32Of(font, 0) != SFNT_VERSION_TRUETYPE) {
                throw new IllegalArgumentException(
                        "these bytes are not a TrueType font");
            }
            Map<String, int[]> found = new HashMap<>();
            int count = readUint16Of(font, 4);
            for (int table = 0; table < count; table++) {
                int record = 12 + table * 16;
                String tag = new String(font, record, 4, StandardCharsets.ISO_8859_1);
                found.put(tag, new int[] {readUint32Of(font, record + 8), readUint32Of(font, record + 12)});
            }
            return new Tables(font, found);
        }

        int findTheOffset(String tag) {
            int[] found = where.get(tag);
            if (found == null) {
                throw new IllegalArgumentException("the font has no " + tag + " table");
            }
            return found[0];
        }

        int readUint16(String tag, int at) {
            return readUint16(findTheOffset(tag), at);
        }

        int readInt16(String tag, int at) {
            return readInt16(findTheOffset(tag), at);
        }

        int readUint16(int base, int at) {
            return readUint16Of(bytes, base + at);
        }

        int readInt16(int base, int at) {
            return (short) readUint16Of(bytes, base + at);
        }

        int readUint32(int base, int at) {
            return readUint32Of(bytes, base + at);
        }

        private static int readUint16Of(byte[] bytes, int at) {
            return ((bytes[at] & 0xFF) << 8) | (bytes[at + 1] & 0xFF);
        }

        private static int readUint32Of(byte[] bytes, int at) {
            return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16)
                    | ((bytes[at + 2] & 0xFF) << 8) | (bytes[at + 3] & 0xFF);
        }
    }
}
