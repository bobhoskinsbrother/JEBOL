package org.jebol.application;

import org.jebol.domain.eval.CompressionMethod;
import org.jebol.domain.eval.compression.brotli.Brotli;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;
import java.util.List;

final class BrotliExtension {

    private static final String HEADER =
            "REBOL [Title: {Rebol Brotli Extension} Type: module Version: 0.1.0 needs: 3.20.5]";

    private static final String SCRIPT = """
            init-words [mode size-hint finished] []
            protect/hide 'init-words
            attempt [
            \tunless parse system/schemes/http/headers/Accept-Encoding [
            \t\tthru #"," any #" " "br" any #" " [end | #","] to end
            \t][
            \t\tappend system/schemes/http/headers/Accept-Encoding ",br"
            \t]
            ]""";

    private static final String NO_COMPRESS = "Failed to compress using the Brotli encoder!";
    private static final String NO_DECOMPRESS = "Failed to decompress using the Brotli decoder!";
    private static final int DEFAULT_LEVEL = 6;
    private static final long NO_LIMIT = 0xFFFFFFFFL;
    private static final long UNSIGNED_32 = 0xFFFFFFFFL;
    private static final long LARGEST_INPUT = Integer.MAX_VALUE;
    private static final int[] LIBRARY_VERSION = {1, 2, 0};

    private BrotliExtension() {
    }

    static Extension create() {
        String data = "data [binary! any-string!] ";
        String part = "/part {Limit the input data to a given length.} length [integer!] {Length of input data.} ";
        return new Extension("brotli", "0.1.0", List.of(
                new ExtensionCommand("init-words", "args [block!] type [block!]", call -> {
                    call.registerCompression("br", method());
                    return LogicValue.of(true);
                }),
                new ExtensionCommand("version", "{Native Brotli version}",
                        call -> TupleValue.of(LIBRARY_VERSION)),
                new ExtensionCommand("compress", "{Compress data using Brotli.} " + data
                        + "{Input data to compress.} " + part
                        + "/level quality [integer!] {Compression level from 0 to 11.}",
                        BrotliExtension::compress),
                new ExtensionCommand("decompress", "{Decompress data using Brotli.} " + data
                        + "{Input data to decompress.} " + part
                        + "/size {Limit the output size.} bytes [integer!] {Maximum number of uncompressed bytes.}",
                        BrotliExtension::decompress)))
                .withScript(SCRIPT)
                .withHeader(HEADER);
    }

    static CompressionMethod method() {
        return new CompressionMethod() {
            @Override
            public byte[] compress(byte[] octets, long level) {
                return Brotli.compress(octets, (int) level);
            }

            @Override
            public byte[] decompress(byte[] octets, long limit) {
                return decompressUpTo(octets, limit & UNSIGNED_32);
            }
        };
    }

    private static Value compress(Command call) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        int level = call.refinements().contains("level")
                ? (int) DeflateExtension.magnitudeAt(call, DeflateExtension.countArgumentsBefore(call, "level"))
                : DEFAULT_LEVEL;
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        try {
            return BinaryValue.ofBytes(Brotli.compress(octets, level));
        } catch (RuntimeException refused) {
            throw new IllegalStateException(NO_COMPRESS);
        }
    }

    private static Value decompress(Command call) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        long limit = call.refinements().contains("size")
                ? DeflateExtension.magnitudeAt(call, DeflateExtension.countArgumentsBefore(call, "size"))
                : NO_LIMIT;
        if (length > LARGEST_INPUT) {
            throw new IllegalStateException(NO_DECOMPRESS);
        }
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        return BinaryValue.ofBytes(decompressUpTo(octets, limit & UNSIGNED_32));
    }

    private static byte[] decompressUpTo(byte[] octets, long limit) {
        long room = limit != NO_LIMIT ? limit : 2L * octets.length;
        if (room == 0) {
            return new byte[0];
        }
        byte[] decoded;
        try {
            decoded = Brotli.decompress(octets, limit != NO_LIMIT && limit <= Integer.MAX_VALUE ? (int) limit : 0);
        } catch (RuntimeException refused) {
            throw new IllegalStateException(NO_DECOMPRESS);
        }
        return limit != NO_LIMIT && decoded.length > limit ? Arrays.copyOf(decoded, (int) limit) : decoded;
    }
}
