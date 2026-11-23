package org.jebol.android.unit;

import org.jebol.adapter.android.AndroidImage;
import org.jebol.domain.host.ImagePort.Pixels;
import org.jebol.domain.image.QuantisedGif;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidImageTest {

    private static final String REBOLS_OWN = "/rebol-suite/units/files/";

    private static final String ANDROID_MADE_HEIF = "/heif/android-made-64x64.heic";

    private static final int ANDROID_MADE_HEIF_LENGTH = 522;

    private static final String ANDROID_MADE_HEIF_MD5 = "2CAC4154B89B6E26DBC51EF8D4BD9E61";

    private final AndroidImage images = new AndroidImage();

    private static Pixels pixels(int wide, int high, int... rgba) {
        byte[] bytes = new byte[rgba.length];
        for (int at = 0; at < rgba.length; at++) {
            bytes[at] = (byte) rgba[at];
        }
        return new Pixels(wide, high, bytes);
    }

    private static Pixels gradient(int wide, int high, boolean withAlpha) {
        byte[] rgba = new byte[wide * high * 4];
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                int at = (down * wide + across) * 4;
                rgba[at] = (byte) (across * 255 / Math.max(1, wide - 1));
                rgba[at + 1] = (byte) (down * 255 / Math.max(1, high - 1));
                rgba[at + 2] = (byte) ((across * 7 + down * 13) & 0xFF);
                rgba[at + 3] = (byte) (withAlpha ? 1 + (across + down) % 255 : 0xFF);
            }
        }
        return new Pixels(wide, high, rgba);
    }

    private static byte[] rebolsOwn(String name) throws IOException {
        try (InputStream carried = AndroidImageTest.class.getResourceAsStream(REBOLS_OWN + name)) {
            assertThat(carried).as(name + " in the test APK").isNotNull();
            return carried.readAllBytes();
        }
    }

    private static String md5(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    @Test
    @DisplayName("it knows PNG, JPEG, GIF, BMP, WebP and HEIF, and not TIFF or DDS")
    void theFormatsItKnows() {
        for (String known : new String[] {"png", "PNG", "jpeg", "jpg", "gif", "bmp", "webp", "heif"}) {
            assertThat(images.hasImageType(known)).as(known).isTrue();
        }
        assertThat(images.hasImageType("tiff")).isFalse();
        assertThat(images.hasImageType("dds")).isFalse();
    }

    @Test
    @DisplayName("a PNG with every alpha comes back exactly, colour beneath the alpha included")
    void pngRoundTripsExactly() {
        Pixels picture = gradient(37, 23, true);

        Pixels back = images.decode(images.encode(picture, "png"), "png", 1);

        assertThat(back.wide()).isEqualTo(37);
        assertThat(back.high()).isEqualTo(23);
        assertThat(back.rgba()).isEqualTo(picture.rgba());
    }

    @Test
    @DisplayName("a lossless WebP comes back exactly")
    void webpRoundTripsExactly() {
        Pixels picture = gradient(29, 31, false);

        byte[] written = images.encode(picture, "webp");

        assertThat(new String(written, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(written, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        assertThat(images.decode(written, "webp", 1).rgba()).isEqualTo(picture.rgba());
    }

    @Test
    @DisplayName("a JPEG comes back opaque and close, its alpha dropped rather than its colour")
    void jpegComesBackClose() {
        int[] transparentRed = new int[16 * 16 * 4];
        for (int at = 0; at < transparentRed.length; at += 4) {
            transparentRed[at] = 200;
            transparentRed[at + 1] = 40;
            transparentRed[at + 2] = 40;
        }
        Pixels picture = pixels(16, 16, transparentRed);

        byte[] written = images.encode(picture, "jpeg");
        Pixels back = images.decode(written, "jpeg", 1);

        assertThat(written[0] & 0xFF).isEqualTo(0xFF);
        assertThat(written[1] & 0xFF).isEqualTo(0xD8);
        int middle = (8 * 16 + 8) * 4;
        assertThat(back.rgba()[middle + 3] & 0xFF).isEqualTo(0xFF);
        assertThat(back.rgba()[middle] & 0xFF).isBetween(185, 215);
        assertThat(back.rgba()[middle + 1] & 0xFF).isBetween(25, 55);
    }

    @Test
    @DisplayName("a BMP is written as the desktop writes it, and Android reads it back")
    void bmpRoundTrips() {
        Pixels picture = gradient(5, 3, false);

        byte[] written = images.encode(picture, "bmp");

        assertThat(new String(written, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("BM");
        assertThat(images.decode(written, "bmp", 1).rgba()).isEqualTo(picture.rgba());
    }

    @Test
    @DisplayName("Rebol's own PNG, JPEG, BMP and GIF flowers all decode at 256 by 256")
    void rebolsFlowersDecode() throws IOException {
        for (String name : new String[] {"flower.png", "flower.jpg", "flower.bmp", "flower.gif"}) {
            String type = name.substring(name.indexOf('.') + 1);
            Pixels decoded = images.decode(rebolsOwn(name), type, 1);
            assertThat(decoded).as(name).isNotNull();
            assertThat(decoded.wide()).as(name).isEqualTo(256);
            assertThat(decoded.high()).as(name).isEqualTo(256);
        }
    }

    @Test
    @DisplayName("a PNG's stored colours come back as stored, its gAMA chunk applying no correction, as on the desktop")
    void aPngsGammaIsNotApplied() throws IOException {
        Pixels fromPng = images.decode(rebolsOwn("flower.png"), "png", 1);
        Pixels fromBmp = images.decode(rebolsOwn("flower.bmp"), "bmp", 1);

        assertThat(fromPng.rgba()).isEqualTo(fromBmp.rgba());
    }

    @Test
    @DisplayName("every frame of Rebol's animated GIF is the one a real R3 answers")
    void everyGifFrame() throws Exception {
        byte[] gif = rebolsOwn("animation.gif");

        assertThat(md5(images.decode(gif, "gif", 1).rgba())).isEqualTo("4D99990699791F57F238C7195ABB0DE7");
        assertThat(md5(images.decode(gif, "gif", 2).rgba())).isEqualTo("878BACEFB949C6435702F87D5B62F9FA");
        assertThat(md5(images.decode(gif, "gif", 3).rgba())).isEqualTo("1414A649C4CF2E2D9DE3A8502A4425AA");
        assertThat(images.decode(gif, "gif", 4)).isNull();
    }

    @Test
    @DisplayName("a GIF is written by the core's codec and read back with the same pixels")
    void gifRoundTrips() {
        Pixels picture = pixels(2, 2, 200, 0, 0, 255, 0, 0, 200, 255, 0, 200, 0, 255, 255, 255, 255, 255);

        assertThat(images.decode(images.encode(picture, "gif"), "gif", 1).rgba()).isEqualTo(picture.rgba());
    }

    @Test
    @DisplayName("a picture of more than 256 colours writes the same GIF bytes on ART as on the JVM")
    void quantisedGifIsTheSameEverywhere() throws Exception {
        assertThat(md5(images.encode(QuantisedGif.gradient(), "gif"))).isEqualTo(QuantisedGif.MD5);
    }

    @Test
    @DisplayName("a HEIF Android made decodes at its size and close to its colour")
    void heifDecodes() throws Exception {
        byte[] heif;
        try (InputStream carried = AndroidImageTest.class.getResourceAsStream(ANDROID_MADE_HEIF)) {
            assertThat(carried).as(ANDROID_MADE_HEIF + " in the test APK").isNotNull();
            heif = carried.readAllBytes();
        }
        assertThat(heif).hasSize(ANDROID_MADE_HEIF_LENGTH);
        assertThat(md5(heif)).isEqualTo(ANDROID_MADE_HEIF_MD5);
        assertThat(new String(heif, 4, 4, StandardCharsets.US_ASCII)).isEqualTo("ftyp");
        assertThat(new String(heif, 8, 4, StandardCharsets.US_ASCII)).isIn("heic", "mif1", "heix");

        Pixels decoded = images.decode(heif, "heif", 1);

        assertThat(decoded.wide()).isEqualTo(64);
        assertThat(decoded.high()).isEqualTo(64);
        int middle = (32 * 64 + 32) * 4;
        assertThat(decoded.rgba()[middle] & 0xFF).isBetween(170, 230);
        assertThat(decoded.rgba()[middle + 1] & 0xFF).isBetween(30, 90);
        assertThat(decoded.rgba()[middle + 3] & 0xFF).isEqualTo(0xFF);
    }

    @Test
    @DisplayName("a frame past the first of a format Android decodes whole answers nothing")
    void onlyTheFirstFrameOfOthers() {
        assertThat(images.decode(images.encode(gradient(3, 3, false), "png"), "png", 2)).isNull();
    }

    @Test
    @DisplayName("bytes that are no picture decode to nothing")
    void notAPicture() {
        assertThat(images.decode(new byte[] {1, 2, 3, 4}, "png", 1)).isNull();
        assertThat(images.decode(new byte[0], "", 1)).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"tiff", "heif", "dds"})
    @DisplayName("a format Android cannot write is written as nothing")
    void unwritableFormats(String format) {
        assertThat(images.encode(gradient(2, 2, false), format)).isNull();
    }
}
