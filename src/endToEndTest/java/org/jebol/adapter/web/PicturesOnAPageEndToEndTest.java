package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PicturesOnAPageEndToEndTest {

    private static final String SIX_OPAQUE_PIXELS =
            "[255.0.0.255 0.255.0.255 0.0.255.255 255.255.255.255 0.0.0.255 128.128.128.255]";

    private static final String SIX_PIXELS_THE_LAST_CLEAR =
            "[255.0.0.255 0.255.0.255 0.0.255.255 255.255.255.255 0.0.0.255 0.0.0.0]";

    @TempDir
    Path downloads;

    private byte[] readFixture(String name) throws IOException {
        try (InputStream fixture = PicturesOnAPageEndToEndTest.class
                .getResourceAsStream("/web/pictures/" + name)) {
            return fixture.readAllBytes();
        }
    }

    private String sha256Of(byte[] octets) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
    }

    private ShippedRunner servingThePictures() throws IOException {
        ShippedRunner runner = new ShippedRunner();
        for (String name : new String[] {"six.png", "six.gif", "six.bmp", "six.jpg", "six.tiff", "six.dds"}) {
            runner.servingFile("/pictures/" + name, readFixture(name), "application/octet-stream");
        }
        return runner;
    }

    @Test
    @DisplayName("the fixtures are the pictures they say they are")
    void theFixturesAreWhatTheySay() throws Exception {
        assertThat(sha256Of(readFixture("six.png")))
                .isEqualTo("acb7bc5c3523eb428f11dc55b553404f0e0ab2f5b96df4eae7a335778d729743");
        assertThat(sha256Of(readFixture("six.gif")))
                .isEqualTo("e7cb69a5521edbd557f17d616d35e678f609d3115cd1e898170b2e8fb6308695");
        assertThat(sha256Of(readFixture("six.bmp")))
                .isEqualTo("08adf2b85b937ef8dd7570588b03c7f016c337f83a315206ce45255a9d0072e7");
        assertThat(sha256Of(readFixture("six.jpg")))
                .isEqualTo("270b16442b9f75236a36b47e9459360e53d23bf25404575c39e7d7897efda2a5");
        assertThat(sha256Of(readFixture("six.tiff")))
                .isEqualTo("f6a8b0c83c8e370f92fbe631f55cdb594515cfcd5ec361019aa50ab80e7dc493");
        assertThat(readFixture("six.dds")).hasSize(8);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a lossless picture decodes in the browser to the pixels a real 3.22.5 reads from the same file")
    void decodesLosslessPictures(String browserName) throws IOException {
        try (ShippedRunner runner = servingThePictures().running("""
                pixels-of: func [name /local img out] [
                    img: load to url! rejoin [{THE-ORIGIN} "/pictures/" name]
                    out: copy []
                    repeat at 6 [append out pick img at]
                    print [name img/size mold out]
                ]
                pixels-of "six.png"
                pixels-of "six.gif"
                pixels-of "six.bmp"
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browserName, runner, downloads).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    "six.png 3x2 " + SIX_PIXELS_THE_LAST_CLEAR,
                    "six.gif 3x2 " + SIX_OPAQUE_PIXELS,
                    "six.bmp 3x2 " + SIX_OPAQUE_PIXELS);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a jpeg decodes to the browser's own reading of it, opaque throughout")
    void decodesAJpeg(String browserName) throws IOException {
        try (ShippedRunner runner = servingThePictures().running("""
                img: load to url! rejoin [{THE-ORIGIN} "/pictures/six.jpg"]
                out: copy []
                repeat at 6 [append out pick img at]
                print ["jpeg" img/size mold out]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browserName, runner, downloads).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    "jpeg 3x2 [78.86.11.255 138.146.71.255 24.12.158.255 255.255.191.255 23.31.0.255 114.102.248.255]");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("png, jpeg and webp are written by the page's canvas, and a png written reads back as the pixels it holds")
    void encodesThroughTheCanvas(String browserName) throws IOException {
        try (ShippedRunner runner = servingThePictures().running("""
                img: load to url! rejoin [{THE-ORIGIN} "/pictures/six.png"]
                again: decode 'png encode 'png img
                out: copy []
                repeat at 6 [append out pick again at]
                print ["again" again/size mold out]
                print ["jpeg" copy/part encode 'jpeg img 2]
                print ["webp" to string! copy/part image/save/as none img 'webp 4]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browserName, runner, downloads).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    "again 3x2 " + SIX_PIXELS_THE_LAST_CLEAR,
                    "jpeg FFD8",
                    "webp RIFF");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("a format the browser cannot do is a catchable error naming the format and the browser")
    void refusesWhatTheBrowserCannotDo(String browserName) throws IOException {
        try (ShippedRunner runner = servingThePictures().running("""
                img: load to url! rejoin [{THE-ORIGIN} "/pictures/six.png"]
                dds: try [load to url! rejoin [{THE-ORIGIN} "/pictures/six.dds"]]
                print ["dds" dds/id dds/arg1]
                tiff: try [load to url! rejoin [{THE-ORIGIN} "/pictures/six.tiff"]]
                print ["tiff" tiff/id tiff/arg1]
                gif: try [encode 'gif img]
                print ["gif" gif/id gif/arg1]
                bmp: try [encode 'bmp img]
                print ["bmp" bmp/id bmp/arg1]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browserName, runner, downloads).opensThePage()) {

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    "dds no-codec dds, which a browser cannot read",
                    "tiff no-codec tiff, which this browser cannot read",
                    "gif no-codec gif, which a browser cannot write",
                    "bmp no-codec bmp, which a browser cannot write");
        }
    }
}
