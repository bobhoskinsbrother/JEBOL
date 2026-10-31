package org.jebol.adapter.web;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.adapter.host.DesktopScreen;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.value.ImageValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TheSuitesPageBrowsers.class)
class TheDrawExampleEndToEndTest {

    private static final Path THE_EXAMPLE = Path.of("examples", "draw", "main.reb");

    private static final int TILES = 22;

    private static final int COLUMNS = 6;

    private static final int GAP = 10;

    private static final int CAPTION_HIGH = 18;

    private static final int TILE_WIDE = 150;

    private static final int TILE_HIGH = 100;

    private static final int WINDOW_COLOUR = 0x181C26;

    private static final int TILE_COLOUR = 0x282E3C;

    private final PageBrowsers browsers;

    TheDrawExampleEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private record Picture(int wide, int high, int[] rgb) {

        int at(int across, int down) {
            return rgb[down * wide + across] & 0xFFFFFF;
        }
    }

    private static List<Integer> tilesThatDrewNothing(Picture shown, int windowAcross, int windowDown) {
        List<Integer> blank = new ArrayList<>();
        for (int tile = 0; tile < TILES; tile++) {
            int left = windowAcross + GAP + (tile % COLUMNS) * (TILE_WIDE + GAP);
            int top = windowDown + GAP + (tile / COLUMNS) * (TILE_HIGH + GAP + CAPTION_HIGH) + CAPTION_HIGH;
            if (!drawsInside(shown, left, top)) {
                blank.add(tile + 1);
            }
        }
        return blank;
    }

    private static boolean drawsInside(Picture shown, int left, int top) {
        for (int down = top + 2; down < top + TILE_HIGH - 2; down++) {
            for (int across = left + 2; across < left + TILE_WIDE - 2; across++) {
                int colour = shown.at(across, down);
                if (colour != TILE_COLOUR && colour != WINDOW_COLOUR) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<int[]> findWindowCorner(Picture shown) {
        for (int down = 0; down < shown.high(); down++) {
            for (int across = 0; across < shown.wide(); across++) {
                if (shown.at(across, down) == WINDOW_COLOUR) {
                    return Optional.of(new int[] {across, down});
                }
            }
        }
        return Optional.empty();
    }

    private static Picture asPicture(ImageValue image) {
        int wide = image.storage().wide();
        int high = image.storage().high();
        int[] rgb = new int[wide * high];
        for (int pixel = 0; pixel < rgb.length; pixel++) {
            int[] channels = image.storage().readPixelAt(pixel + 1);
            rgb[pixel] = channels[0] << 16 | channels[1] << 8 | channels[2];
        }
        return new Picture(wide, high, rgb);
    }

    private static Picture asPicture(BufferedImage image) {
        return new Picture(image.getWidth(), image.getHeight(),
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    @Test
    @DisplayName("on the desktop, the example's window is 970x522 and every one of its 22 tiles draws something")
    void everyTileDrawsOnTheDesktop() {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withScreen(DesktopScreen.onThisMachine(new ClasspathFiles()))
                        .withFiles(FileSystemPort.rootFileSystem(Path.of(".").toAbsolutePath())),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        String source = """
                view: func [window /options settings] [shot: to image! window]
                change-dir %examples/draw/
                do %main.reb
                shot""";
        interpreter.defineNewWordsIn(source);
        Picture shown = asPicture((ImageValue) interpreter.run(source).value());

        assertThat(List.of(shown.wide(), shown.high())).containsExactly(970, 522);
        assertThat(tilesThatDrewNothing(shown, 0, 0)).isEmpty();
    }

    @Test
    @DisplayName("on a page, the example runs as it ships in the worker and every one of its 22 tiles draws on the canvas")
    void everyTileDrawsOnAPage() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running(Files.readString(THE_EXAMPLE));
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers, "chrome", runner).opensThePage()) {

            Picture[] shown = new Picture[1];
            new PageWait(operator.browser(), Duration.ofSeconds(60)).until(anything -> {
                shown[0] = captureTheCanvas(operator);
                return findWindowCorner(shown[0]).isPresent()
                        && tilesThatDrewNothing(shown[0], findWindowCorner(shown[0]).get()[0],
                                findWindowCorner(shown[0]).get()[1]).isEmpty();
            });
            int[] corner = findWindowCorner(shown[0]).orElseThrow();
            assertThat(tilesThatDrewNothing(shown[0], corner[0], corner[1])).isEmpty();
        }
    }

    private static Picture captureTheCanvas(AnOperatorAtThePage operator) {
        try {
            byte[] png = operator.browser().findElement(By.id("screen")).getScreenshotAs(OutputType.BYTES);
            return asPicture(ImageIO.read(new ByteArrayInputStream(png)));
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
