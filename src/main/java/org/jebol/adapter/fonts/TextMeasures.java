package org.jebol.adapter.fonts;

import org.jebol.domain.render.TextMeasure;

/**
 * Where the measure of text comes from, on the machine this build runs on.
 *
 * <p>The desktop measures with the JDK's own fonts; the web build substitutes
 * this class whole, because its measure is not the JDK's and the web has no
 * fonts of its own yet. A class-level substitution rather than a constructor
 * call at the point of use, which is the shape the web build's substitutions
 * take.
 */
public final class TextMeasures {

    private TextMeasures() {
    }

    public static TextMeasure ofTheMachineThisBuildRunsOn() {
        return new JavaTextMeasure();
    }
}
