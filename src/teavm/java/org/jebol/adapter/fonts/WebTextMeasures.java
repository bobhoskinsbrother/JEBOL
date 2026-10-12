package org.jebol.adapter.fonts;

import org.jebol.domain.render.TextExtent;
import org.jebol.domain.render.TextMeasure;
import org.jebol.domain.render.TextRun;
import org.jebol.domain.host.ScreenPort;

/**
 * The web build's measure of text, which measures nothing yet.
 *
 * <p>A page's canvas knows its own fonts, but the interpreter cannot reach it
 * synchronously from where it runs, and there is no font reader in the domain
 * yet -- both belong to the drawing engine, goal 1 step 3. Until then
 * measuring refuses with the same no-service a screen without a display
 * gives, so a script hears a named absence rather than reading a wrong width
 * and laying text out with it.
 */
public final class WebTextMeasures {

    private WebTextMeasures() {
    }

    public static TextMeasure ofTheMachineThisBuildRunsOn() {
        return new TextMeasure() {
            @Override
            public TextExtent extentOf(TextRun run) {
                throw new ScreenPort.Denied("no-service",
                        "the web build cannot measure text yet");
            }
        };
    }
}
