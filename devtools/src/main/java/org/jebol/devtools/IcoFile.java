package org.jebol.devtools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class IcoFile {

    public static final List<Integer> SIZES = List.of(16, 24, 32, 48, 64, 128, 256);

    private static final int HEADER_BYTES = 6;
    private static final int ENTRY_BYTES = 16;
    private static final short ICON_TYPE = 1;
    private static final short PLANES = 1;
    private static final short BITS_A_PIXEL = 32;
    private static final int SIZE_THAT_IS_WRITTEN_AS_NOUGHT = 256;

    private IcoFile() {
    }

    public static void write(BufferedImage source, Path target) {
        List<byte[]> pngs = new ArrayList<>();
        SIZES.forEach(size -> pngs.add(IconSizes.encodePng(IconSizes.scaled(source, size))));
        int total = HEADER_BYTES + ENTRY_BYTES * SIZES.size() + pngs.stream().mapToInt(png -> png.length).sum();
        ByteBuffer writing = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        writing.putShort((short) 0).putShort(ICON_TYPE).putShort((short) SIZES.size());
        int offset = HEADER_BYTES + ENTRY_BYTES * SIZES.size();
        for (int index = 0; index < SIZES.size(); index++) {
            byte side = (byte) (SIZES.get(index) == SIZE_THAT_IS_WRITTEN_AS_NOUGHT ? 0 : SIZES.get(index));
            writing.put(side).put(side).put((byte) 0).put((byte) 0)
                    .putShort(PLANES).putShort(BITS_A_PIXEL)
                    .putInt(pngs.get(index).length).putInt(offset);
            offset += pngs.get(index).length;
        }
        pngs.forEach(writing::put);
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.write(target, writing.array());
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }
}
