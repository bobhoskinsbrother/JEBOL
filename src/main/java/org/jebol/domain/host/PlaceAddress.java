package org.jebol.domain.host;

import org.jebol.domain.read.Transcoder;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class PlaceAddress {

    private static final String UNRESERVED_PUNCTUATION = "-._~";
    private static final char ESCAPE = '%';
    private static final int ESCAPE_LENGTH = 3;
    private static final String HEX_DIGITS = "0123456789ABCDEF";
    private static final int NIBBLE_BITS = 4;
    private static final int NIBBLE_MASK = 0x0F;
    private static final int OCTET_MASK = 0xFF;

    String write(List<Value> items) {
        String written = items.stream().map(Molder::moldAll).collect(Collectors.joining(" "));
        StringBuilder escaped = new StringBuilder();
        for (byte octet : written.getBytes(StandardCharsets.UTF_8)) {
            escaped.append(isUnreserved(octet) ? String.valueOf((char) octet) : escape(octet));
        }
        return escaped.toString();
    }

    Optional<BlockValue> read(String address) {
        return unescape(address)
                .flatMap(this::decodeUtf8)
                .flatMap(this::readAsData);
    }

    private boolean isUnreserved(byte octet) {
        char asCharacter = (char) octet;
        return octet >= 0
                && ((asCharacter >= 'a' && asCharacter <= 'z')
                || (asCharacter >= 'A' && asCharacter <= 'Z')
                || (asCharacter >= '0' && asCharacter <= '9')
                || UNRESERVED_PUNCTUATION.indexOf(asCharacter) >= 0);
    }

    private String escape(byte octet) {
        int unsigned = octet & OCTET_MASK;
        return ESCAPE + "" + HEX_DIGITS.charAt(unsigned >> NIBBLE_BITS) + HEX_DIGITS.charAt(unsigned & NIBBLE_MASK);
    }

    private Optional<ByteBuffer> unescape(String address) {
        ByteBuffer octets = ByteBuffer.allocate(address.getBytes(StandardCharsets.UTF_8).length);
        int at = 0;
        while (at < address.length()) {
            if (address.charAt(at) != ESCAPE) {
                int codePoint = address.codePointAt(at);
                octets.put(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
                at += Character.charCount(codePoint);
                continue;
            }
            Optional<Integer> escaped = readEscapeAt(address, at);
            if (escaped.isEmpty()) {
                return Optional.empty();
            }
            octets.put(escaped.get().byteValue());
            at += ESCAPE_LENGTH;
        }
        return Optional.of(octets.flip());
    }

    private Optional<Integer> readEscapeAt(String address, int at) {
        if (at + ESCAPE_LENGTH > address.length()) {
            return Optional.empty();
        }
        int high = HEX_DIGITS.indexOf(Character.toUpperCase(address.charAt(at + 1)));
        int low = HEX_DIGITS.indexOf(Character.toUpperCase(address.charAt(at + 2)));
        return high < 0 || low < 0
                ? Optional.empty()
                : Optional.of((high << NIBBLE_BITS) | low);
    }

    private Optional<String> decodeUtf8(ByteBuffer octets) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(octets)
                    .toString());
        } catch (CharacterCodingException notUtf8) {
            return Optional.empty();
        }
    }

    private Optional<BlockValue> readAsData(String text) {
        TranscodeResult read = Transcoder.transcode(text);
        return read.values().map(values -> BlockValue.of(values.listItems()));
    }
}
