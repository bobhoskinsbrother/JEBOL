package org.jebol.application;

import org.jebol.domain.eval.CompressionRefused;
import org.jebol.domain.eval.compression.zstd.ZstdCompressionStream;
import org.jebol.domain.eval.compression.zstd.ZstdDecompressionStream;
import org.jebol.domain.eval.compression.zstd.ZstdOneShot;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

final class ZstdExtension {

    private static final String HEADER = "REBOL [Title: {Rebol Zstd Extension} Name: zstd Type: module "
            + "Version: 0.1.0 Needs: 3.20.5 Author: Oldes License: MIT Url: https://github.com/Oldes/Rebol-Zstd]";

    private static final String SCRIPT = """
            init-words [] []
            protect/hide 'init-words
            attempt [
                unless find system/schemes/http/headers/Accept-Encoding "zstd" [
                    append system/schemes/http/headers/Accept-Encoding ",zstd"
                ]
            ]""";

    private static final String INVALID_HANDLE = "Invalid Zstd encoder or decoder handle!";
    private static final String ENCODER = "zstd-encoder";
    private static final String DECODER = "zstd-decoder";
    private static final long NO_LIMIT = 0xFFFFFFFFL;
    private static final long LARGEST_SERIES = Integer.MAX_VALUE;
    private static final int DEFAULT_COMMAND_LEVEL = 6;
    private static final int DEFAULT_ENCODER_LEVEL = 3;
    private static final int VERSION_NUMBER = 10507;

    private ZstdExtension() {
    }

    static BundledExtension create() {
        ZstdOneShot oneShot = new ZstdOneShot();
        String data = "data [binary! any-string!] ";
        String part = "/part {Limit the input data to a given length.} length [integer!] {Length of input data.} ";
        return new BundledExtension(new Extension("zstd", "0.1.0", List.of(
                new ExtensionCommand("init-words", "args [block!] type [block!]", call -> {
                    call.registerCompression("zstd", oneShot.method());
                    return LogicValue.of(true);
                }),
                new ExtensionCommand("version", "{Native Zstd version}", call -> TupleValue.of(
                        (VERSION_NUMBER >>> 24) & 0xFF, (VERSION_NUMBER >>> 12) & 0xFF, VERSION_NUMBER & 0xFF)),
                new ExtensionCommand("compress", "{Compress data using Zstandard} " + data
                        + "{Input data to compress.} " + part
                        + "/level quality [integer!] {Compression level from 1 to 22.}", ZstdExtension::compress),
                new ExtensionCommand("decompress", "{Decompress data using Zstandard} " + data
                        + "{Input data to decompress.} " + part
                        + "/size {Limit the output size.} bytes [integer!] {Maximum number of uncompressed bytes.}",
                        call -> decompress(call, oneShot)),
                new ExtensionCommand("make-encoder", "{Create a new Zstandard encoder handle.} "
                        + "/level quality [integer!] {Compression level from 0 to 11.}", ZstdExtension::makeEncoder),
                new ExtensionCommand("make-decoder", "{Create a new Zstandard decoder handle.}",
                        call -> call.makeHandle(DECODER, ZstdStream.decoding(new ZstdDecompressionStream()))),
                new ExtensionCommand("write", "{Feed data into a Zstandard streaming codec.} "
                        + "codec [handle!] {Zstandard encoder or decoder handle.} "
                        + "data [binary! any-string! none!] "
                        + "{Data to compress or decompress, or NONE to finish the stream.} "
                        + "/flush {Finish the current data block and return the encoded chunk.} "
                        + "/finish {Encode all remaining input and mark the stream as complete.}",
                        ZstdExtension::write),
                new ExtensionCommand("read", "{Retrieve pending encoded or decoded data from the stream.} "
                        + "codec [handle!] {Zstandard encoder or decoder handle.}",
                        call -> unwrapStream(call).read())))
                .withHandleType(new HandleType(ENCODER, ZstdStream.class))
                .withHandleType(new HandleType(DECODER, ZstdStream.class))
                .withScript(SCRIPT), HEADER);
    }

    private static Value compress(Command call) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        long level = call.refinements().contains("level")
                ? ((int) DeflateExtension.magnitudeAt(call, DeflateExtension.countArgumentsBefore(call, "level")))
                & NO_LIMIT
                : DEFAULT_COMMAND_LEVEL;
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        try {
            return BinaryValue.ofBytes(ZstdOneShot.compress(octets, level));
        } catch (CompressionRefused refused) {
            throw new IllegalStateException(ZstdStream.NO_COMPRESS);
        }
    }

    private static Value decompress(Command call, ZstdOneShot oneShot) {
        long length = DeflateExtension.readLengthWithinAnyPart(call);
        if (length < 0 || length > LARGEST_SERIES) {
            throw new IllegalStateException(ZstdStream.NO_DECOMPRESS);
        }
        long limit = call.refinements().contains("size")
                ? DeflateExtension.magnitudeAt(call, DeflateExtension.countArgumentsBefore(call, "size")) & NO_LIMIT
                : NO_LIMIT;
        byte[] octets = StoredOctets.readFromIndex(call.arguments().getFirst(), length);
        try {
            return BinaryValue.ofBytes(oneShot.decompress(octets, limit));
        } catch (CompressionRefused refused) {
            throw new IllegalStateException(ZstdStream.NO_DECOMPRESS);
        }
    }

    private static Value makeEncoder(Command call) {
        int level = call.refinements().contains("level")
                ? ZstdCompressionStream.clampLevel((int) DeflateExtension.magnitudeAt(call, 0))
                : DEFAULT_ENCODER_LEVEL;
        return call.makeHandle(ENCODER, ZstdStream.encoding(new ZstdCompressionStream(level)));
    }

    private static ZstdStream unwrapStream(Command call) {
        try {
            return call.unwrap(call.arguments().getFirst(), ZstdStream.class);
        } catch (Raised notOurs) {
            throw new IllegalStateException(INVALID_HANDLE);
        }
    }

    private static Value write(Command call) {
        ZstdStream stream = unwrapStream(call);
        Value data = call.arguments().get(1);
        Optional<byte[]> octets = data instanceof NoneValue
                ? Optional.empty()
                : Optional.of(StoredOctets.readFromIndex(data, StoredOctets.countUnitsFromIndex(data)));
        return stream.write(call.arguments().getFirst(), octets, call.refinements().contains("flush"),
                call.refinements().contains("finish"));
    }
}
