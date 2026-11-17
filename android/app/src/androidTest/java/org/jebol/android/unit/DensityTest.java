package org.jebol.android.unit;

import org.jebol.adapter.android.Density;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DensityTest {

    @ParameterizedTest(name = "{0} pixels at density {1} is {2} dp")
    @CsvSource({
            "800, 1, 800",
            "1600, 2, 800",
            "2560, 2, 1280",
            "1080, 2.625, 411",
            "2400, 2.625, 914",
            "1, 2.625, 0",
    })
    @DisplayName("a length in dp is the pixels divided by the density, rounded down so it never exceeds the view")
    void measuresInDp(int pixels, float density, int dp) {
        assertThat(new Density(density).toDp(pixels)).isEqualTo(dp);
    }

    @ParameterizedTest(name = "density {0} is {1} dpi")
    @CsvSource({"1, 160", "2, 320", "2.625, 420"})
    @DisplayName("the screen's dpi is 160 times the density")
    void answersTheDpi(float density, int dpi) {
        assertThat(new Density(density).screenDpi()).isEqualTo(dpi);
    }

    @ParameterizedTest(name = "{0} pixels at density {1} is {2} dp")
    @CsvSource({"0, 2, 0", "21, 2, 10", "1050, 2.625, 400"})
    @DisplayName("a finger's position in dp is its pixels divided by the density, rounded down")
    void placesAFinger(float pixels, float density, int dp) {
        assertThat(new Density(density).toDp(pixels)).isEqualTo(dp);
    }
}
