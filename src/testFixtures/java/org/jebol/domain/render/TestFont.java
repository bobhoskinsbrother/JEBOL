package org.jebol.domain.render;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestFont {

    public static final int UNITS_PER_EM = 1000;
    public static final int ASCENDER = 800;
    public static final int DESCENDER = -200;
    public static final int ADVANCE_OF_A = 100;
    public static final int ADVANCE_OF_B = 700;

    private static final int GLYPHS = 3;
    private static final int METRICS = 3;
    private static final int ADVANCE_OF_NOTDEF = 500;
    private static final char FIRST_CHARACTER = 'A';
    private static final char LAST_CHARACTER = 'B';
    private static final int GLYPH_OF_A = 1;
    private static final String FAMILY = "Test Sans";

    private TestFont() {
    }

    public static byte[] bytes() {
        Map<String, byte[]> tables = new LinkedHashMap<>();
        tables.put("cmap", cmap());
        tables.put("head", head());
        tables.put("hhea", hhea());
        tables.put("hmtx", hmtx());
        tables.put("maxp", maxp());
        tables.put("name", name());
        return wholeFontOf(tables);
    }

    private static byte[] wholeFontOf(Map<String, byte[]> tables) {
        int records = tables.size();
        int storeAt = 12 + records * 16;
        ByteBuffer font = ByteBuffer.allocate(storeAt + tables.values().stream()
                .mapToInt(one -> padded(one.length)).sum());
        font.putInt(0x00010000);
        font.putShort((short) records);
        font.putShort((short) searchRangeFor(records));
        font.putShort((short) entrySelectorFor(records));
        font.putShort((short) rangeShiftFor(records));
        int at = storeAt;
        for (Map.Entry<String, byte[]> table : tables.entrySet()) {
            font.put(table.getKey().getBytes(StandardCharsets.ISO_8859_1));
            font.putInt(0);
            font.putInt(at);
            font.putInt(table.getValue().length);
            at += padded(table.getValue().length);
        }
        for (byte[] table : tables.values()) {
            font.put(table);
            font.position(font.position() + padded(table.length) - table.length);
        }
        return font.array();
    }

    private static int padded(int length) {
        return (length + 3) & ~3;
    }

    private static int searchRangeFor(int records) {
        int power = Integer.highestOneBit(records);
        return power * 16;
    }

    private static int entrySelectorFor(int records) {
        return Integer.numberOfTrailingZeros(Integer.highestOneBit(records));
    }

    private static int rangeShiftFor(int records) {
        return records * 16 - searchRangeFor(records);
    }

    private static byte[] head() {
        ByteBuffer table = ByteBuffer.allocate(54);
        table.putInt(0x00010000);
        table.putInt(0x00010000);
        table.putInt(0);
        table.putInt(0x5F0F3CF5);
        table.putShort((short) 0);
        table.putShort((short) UNITS_PER_EM);
        table.putLong(0);
        table.putLong(0);
        table.putShort((short) 0);
        table.putShort((short) 0);
        table.putShort((short) ADVANCE_OF_B);
        table.putShort((short) 0);
        table.putShort((short) 0);
        table.putShort((short) 8);
        table.putShort((short) 2);
        table.putShort((short) 0);
        table.putShort((short) 0);
        return table.array();
    }

    private static byte[] hhea() {
        ByteBuffer table = ByteBuffer.allocate(36);
        table.putInt(0x00010000);
        table.putShort((short) ASCENDER);
        table.putShort((short) DESCENDER);
        table.putShort((short) 0);
        table.putShort((short) ADVANCE_OF_B);
        table.putShort((short) 0);
        table.putShort((short) 0);
        table.putShort((short) ADVANCE_OF_B);
        table.putShort((short) 1);
        table.putShort((short) 0);
        table.putShort((short) 0);
        table.putLong(0);
        table.putShort((short) 0);
        table.putShort((short) METRICS);
        return table.array();
    }

    private static byte[] maxp() {
        ByteBuffer table = ByteBuffer.allocate(32);
        table.putInt(0x00010000);
        table.putShort((short) GLYPHS);
        for (int field = 0; field < 13; field++) {
            table.putShort((short) 0);
        }
        return table.array();
    }

    private static byte[] hmtx() {
        ByteBuffer table = ByteBuffer.allocate(METRICS * 4);
        table.putShort((short) ADVANCE_OF_NOTDEF);
        table.putShort((short) 0);
        table.putShort((short) ADVANCE_OF_A);
        table.putShort((short) 0);
        table.putShort((short) ADVANCE_OF_B);
        table.putShort((short) 0);
        return table.array();
    }

    private static byte[] cmap() {
        int segments = 2;
        int subtableLength = 14 + segments * 2 + 2 + segments * 6;
        ByteBuffer table = ByteBuffer.allocate(12 + subtableLength);
        table.putShort((short) 0);
        table.putShort((short) 1);
        table.putShort((short) 3);
        table.putShort((short) 1);
        table.putInt(12);
        table.putShort((short) 4);
        table.putShort((short) subtableLength);
        table.putShort((short) 0);
        table.putShort((short) (segments * 2));
        table.putShort((short) searchRangeFor(segments));
        table.putShort((short) entrySelectorFor(segments));
        table.putShort((short) rangeShiftFor(segments));
        table.putShort((short) LAST_CHARACTER);
        table.putShort((short) 0xFFFF);
        table.putShort((short) 0);
        table.putShort((short) FIRST_CHARACTER);
        table.putShort((short) 0xFFFF);
        table.putShort((short) (GLYPH_OF_A - FIRST_CHARACTER));
        table.putShort((short) 1);
        table.putShort((short) 0);
        table.putShort((short) 0);
        return table.array();
    }

    private static byte[] name() {
        byte[] written = FAMILY.getBytes(StandardCharsets.UTF_16BE);
        ByteBuffer table = ByteBuffer.allocate(6 + 12 + written.length);
        table.putShort((short) 0);
        table.putShort((short) 1);
        table.putShort((short) 18);
        table.putShort((short) 3);
        table.putShort((short) 1);
        table.putShort((short) 0x0409);
        table.putShort((short) 1);
        table.putShort((short) written.length);
        table.putShort((short) 0);
        table.put(written);
        return table.array();
    }
}
