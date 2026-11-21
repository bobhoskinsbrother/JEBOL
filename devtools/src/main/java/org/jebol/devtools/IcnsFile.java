package org.jebol.devtools;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class IcnsFile {

    public record Entry(String type, int size) {
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("icp4", 16), new Entry("ic11", 32), new Entry("icp5", 32), new Entry("ic12", 64),
            new Entry("ic07", 128), new Entry("ic13", 256), new Entry("ic08", 256), new Entry("ic14", 512),
            new Entry("ic09", 512), new Entry("ic10", 1024));

    private static final int HEADER_BYTES = 8;

    private IcnsFile() {
    }

    public static void write(BufferedImage source, Path target) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Entry entry : ENTRIES) {
            byte[] png = IconSizes.encodePng(IconSizes.scaled(source, entry.size()));
            body.writeBytes(header(entry.type(), HEADER_BYTES + png.length));
            body.writeBytes(png);
        }
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        whole.writeBytes(header("icns", HEADER_BYTES + body.size()));
        whole.writeBytes(body.toByteArray());
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.write(target, whole.toByteArray());
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }

    private static byte[] header(String type, int length) {
        return ByteBuffer.allocate(HEADER_BYTES).put(type.getBytes(StandardCharsets.US_ASCII)).putInt(length).array();
    }
}
