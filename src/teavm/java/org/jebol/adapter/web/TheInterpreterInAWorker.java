package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.render.PaintList;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

/**
 * The interpreter a page's worker runs. The worker's shell script loads the
 * WebAssembly module, calls {@link #main} to boot, and then speaks for the
 * page: it hands over the script to run, and everything the page posts --
 * clicks, keys, how big the viewport is -- arrives in a queue this side
 * pulls, on the interpreter's own thread, between slices of a wait.
 *
 * <p>Nothing here blocks the page. The page's own thread paints what the
 * interpreter sends and posts what the operator does; the waiting is all on
 * the worker's side, where TeaVM's pause-and-resume keeps the shell's event
 * loop alive between slices.
 */
public class TheInterpreterInAWorker {

    private static BrowserScreen screen;
    private static Interpreter interpreter;
    private static JSConsumer<String> theNextScriptToSend;

    /**
     * TeaVM exports this as {@code main}, and the run loop lives on this
     * fiber: the one path into the module where the interpreter may suspend.
     * It boots, then waits for the page to hand it a script, runs it, and
     * waits again. A wait inside a script suspends this same fiber and is
     * resumed by the worker's event loop, which is also what keeps the pulls
     * of the page's postings flowing between slices.
     */
    public static void main(String[] args) {
        screen = BrowserScreen.seenBy(new ThePageOnTheOtherSide());
        interpreter = Interpreter.writingTo(
                TheInterpreterInAWorker::asTheScriptPrints, aWorkerRunsWhereTheScreenIs());
        interpreter.useScreen(screen);
        say("booted", "");
        while (true) {
            String source = aScriptThePageHandsOver().await();
            say("finished", interpreter.display(interpreter.run(source)));
        }
    }

    private static JSPromise<String> aScriptThePageHandsOver() {
        return new JSPromise<>((handedOver, refused) -> theNextScriptToSend = handedOver);
    }

    private static Bounds aWorkerRunsWhereTheScreenIs() {
        return Bounds.standard().withoutAWallClockLimit().granting(HostService.WINDOWS);
    }

    /**
     * The page's shell calls this when it has a script to run. It hands the
     * script to the run loop waiting inside main's fiber rather than running
     * it here: a call arriving from a page callback is not a place where the
     * interpreter may suspend, and handing over returns at once.
     */
    @JSExport
    public static void runTheScript(String source) {
        JSConsumer<String> waiting = theNextScriptToSend;
        theNextScriptToSend = null;
        if (waiting == null) {
            say("failed", "a script is already running in this worker");
            return;
        }
        waiting.accept(source);
    }

    private static void asTheScriptPrints(String text) {
        say("printed", text);
    }

    private static void say(String kind, String text) {
        sendToThePage("{" + field("kind", kind) + "," + field("text", text) + "}");
    }

    private static String field(String name, String value) {
        return PaintListAsJson.asAString(name) + ":" + PaintListAsJson.asAString(value);
    }

    private static final class ThePageOnTheOtherSide implements BrowserScreen.Viewer {

        private boolean thePageHasSpoken;

        @Override
        public boolean isConnected() {
            return thePageHasSpoken;
        }

        @Override
        public void paint(PaintList painting) {
            sendToThePage("{"
                    + field("kind", "paint") + ","
                    + PaintListAsJson.asAString("picture") + ":"
                    + PaintListAsJson.written(painting,
                            (int) Math.round(screen.viewport().x()),
                            (int) Math.round(screen.viewport().y()))
                    + "}");
        }

        @Override
        public void takeWhatThePagePosted() {
            for (String posted = takeWhatThePageSent(); posted != null; posted = takeWhatThePageSent()) {
                thePageHasSpoken = true;
                WhatThePageSaid said = new WhatThePageSaid(posted);
                if (said.isAMeasurement()) {
                    if (said.isAWholeMeasurement()) {
                        screen.theBrowserMeasures(said.wide(), said.high());
                    }
                } else {
                    said.asAnEvent().ifPresent(kind ->
                            said.carrying(kind).ifPresent(detail ->
                                    screen.theBrowserReports(kind, detail)));
                }
            }
        }
    }

    @JSBody(params = "message", script = "self.postMessage(message);")
    private static native void sendToThePage(String message);

    @JSBody(script = "return inbox.length > 0 ? inbox.shift() : null;")
    private static native String takeWhatThePageSent();
}
