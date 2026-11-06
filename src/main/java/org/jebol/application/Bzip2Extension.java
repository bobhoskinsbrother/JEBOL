package org.jebol.application;

import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.compression.bzip2.Bzip2OneShot;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

final class Bzip2Extension {

    private static final String HEADER = "REBOL [Title: {Rebol Bzip2 Extension} Name: Bzip2 Type: module "
            + "Version: 1.1.0 Needs: 3.20.5 Author: Oldes Date: 14-Apr-2026/7:02:37 License: MIT "
            + "Url: https://github.com/Oldes/Rebol-Bzip2]";

    private static final String SCRIPT = """
            init-words [] []
            protect/hide 'init-words""";

    private static final String LIBRARY_VERSION = "1.1.0";
    private static final String BAD_SIZE = "Invalid output size limit (/size).";
    private static final String BAD_MAX = "Invalid /max (must be >= 0).";
    private static final String INVALID_HANDLE = "Invalid bzip2 streaming handle.";
    private static final String ENCODER = "bzip2-encoder";
    private static final String DECODER = "bzip2-decoder";
    private static final int DEFAULT_LEVEL = 6;
    private static final int LARGEST_BLOCK_SIZE = 9;
    private static final long UNSIGNED_32 = 0xFFFFFFFFL;

    private Bzip2Extension() {
    }

    static BundledExtension create() {
        String data = "data [binary! any-string!] ";
        String part = "/part {Limit the input data to a given length.} length [integer!] {Length of input data.} ";
        return new BundledExtension(new Extension("bzip2", "1.1.0", List.of(
                new ExtensionCommand("init-words", "args [block!] type [block!]", call -> {
                    call.registerCompression("bzip2", Bzip2OneShot.method());
                    return LogicValue.of(true);
                }),
                new ExtensionCommand("version", "{Libbzip2 version string (BZ2_bzlibVersion)}",
                        call -> StringValue.of(LIBRARY_VERSION)),
                new ExtensionCommand("compress", "{Compress data using bzip2} " + data + "{Input data to compress.} "
                        + part + "/level quality [integer!] {Block size 100k: 1 (fast) to 9 (best).}",
                        Bzip2Extension::compress),
                new ExtensionCommand("decompress", "{Decompress bzip2 data} " + data + "{Input data to decompress.} "
                        + part + "/size {Limit the output size.} bytes [integer!] {Maximum number of uncompressed bytes.} "
                        + "/max {Cap allocated output (ZIP bomb guard).} "
                        + "ceiling [integer!] {Maximum bytes to allocate while decompressing.}",
                        Bzip2Extension::decompress),
                new ExtensionCommand("make-encoder", "{Create a new bzip2 encoder handle.} "
                        + "/level quality [integer!] {Block size 100k: 1 (fast) to 9 (best).}",
                        Bzip2Extension::makeEncoder),
                new ExtensionCommand("make-decoder", "{Create a new bzip2 decoder handle.}",
                        call -> call.makeHandle(DECODER, Bzip2Stream.decoding())),
                new ExtensionCommand("write", "{Feed data into a bzip2 streaming codec.} "
                        + "codec [handle!] {Encoder or decoder handle.} "
                        + "data [binary! any-string! none!] "
                        + "{Data to compress or decompress, or NONE to finish encoder output.} "
                        + "/flush {Flush encoder output (BZ_FLUSH).} /finish {Finish encoder stream (BZ_FINISH).}",
                        Bzip2Extension::write),
                new ExtensionCommand("read", "{Retrieve pending data from the codec buffer.} codec [handle!]",
                        call -> unwrapStream(call).read())))
                .withHandleType(new HandleType(ENCODER, Bzip2Stream.class))
                .withHandleType(new HandleType(DECODER, Bzip2Stream.class))
                .withScript(SCRIPT), HEADER);
    }

    private static Value compress(Command call) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        int level = call.refinements().contains("level")
                ? (int) DeflateExtension.magnitudeAt(call, DeflateExtension.countArgumentsBefore(call, "level"))
                : DEFAULT_LEVEL;
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        try {
            return BinaryValue.ofBytes(Bzip2OneShot.compress(octets, level & UNSIGNED_32));
        } catch (CompressionRefused refused) {
            throw new IllegalStateException(Bzip2Stream.NO_COMPRESS);
        }
    }

    private static Value decompress(Command call) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        int at = DeflateExtension.countArgumentsBefore(call, "size");
        long limit = Bzip2OneShot.NO_LIMIT;
        if (call.refinements().contains("size")) {
            long asked = DeflateExtension.magnitudeAt(call, at);
            if (asked < 0) {
                throw new IllegalStateException(BAD_SIZE);
            }
            limit = Math.min(asked, Bzip2OneShot.LARGEST_OUTPUT);
            at++;
        }
        long ceiling = Bzip2OneShot.NO_CEILING;
        if (call.refinements().contains("max")) {
            long asked = DeflateExtension.magnitudeAt(call, at);
            if (asked < 0) {
                throw new IllegalStateException(BAD_MAX);
            }
            ceiling = Math.min(asked, Bzip2OneShot.LARGEST_OUTPUT);
        }
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        try {
            return BinaryValue.ofBytes(Bzip2OneShot.decompress(octets, limit, ceiling));
        } catch (CompressionRefused refused) {
            throw new IllegalStateException(Bzip2Stream.NO_DECOMPRESS);
        }
    }

    private static Value makeEncoder(Command call) {
        int blockSize = call.refinements().contains("level")
                ? Math.max(1, Math.min(LARGEST_BLOCK_SIZE, (int) DeflateExtension.magnitudeAt(call, 0)))
                : DEFAULT_LEVEL;
        return call.makeHandle(ENCODER, Bzip2Stream.encoding(blockSize));
    }

    private static Bzip2Stream unwrapStream(Command call) {
        try {
            return call.unwrap(call.arguments().getFirst(), Bzip2Stream.class);
        } catch (Raised notOurs) {
            throw new IllegalStateException(INVALID_HANDLE);
        }
    }

    private static Value write(Command call) {
        Bzip2Stream stream = unwrapStream(call);
        Value data = call.arguments().get(1);
        Optional<byte[]> octets = data instanceof NoneValue
                ? Optional.empty()
                : Optional.of(StoredOctets.readFromIndex(data, StoredOctets.countUnitsFromIndex(data)));
        return stream.write(call.arguments().getFirst(), octets, call.refinements().contains("flush"),
                call.refinements().contains("finish"));
    }
}
