package org.jebol.render;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.render.Html;
import org.jebol.domain.render.Layout;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;

/**
 * Renders a REBOL layout to markup.
 *
 * <p>The layout dialect goes in, HTML comes out. This is what makes JEBOL
 * useful in a web context: the target is markup rather than a window, and a
 * VID layout is already a description of a page rather than a sequence of
 * draw calls, so rendering it here is more natural than rendering it to a
 * desktop toolkit would have been.
 *
 * <p>VID-shaped rather than pixel-faithful. Existing layouts mostly work;
 * chasing a 2001 toolkit's positioning model would produce markup nobody
 * wants to style, and pixel fidelity to a desktop window is not what makes
 * the language useful in a browser.
 */
public final class Markup {

    private Markup() {
    }

    /**
     * Runs the source and renders whatever layout it produced.
     *
     * <p>Source that produces no layout renders an empty page rather than an
     * invented one, because guessing at what somebody meant is worse than
     * showing them nothing.
     */
    public static String render(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);

        if (!outcome.succeeded() || !(outcome.value() instanceof BlockValue block)) {
            return Html.render(java.util.List.of());
        }
        return Html.render(Layout.facesIn(
                block.datatype() == Datatype.BLOCK ? block : block.as(Datatype.BLOCK)));
    }
}
