package org.jebol.application;

import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class StoredOctets {

    private StoredOctets() {
    }

    static long countUnitsFromIndex(Value data) {
        return readAllFromIndex(data).length;
    }

    static byte[] readFromIndex(Value data, long length) {
        byte[] stored = readAllFromIndex(data);
        return Arrays.copyOf(stored, (int) Math.max(0, Math.min(stored.length, length)));
    }

    private static byte[] readAllFromIndex(Value data) {
        int skipped = ((RebolSeries) data).index() - 1;
        if (data instanceof BinaryValue bytes) {
            byte[] stored = bytes.head().octetsFromHere();
            return Arrays.copyOfRange(stored, Math.min(skipped, stored.length), stored.length);
        }
        String characters = ((AnyStringValue) data).head().text();
        int characterCount = characters.codePointCount(0, characters.length());
        int from = characters.offsetByCodePoints(0, Math.min(skipped, characterCount));
        return characters.substring(from).getBytes(StandardCharsets.UTF_8);
    }
}
