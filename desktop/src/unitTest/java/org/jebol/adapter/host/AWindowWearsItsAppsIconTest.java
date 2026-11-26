package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AWindowWearsItsAppsIconTest {

    private static final Color THE_APPS_OWN = new Color(20, 90, 200);

    private static final LeavingConfirmation LEAVES = () -> true;

    @TempDir
    Path folder;

    private Path anAppsIcon() throws IOException {
        BufferedImage picture = new BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB);
        for (int down = 0; down < 1024; down++) {
            for (int across = 0; across < 1024; across++) {
                picture.setRGB(across, down, THE_APPS_OWN.getRGB());
            }
        }
        Path written = folder.resolve("icon.png");
        ImageIO.write(picture, "png", written.toFile());
        return written;
    }

    private static Color middleOf(BufferedImage drawn) {
        return new Color(drawn.getRGB(drawn.getWidth() / 2, drawn.getHeight() / 2), true);
    }

    @Test
    @DisplayName("an app's icon is drawn at each size a window asks for, square, from the app's own picture")
    void drawsTheAppsPictureAtEachSize() throws IOException {
        AppIcon icon = AppIcon.readFrom(anAppsIcon());

        for (int size : new int[]{16, 32, 256}) {
            BufferedImage drawn = icon.drawAt(size);
            assertThat(drawn.getWidth()).isEqualTo(size);
            assertThat(drawn.getHeight()).isEqualTo(size);
            assertThat(middleOf(drawn)).isEqualTo(THE_APPS_OWN);
        }
    }

    @Test
    @DisplayName("a screen wearing an app's icon gives its windows that icon")
    void aScreenWearingAnAppsIconGivesItToItsWindows() throws IOException {
        DesktopScreen screen = DesktopScreen.confirmingWith(new ClasspathFiles(), LEAVES, DesktopSystem.WINDOWS)
                .wearing(AppIcon.readFrom(anAppsIcon()));

        assertThat(middleOf(screen.iconAt(32))).isEqualTo(THE_APPS_OWN);
    }

    @Test
    @DisplayName("and one wearing none gives JEBOL's own, as an unpackaged program has")
    void aScreenWearingNoneGivesJebolsOwn() {
        DesktopScreen screen = DesktopScreen.confirmingWith(new ClasspathFiles(), LEAVES, DesktopSystem.WINDOWS);

        assertThat(middleOf(screen.iconAt(32))).isEqualTo(middleOf(new JebolIcon().drawAt(32)));
    }
}
