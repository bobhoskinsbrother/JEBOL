package org.jebol.adapter.web;

import org.jebol.domain.render.TextExtent;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.render.TextRun;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSPromise;

final class BrowserTextMeasurement implements TextMeasurement {

    private boolean isFontLoaded;

    @Override
    public TextExtent textExtent(TextRun run) {
        if (!isFontLoaded) {
            loadCarriedFont().await();
            isFontLoaded = true;
        }
        Measured measured = measureInWorker(run.text(), run.size(), run.bold(), run.italic());
        return new TextExtent(measured.getWide(), measured.getAscent(), measured.getDescent());
    }

    interface Measured extends JSObject {

        @JSProperty
        double getWide();

        @JSProperty
        double getAscent();

        @JSProperty
        double getDescent();
    }

    @JSBody(script = "return jebolLoadCarriedFont();")
    private static native JSPromise<JSObject> loadCarriedFont();

    @JSBody(params = {"text", "size", "bold", "italic"},
            script = "return jebolMeasureText(text, size, bold, italic);")
    private static native Measured measureInWorker(String text, double size, boolean bold, boolean italic);
}
