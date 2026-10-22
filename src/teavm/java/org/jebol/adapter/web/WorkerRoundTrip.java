package org.jebol.adapter.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSString;

public final class WorkerRoundTrip implements PageRoundTrip {

    private int asksMade;

    @Override
    public PageAnswer ask(PageAsk asked) {
        asksMade++;
        JSString answer = postAsk(asksMade, asked.writeNumbered(asksMade)).await();
        return new PageAnswer(answer.stringValue());
    }

    @JSBody(params = {"number", "written"}, script = "return jebolAsk(number, written);")
    private static native JSPromise<JSString> postAsk(int number, String written);
}
