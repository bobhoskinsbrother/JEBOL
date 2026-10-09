package org.jebol.domain.render;

@FunctionalInterface
public interface TextMeasure {

    TextExtent extentOf(TextRun run);
}
