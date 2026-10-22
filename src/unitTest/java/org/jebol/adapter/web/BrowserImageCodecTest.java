package org.jebol.adapter.web;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.value.Raised;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowserImageCodecTest {

    private final ImagePort.Pixels onePixel = new ImagePort.Pixels(1, 1, new byte[] {1, 2, 3, 4});
    private final ABrowsersPictures browser = new ABrowsersPictures();
    private final BrowserImageCodec codec = new BrowserImageCodec(browser);

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"png", "PNG", "jpeg", "jpg", "gif", "bmp", "dds", "tiff", "webp"})
    @DisplayName("every format the catalogue names is a format the codec knows, whatever its case, and webp besides")
    void knowsTheCataloguesFormats(String type) {
        assertThat(codec.hasImageType(type)).isTrue();
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = {"", "svg", "heif", "pn", "pngs"})
    @DisplayName("a name the catalogue does not hold is not a format the codec knows")
    void knowsNothingElse(String type) {
        assertThat(codec.hasImageType(type)).isFalse();
    }

    @Test
    @DisplayName("decoding hands the bytes to the browser and answers the pixels it read")
    void decodesThroughTheBrowser() {
        browser.decodesTo(onePixel);

        assertThat(codec.decode(new byte[] {9}, "png", 1)).isSameAs(onePixel);
        assertThat(codec.decode(new byte[] {9}, "", 1)).isSameAs(onePixel);
    }

    @Test
    @DisplayName("bytes the browser cannot read, in a format it can, are not a picture")
    void unreadableBytesAreNoPicture() {
        assertThat(codec.decode(new byte[] {9}, "png", 1)).isNull();
    }

    @Test
    @DisplayName("only the first frame can be read, so any other frame is not there")
    void onlyTheFirstFrame() {
        browser.decodesTo(onePixel);

        assertThat(codec.decode(new byte[] {9}, "gif", 2)).isNull();
        assertThat(codec.decode(new byte[] {9}, "gif", 0)).isNull();
    }

    @Test
    @DisplayName("dds is refused before the browser is asked, naming the format and the browser")
    void refusesDds() {
        assertThatThrownBy(() -> codec.decode(new byte[] {9}, "DDS", 1))
                .isInstanceOf(Raised.class)
                .hasMessageContaining("dds")
                .hasMessageContaining("browser")
                .satisfies(raised -> assertThat(((Raised) raised).error().errorId()).isEqualTo("no-codec"));
        assertThat(browser.decodesAsked()).isZero();
    }

    @Test
    @DisplayName("tiff is tried, because Safari reads it, and a browser that cannot is named")
    void triesTiff() {
        assertThatThrownBy(() -> codec.decode(new byte[] {9}, "tiff", 1))
                .isInstanceOf(Raised.class)
                .hasMessageContaining("tiff")
                .hasMessageContaining("browser");
        browser.decodesTo(onePixel);
        assertThat(codec.decode(new byte[] {9}, "tiff", 1)).isSameAs(onePixel);
    }

    @Test
    @DisplayName("encoding asks the browser's canvas for the format's media type")
    void encodesThroughTheCanvas() {
        browser.encodesTo(new byte[] {7});

        assertThat(codec.encode(onePixel, "png")).containsExactly(7);
        assertThat(codec.encode(onePixel, "JPG")).containsExactly(7);
        assertThat(codec.encode(onePixel, "webp")).containsExactly(7);
        assertThat(codec.encode(onePixel, "jpeg")).containsExactly(7);
        assertThat(browser.mediaTypesAsked()).containsExactly("image/png", "image/jpeg", "image/webp", "image/jpeg");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"gif", "bmp", "tiff", "dds"})
    @DisplayName("a format a canvas cannot write is refused, naming the format and the browser")
    void refusesWhatACanvasCannotWrite(String type) {
        assertThatThrownBy(() -> codec.encode(onePixel, type))
                .isInstanceOf(Raised.class)
                .hasMessageContaining(type)
                .hasMessageContaining("browser");
        assertThat(browser.mediaTypesAsked()).isEmpty();
    }

    @Test
    @DisplayName("a browser that wrote something other than what was asked is refused rather than trusted")
    void refusesWhenTheBrowserWroteSomethingElse() {
        assertThatThrownBy(() -> codec.encode(onePixel, "webp"))
                .isInstanceOf(Raised.class)
                .hasMessageContaining("webp");
    }

    private static final class ABrowsersPictures implements BrowserPictures {

        private Optional<ImagePort.Pixels> decoded = Optional.empty();
        private Optional<byte[]> encoded = Optional.empty();
        private int decodes;
        private final List<String> mediaTypes = new ArrayList<>();

        void decodesTo(ImagePort.Pixels pixels) {
            decoded = Optional.of(pixels);
        }

        void encodesTo(byte[] octets) {
            encoded = Optional.of(octets);
        }

        int decodesAsked() {
            return decodes;
        }

        List<String> mediaTypesAsked() {
            return mediaTypes;
        }

        @Override
        public Optional<ImagePort.Pixels> decode(byte[] octets) {
            decodes++;
            return decoded;
        }

        @Override
        public Optional<byte[]> encode(ImagePort.Pixels image, String mediaType) {
            mediaTypes.add(mediaType);
            return encoded;
        }
    }
}
