package org.jebol.domain.eval;

/**
 * A compression an extension registers, as {@code RL_REGISTER_COMPRESS_METHOD} registers a pair of C functions.
 * COMPRESS and DECOMPRESS call it in place of the built-in method of the same name.
 *
 * <p>Both numbers arrive as R3 holds them, unsigned and 32 bits wide: {@code /level} as {@code VAL_UNT32}, so a
 * negative level is a large one, and {@link #NOT_ASKED} where the refinement was not given. A method that cannot
 * do what it was asked throws {@link CompressionRefused}, and the script sees {@code bad-press} with its code.
 */
public interface CompressionMethod {

    long NOT_ASKED = 0xFFFFFFFFL;

    byte[] compress(byte[] octets, long level);

    byte[] decompress(byte[] octets, long limit);
}
