package org.jebol.application;

import org.jebol.domain.eval.Encodings;
import org.jebol.domain.eval.compression.SeriesRoom;
import org.jebol.domain.eval.compression.deflate.DeflateCompressor;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.List;

final class DeflateExtension {

    private static final String HEADER = "REBOL [Title: {Rebol Deflate Extension} Name: Deflate Type: module "
            + "Version: 0.1.0 Needs: 3.20.5 Author: Oldes License: MIT Url: https://github.com/Oldes/Rebol-Deflate]";

    private static final String SCRIPT = """
            init-words [] []
            protect/hide 'init-words
            attempt [
                unless find system/schemes/http/headers/Accept-Encoding "deflate" [
                    append system/schemes/http/headers/Accept-Encoding ",deflate"
                ]
            ]""";

    private static final String NO_DECOMPRESS = "Failed to decompress using the Deflate decoder!";

    private static final long NO_LIMIT = 0xFFFFFFFFL;
    private static final long LARGEST_SERIES = Integer.MAX_VALUE;
    private static final int LIBDEFLATE_MAJOR = 1;
    private static final int LIBDEFLATE_MINOR = 25;
    private static final List<String> REGISTERED = List.of("deflate", "gzip", "zlib");

    private DeflateExtension() {
    }

    static BundledExtension create() {
        return new BundledExtension(new Extension("deflate", "0.1.0", List.of(
                new ExtensionCommand("init-words", "args [block!] type [block!]", call -> {
                    REGISTERED.forEach(call::restoreBuiltInCompression);
                    return LogicValue.of(true);
                }),
                new ExtensionCommand("version", "{Native Deflate version}",
                        call -> TupleValue.of(LIBDEFLATE_MAJOR, LIBDEFLATE_MINOR, 0)),
                new ExtensionCommand("compress", "{Compress data using Deflate} "
                        + "data [binary! any-string!] {Input data to compress.} "
                        + "/part {Limit the input data to a given length.} length [integer!] {Length of input data.} "
                        + "/level quality [integer!] {Compression level from 1 to 9.} "
                        + "/zlib {Assumes the zlib wrapper format} /gzip {Assumes the gzip wrapper format}",
                        DeflateExtension::compress),
                new ExtensionCommand("decompress", "{Decompress data using Deflate} "
                        + "data [binary! any-string!] {Input data to decompress.} "
                        + "/part {Limit the input data to a given length.} length [integer!] {Length of input data.} "
                        + "/size {Limit the output size.} bytes [integer!] {Maximum number of uncompressed bytes.} "
                        + "/zlib {Assumes the zlib wrapper format} /gzip {Assumes the gzip wrapper format}",
                        DeflateExtension::decompress)))
                .withScript(SCRIPT), HEADER);
    }

    static String readMethod(Command call) {
        if (call.refinements().contains("gzip")) {
            return "gzip";
        }
        return call.refinements().contains("zlib") ? "zlib" : "deflate";
    }

    static long magnitudeAt(Command call, int at) {
        return ((IntegerValue) call.arguments().get(at)).magnitude();
    }

    static long readLengthWithinAnyPart(Command call) {
        long length = StoredOctets.countUnitsFromIndex(call.arguments().getFirst());
        if (call.refinements().contains("part")) {
            length = Math.max(0, Math.min(length, magnitudeAt(call, 1)));
        }
        return length;
    }

    static int countArgumentsBefore(Command call, String refinement) {
        return call.refinements().contains("part") && !"part".equals(refinement) ? 2 : 1;
    }

    private static Value compress(Command call) {
        long length = readLengthWithinAnyPart(call);
        int level = call.refinements().contains("level")
                ? (int) magnitudeAt(call, countArgumentsBefore(call, "level"))
                : 0;
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        DeflateCompressor compressor = new DeflateCompressor(level);
        return BinaryValue.ofBytes(switch (readMethod(call)) {
            case "gzip" -> compressor.wrapInGzip(octets);
            case "zlib" -> compressor.wrapInZlib(octets);
            default -> compressor.deflate(octets);
        });
    }

    private static Value decompress(Command call) {
        long length = readLengthWithinAnyPart(call);
        if (length < 0 || length > LARGEST_SERIES) {
            throw new IllegalStateException(NO_DECOMPRESS);
        }
        long limit = call.refinements().contains("size")
                ? magnitudeAt(call, countArgumentsBefore(call, "size")) & NO_LIMIT
                : NO_LIMIT;
        long outputLength = limit != NO_LIMIT ? limit : length << 2;
        if (outputLength == 0) {
            return BinaryValue.ofBytes(new byte[0]);
        }
        SeriesRoom.makeBinaryOf(Math.min(outputLength, LARGEST_SERIES));
        byte[] whole;
        try {
            whole = Encodings.inflateAsBuiltIn(
                    StoredOctets.readFromIndex(call.arguments().getFirst(), length), readMethod(call));
        } catch (IllegalArgumentException unreadable) {
            throw new IllegalStateException(NO_DECOMPRESS);
        }
        if (limit != NO_LIMIT && whole.length != limit) {
            throw new IllegalStateException(NO_DECOMPRESS);
        }
        return BinaryValue.ofBytes(whole);
    }
}
