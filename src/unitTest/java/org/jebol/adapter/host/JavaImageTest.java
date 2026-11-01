package org.jebol.adapter.host;

import org.jebol.domain.host.ImagePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.assertThat;

class JavaImageTest {

    private static final int GREY = 0;

    private static final int GREY_WITH_ALPHA = 4;

    private static byte[] chunk(String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(name).put(data)
                .putInt((int) crc.getValue()).array();
    }

    private static byte[] png(int wide, int depth, int colourType, int... row) {
        byte[] header = ByteBuffer.allocate(13).putInt(wide).putInt(1)
                .put((byte) depth).put((byte) colourType).put((byte) 0).put((byte) 0).put((byte) 0).array();
        byte[] scanline = new byte[row.length + 1];
        for (int at = 0; at < row.length; at++) {
            scanline[at + 1] = (byte) row[at];
        }
        Deflater deflater = new Deflater();
        deflater.setInput(scanline);
        deflater.finish();
        byte[] packed = new byte[256];
        int length = deflater.deflate(packed);
        byte[] compressed = new byte[length];
        System.arraycopy(packed, 0, compressed, 0, length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(chunk("IHDR", header));
        out.writeBytes(chunk("IDAT", compressed));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    private static int[] rgbaAt(ImagePort.Pixels read, int pixel) {
        byte[] rgba = read.rgba();
        return new int[] {rgba[pixel * 4] & 0xFF, rgba[pixel * 4 + 1] & 0xFF, rgba[pixel * 4 + 2] & 0xFF,
            rgba[pixel * 4 + 3] & 0xFF};
    }

    @Test
    @DisplayName("an 8-bit grey PNG reads as its own grey levels, not as levels moved through a gamma curve")
    void readsGreyLevelsAsStored() {
        ImagePort.Pixels read = new JavaImage().decode(png(2, 8, GREY, 0x40, 0xC0), "", 1);
        assertThat(rgbaAt(read, 0)).containsExactly(0x40, 0x40, 0x40, 0xFF);
        assertThat(rgbaAt(read, 1)).containsExactly(0xC0, 0xC0, 0xC0, 0xFF);
    }

    @Test
    @DisplayName("a grey PNG with alpha keeps both its grey and its alpha as stored")
    void readsGreyAndAlphaAsStored() {
        ImagePort.Pixels read = new JavaImage().decode(png(1, 8, GREY_WITH_ALPHA, 0x40, 0x80), "", 1);
        assertThat(rgbaAt(read, 0)).containsExactly(0x40, 0x40, 0x40, 0x80);
    }

    @Test
    @DisplayName("a 16-bit grey PNG keeps the high byte of each sample")
    void keepsTheHighByteOfSixteenBitGrey() {
        ImagePort.Pixels read = new JavaImage().decode(png(1, 16, GREY, 0x40, 0xFF), "", 1);
        assertThat(rgbaAt(read, 0)).containsExactly(0x40, 0x40, 0x40, 0xFF);
    }

    @Test
    @DisplayName("a 4-bit grey PNG spreads its sixteen levels across the whole byte")
    void spreadsFourBitGreyAcrossTheByte() {
        ImagePort.Pixels read = new JavaImage().decode(png(2, 4, GREY, 0x5F), "", 1);
        assertThat(rgbaAt(read, 0)).containsExactly(85, 85, 85, 0xFF);
        assertThat(rgbaAt(read, 1)).containsExactly(0xFF, 0xFF, 0xFF, 0xFF);
    }
}
