package org.jebol.domain.render;

@FunctionalInterface
public interface TextMeasure {

    TextExtent measureTheExtentOf(TextRun run);
}
