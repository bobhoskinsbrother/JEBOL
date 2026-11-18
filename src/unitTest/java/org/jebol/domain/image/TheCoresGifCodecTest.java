package org.jebol.domain.image;

import org.jebol.domain.host.ImagePort.Pixels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TheCoresGifCodecTest {

    private static String md5(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private static Pixels noise(int wide, int high, int colours) {
        byte[] rgba = new byte[wide * high * 4];
        long state = colours;
        for (int at = 0; at < wide * high; at++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            int colour = (int) ((state >>> 33) % colours) * 0x010305;
            rgba[at * 4] = (byte) (colour >> 16);
            rgba[at * 4 + 1] = (byte) (colour >> 8);
            rgba[at * 4 + 2] = (byte) colour;
            rgba[at * 4 + 3] = (byte) 0xFF;
        }
        return new Pixels(wide, high, rgba);
    }

    @Test
    @DisplayName("a picture of more than 256 colours is written to the same bytes every time, which ART must match")
    void quantisingIsTheSameEveryTime() throws NoSuchAlgorithmException {
        String first = md5(GifEncoder.encode(QuantisedGif.gradient()));

        assertThat(md5(GifEncoder.encode(QuantisedGif.gradient()))).isEqualTo(first);
        assertThat(first).isEqualTo(QuantisedGif.MD5);
    }

    @Test
    @DisplayName("a picture of 256 colours or fewer reads back exactly, whatever the code size and however many tables")
    void ownPaletteReadsBackExactly() {
        for (int colours : new int[] {1, 2, 3, 4, 5, 16, 17, 255, 256}) {
            Pixels picture = noise(150, 130, colours);

            Pixels back = GifDecoder.decodeFrame(GifEncoder.encode(picture), 1).orElseThrow();

            assertThat(back.rgba()).as(colours + " colours").isEqualTo(picture.rgba());
        }
    }

    @Test
    @DisplayName("a picture is written fully opaque, its colours kept and its alpha dropped")
    void alphaIsDropped() {
        Pixels picture = new Pixels(2, 1, new byte[] {10, 20, 30, 0, 40, 50, 60, (byte) 128});

        Pixels back = GifDecoder.decodeFrame(GifEncoder.encode(picture), 1).orElseThrow();

        assertThat(back.rgba()).containsExactly(10, 20, 30, -1, 40, 50, 60, -1);
    }

    @Test
    @DisplayName("a quantised picture holds at most 256 colours, each close to the picture's own")
    void quantisedPictureIsClose() {
        Pixels picture = QuantisedGif.gradient();

        Pixels back = GifDecoder.decodeFrame(GifEncoder.encode(picture), 1).orElseThrow();
        Set<Integer> colours = new HashSet<>();
        long distance = 0;
        for (int at = 0; at < back.rgba().length; at += 4) {
            colours.add((back.rgba()[at] & 0xFF) << 16 | (back.rgba()[at + 1] & 0xFF) << 8 | (back.rgba()[at + 2] & 0xFF));
            for (int channel = 0; channel < 3; channel++) {
                distance += Math.abs((back.rgba()[at + channel] & 0xFF) - (picture.rgba()[at + channel] & 0xFF));
            }
        }

        assertThat(colours).hasSizeLessThanOrEqualTo(256);
        assertThat((double) distance / (back.rgba().length / 4 * 3)).isLessThan(8.0);
    }

    @Test
    @DisplayName("the 1 by 1 white picture Rebol's suite saves reads back white")
    void oneWhitePixel() {
        Pixels white = new Pixels(1, 1, new byte[] {-1, -1, -1, -1});

        assertThat(GifDecoder.decodeFrame(GifEncoder.encode(white), 1).orElseThrow().rgba())
                .containsExactly(-1, -1, -1, -1);
    }
}
