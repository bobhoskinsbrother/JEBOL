package org.jebol.adapter.host;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

final class JebolsIcon {

    static final String THE_APPLICATIONS_NAME = "JEBOL";

    private static final String THE_MARK = "J";

    private static final Color THE_TOP_OF_THE_TILE = new Color(250, 214, 92);
    private static final Color THE_FOOT_OF_THE_TILE = new Color(222, 122, 40);
    private static final Color THE_MARKS_COLOUR = Color.WHITE;
    private static final Color THE_MARKS_SHADOW = new Color(60, 30, 10, 160);

    private static final double HOW_MUCH_OF_THE_SIZE_IS_MARGIN = 0.06;
    private static final double HOW_ROUND_THE_CORNERS_ARE = 0.22;
    private static final double HOW_TALL_THE_MARK_IS = 0.62;
    private static final double HOW_FAR_THE_SHADOW_FALLS = 0.025;

    BufferedImage drawnAt(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("an icon needs at least one pixel");
        }
        BufferedImage drawn = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D onto = drawn.createGraphics();
        try {
            onto.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            onto.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawTheTile(onto, size);
            drawTheMark(onto, size);
        } finally {
            onto.dispose();
        }
        return drawn;
    }

    private void drawTheTile(Graphics2D onto, int size) {
        double margin = size * HOW_MUCH_OF_THE_SIZE_IS_MARGIN;
        double side = size - 2 * margin;
        double rounding = side * HOW_ROUND_THE_CORNERS_ARE * 2;
        onto.setPaint(new GradientPaint(0, (float) margin, THE_TOP_OF_THE_TILE,
                0, (float) (margin + side), THE_FOOT_OF_THE_TILE));
        onto.fill(new RoundRectangle2D.Double(margin, margin, side, side, rounding, rounding));
    }

    private void drawTheMark(Graphics2D onto, int size) {
        onto.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(1, (int) (size * HOW_TALL_THE_MARK_IS))));
        FontMetrics measured = onto.getFontMetrics();
        float across = (size - measured.stringWidth(THE_MARK)) / 2f;
        float baseline = (size - measured.getAscent() - measured.getDescent()) / 2f + measured.getAscent();
        float shadowFalls = (float) Math.max(1, size * HOW_FAR_THE_SHADOW_FALLS);
        onto.setColor(THE_MARKS_SHADOW);
        onto.drawString(THE_MARK, across + shadowFalls, baseline + shadowFalls);
        onto.setColor(THE_MARKS_COLOUR);
        onto.drawString(THE_MARK, across, baseline);
    }
}
