package org.jebol.application;

import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class StoredOctets {

    private static final int WIDEST_CHARACTER_STORED_IN_BYTE = 0xFF;

    private StoredOctets() {
    }

    static long countUnitsFromIndex(Value data) {
        int skipped = ((RebolSeries) data).index() - 1;
        long units = data instanceof BinaryValue bytes
                ? bytes.head().octetsFromHere().length
                : ((AnyStringValue) data).head().text().codePointCount(0, ((AnyStringValue) data).head().text().length());
        return units - skipped;
    }

    static byte[] readFromIndex(Value data, long length) {
        int skipped = ((RebolSeries) data).index() - 1;
        byte[] stored = readStoredFromHead(data);
        int from = Math.min(skipped, stored.length);
        int to = (int) Math.min(stored.length, from + Math.max(0, length));
        return Arrays.copyOfRange(stored, from, to);
    }

    private static byte[] readStoredFromHead(Value data) {
        if (data instanceof BinaryValue bytes) {
            return bytes.head().octetsFromHere();
        }
        String characters = ((AnyStringValue) data).head().text();
        boolean isStoredWide = characters.codePoints().anyMatch(code -> code > WIDEST_CHARACTER_STORED_IN_BYTE);
        return characters.getBytes(isStoredWide ? StandardCharsets.UTF_16LE : StandardCharsets.ISO_8859_1);
    }
}
