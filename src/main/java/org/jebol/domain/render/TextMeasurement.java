package org.jebol.domain.render;

@FunctionalInterface
public interface TextMeasurement {

    TextExtent textExtent(TextRun run);
}
