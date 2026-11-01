package org.jebol.domain.eval.blend2d;

import java.util.Optional;

public final class OpenTypeFace {

    static final int FACE_TYPE_OPENTYPE = 1;

    static final int OUTLINE_TRUETYPE = 1;

    static final int OUTLINE_CFF = 2;

    static final int FLAG_TYPOGRAPHIC_NAMES = 0x00000001;

    static final int FLAG_TYPOGRAPHIC_METRICS = 0x00000002;

    static final int FLAG_CHAR_TO_GLYPH_MAPPING = 0x00000004;

    static final int FLAG_HORIZONTAL_KERNING = 0x00000040;

    static final int FLAG_PANOSE_DATA = 0x00000200;

    static final int FLAG_UNICODE_COVERAGE = 0x00000400;

    static final int FLAG_BASELINE_Y_EQUALS_0 = 0x00001000;

    static final int FLAG_LSB_POINT_X_EQUALS_0 = 0x00002000;

    static final int FLAG_SYMBOL_FONT = 0x40000000;

    static final int FLAG_LAST_RESORT_FONT = 0x80000000;

    static final int DIAG_WRONG_NAME_DATA = 0x00000001;

    static final int DIAG_FIXED_NAME_DATA = 0x00000002;

    static final int DIAG_WRONG_KERN_DATA = 0x00000004;

    static final int DIAG_FIXED_KERN_DATA = 0x00000008;

    static final int DIAG_WRONG_CMAP_DATA = 0x00000010;

    static final int DIAG_WRONG_CMAP_FORMAT = 0x00000020;

    private static final String[] TABLE_NAMES = {
        "head", "maxp", "OS/2", "post", "name", "cmap", "hhea", "hmtx", "vhea", "vmtx", "kern", "BASE", "GDEF",
        "GPOS", "GSUB", "glyf", "loca", "CFF ", "CFF2"};

    private static final long[] TABLE_TAGS = new long[TABLE_NAMES.length];

    static {
        for (int at = 0; at < TABLE_NAMES.length; at++) {
            TABLE_TAGS[at] = OpenTypeFontData.tag(TABLE_NAMES[at]);
        }
    }

    private static final int HEAD_SIZE = 54;

    private static final int MAXP_SIZE = 6;

    private static final int OS2_FIRST_SIZE = 68;

    private static final int OS2_TYPOGRAPHIC_SIZE = 78;

    private static final int FEWEST_UNITS_PER_EM = 16;

    private static final int MOST_UNITS_PER_EM = 16384;

    private static final int HEAD_BASELINE_Y_EQUALS_0 = 0x0001;

    private static final int HEAD_LSB_POINT_X_EQUALS_0 = 0x0002;

    private static final int HEAD_LAST_RESORT_FONT = 0x4000;

    private static final int SELECTION_USE_TYPOGRAPHIC_METRICS = 0x0080;

    final int faceIndex;

    int outlineType;

    int glyphCount;

    long revision;

    int faceFlags;

    int diagFlags;

    int unitsPerEm;

    boolean hasLongLocaOffsets;

    OpenTypeTable glyf = OpenTypeTable.EMPTY;

    OpenTypeTable loca = OpenTypeTable.EMPTY;

    OpenTypeCharacterMap characterMap;

    OpenTypeMetrics metrics;

    OpenTypeLayout layout;

    OpenTypeKerning kerning;

    CffOutlines cff;

    private OpenTypeFace(int faceIndex) {
        this.faceIndex = faceIndex;
    }

    public static Optional<OpenTypeFace> read(byte[] bytes) {
        if (bytes.length == 0) {
            return Optional.empty();
        }
        return OpenTypeFontData.read(bytes).flatMap(data -> {
            OpenTypeFace face = new OpenTypeFace(0);
            OpenTypeTable[] tables = data.findTables(0, TABLE_TAGS);
            return face.initialise(tables) ? Optional.of(face) : Optional.empty();
        });
    }

    private static OpenTypeTable findTable(OpenTypeTable[] tables, String name) {
        for (int at = 0; at < TABLE_NAMES.length; at++) {
            if (TABLE_NAMES[at].equals(name)) {
                return tables[at];
            }
        }
        throw new IllegalArgumentException(name);
    }

