package org.jebol.application;

import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.compression.zlibng.ZlibNgDeflater;
import org.jebol.domain.eval.compression.zlibng.ZlibNgInflater;
import org.jebol.domain.eval.compression.zlibng.ZlibNgOneShot;
import org.jebol.domain.eval.compression.zlibng.ZlibNgOneShot.Wrapping;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

final class ZlibNgExtension {

    private static final String HEADER = "REBOL [Title: {Rebol Zlib-NG Extension} Name: zlib-ng Type: module "
            + "Version: 0.1.0 Needs: 3.20.5 Author: Oldes License: MIT Url: https://github.com/Oldes/Rebol-Zlib-ng]";

    private static final String SCRIPT = """
            init-words [] []
            protect/hide 'init-words
            Z_FILTERED:     1
            Z_HUFFMAN_ONLY: 2
            Z_RLE:          3
            Z_FIXED:        4""";

    private static final String INVALID_HANDLE = "Invalid Zlib encoder or decoder handle!";
    private static final String NO_STREAM = "Failed to create Zlib stream!";

    private static final String ENCODER = "zlib-ng-encoder";
    private static final String DECODER = "zlib-ng-decoder";

    private static final long NO_LIMIT = 0xFFFFFFFFL;
    private static final long LARGEST_SERIES = Integer.MAX_VALUE;
    private static final int RAW_WINDOW = -15;
    private static final int ZLIB_WINDOW = 15;
    private static final int GZIP_WINDOW = 31;
    private static final int THE_ONLY_STREAMING_LEVEL = 1;

    private static final int UNIX = 3;
    private static final int WINDOWS = 10;
    private static final int APPLE = 19;

    private record Registration(String name, Wrapping wrapping) {
    }

    private static final List<Registration> REGISTERED = List.of(
            new Registration("deflate", Wrapping.DEFLATE),
            new Registration("zlib", Wrapping.ZLIB),
            new Registration("gzip", Wrapping.GZIP),
            new Registration("deflate-ng", Wrapping.DEFLATE),
            new Registration("zlib-ng", Wrapping.ZLIB),
            new Registration("gzip-ng", Wrapping.GZIP));

    private ZlibNgExtension() {
    }

    static BundledExtension create() {
        String data = "data [binary! any-string!] ";
        String part = "/part {Limit the input data to a given length.} length [integer!] {Length of input data.} ";
        String wrappers = "/zlib {Assumes the zlib wrapper format} /gzip {Assumes the gzip wrapper format}";
        String level = "/level {Compression level (0..9, default 6)} quality [integer!] {0=fastest, 9=best ratio}";
        return new BundledExtension(new Extension("zlib-ng", "2.3.2", List.of(
                new ExtensionCommand("init-words", "args [block!] type [block!]", ZlibNgExtension::register),
                new ExtensionCommand("version", "{Native Zlib-ng version}", call -> TupleValue.of(2, 3, 2)),
                new ExtensionCommand("compress", "{Compress data using Zlib.} " + data + "{Input data to compress.} "
                        + part + "/level quality [integer!] {Compression level from 0 to 9.} " + wrappers,
                        ZlibNgExtension::compress),
                new ExtensionCommand("decompress", "{Decompress data using Zlib.} " + data
                        + "{Input data to decompress.} " + part
                        + "/size {Limit the output size.} bytes [integer!] {Maximum number of uncompressed bytes.} "
                        + wrappers, ZlibNgExtension::decompress),
                new ExtensionCommand("make-encoder", "{Creates a deflate encoder handle for streaming compression.} "
                        + level + " /window {Window size configuration} "
                        + "window-bits [integer!] {8..15 (zlib), -8..-15 (raw deflate), 24..31 (gzip)} "
                        + "/strategy {Compression strategy} "
                        + "method [integer!] {1=Z_FILTERED, 2=Z_HUFFMAN_ONLY, 3=Z_RLE, 4=Z_FIXED}",
                        call -> makeEncoder(call, readWindowOfEncoder(call), readStrategy(call))),
                new ExtensionCommand("make-zlib-encoder", "{Creates a zlib-wrapped deflate encoder (window-bits=15).} "
                        + "/level {Compression level (0-9, default 6)} quality [integer!] {0=fastest, 9=best ratio}",
                        call -> makeEncoder(call, ZLIB_WINDOW, ZlibNgDeflater.Z_DEFAULT_STRATEGY)),
                new ExtensionCommand("make-gzip-encoder", "{Creates a gzip-wrapped deflate encoder (window-bits=31).} "
                        + level, call -> makeEncoder(call, GZIP_WINDOW, ZlibNgDeflater.Z_DEFAULT_STRATEGY)),
                new ExtensionCommand("make-decoder", "{Creates a deflate decoder handle for streaming decompression.} "
                        + "/window {Expected window size (auto-detect by default)} "
                        + "window-bits [integer!] {8..15 (zlib), -8..-15 (raw deflate), 24..31 (gzip)}",
                        call -> makeDecoder(call, RAW_WINDOW)),
                new ExtensionCommand("make-zlib-decoder", "{Creates a zlib-wrapped deflate decoder (window-bits=15).}",
                        call -> makeDecoder(call, ZLIB_WINDOW)),
                new ExtensionCommand("make-gzip-decoder", "{Creates a gzip-wrapped deflate decoder (window-bits=31).}",
                        call -> makeDecoder(call, GZIP_WINDOW)),
                new ExtensionCommand("write", "{Feed data into a Zlib streaming codec.} "
                        + "codec [handle!] {Zlib encoder or decoder handle.} "
                        + "data [binary! any-string! none!] "
                        + "{Data to compress or decompress, or NONE to finish the stream.} "
                        + "/flush {Finish the current data block and return the encoded chunk.} "
                        + "/finish {Encode all remaining input and mark the stream as complete.}",
                        ZlibNgExtension::write),
                new ExtensionCommand("read", "{Retrieve pending encoded or decoded data from the stream.} "
                        + "codec [handle!] {Zlib encoder or decoder handle.}",
                        call -> unwrapStream(call).read())))
                .withHandleType(new HandleType(ENCODER, ZlibNgStream.class))
                .withHandleType(new HandleType(DECODER, ZlibNgStream.class))
                .withScript(SCRIPT), HEADER);
    }

