package org.jebol.adapter.web;

import org.jebol.adapter.host.DesktopPainting;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("browser")
class BothRenderersSideBySideTest {

    private static final int PAGE_WIDE = 600;
    private static final int PAGE_HIGH = 400;

    private static final String WHERE_TO_PUT_THEM =
            System.getProperty("jebol.pictures", "");

    private WebScreenServer serving;
    private BrowserScreen screen;
    private ChromeDriver browser;
    private ObjectValue theDrawDialect;

    @BeforeEach
    void openAPage() throws IOException {
        serving = WebScreenServer.on(0);
        screen = BrowserScreen.seenBy(serving);
        serving.reportTo(screen);

        ChromeOptions asked = new ChromeOptions();
        asked.addArguments("--headless=new",
                "--window-size=" + PAGE_WIDE + "," + PAGE_HIGH,
                "--force-device-scale-factor=1",
                "--hide-scrollbars",
                "--no-sandbox");
        browser = new ChromeDriver(asked);
        browser.get(serving.address());

        new WebDriverWait(browser, Duration.ofSeconds(20))
                .until(anything -> screen.hasADisplay());
    }

    @AfterEach
    void closeThePage() {
        if (browser != null) {
            browser.quit();
        }
        serving.close();
    }

    private record APathWorthLookingAt(String name, String drawBlock, String setUp) {

        APathWorthLookingAt(String name, String drawBlock) {
            this(name, drawBlock, "");
        }
    }

    private static final java.util.List<APathWorthLookingAt> EVERY_PATH =
            java.util.List.of(
                    new APathWorthLookingAt("box",
                            "fill-pen 70.76.92 pen 255.255.255 line-width 2 "
                                    + "box 40x40 260x160"),
                    new APathWorthLookingAt("circle",
                            "fill-pen 244.196.66 pen 255.255.255 line-width 3 "
                                    + "circle 150x100 70"),
                    new APathWorthLookingAt("ellipse",
                            "fill-pen 96.198.128 pen 255.255.255 line-width 3 "
                                    + "ellipse 30x40 240x120"),
                    new APathWorthLookingAt("arc",
                            "pen 96.198.128 line-width 5 fill-pen off "
                                    + "arc 150x100 80x60 200 140"),
                    new APathWorthLookingAt("sloped-line",
                            "pen 232.93.74 line-width 5 fill-pen off "
                                    + "line 30x30 270x170"),
                    new APathWorthLookingAt("polygon",
                            "fill-pen 130.170.255 pen 255.255.255 line-width 2 "
                                    + "polygon 40x160 90x40 160x150 240x50 270x170"),
                    new APathWorthLookingAt("cubic-curve",
                            "pen 130.170.255 line-width 4 fill-pen off "
                                    + "curve 30x150 110x30 200x180 270x60"),
                    new APathWorthLookingAt("quadratic-curve",
                            "pen 244.196.66 line-width 4 fill-pen off "
                                    + "curve 30x150 150x20 270x150"),
                    new APathWorthLookingAt("triangle",
                            "fill-pen 70.76.92 pen 255.255.255 line-width 2 "
                                    + "triangle 40x170 270x170 155x40"),
                    new APathWorthLookingAt("spline-open",
                            "pen 96.198.128 line-width 4 fill-pen off "
                                    + "spline 20 30x150 90x50 170x160 260x60"),
                    new APathWorthLookingAt("spline-closed",
                            "fill-pen 58.140.208 pen 255.255.255 line-width 2 "
                                    + "spline 20 closed 60x140 150x40 250x140 150x180"),
                    new APathWorthLookingAt("shape-path",
                            "fill-pen 232.93.74 pen 255.255.255 line-width 2 shape ["
                                    + "move 40x160 line 100x40 curve 160x20 220x180 270x60"
                                    + " line 250x170 close]"),
                    new APathWorthLookingAt("transform-turned",
                            "transform 30 150x100 1 1 0x0 "
                                    + "fill-pen 244.196.66 pen 255.255.255 line-width 2 "
                                    + "box 60x50 240x150"),
                    new APathWorthLookingAt("dashed-line",
                            "pen 232.93.74 line-width 4 fill-pen off "
                                    + "line-pattern 255.0.0 14 8 line 30x100 270x100"),
                    new APathWorthLookingAt("arrowed-line",
                            "pen 244.196.66 line-width 4 fill-pen 244.196.66 "
                                    + "arrow 244.196.66 1x1 line 40x150 260x60"),
                    new APathWorthLookingAt("linear-gradient",
                            "pen off grad-pen linear normal 40x40 0 220 35 1 1 "
                                    + "[232.93.74 244.196.66 96.198.128] "
                                    + "box 30x40 270x160"),
                    new APathWorthLookingAt("radial-gradient",
                            "pen off grad-pen radial normal 150x100 0 90 0 1 1 "
                                    + "[244.196.66 58.140.208] circle 150x100 85"),
                    new APathWorthLookingAt("image-scaled",
                            "image (held) 40x40 240x160",
                            "held: make image! [40x40 255.100.40]"),
                    new APathWorthLookingAt("image-turned",
                            "rotate 20 image (held) 60x40 240x150",
                            "held: make image! [40x40 96.198.128]"),
                    new APathWorthLookingAt("text-drawn",
                            "pen 244.196.66 text vectorial 40x80 260x40 "
                                    + "[{DRAW writes text now}]"),
                    new APathWorthLookingAt("gouraud-triangle",
                            "pen off triangle 40x170 270x170 155x30 "
                                    + "232.93.74 96.198.128 58.140.208 1"),
                    new APathWorthLookingAt("conic-gradient",
                            "pen off grad-pen conic normal 150x100 0 120 0 1 1 "
                                    + "[232.93.74 244.196.66 96.198.128 58.140.208] "
                                    + "circle 150x100 85"),
                    new APathWorthLookingAt("diamond-gradient",
                            "pen off grad-pen diamond normal 150x100 0 110 0 1 1 "
                                    + "[244.196.66 58.140.208] box 30x20 270x180"),
                    new APathWorthLookingAt("dashed-two-colour",
                            "pen 232.93.74 line-width 6 fill-pen off "
                                    + "line-pattern 96.198.128 16 10 line 30x100 270x100"),
                    new APathWorthLookingAt("gamma-lightened",
                            "gamma 2.2 fill-pen 120.60.30 pen off box 30x30 270x170"),
                    new APathWorthLookingAt("rich-text",
                            "pen 244.196.66 text vectorial 30x70 260x60 "
                                    + "[20 bold {Bold} /bold 96.198.128 { then green}]"),
                    new APathWorthLookingAt("image-keyed",
                            "image (held) 255.100.40 30x40 270x160",
                            "held: make image! [4x4 255.100.40] "
                                    + "held/2: 58.140.208 held/7: 58.140.208"),
                    new APathWorthLookingAt("clipped",
                            "clip 60x60 240x140 "
                                    + "fill-pen 96.198.128 pen off circle 150x100 90"));

