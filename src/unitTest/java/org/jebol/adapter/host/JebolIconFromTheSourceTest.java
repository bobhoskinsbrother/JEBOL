package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JebolIconFromTheSourceTest {

    private final JebolIcon icon = new JebolIcon();

    private Color readTheColourAt(BufferedImage drawn, int across, int down) {
        return new Color(drawn.getRGB(across, down), true);
    }

    @ParameterizedTest(name = "drawn at {0} it is {0} square")
    @ValueSource(ints = {1, 16, 32, 512})
    @DisplayName("it is drawn square, at the size asked for")
    void isSquare(int size) {
        BufferedImage drawn = icon.drawAt(size);

        assertThat(drawn.getWidth()).isEqualTo(size);
        assertThat(drawn.getHeight()).isEqualTo(size);
    }

    @ParameterizedTest(name = "a size of {0} is refused")
    @ValueSource(ints = {0, -1})
    @DisplayName("a size of nothing or less is refused, as there is no picture to draw")
    void refusesASizeOfNothing(int size) {
        assertThatThrownBy(() -> icon.drawAt(size)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("its corners are see-through, so it sits in the dock as a rounded tile")
    void leavesTheCornersSeeThrough() {
        BufferedImage drawn = icon.drawAt(256);

        assertThat(readTheColourAt(drawn, 0, 0).getAlpha()).isZero();
        assertThat(readTheColourAt(drawn, 255, 0).getAlpha()).isZero();
        assertThat(readTheColourAt(drawn, 0, 255).getAlpha()).isZero();
        assertThat(readTheColourAt(drawn, 255, 255).getAlpha()).isZero();
    }

    @Test
    @DisplayName("its middle is solid")
    void fillsTheMiddle() {
        assertThat(readTheColourAt(icon.drawAt(256), 128, 40).getAlpha()).isEqualTo(255);
    }

    @Test
    @DisplayName("it carries a mark, not a plain tile: its middle holds more than one colour")
    void carriesAMark() {
        BufferedImage drawn = icon.drawAt(256);
        Set<Integer> seen = new HashSet<>();
        for (int down = 64; down < 192; down++) {
            for (int across = 64; across < 192; across++) {
                seen.add(drawn.getRGB(across, down));
            }
        }

        assertThat(seen.size()).isGreaterThan(2);
    }

    @Test
    @DisplayName("drawn twice it is the same picture, so the dock and the title bar agree")
    void drawsTheSameEachTime() {
        BufferedImage once = icon.drawAt(64);
        BufferedImage again = icon.drawAt(64);
        for (int down = 0; down < 64; down++) {
            for (int across = 0; across < 64; across++) {
                assertThat(again.getRGB(across, down)).isEqualTo(once.getRGB(across, down));
            }
        }
    }

    @Test
    @DisplayName("the application's name is JEBOL")
    void namesTheApplicationJebol() {
        assertThat(JebolIcon.APPLICATION_NAME).isEqualTo("JEBOL");
    }
}
