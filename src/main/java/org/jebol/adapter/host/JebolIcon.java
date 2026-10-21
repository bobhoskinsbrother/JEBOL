package org.jebol.adapter.host;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

final class JebolIcon {

    static final String APPLICATION_NAME = "JEBOL";

    private static final String MARK = "J";

    private static final Color TOP_OF_TILE = new Color(250, 214, 92);
    private static final Color FOOT_OF_TILE = new Color(222, 122, 40);
    private static final Color LOGO_COLOUR = Color.WHITE;
    private static final Color LOGO_SHADOW = new Color(60, 30, 10, 160);

    private static final double MARGIN_PERCENTAGE = 0.06;
    private static final double CORNER_ROUNDING = 0.22;
    private static final double ICON_PERCENTAGE = 0.62;
    private static final double SHADOW_PERCENTAGE = 0.025;

    BufferedImage drawAt(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("an icon needs at least one pixel");
        }
        BufferedImage drawn = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D onto = drawn.createGraphics();
        try {
            onto.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            onto.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawTile(onto, size);
            drawIcon(onto, size);
        } finally {
            onto.dispose();
        }
        return drawn;
    }

    private void drawTile(Graphics2D onto, int size) {
        double margin = size * MARGIN_PERCENTAGE;
        double side = size - 2 * margin;
        double rounding = side * CORNER_ROUNDING * 2;
        onto.setPaint(new GradientPaint(0, (float) margin, TOP_OF_TILE,
                0, (float) (margin + side), FOOT_OF_TILE));
        onto.fill(new RoundRectangle2D.Double(margin, margin, side, side, rounding, rounding));
    }

    private void drawIcon(Graphics2D onto, int size) {
        onto.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(1, (int) (size * ICON_PERCENTAGE))));
        FontMetrics measured = onto.getFontMetrics();
        float across = (size - measured.stringWidth(MARK)) / 2f;
        float baseline = (size - measured.getAscent() - measured.getDescent()) / 2f + measured.getAscent();
        float shadowFalls = (float) Math.max(1, size * SHADOW_PERCENTAGE);
        onto.setColor(LOGO_SHADOW);
        onto.drawString(MARK, across + shadowFalls, baseline + shadowFalls);
        onto.setColor(LOGO_COLOUR);
        onto.drawString(MARK, across, baseline);
    }
}
