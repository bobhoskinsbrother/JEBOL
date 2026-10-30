package org.jebol.domain.eval.compression.lz4;

import org.jebol.domain.eval.compression.SeriesRoom;

public final class Lz4 {

    private static final int LOWEST_LEVEL = 1;
    private static final int HIGHEST_LEVEL = 12;
    private static final int HIGHEST_MID_LEVEL = 2;
    private static final int HIGHEST_HASH_CHAIN_LEVEL = 9;
    private static final int ROOM_PER_COMPRESSED_OCTET = 3;
    private static final int[] HASH_CHAIN_ATTEMPTS = {0, 0, 0, 4, 8, 16, 32, 64, 128, 256};
    private static final int[] OPTIMAL_ATTEMPTS = {96, 512, 16384};
    private static final int[] OPTIMAL_TARGET_LENGTHS = {64, 128, 1 << 12};

    private Lz4() {
    }

    public static byte[] compress(byte[] octets, int level) {
        if (octets.length == 0) {
            return new byte[1];
        }
        int clamped = Math.max(LOWEST_LEVEL, Math.min(HIGHEST_LEVEL, level));
        if (clamped <= HIGHEST_MID_LEVEL) {
            return Lz4MidCompressor.compress(octets);
        }
        if (clamped <= HIGHEST_HASH_CHAIN_LEVEL) {
            return Lz4HashChainCompressor.compress(octets, HASH_CHAIN_ATTEMPTS[clamped]);
        }
        int optimal = clamped - HIGHEST_HASH_CHAIN_LEVEL - 1;
        return Lz4OptimalCompressor.compress(octets, OPTIMAL_ATTEMPTS[optimal],
                OPTIMAL_TARGET_LENGTHS[optimal], clamped == HIGHEST_LEVEL);
    }

    public static byte[] decompress(byte[] octets, int wanted) {
        long room = SeriesRoom.makeBinaryOf(wanted > 0 ? wanted : (long) ROOM_PER_COMPRESSED_OCTET * octets.length);
        byte[] decoded = Lz4BlockDecoder.decode(octets, room);
        if (decoded == null) {
            throw new IllegalArgumentException("LZ4 refuses the block");
        }
        return decoded;
    }
}
