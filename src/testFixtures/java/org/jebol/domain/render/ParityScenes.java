package org.jebol.domain.render;

import java.util.List;

public final class ParityScenes {

    public static final int WIDE = 420;

    public static final int HIGH = 260;

    public static final int HOW_FAR_TWO_STACKED_BLENDS_MAY_ROUND_APART = 1;

    public static final Shapes GOB_PANELS = new Shapes("gob-panels", """
            system/view/screen-gob/color: 0.0.0
            panel: make gob! [size: 420x260 color: 30.34.44]
            append panel make gob! [offset: 0x0    size: 420x44  color: 58.140.208]
            append panel make gob! [offset: 20x70  size: 120x90  color: 232.93.74]
            append panel make gob! [offset: 155x70 size: 120x90  color: 96.198.128]
            inner: make gob! [offset: 290x70 size: 110x90 color: 244.196.66]
            append inner make gob! [offset: 20x20 size: 400x400 color: 20.20.20]
            append panel inner
            view/no-wait panel
            system/view/screen-gob
            """, 6, PixelAgreement.EXACTLY);

    public static final Shapes DRAW_BLOCK = new Shapes("draw-block", """
            system/view/screen-gob/color: 0.0.0
            view/no-wait make gob! [
                size: 420x260
                draw: [
                    fill-pen 30.34.44  pen off  box 0x0 420x260
                    fill-pen 58.140.208 box 0x0 420x44
                    fill-pen 232.93.74  box 20x70 140x160
                    fill-pen 96.198.128 box 155x70 275x160
                    push [
                        translate 20x190
                        fill-pen 70.76.92 box 0x0 380x40
                    ]
                    fill-pen 244.196.66 pen 20.20.20 line-width 3
                    circle 90x115 40
                    pen 255.255.255 line-width 5 fill-pen off
                    line 180x90 340x150
                    arc 250x115 40x40 200 140
                ]
            ]
            system/view/screen-gob
            """, 5, PixelAgreement.EXACTLY);

    public static final Shapes STROKED_CURVES = new Shapes("stroked-curves", """
            system/view/screen-gob/color: 0.0.0
            view/no-wait make gob! [
                size: 420x260
                draw: [
                    pen off fill-pen 30.34.44 box 0x0 420x260
                    fill-pen off
                    pen 130.170.255 line-width 4 curve 20x120 90x10 160x200 220x40
                    pen 244.196.66 line-width 3 curve 230x120 310x10 400x120
                    pen 96.198.128 line-width 4 spline 20 20x240 80x160 150x250 210x170
                    pen 232.93.74 line-width 5 line 230x240 300x150 330x230 400x160
                    pen 255.255.255 line-width 2 arc 330x200 50x40 200 140
                    pen 232.93.74 line-width 4 line-pattern 96.198.128 14 8 line 20x140 400x145
                    pen 244.196.66 line-width 3 fill-pen 244.196.66 arrow 244.196.66 1x1 line 240x60 400x20
                ]
            ]
            system/view/screen-gob
            """, 6, PixelAgreement.EXACTLY);

    public static final Shapes SEE_THROUGH_COLOURS_AND_ROUNDED_CORNERS = new Shapes("see-through-colours", """
            system/view/screen-gob/color: 0.0.0
            view/no-wait make gob! [
                size: 420x260
                draw: [
                    pen off
                    fill-pen 244.196.66 box 0x0 420x260
                    fill-pen 0.0.0.128 box 20x20 200x120 12
                    fill-pen 200.0.0.64 box 120x60 380x220 30
                    fill-pen 58.140.208 pen 20.20.20 line-width 2
                    box 240x30 400x110 8
                ]
            ]
            system/view/screen-gob
            """, 4, HOW_FAR_TWO_STACKED_BLENDS_MAY_ROUND_APART);

    public static final String A_CENTRED_LABEL = """
            system/view/screen-gob/color: 255.255.255
            labelled: make gob! [size: 300x200]
            labelled/text: compose [
                font (make object! [size: 20 color: 0.0.0])
                para (make object! [origin: 0x0 margin: 0x0 align: 'center valign: 'middle])
                "MMMMMM"
            ]
            view/no-wait labelled
            system/view/screen-gob/1/offset: 20x10
            show system/view/screen-gob
            """;

    public static final String A_LABEL_TOO_LONG_FOR_ITS_GOB = """
            system/view/screen-gob/color: 255.255.255
            labelled: make gob! [size: 300x200]
            labelled/text: compose [
                font (make object! [size: 20 color: 0.0.0])
                para (make object! [origin: 0x0 margin: 0x0 wrap?: true])
                "MMMM MMMM MMMM MMMM MMMM MMMM MMMM MMMM"
            ]
            view/no-wait labelled
            system/view/screen-gob/1/offset: 20x10
            show system/view/screen-gob
            """;

    public static final String A_WHITE_LABEL_WITH_A_SHADOW = """
            system/view/screen-gob/color: 255.255.255
            labelled: make gob! [size: 420x260]
            labelled/text: compose [
                font (make object! [size: 20 color: 255.255.255 shadow: 2x2])
                "MMMMMM"
            ]
            view/no-wait labelled
            system/view/screen-gob
            """;

    private ParityScenes() {
    }

    public static List<Shapes> shapes() {
        return List.of(GOB_PANELS, DRAW_BLOCK, STROKED_CURVES, SEE_THROUGH_COLOURS_AND_ROUNDED_CORNERS);
    }

    public record Shapes(String name, String rebol, int fewestColours, int insideAShapeAllowance) {

        public String referenceResource() {
            return "/parity/" + name + ".rgba";
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
