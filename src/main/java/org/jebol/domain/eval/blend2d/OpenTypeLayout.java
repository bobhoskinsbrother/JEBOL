package org.jebol.domain.eval.blend2d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class OpenTypeLayout {

    private static final int HEADER_1_0_SIZE = 10;

    private static final int HEADER_1_1_SIZE = 14;

    private static final int LOOKUP_TABLE_SIZE = 6;

    private static final int TAG_RECORD_SIZE = 6;

    private static final int SCRIPT_TABLE_SIZE = 4;

    private static final int LANGUAGE_SYSTEM_SIZE = 6;

    private static final int FEATURE_TABLE_SIZE = 4;

    private static final int COVERAGE_HEADER_SIZE = 4;

    private static final int COVERAGE_RANGE_SIZE = 6;

    private static final int CLASS_RANGE_SIZE = 6;

    private static final long LATIN = OpenTypeFontData.tag("latn");

    private static final long DEFAULT_SCRIPT = OpenTypeFontData.tag("DFLT");

    private static final long KERN = OpenTypeFontData.tag("kern");

    private static final Set<Long> ENABLED_BY_DEFAULT = Set.of(
            OpenTypeFontData.tag("calt"), OpenTypeFontData.tag("clig"), OpenTypeFontData.tag("cpsp"), KERN,
            OpenTypeFontData.tag("liga"), OpenTypeFontData.tag("opbd"), OpenTypeFontData.tag("rvrn"));

    private static final int[] GSUB_FORMAT_COUNTS = {0, 2, 1, 1, 1, 3, 3, 1, 1};

    private static final int[] GPOS_FORMAT_COUNTS = {0, 2, 2, 1, 1, 1, 1, 3, 3, 1};

    private static final int GSUB_EXTENSION = 7;

    private static final int GPOS_EXTENSION = 9;

    record Kind(OpenTypeTable table, int lookupListOffset, int lookupCount, int featureListOffset,
                int scriptListOffset, boolean isSubstitution) {

        boolean hasLookups() {
            return lookupCount != 0;
        }
    }

    private final Kind substitution;

    private final Kind positioning;

    private final boolean hasGPosKern;

    private final Map<Long, Boolean> validity = new HashMap<>();

    private OpenTypeLayout(Kind substitution, Kind positioning, boolean hasGPosKern) {
        this.substitution = substitution;
        this.positioning = positioning;
        this.hasGPosKern = hasGPosKern;
    }

    static Optional<OpenTypeLayout> read(OpenTypeFace face, OpenTypeTable gdef, OpenTypeTable gsub,
                                         OpenTypeTable gpos) {
        Set<Long> featureTags = new HashSet<>();
        Kind substitution = gsub.isEmpty() ? empty(true) : readKind(gsub, true, featureTags);
        Kind positioning = gpos.isEmpty() ? empty(false) : readKind(gpos, false, featureTags);
        boolean kernAvailable = positioning.hasLookups() && featureTags.contains(KERN);
        return Optional.of(new OpenTypeLayout(substitution, positioning, kernAvailable));
    }

    private static Kind empty(boolean isSubstitution) {
        return new Kind(OpenTypeTable.EMPTY, 0, 0, 0, 0, isSubstitution);
    }

    private static Kind readKind(OpenTypeTable table, boolean isSubstitution, Set<Long> featureTags) {
        if (!table.fits(HEADER_1_0_SIZE)) {
            return empty(isSubstitution);
        }
        long version = table.readU32(0);
        if (version < 0x00010000L || version > 0x00010001L) {
            return empty(isSubstitution);
        }
        int headerSize = version >= 0x00010001L ? HEADER_1_1_SIZE : HEADER_1_0_SIZE;
        if (!table.fits(headerSize)) {
            return empty(isSubstitution);
        }
        int lookupListOffset = table.readU16(8);
        int featureListOffset = table.readU16(6);
        int scriptListOffset = table.readU16(4);
        lookupListOffset = lookupListOffset == table.size() ? 0 : lookupListOffset;
        featureListOffset = featureListOffset == table.size() ? 0 : featureListOffset;
        scriptListOffset = scriptListOffset == table.size() ? 0 : scriptListOffset;
        for (int offset : new int[] {lookupListOffset, featureListOffset, scriptListOffset}) {
            if (offset != 0 && (offset < headerSize || offset >= table.size() - 2)) {
                return empty(isSubstitution);
            }
        }
        int lookupCount = 0;
        int storedLookupListOffset = 0;
        if (lookupListOffset != 0) {
            int count = table.readU16(lookupListOffset);
            if (count != 0) {
                lookupCount = count;
                storedLookupListOffset = lookupListOffset;
            }
        }
        int storedFeatureListOffset = 0;
        if (featureListOffset != 0) {
            int count = table.readU16(featureListOffset);
            for (int feature = 0; feature < count; feature++) {
                featureTags.add(table.readU32(featureListOffset + 2 + feature * TAG_RECORD_SIZE));
            }
            if (count != 0) {
                storedFeatureListOffset = featureListOffset;
            }
        }
        int storedScriptListOffset = 0;
        if (scriptListOffset != 0 && table.readU16(scriptListOffset) != 0) {
            storedScriptListOffset = scriptListOffset;
        }
        return new Kind(lookupCount != 0 ? table : OpenTypeTable.EMPTY, storedLookupListOffset, lookupCount,
                storedFeatureListOffset, storedScriptListOffset, isSubstitution);
    }

    boolean hasGPosKern() {
        return hasGPosKern;
    }

    boolean hasSubstitutions() {
        return substitution.hasLookups();
    }

    boolean hasPositioning() {
        return positioning.hasLookups();
    }

    private static boolean[] calculatePlan(Kind kind) {
        OpenTypeTable table = kind.table();
        if (table.isEmpty()) {
            return new boolean[0];
        }
        OpenTypeTable scriptList = table.subTable(kind.scriptListOffset());
        OpenTypeTable featureList = table.subTable(kind.featureListOffset());
        OpenTypeTable script = findScript(scriptList);
        if (script.isEmpty() || !script.fits(SCRIPT_TABLE_SIZE)) {
            return new boolean[0];
        }
        int languageSystemOffset = script.readU16(0);
        if (languageSystemOffset == 0) {
            return new boolean[0];
        }
        OpenTypeTable languageSystem = script.subTable(languageSystemOffset);
        if (!languageSystem.fits(LANGUAGE_SYSTEM_SIZE)) {
            return new boolean[0];
        }
        int featureIndexCount = languageSystem.readU16(4);
        if (languageSystem.size() < LANGUAGE_SYSTEM_SIZE + featureIndexCount * 2L) {
            return new boolean[0];
        }
        int featureCount = featureList.readU16(0);
        if (featureList.size() < 2 + featureCount * 2L) {
            return new boolean[0];
        }
        int lookupCount = kind.lookupCount();
        boolean[] plan = new boolean[lookupCount];
        int index = -1;
        int featureIndex = languageSystem.readU16(2);
        while (true) {
            if (featureIndex < featureCount) {
                long featureTag = featureList.readU32(2 + featureIndex * TAG_RECORD_SIZE);
                if (ENABLED_BY_DEFAULT.contains(featureTag)) {
                    int featureOffset = featureList.readU16(2 + featureIndex * TAG_RECORD_SIZE + 4);
                    OpenTypeTable feature = featureList.subTable(featureOffset);
                    if (feature.fits(FEATURE_TABLE_SIZE)) {
                        int lookupIndexCount = feature.readU16(2);
                        if (feature.size() >= FEATURE_TABLE_SIZE + lookupIndexCount * 2L) {
                            for (int lookup = 0; lookup < lookupIndexCount; lookup++) {
                                int lookupIndex = feature.readU16(FEATURE_TABLE_SIZE + lookup * 2);
                                if (lookupIndex < lookupCount) {
                                    plan[lookupIndex] = true;
                                }
                            }
                        }
                    }
                }
            }
            if (++index >= featureIndexCount) {
                break;
            }
            featureIndex = languageSystem.readU16(LANGUAGE_SYSTEM_SIZE + index * 2);
        }
        return plan;
    }

    private static OpenTypeTable findScript(OpenTypeTable scriptList) {
        if (scriptList.size() < 2) {
            return OpenTypeTable.EMPTY;
        }
        int scriptCount = scriptList.readU16(0);
        OpenTypeTable found = OpenTypeTable.EMPTY;
        if (scriptList.size() >= 2 + (long) scriptCount * TAG_RECORD_SIZE) {
            for (int script = 0; script < scriptCount; script++) {
                long recordTag = scriptList.readU32(2 + script * TAG_RECORD_SIZE);
                if (recordTag == LATIN || recordTag == DEFAULT_SCRIPT) {
                    found = scriptList.subTable(scriptList.readU16(2 + script * TAG_RECORD_SIZE + 4));
                    if (recordTag == LATIN) {
                        break;
                    }
                }
            }
        }
        return found;
    }

    int[] substitute(int[] glyphs) {
        if (!substitution.hasLookups()) {
            return glyphs;
        }
        int[][] buffer = {glyphs};
        boolean[] plan = calculatePlan(substitution);
        forEachSubTable(substitution, plan, (type, subTable) -> buffer[0] = applySubstitution(type, subTable,
                buffer[0]));
        return buffer[0];
    }

    void position(int[] glyphs, int[][] placements) {
        if (!positioning.hasLookups() || glyphs.length == 0) {
            return;
        }
        boolean[] plan = calculatePlan(positioning);
        forEachSubTable(positioning, plan, (type, subTable) -> applyPositioning(type, subTable, glyphs,
                placements));
    }

    private interface SubTableAction {
        void apply(int type, OpenTypeTable subTable);
    }

    private void forEachSubTable(Kind kind, boolean[] plan, SubTableAction action) {
        OpenTypeTable lookupList = kind.table().subTableUnchecked(kind.lookupListOffset());
        for (int lookupIndex = 0; lookupIndex < plan.length; lookupIndex++) {
            if (!plan[lookupIndex] || !isValid(kind, lookupIndex)) {
                continue;
            }
            OpenTypeTable lookup = lookupList.subTableUnchecked(lookupList.readU16(2 + lookupIndex * 2));
            int type = lookup.readU16(0);
            int subTableCount = lookup.readU16(4);
            for (int sub = 0; sub < subTableCount; sub++) {
                action.apply(type, lookup.subTableUnchecked(lookup.readU16(LOOKUP_TABLE_SIZE + sub * 2)));
            }
        }
    }

    private boolean isValid(Kind kind, int lookupIndex) {
        long key = (kind.isSubstitution() ? 1L << 32 : 0L) | lookupIndex;
        return validity.computeIfAbsent(key, ignored -> validateLookup(kind, lookupIndex));
    }

    private static boolean validateLookup(Kind kind, int lookupIndex) {
        OpenTypeTable table = kind.table();
        if (!table.fits(HEADER_1_0_SIZE)) {
            return false;
        }
        boolean isSubstitution = kind.isSubstitution();
        int[] formatCounts = isSubstitution ? GSUB_FORMAT_COUNTS : GPOS_FORMAT_COUNTS;
        int extensionType = isSubstitution ? GSUB_EXTENSION : GPOS_EXTENSION;
        OpenTypeTable lookupList = table.subTable(kind.lookupListOffset());
        int lookupCount = kind.lookupCount();
        if (lookupIndex >= lookupCount) {
            return false;
        }
        int lookupListSize = 2 + lookupCount * 2;
        if (!lookupList.fits(lookupListSize)) {
            return false;
        }
        int lookupOffset = lookupList.readU16(2 + lookupIndex * 2);
        if (lookupOffset < lookupListSize || lookupOffset >= lookupList.size() - LOOKUP_TABLE_SIZE) {
            return false;
        }
        OpenTypeTable lookup = lookupList.subTable(lookupOffset);
        int type = lookup.readU16(0);
        if (type == 0 || type >= formatCounts.length) {
            return false;
        }
        int subTableCount = lookup.readU16(4);
        int lookupSize = LOOKUP_TABLE_SIZE + subTableCount * 2;
        if (!lookup.fits(lookupSize)) {
            return false;
        }
        int subTableMinimum = type == extensionType ? 8 : 6;
        for (int sub = 0; sub < subTableCount; sub++) {
            int subTableOffset = lookup.readU16(LOOKUP_TABLE_SIZE + sub * 2);
            if (subTableOffset < lookupSize || subTableOffset >= lookup.size() - subTableMinimum) {
                return false;
            }
            if (type == extensionType) {
                return false;
            }
            OpenTypeTable subTable = lookup.subTable(subTableOffset);
            int format = subTable.readU16(0);
            if (format - 1 < 0 || format - 1 >= formatCounts[type]) {
                return false;
            }
            boolean isValid = isSubstitution ? validateSubstitution(type, format, subTable)
                    : validatePositioning(type, format, subTable);
            if (!isValid) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateSubstitution(int type, int format, OpenTypeTable subTable) {
        return switch (type) {
            case 1 -> format == 1 ? validateWithCoverage(subTable, 6) >= 0 : validateSingleSubstitution2(subTable);
            case 2 -> validateMultipleSubstitution(subTable);
            case 3 -> validateAlternateSubstitution(subTable);
            case 4 -> validateLigatureSubstitution(subTable);
            case 8 -> validateReverseChained(subTable);
            default -> true;
        };
    }

    private static boolean validatePositioning(int type, int format, OpenTypeTable subTable) {
        return switch (type) {
            case 1 -> validateSingleAdjustment(format, subTable);
            case 2 -> format == 1 ? validatePairAdjustment1(subTable) : validatePairAdjustment2(subTable);
            case 4, 5, 6 -> false;
            default -> true;
        };
    }

    private static int validateCoverage(OpenTypeTable coverage) {
        if (!coverage.fits(COVERAGE_HEADER_SIZE)) {
            return -1;
        }
        int format = coverage.readU16(0);
        int count = coverage.readU16(2);
        if (format == 1) {
            if (!coverage.fits(COVERAGE_HEADER_SIZE + count * 2L) || count == 0) {
                return -1;
            }
            return count;
        }
        if (format != 2 || !coverage.fits(COVERAGE_HEADER_SIZE + (long) count * COVERAGE_RANGE_SIZE) || count == 0) {
            return -1;
        }
        int first = coverage.readU16(4);
        int last = coverage.readU16(6);
        int current = coverage.readU16(8);
        if (first > last || current != 0) {
            return -1;
        }
        current += last - first + 1;
        for (int range = 1; range < count; range++) {
            int at = COVERAGE_HEADER_SIZE + range * COVERAGE_RANGE_SIZE;
            first = coverage.readU16(at);
            if (first <= last) {
                return -1;
            }
            last = coverage.readU16(at + 2);
            if (first > last || coverage.readU16(at + 4) != current) {
                return -1;
            }
            current += last - first + 1;
        }
        return current;
    }

    private static int validateWithCoverage(OpenTypeTable subTable, int headerSize) {
        if (!subTable.fits(headerSize)) {
            return -1;
        }
        int coverageOffset = subTable.readU16(2);
        if (coverageOffset < headerSize || coverageOffset >= subTable.size()) {
            return -1;
        }
        return validateCoverage(subTable.subTable(coverageOffset));
    }

    private static boolean validateSingleSubstitution2(OpenTypeTable subTable) {
        if (validateWithCoverage(subTable, 6) < 0) {
            return false;
        }
        return subTable.fits(6 + subTable.readU16(4) * 2L);
    }

    private static boolean validateMultipleSubstitution(OpenTypeTable subTable) {
        if (validateWithCoverage(subTable, 6) < 0) {
            return false;
        }
        int count = subTable.readU16(4);
        int headerSize = 6 + count * 2;
        if (!subTable.fits(headerSize)) {
            return false;
        }
        for (int sequence = 0; sequence < count; sequence++) {
            int offset = subTable.readU16(6 + sequence * 2);
            if (offset < headerSize || offset >= subTable.size() - 4) {
                return false;
            }
            OpenTypeTable sequenceTable = subTable.subTable(offset);
            if (sequenceTable.fits(2 + sequenceTable.readU16(0) * 2L)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateAlternateSubstitution(OpenTypeTable subTable) {
        if (validateWithCoverage(subTable, 6) < 0) {
            return false;
        }
        int count = subTable.readU16(4);
        int headerSize = 6 + count * 2;
        if (!subTable.fits(headerSize)) {
            return false;
        }
        for (int set = 0; set < count; set++) {
            int offset = subTable.readU16(6 + set * 2);
            if (offset < headerSize || offset >= subTable.size() - 4) {
                return false;
            }
            int length = subTable.readU16(offset);
            if (length == 0 || offset + 2 + length * 2L > subTable.size()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateLigatureSubstitution(OpenTypeTable subTable) {
        if (validateWithCoverage(subTable, 6) < 0) {
            return false;
        }
        int count = subTable.readU16(4);
        int headerSize = 6 + count * 2;
        if (!subTable.fits(headerSize)) {
            return false;
        }
        for (int set = 0; set < count; set++) {
            int setOffset = subTable.readU16(6 + set * 2);
            if (setOffset < headerSize || setOffset >= subTable.size() - 4) {
                return false;
            }
            OpenTypeTable ligatureSet = subTable.subTable(setOffset);
            int ligatureCount = ligatureSet.readU16(0);
            if (ligatureCount == 0) {
                return false;
            }
            int setHeaderSize = 2 + ligatureCount * 2;
            if (!ligatureSet.fits(setHeaderSize)) {
                return false;
            }
            for (int ligature = 0; ligature < ligatureCount; ligature++) {
                int ligatureOffset = ligatureSet.readU16(2 + ligature * 2);
                if (ligatureOffset < setHeaderSize || ligatureOffset >= ligatureSet.size() - 6) {
                    return false;
                }
                OpenTypeTable ligatureTable = ligatureSet.subTable(ligatureOffset);
                int components = ligatureTable.readU16(2);
                if (components < 2 || !ligatureTable.fits(2 + components * 2L)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean validateReverseChained(OpenTypeTable subTable) {
        if (!subTable.fits(6)) {
            return false;
        }
        int backtrackCount = subTable.readU16(4);
        int lookaheadOffset = 6 + backtrackCount * 2;
        if (!subTable.fits(lookaheadOffset + 2L)) {
            return false;
        }
        int lookaheadCount = subTable.readU16(lookaheadOffset);
        int substitutionOffset = lookaheadOffset + 2 + lookaheadCount * 2;
        if (!subTable.fits(substitutionOffset + 2L)) {
            return false;
        }
        int substitutionCount = subTable.readU16(substitutionOffset);
        int headerSize = substitutionOffset + 2 + substitutionCount * 2;
        if (!subTable.fits(headerSize)) {
            return false;
        }
        int coverageOffset = subTable.readU16(2);
        if (coverageOffset < headerSize || coverageOffset >= subTable.size()) {
            return false;
        }
        if (validateCoverage(subTable.subTable(coverageOffset)) != substitutionCount) {
            return false;
        }
        return validateCoverages(subTable, 6, backtrackCount, headerSize)
                && validateCoverages(subTable, lookaheadOffset + 2, lookaheadCount, headerSize);
    }

    private static boolean validateCoverages(OpenTypeTable subTable, int arrayAt, int count, int start) {
        for (int at = 0; at < count; at++) {
            int offset = subTable.readU16(arrayAt + at * 2);
            if (offset == 0) {
                continue;
            }
            if (offset < start || offset >= subTable.size() || validateCoverage(subTable.subTable(offset)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int sizeOfValueRecord(int valueFormat) {
        return Integer.bitCount(valueFormat & 0xFF) * 2;
    }

    private static boolean validateSingleAdjustment(int format, OpenTypeTable subTable) {
        int headerSize = format == 1 ? 6 : 8;
        if (validateWithCoverage(subTable, headerSize) < 0) {
            return false;
        }
        int valueFormat = subTable.readU16(4);
        if (valueFormat == 0) {
            return false;
        }
        if (format == 1) {
            return subTable.fits(6L + sizeOfValueRecord(valueFormat));
        }
        int valueCount = subTable.readU16(6);
        return valueCount != 0 && subTable.fits(8L + (long) sizeOfValueRecord(valueFormat) * valueCount);
    }

    private static boolean validatePairAdjustment1(OpenTypeTable subTable) {
        if (validateWithCoverage(subTable, 10) < 0) {
            return false;
        }
        int count = subTable.readU16(8);
        int recordSize = 2 + sizeOfValueRecord(subTable.readU16(4)) + sizeOfValueRecord(subTable.readU16(6));
        int headerSize = 10 + count * 2;
        if (!subTable.fits(headerSize)) {
            return false;
        }
        for (int set = 0; set < count; set++) {
            int offset = subTable.readU16(10 + set * 2);
            if (offset < headerSize || offset >= subTable.size() - 2) {
                return false;
            }
            OpenTypeTable pairSet = subTable.subTable(offset);
            if (!pairSet.fits((long) pairSet.readU16(0) * recordSize)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validatePairAdjustment2(OpenTypeTable subTable) {
        if (validateWithCoverage(subTable, 16) < 0) {
            return false;
        }
        long recordCount = (long) subTable.readU16(12) * subTable.readU16(14);
        long recordSize = sizeOfValueRecord(subTable.readU16(4)) + sizeOfValueRecord(subTable.readU16(6));
        long size = recordCount * recordSize;
        if (size > subTable.size() - 16L) {
            size = 0xFFFFFFFFL;
        }
        return subTable.fits(size);
    }

    private record Coverage(OpenTypeTable table, int format, int count) {

        static Coverage at(OpenTypeTable table) {
            return new Coverage(table, table.readU16(0), table.readU16(2));
        }

        int minimum() {
            return format == 1 ? table.readU16(4) : table.readU16(4);
        }

        int maximum() {
            return format == 1 ? table.readU16(4 + (count - 1) * 2)
                    : table.readU16(4 + (count - 1) * COVERAGE_RANGE_SIZE + 2);
        }

        boolean contains(int glyph) {
            return glyph >= minimum() && glyph <= maximum();
        }

        int find(int glyph) {
            int base = 0;
            int size = count;
            if (format == 1) {
                for (int half = size / 2; half != 0; half = size / 2) {
                    int middle = base + half;
                    size -= half;
                    if (glyph >= table.readU16(4 + middle * 2)) {
                        base = middle;
                    }
                }
                return table.readU16(4 + base * 2) == glyph ? base : -1;
            }
            for (int half = size / 2; half != 0; half = size / 2) {
                int middle = base + half;
                size -= half;
                if (glyph >= table.readU16(4 + middle * COVERAGE_RANGE_SIZE)) {
                    base = middle;
                }
            }
            int at = 4 + base * COVERAGE_RANGE_SIZE;
            int first = table.readU16(at);
            int last = table.readU16(at + 2);
            return glyph >= first && glyph <= last ? table.readU16(at + 4) + glyph - first : -1;
        }
    }

    private record ClassDefinition(OpenTypeTable table, int format, int count, int firstGlyph) {

        static ClassDefinition at(OpenTypeTable table) {
            if (table.size() < 6) {
                return new ClassDefinition(table, 0, 0, 0);
            }
            int format = table.readU16(0);
            if (format == 1) {
                int count = table.readU16(4);
                boolean fits = count != 0 && 6 + count * 2L <= table.size();
                return new ClassDefinition(table, fits ? 1 : 0, count, table.readU16(2));
            }
            if (format == 2) {
                int count = table.readU16(2);
                boolean fits = count != 0 && 4 + (long) count * CLASS_RANGE_SIZE <= table.size();
                return new ClassDefinition(table, fits ? 2 : 0, count, table.readU16(4));
            }
            return new ClassDefinition(table, 0, 0, 0);
        }

        int classOf(int glyph) {
            if (format == 1) {
                int index = glyph - firstGlyph;
                return Integer.compareUnsigned(index, count) < 0 ? table.readU16(6 + index * 2) : 0;
            }
            int base = 0;
            int size = count;
            for (int half = size / 2; half != 0; half = size / 2) {
                int middle = base + half;
                size -= half;
                if (glyph >= table.readU16(4 + middle * CLASS_RANGE_SIZE)) {
                    base = middle;
                }
            }
            int at = 4 + base * CLASS_RANGE_SIZE;
            return glyph < table.readU16(at) || glyph > table.readU16(at + 2) ? 0 : table.readU16(at + 4);
        }
    }

    private static int[] applySubstitution(int type, OpenTypeTable subTable, int[] glyphs) {
        if (glyphs.length == 0) {
            return glyphs;
        }
        int format = subTable.readU16(0);
        return switch (type) {
            case 1 -> {
                substituteSingle(format, subTable, glyphs);
                yield glyphs;
            }
            case 3 -> {
                substituteAlternate(subTable, glyphs);
                yield glyphs;
            }
            case 4 -> substituteLigatures(subTable, glyphs);
            case 8 -> {
                substituteReverseChained(subTable, glyphs);
                yield glyphs;
            }
            default -> glyphs;
        };
    }

    private static void substituteSingle(int format, OpenTypeTable subTable, int[] glyphs) {
        Coverage coverage = Coverage.at(subTable.subTable(subTable.readU16(2)));
        int delta = subTable.readU16(4);
        for (int at = 0; at < glyphs.length; at++) {
            int glyph = glyphs[at];
            if (!coverage.contains(glyph)) {
                continue;
            }
            int index = coverage.find(glyph);
            if (index < 0) {
                continue;
            }
            if (format == 1) {
                glyphs[at] = (glyph + delta) & 0xFFFF;
            } else if (index < subTable.readU16(4)) {
                glyphs[at] = subTable.readU16(6 + index * 2);
            }
        }
    }

    private static void substituteAlternate(OpenTypeTable subTable, int[] glyphs) {
        Coverage coverage = Coverage.at(subTable.subTable(subTable.readU16(2)));
        int setCount = subTable.readU16(4);
        for (int at = 0; at < glyphs.length; at++) {
            if (!coverage.contains(glyphs[at])) {
                continue;
            }
            int index = coverage.find(glyphs[at]);
            if (index >= 0 && index < setCount) {
                glyphs[at] = subTable.readU16(subTable.readU16(6 + index * 2) + 2);
            }
        }
    }

    private static int[] matchLigature(OpenTypeTable ligatureSet, int[] glyphs, int at) {
        int ligatureCount = ligatureSet.readU16(0);
        int available = glyphs.length - at;
        for (int ligature = 0; ligature < ligatureCount; ligature++) {
            OpenTypeTable entry = ligatureSet.subTableUnchecked(ligatureSet.readU16(2 + ligature * 2));
            int components = entry.readU16(2);
            if (components > available) {
                continue;
            }
            int component = 1;
            while (entry.readU16(4 + (component - 1) * 2) == glyphs[at + component]) {
                if (++component >= components) {
                    return new int[] {entry.readU16(0), components};
                }
            }
        }
        return null;
    }

    private static int[] substituteLigatures(OpenTypeTable subTable, int[] glyphs) {
        Coverage coverage = Coverage.at(subTable.subTable(subTable.readU16(2)));
        int setCount = subTable.readU16(4);
        int[] out = new int[glyphs.length];
        int written = 0;
        int at = 0;
        boolean didSubstitute = false;
        while (at < glyphs.length) {
            int glyph = glyphs[at];
            if (coverage.contains(glyph)) {
                int index = coverage.find(glyph);
                if (index >= 0 && index < setCount) {
                    OpenTypeTable ligatureSet = subTable.subTableUnchecked(subTable.readU16(6 + index * 2));
                    int[] match = matchLigature(ligatureSet, glyphs, at);
                    if (match != null) {
                        out[written++] = match[0];
                        at += match[1];
                        didSubstitute = true;
                        continue;
                    }
                }
            }
            out[written++] = glyph;
            at++;
        }
        return didSubstitute ? java.util.Arrays.copyOf(out, written) : glyphs;
    }

    private static boolean isCovered(OpenTypeTable subTable, int coverageOffset, int glyph) {
        Coverage coverage = Coverage.at(subTable.subTableUnchecked(coverageOffset));
        return coverage.contains(glyph) && coverage.find(glyph) >= 0;
    }

    private static void substituteReverseChained(OpenTypeTable subTable, int[] glyphs) {
        int backtrackCount = subTable.readU16(4);
        int lookaheadOffset = 6 + backtrackCount * 2;
        int lookaheadCount = subTable.readU16(lookaheadOffset);
        int substitutionOffset = lookaheadOffset + 2 + lookaheadCount * 2;
        long end = Math.min(glyphs.length, (long) glyphs.length - lookaheadCount - 1 < 0 ? Long.MAX_VALUE
                : (long) glyphs.length - lookaheadCount - 1);
        int begin = backtrackCount;
        if (glyphs.length < lookaheadCount || begin >= end) {
            return;
        }
        int substitutionCount = subTable.readU16(substitutionOffset);
        Coverage coverage = Coverage.at(subTable.subTableUnchecked(subTable.readU16(2)));
        int at = (int) end;
        do {
            int glyph = glyphs[--at];
            int index = coverage.contains(glyph) ? coverage.find(glyph) : -1;
            if (index < 0 || index >= substitutionCount) {
                continue;
            }
            boolean matches = true;
            for (int back = 0; back < backtrackCount && matches; back++) {
                int position = at - 1 - back;
                matches = position >= 0 && isCovered(subTable, subTable.readU16(6 + back * 2), glyphs[position]);
            }
            for (int ahead = 0; ahead < lookaheadCount && matches; ahead++) {
                int position = at + 1 + ahead;
                matches = position < glyphs.length
                        && isCovered(subTable, subTable.readU16(lookaheadOffset + 2 + ahead * 2), glyphs[position]);
            }
            if (matches) {
                glyphs[at] = subTable.readU16(substitutionOffset + 2 + index * 2);
            }
        } while (at != begin);
    }

    private static void applyPositioning(int type, OpenTypeTable subTable, int[] glyphs, int[][] placements) {
        int format = subTable.readU16(0);
        if (type == 1) {
            positionSingle(format, subTable, glyphs, placements);
        } else if (type == 2) {
            if (format == 1) {
                positionPairs(subTable, glyphs, placements);
            } else {
                positionPairClasses(subTable, glyphs, placements);
            }
        }
    }

    private static int applyValue(OpenTypeTable table, int at, int valueFormat, int[] placement) {
        int cursor = at;
        for (int bit = 0; bit < 8; bit++) {
            if ((valueFormat & 1 << bit) == 0) {
                continue;
            }
            if (bit < 4) {
                placement[bit] += table.readI16(cursor);
            }
            cursor += 2;
        }
        return cursor;
    }

    private static void positionSingle(int format, OpenTypeTable subTable, int[] glyphs, int[][] placements) {
        Coverage coverage = Coverage.at(subTable.subTable(subTable.readU16(2)));
        int valueFormat = subTable.readU16(4);
        int recordSize = sizeOfValueRecord(valueFormat);
        for (int at = 0; at < glyphs.length; at++) {
            if (!coverage.contains(glyphs[at])) {
                continue;
            }
            int index = coverage.find(glyphs[at]);
            if (index < 0) {
                continue;
            }
            if (format == 1) {
                applyValue(subTable, 6, valueFormat, placements[at]);
            } else if (index < subTable.readU16(6)) {
                applyValue(subTable, 8 + index * recordSize, valueFormat, placements[at]);
            }
        }
    }

    private static void positionPairs(OpenTypeTable subTable, int[] glyphs, int[][] placements) {
        if (glyphs.length < 2) {
            return;
        }
        Coverage coverage = Coverage.at(subTable.subTable(subTable.readU16(2)));
        int format1 = subTable.readU16(4);
        int format2 = subTable.readU16(6);
        int setCount = subTable.readU16(8);
        int recordSize = 2 + sizeOfValueRecord(format1) + sizeOfValueRecord(format2);
        for (int at = 0; at < glyphs.length - 1; at++) {
            int left = glyphs[at];
            int right = glyphs[at + 1];
            if (!coverage.contains(left)) {
                continue;
            }
            int index = coverage.find(left);
            if (index < 0 || index >= setCount) {
                continue;
            }
            int setOffset = subTable.readU16(10 + index * 2);
            int pairCount = subTable.readU16(setOffset);
            int record = findRecord(subTable, setOffset + 2, recordSize, pairCount, right);
            if (record < 0) {
                continue;
            }
            int cursor = record + 2;
            if (format1 != 0) {
                cursor = applyValue(subTable, cursor, format1, placements[at]);
            }
            if (format2 != 0) {
                applyValue(subTable, cursor, format2, placements[at + 1]);
            }
        }
    }

    private static int findRecord(OpenTypeTable table, int arrayAt, int recordSize, int count, int glyph) {
        if (count == 0) {
            return -1;
        }
        int base = arrayAt;
        int size = count;
        for (int half = size / 2; half != 0; half = size / 2) {
            int middle = base + half * recordSize;
            size -= half;
            if (glyph >= table.readU16(middle)) {
                base = middle;
            }
        }
        return table.readU16(base) == glyph ? base : -1;
    }

    private static void positionPairClasses(OpenTypeTable subTable, int[] glyphs, int[][] placements) {
        Coverage coverage = Coverage.at(subTable.subTable(subTable.readU16(2)));
        ClassDefinition first = ClassDefinition.at(subTable.subTable(subTable.readU16(8)));
        ClassDefinition second = ClassDefinition.at(subTable.subTable(subTable.readU16(10)));
        if (first.format() == 0 || second.format() == 0 || glyphs.length < 2) {
            return;
        }
        int format1 = subTable.readU16(4);
        int format2 = subTable.readU16(6);
        int recordSize = sizeOfValueRecord(format1) + sizeOfValueRecord(format2);
        int class2Count = subTable.readU16(14);
        long recordCount = (long) subTable.readU16(12) * class2Count;
        for (int at = 0; at < glyphs.length - 1; at++) {
            int left = glyphs[at];
            if (!coverage.contains(left) || coverage.find(left) < 0) {
                continue;
            }
            long classIndex = (long) first.classOf(left) * class2Count + second.classOf(glyphs[at + 1]);
            if (classIndex >= recordCount) {
                continue;
            }
            int cursor = 16 + (int) classIndex * recordSize;
            if (format1 != 0) {
                cursor = applyValue(subTable, cursor, format1, placements[at]);
            }
            if (format2 != 0) {
                applyValue(subTable, cursor, format2, placements[at + 1]);
            }
        }
    }
}
