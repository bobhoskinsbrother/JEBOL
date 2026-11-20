package org.jebol.android.unit;

import org.jebol.adapter.android.HexColour;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HexColourTest {

    @Test
    @DisplayName("six hex digits read as three octets, with or without a leading hash, in either case")
    void sixDigitsRead() {
        assertThat(HexColour.read("336699")).hasValueSatisfying(octets -> assertThat(octets).containsExactly(51, 102, 153));
        assertThat(HexColour.read("#FF00aa")).hasValueSatisfying(octets -> assertThat(octets).containsExactly(255, 0, 170));
        assertThat(HexColour.read(" #000000 ")).hasValueSatisfying(octets -> assertThat(octets).containsExactly(0, 0, 0));
    }

    @Test
    @DisplayName("anything but six hex digits reads as nothing, so the swatch keeps the colour it had")
    void anythingElseIsNothing() {
        assertThat(HexColour.read("")).isEmpty();
        assertThat(HexColour.read("12345")).isEmpty();
        assertThat(HexColour.read("1234567")).isEmpty();
        assertThat(HexColour.read("#12g456")).isEmpty();
    }

    @Test
    @DisplayName("three octets write as six lower-case digits with no hash, as the field shows them")
    void octetsWrite() {
        assertThat(HexColour.write(new int[] {51, 102, 153})).isEqualTo("336699");
        assertThat(HexColour.write(new int[] {255, 0, 170})).isEqualTo("ff00aa");
    }

    @Test
    @DisplayName("a colour taken to hue, saturation and value and back is the same colour")
    void hsvRoundTrips() {
        for (int[] colour : new int[][] {{0, 0, 0}, {255, 255, 255}, {51, 102, 153}, {255, 0, 0}, {12, 200, 7}}) {
            assertThat(HexColour.fromHsv(HexColour.toHsv(colour))).as(HexColour.write(colour)).containsExactly(colour);
        }
    }
}
