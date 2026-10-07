package org.jebol.domain.value;

final class CharacterBoundary {

    private static final int CONTINUATION_MASK = 0xC0;
    private static final int CONTINUATION_BITS = 0x80;

    int drawnIn(byte[] octets, RandomDraw draw) {
        int at = draw.below(octets.length);
        while (at > 0 && (octets[at] & CONTINUATION_MASK) == CONTINUATION_BITS) {
            at--;
        }
        return at;
    }
}
