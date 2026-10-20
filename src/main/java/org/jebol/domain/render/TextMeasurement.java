package org.jebol.domain.render;

@FunctionalInterface
public interface TextMeasurement {

    TextExtent measureTheExtentOf(TextRun run);
}