    private boolean initialise(OpenTypeTable[] tables) {
        if (!readHead(findTable(tables, "head")) || !readMaximumProfile(findTable(tables, "maxp"))) {
            return false;
        }
        readOs2(findTable(tables, "OS/2"));
        if (!OpenTypeNames.read(this, findTable(tables, "name"))) {
            return false;
        }
        characterMap = OpenTypeCharacterMap.read(this, findTable(tables, "cmap"));
        OpenTypeTable cffTable = findTable(tables, "CFF ");
        OpenTypeTable cff2Table = findTable(tables, "CFF2");
        if (!cffTable.isEmpty() || !cff2Table.isEmpty()) {
            Optional<CffOutlines> outlines = cff2Table.isEmpty()
                    ? CffOutlines.read(cffTable, 0) : CffOutlines.read(cff2Table, 1);
            if (outlines.isEmpty()) {
                return false;
            }
            cff = outlines.get();
            outlineType = OUTLINE_CFF + cff.version();
        } else if (!findTable(tables, "glyf").isEmpty() && !findTable(tables, "loca").isEmpty()) {
            outlineType = OUTLINE_TRUETYPE;
            glyf = findTable(tables, "glyf");
            loca = findTable(tables, "loca");
        } else {
            return false;
        }
        Optional<OpenTypeMetrics> readMetrics = OpenTypeMetrics.read(this, findTable(tables, "hhea"),
                findTable(tables, "hmtx"), findTable(tables, "vhea"), findTable(tables, "vmtx"));
        if (readMetrics.isEmpty()) {
            return false;
        }
        metrics = readMetrics.get();
        Optional<OpenTypeLayout> readLayout = OpenTypeLayout.read(this, findTable(tables, "GDEF"),
                findTable(tables, "GSUB"), findTable(tables, "GPOS"));
        if (readLayout.isEmpty()) {
            return false;
        }
        layout = readLayout.get();
        if (!layout.hasGPosKern()) {
            Optional<OpenTypeKerning> readKerning = OpenTypeKerning.read(this, findTable(tables, "kern"));
            if (readKerning.isEmpty()) {
                return false;
            }
            kerning = readKerning.get();
        }
        return true;
    }

    private boolean readHead(OpenTypeTable head) {
        if (!head.fits(HEAD_SIZE)) {
            return false;
        }
        long fontRevision = head.readU32(4);
        int flags = head.readU16(16);
        int units = head.readU16(18);
        if ((flags & HEAD_LAST_RESORT_FONT) != 0) {
            faceFlags |= FLAG_LAST_RESORT_FONT;
        }
        if ((flags & HEAD_BASELINE_Y_EQUALS_0) != 0) {
            faceFlags |= FLAG_BASELINE_Y_EQUALS_0;
        }
        if ((flags & HEAD_LSB_POINT_X_EQUALS_0) != 0) {
            faceFlags |= FLAG_LSB_POINT_X_EQUALS_0;
        }
        if (units < FEWEST_UNITS_PER_EM || units > MOST_UNITS_PER_EM) {
            return false;
        }
        int indexToLocFormat = head.readU16(50);
        if (head.readU16(52) != 0 || indexToLocFormat > 1) {
            return false;
        }
        revision = fontRevision;
        unitsPerEm = units;
        hasLongLocaOffsets = indexToLocFormat == 1;
        return true;
    }

    private boolean readMaximumProfile(OpenTypeTable maxp) {
        if (!maxp.fits(MAXP_SIZE)) {
            return false;
        }
        glyphCount = maxp.readU16(4);
        return glyphCount != 0;
    }

    private void readOs2(OpenTypeTable os2) {
        if (!os2.fits(OS2_FIRST_SIZE)) {
            return;
        }
        boolean hasPanose = false;
        for (int at = 32; at < 42; at++) {
            hasPanose |= os2.readU8(at) != 0;
        }
        if (hasPanose) {
            faceFlags |= FLAG_PANOSE_DATA;
        }
        if ((os2.readU32(42) | os2.readU32(46) | os2.readU32(50) | os2.readU32(54)) != 0) {
            faceFlags |= FLAG_UNICODE_COVERAGE;
        }
        if (os2.fits(OS2_TYPOGRAPHIC_SIZE) && (os2.readU16(62) & SELECTION_USE_TYPOGRAPHIC_METRICS) != 0) {
            faceFlags |= FLAG_TYPOGRAPHIC_METRICS;
        }
    }

    public String describe() {
        return "faceType:    " + FACE_TYPE_OPENTYPE + "\n"
                + "outlineType: " + outlineType + "\n"
                + "glyphCount:  " + glyphCount + "\n"
                + "revision:    " + revision + "\n"
                + "faceIndex:   " + faceIndex + "\n"
                + "faceFlags:   " + Integer.toUnsignedString(faceFlags) + "\n"
                + "diagFlags:   " + diagFlags + "\n";
    }
}
