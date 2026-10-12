package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

public class TheInterpreterInAWorker {

    private static BrowserScreen screen;
    private static WebConsole console;
    private static Interpreter interpreter;
    private static JSConsumer<String> theNextScriptToSend;

    public static void main(String[] args) {
        ThePageOnTheOtherSide thePage = new ThePageOnTheOtherSide();
        screen = BrowserScreen.seenBy(thePage);
        console = new WebConsole(thePage::takeWhatThePagePosted);
        interpreter = Interpreter.writingTo(
                TheInterpreterInAWorker::asTheScriptPrints, aWorkerRunsWhereTheScreenIs());
        interpreter.useScreen(screen);
        interpreter.useConsole(console);
        interpreter.useFileSystem(new WebFiles());
        interpreter.useHttp(new WebHttp());
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
        return Bounds.standard()
                .withoutAWallClockLimit()
                .granting(HostService.CONSOLE)
                .granting(HostService.FILES)
                .granting(HostService.WINDOWS)
                .granting(HostService.WEB)
                .lacking(HostService.NETWORK)
                .lacking(HostService.PROCESSES)
                .lacking(HostService.ENVIRONMENT)
                .lacking(HostService.WORKING_DIRECTORY)
                .called("a browser");
    }

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
                } else if (said.isALine()) {
                    console.aLineWasTyped(said.theLine());
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
