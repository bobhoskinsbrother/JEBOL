package org.jebol.adapter.fonts;

import org.jebol.domain.render.TextExtent;
import org.jebol.domain.render.TextMeasure;
import org.jebol.domain.render.TextRun;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class JavaTextMeasure implements TextMeasure {

    private static final Font THE_FONT_TEXT_IS_WRITTEN_IN = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private final Graphics2D measuringSurface;

    public JavaTextMeasure() {
        measuringSurface = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        measuringSurface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        measuringSurface.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    public Font fontOf(TextRun run) {
        return THE_FONT_TEXT_IS_WRITTEN_IN.deriveFont(
                (run.bold() ? Font.BOLD : 0) | (run.italic() ? Font.ITALIC : 0),
                (float) run.size());
    }

    @Override
    public synchronized TextExtent extentOf(TextRun run) {
        FontMetrics measured = measuringSurface.getFontMetrics(fontOf(run));
        return new TextExtent(measured.stringWidth(run.text()), measured.getAscent(), measured.getDescent());
    }
}