    private static int readOperatingSystem(Command call) {
        if (call.readSystemField("platform") instanceof AnyWordValue platform) {
            return switch (platform.canonical()) {
                case "windows" -> WINDOWS;
                case "macos" -> APPLE;
                default -> UNIX;
            };
        }
        return UNIX;
    }

    private static Value register(Command call) {
        int operatingSystem = readOperatingSystem(call);
        REGISTERED.forEach(each ->
                call.registerCompression(each.name(), ZlibNgOneShot.method(each.wrapping(), operatingSystem)));
        return LogicValue.of(true);
    }

    private static Wrapping readWrapping(Command call) {
        return switch (DeflateExtension.readMethod(call)) {
            case "gzip" -> Wrapping.GZIP;
            case "zlib" -> Wrapping.ZLIB;
            default -> Wrapping.DEFLATE;
        };
    }

    private static Value compress(Command call) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        long level = call.refinements().contains("level")
                ? ((int) DeflateExtension.magnitudeAt(call, DeflateExtension.countArgumentsBefore(call, "level"))) & NO_LIMIT
                : 0;
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        try {
            return BinaryValue.ofBytes(ZlibNgOneShot.compress(readWrapping(call), octets, level,
                    readOperatingSystem(call)));
        } catch (CompressionRefused refused) {
            throw new IllegalStateException(ZlibNgStream.NO_COMPRESS);
        }
    }

    private static Value decompress(Command call) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        if (length < 0 || length > LARGEST_SERIES) {
            throw new IllegalStateException(ZlibNgStream.NO_DECOMPRESS);
        }
        long limit = call.refinements().contains("size")
                ? DeflateExtension.magnitudeAt(call, DeflateExtension.countArgumentsBefore(call, "size")) & NO_LIMIT
                : NO_LIMIT;
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        try {
            return BinaryValue.ofBytes(ZlibNgOneShot.decompress(readWrapping(call), octets, limit));
        } catch (CompressionRefused refused) {
            throw new IllegalStateException(ZlibNgStream.NO_DECOMPRESS);
        }
    }

    private static int argumentOf(Command call, String refinement, List<String> inOrder) {
        int at = 0;
        for (String each : inOrder) {
            if (each.equals(refinement)) {
                return at;
            }
            if (call.refinements().contains(each)) {
                at++;
            }
        }
        throw new IllegalArgumentException(refinement);
    }

    private static int readWindowOfEncoder(Command call) {
        if (!call.refinements().contains("window")) {
            return RAW_WINDOW;
        }
        return (int) ((IntegerValue) call.arguments()
                .get(argumentOf(call, "window", List.of("level", "window", "strategy")))).magnitude();
    }

    private static int readStrategy(Command call) {
        if (!call.refinements().contains("strategy")) {
            return ZlibNgDeflater.Z_DEFAULT_STRATEGY;
        }
        return (int) ((IntegerValue) call.arguments()
                .get(argumentOf(call, "strategy", List.of("level", "window", "strategy")))).magnitude();
    }

    private static Value makeEncoder(Command call, int windowBits, int strategy) {
        ZlibNgDeflater encoder = ZlibNgDeflater.open(THE_ONLY_STREAMING_LEVEL, windowBits,
                        ZlibNgDeflater.DEFAULT_MEM_LEVEL, strategy, readOperatingSystem(call))
                .orElseThrow(() -> new IllegalStateException(NO_STREAM));
        return call.makeHandle(ENCODER, ZlibNgStream.encoding(encoder));
    }

    private static Value makeDecoder(Command call, int windowBits) {
        ZlibNgInflater decoder = ZlibNgInflater.open(windowBits)
                .orElseThrow(() -> new IllegalStateException(NO_STREAM));
        return call.makeHandle(DECODER, ZlibNgStream.decoding(decoder));
    }

    private static ZlibNgStream unwrapStream(Command call) {
        try {
            return call.unwrap(call.arguments().getFirst(), ZlibNgStream.class);
        } catch (Raised notOurs) {
            throw new IllegalStateException(INVALID_HANDLE);
        }
    }

    private static Value write(Command call) {
        ZlibNgStream stream = unwrapStream(call);
        Value data = call.arguments().get(1);
        Optional<byte[]> octets = data instanceof NoneValue
                ? Optional.empty()
                : Optional.of(StoredOctets.readFromIndex(data, StoredOctets.countUnitsFromIndex(data)));
        return stream.write(call.arguments().getFirst(), octets,
                call.refinements().contains("flush"), call.refinements().contains("finish"));
    }
}