    @Test
    @DisplayName("every path kind drawn in both renderers, and written out to look at")
    void everyPathKindInBothRenderers() throws Exception {
        Path into = Path.of(WHERE_TO_PUT_THEM.isEmpty()
                ? "build/renderer-pictures"
                : WHERE_TO_PUT_THEM);
        Files.createDirectories(into);

        for (APathWorthLookingAt path : EVERY_PATH) {
            String source = path.setUp() + """

                    system/view/screen-gob/color: 24.28.38
                    shown: make gob! [offset: 10x10 size: 300x200]
                    shown/draw: compose [""" + path.drawBlock() + """
                    ]
                    view/no-wait shown
                    system/view/screen-gob
                    """;
            Interpreter interpreter = anInterpreterOnThisPage();
            interpreter.defineFreshWordsIn(source);
            GobValue root = (GobValue) interpreter.run(source).value();

            BufferedImage fromTheBrowser = whatTheCanvasShows();
            BufferedImage fromJava2D = whatJava2DPaints(root,
                    fromTheBrowser.getWidth(), fromTheBrowser.getHeight());

            ImageIO.write(fromTheBrowser, "png",
                    new File(into.toFile(), path.name() + "-browser.png"));
            ImageIO.write(fromJava2D, "png",
                    new File(into.toFile(), path.name() + "-java2d.png"));
            ImageIO.write(whereTheyDiffer(fromTheBrowser, fromJava2D), "png",
                    new File(into.toFile(), path.name() + "-difference.png"));

            assertThat(fromTheBrowser.getWidth()).isEqualTo(fromJava2D.getWidth());
        }
    }

    private static BufferedImage whereTheyDiffer(BufferedImage one, BufferedImage other) {
        BufferedImage map = new BufferedImage(
                one.getWidth(), one.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int down = 0; down < one.getHeight(); down++) {
            for (int across = 0; across < one.getWidth(); across++) {
                int here = one.getRGB(across, down);
                int there = other.getRGB(across, down);
                int worst = 0;
                for (int shift : new int[] {16, 8, 0}) {
                    worst = Math.max(worst, Math.abs(
                            ((here >> shift) & 0xFF) - ((there >> shift) & 0xFF)));
                }
                int shown = Math.min(255, worst * 4);
                map.setRGB(across, down, (shown << 16) | (shown << 8) | shown);
            }
        }
        return map;
    }

    private BufferedImage whatTheCanvasShows() throws IOException {
        WebElement canvas = browser.findElement(By.id("screen"));
        return ImageIO.read(new ByteArrayInputStream(
                canvas.getScreenshotAs(OutputType.BYTES)));
    }

    private BufferedImage whatJava2DPaints(GobValue root, int wide, int high) {
        BufferedImage surface =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
        Graphics2D onto = surface.createGraphics();
        try {
            onto.setColor(Color.WHITE);
            onto.fillRect(0, 0, wide, high);
            DesktopPainting.execute(onto,
                    PaintList.ofTheScreen(root, wide, high, theDrawDialect));
        } finally {
            onto.dispose();
        }
        return surface;
    }

    private Interpreter anInterpreterOnThisPage() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        theDrawDialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        return interpreter;
    }
}
