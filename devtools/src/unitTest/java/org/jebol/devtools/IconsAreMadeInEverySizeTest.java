package org.jebol.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class IconsAreMadeInEverySizeTest {

    private static final int ICON_BLUE = new Color(0, 120, 215).getRGB();
    private static final int ICON_WHITE = Color.WHITE.getRGB();

    @TempDir
    Path directory;

    private final BufferedImage icon = ATestApp.drawIcon(1024, 1024);

    private record Entry(String type, int width, int height, BufferedImage image) {
    }

    private static BufferedImage decodePng(byte[] bytes) throws IOException {
        assertThat(Arrays.copyOf(bytes, 8)).containsExactly(0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n');
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).isNotNull();
        return image;
    }

    @ParameterizedTest(name = "{0} pixels")
    @ValueSource(ints = {16, 32, 48, 192, 512, 1024})
    @DisplayName("the icon is scaled to each size, keeping what it shows")
    void theIconIsScaledToEachSize(int size) {
        BufferedImage scaled = IconSizes.scaled(icon, size);

        assertThat(scaled.getWidth()).isEqualTo(size);
        assertThat(scaled.getHeight()).isEqualTo(size);
        assertThat(scaled.getRGB(size / 2, size / 2)).isEqualTo(ICON_WHITE);
        assertThat(scaled.getRGB(0, 0)).isEqualTo(ICON_BLUE);
    }

    @Test
    @DisplayName("an .ico holds a PNG at each of Windows' sizes, from 16 to 256, each entry pointing at its own bytes")
    void anIcoHoldsEachSize() throws IOException {
        Path ico = directory.resolve("app.ico");
        IcoFile.write(icon, ico);

        byte[] bytes = Files.readAllBytes(ico);
        ByteBuffer reading = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(reading.getShort()).isZero();
        assertThat(reading.getShort()).isEqualTo((short) 1);
        int count = reading.getShort();
        List<Entry> entries = new ArrayList<>();
        int expectedOffset = 6 + 16 * count;
        for (int index = 0; index < count; index++) {
            int width = Byte.toUnsignedInt(reading.get());
            int height = Byte.toUnsignedInt(reading.get());
            assertThat(reading.get()).as("colours in the palette").isZero();
            assertThat(reading.get()).as("reserved").isZero();
            assertThat(reading.getShort()).as("planes").isEqualTo((short) 1);
            assertThat(reading.getShort()).as("bits a pixel").isEqualTo((short) 32);
            int size = reading.getInt();
            int offset = reading.getInt();
            assertThat(offset).isEqualTo(expectedOffset);
            expectedOffset += size;
            BufferedImage image = decodePng(Arrays.copyOfRange(bytes, offset, offset + size));
            entries.add(new Entry("", width == 0 ? 256 : width, height == 0 ? 256 : height, image));
        }

        assertThat(expectedOffset).isEqualTo(bytes.length);
        assertThat(entries).extracting(Entry::width).containsExactly(16, 24, 32, 48, 64, 128, 256);
        for (Entry entry : entries) {
            assertThat(entry.height()).isEqualTo(entry.width());
            assertThat(entry.image().getWidth()).isEqualTo(entry.width());
            assertThat(entry.image().getHeight()).isEqualTo(entry.width());
        }
    }

    @Test
    @DisplayName("an .icns holds a PNG for each of macOS's types, its lengths adding up to the file's")
    void anIcnsHoldsEachType() throws IOException {
        Path icns = directory.resolve("app.icns");
        IcnsFile.write(icon, icns);

        byte[] bytes = Files.readAllBytes(icns);
        ByteBuffer reading = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        assertThat(type(reading)).isEqualTo("icns");
        assertThat(reading.getInt()).isEqualTo(bytes.length);
        List<Entry> entries = new ArrayList<>();
        while (reading.hasRemaining()) {
            String type = type(reading);
            int length = reading.getInt();
            byte[] png = new byte[length - 8];
            reading.get(png);
            BufferedImage image = decodePng(png);
            entries.add(new Entry(type, image.getWidth(), image.getHeight(), image));
        }

        assertThat(entries).extracting(Entry::type, Entry::width).containsExactly(
                tuple("icp4", 16),
                tuple("ic11", 32),
                tuple("icp5", 32),
                tuple("ic12", 64),
                tuple("ic07", 128),
                tuple("ic13", 256),
                tuple("ic08", 256),
                tuple("ic14", 512),
                tuple("ic09", 512),
                tuple("ic10", 1024));
        entries.forEach(entry -> assertThat(entry.height()).isEqualTo(entry.width()));
    }

    private static String type(ByteBuffer reading) {
        byte[] type = new byte[4];
        reading.get(type);
        return new String(type, StandardCharsets.US_ASCII);
    }

    @Test
    @DisplayName("with no foreground of its own, the icon sits inside the adaptive icon's safe zone, transparent around it")
    void theIconSitsInTheSafeZone() {
        BufferedImage foreground = AdaptiveIcon.foreground(icon, Optional.empty(), 432);

        assertThat(foreground.getWidth()).isEqualTo(432);
        assertThat(foreground.getHeight()).isEqualTo(432);
        assertThat(foreground.getRGB(216, 216)).isEqualTo(ICON_WHITE);
        assertThat(foreground.getRGB(10, 10) >>> 24).as("alpha outside the safe zone").isZero();
        int edgeOfSafeZone = (432 - 432 * 66 / 108) / 2;
        assertThat(foreground.getRGB(edgeOfSafeZone + 2, edgeOfSafeZone + 2)).isEqualTo(ICON_BLUE);
        assertThat(foreground.getRGB(edgeOfSafeZone - 2, edgeOfSafeZone - 2) >>> 24).isZero();
    }

    @Test
    @DisplayName("a foreground of the app's own fills the whole layer, since it was drawn for it")
    void anOwnForegroundFillsTheLayer() {
        BufferedImage own = ATestApp.drawIcon(1024, 1024);

        BufferedImage foreground = AdaptiveIcon.foreground(icon, Optional.of(own), 432);

        assertThat(foreground.getRGB(2, 2)).isEqualTo(ICON_BLUE);
    }

    @Test
    @DisplayName("Android's launcher icon is made at each density, 108dp a side")
    void theLauncherIconIsMadeAtEachDensity() {
        assertThat(AdaptiveIcon.DENSITIES).containsExactly(
                Map.entry("mdpi", 108),
                Map.entry("hdpi", 162),
                Map.entry("xhdpi", 216),
                Map.entry("xxhdpi", 324),
                Map.entry("xxxhdpi", 432));
    }
}
