package org.jebol.render;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.render.Html;
import org.jebol.domain.render.Layout;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;

import java.util.List;

/** Renders a REBOL layout to markup: the layout dialect in, HTML out. */
public final class Markup {

    private Markup() {
    }

    /** Runs the source and renders whatever layout it produced. */
    public static String render(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);

        if (!outcome.succeeded() || !(outcome.value() instanceof BlockValue block)) {
            return anEmptyPageRatherThanAnInventedOne();
        }
        return Html.render(Layout.facesIn(
                block.datatype() == Datatype.BLOCK ? block : block.as(Datatype.BLOCK)));
    }

    private static String anEmptyPageRatherThanAnInventedOne() {
        return Html.render(List.of());
    }
}
