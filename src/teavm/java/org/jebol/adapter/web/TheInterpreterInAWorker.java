package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.SurfaceCall;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class TheInterpreterInAWorker {

    private static BrowserScreen screen;
    private static WebConsole console;
    private static WebFiles files;
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
        files = new WebFiles(TheBrowsersStore.forThePage());
        interpreter.useFileSystem(files);
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
                .granting(HostService.WORKING_DIRECTORY)
                .lacking(HostService.NETWORK)
                .lacking(HostService.PROCESSES)
                .lacking(HostService.ENVIRONMENT)
                .lacking(HostService.CERTIFICATES)
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
        return SurfaceCallsAsJson.asAString(name) + ":" + SurfaceCallsAsJson.asAString(value);
    }

    private static final class ThePageOnTheOtherSide implements BrowserScreen.Viewer {

        private boolean thePageHasSpoken;

        @Override
        public boolean isConnected() {
            return thePageHasSpoken;
        }

        @Override
        public void paint(List<SurfaceCall> painting) {
            sendToThePage("{"
                    + field("kind", "paint") + ","
                    + SurfaceCallsAsJson.asAString("picture") + ":"
                    + SurfaceCallsAsJson.written(painting,
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
                } else if (said.isAFile()) {
                    said.theFileName().ifPresent(name -> said.theFileText().ifPresent(text ->
                            putAFileThePageHandedOver(name, text)));
                } else {
                    said.asAnEvent().ifPresent(kind ->
                            said.carrying(kind).ifPresent(detail ->
                                    screen.theBrowserReports(kind, detail)));
                }
            }
        }

        private void putAFileThePageHandedOver(String name, String text) {
            try {
                String parent = name.contains("/")
                        ? name.substring(0, name.lastIndexOf('/'))
                        : "";
                if (!parent.isEmpty()) {
                    files.makeDirectory(parent, true);
                }
                files.write(name, text.getBytes(StandardCharsets.UTF_8));
            } catch (FilePort.Denied refused) {
                say("failed", "the page's file " + name + " would not go in: "
                        + refused.getMessage());
            }
        }
    }

    @JSBody(params = "message", script = "self.postMessage(message);")
    private static native void sendToThePage(String message);

    @JSBody(script = "return inbox.length > 0 ? inbox.shift() : null;")
    private static native String takeWhatThePageSent();
}
