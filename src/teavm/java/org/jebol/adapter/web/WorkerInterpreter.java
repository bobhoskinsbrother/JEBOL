package org.jebol.adapter.web;

import org.jebol.adapter.host.JavaHost;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.DrawingCommand;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class WorkerInterpreter {

    private static BrowserScreen screen;
    private static WebConsole console;
    private static WebFiles files;
    private static Interpreter interpreter;
    private static JSConsumer<String> theNextScriptToSend;

    public static void main(String[] args) {
        ThePageOnTheOtherSide thePage = new ThePageOnTheOtherSide();
        console = new WebConsole(thePage::takePostedLines);
        files = new WebFiles(BrowserStore.openForThePage());
        HostPorts ports = new JavaHost().ports()
                .withDigests(new BrowserDigests())
                .withConsole(console)
                .withFiles(files)
                .withHttp(new WebHttp());
        screen = BrowserScreen.showTo(thePage, ports.carriedFiles());
        interpreter = Interpreter.createOn(
                ports.withScreen(screen),
                WorkerInterpreter::asTheScriptPrints,
                boundAWorkerToTheScreen());
        say("booted", "");
        while (true) {
            String source = awaitAScriptFromThePage().await();
            say("finished", interpreter.display(interpreter.run(source)));
        }
    }

    private static JSPromise<String> awaitAScriptFromThePage() {
        return new JSPromise<>((handedOver, refused) -> theNextScriptToSend = handedOver);
    }

    private static Bounds boundAWorkerToTheScreen() {
        return Bounds.standard()
                .withoutTimeLimit()
                .grant(HostService.CONSOLE)
                .grant(HostService.FILES)
                .grant(HostService.WINDOWS)
                .grant(HostService.WEB)
                .grant(HostService.WORKING_DIRECTORY)
                .markUnavailable(HostService.NETWORK)
                .markUnavailable(HostService.PROCESSES)
                .markUnavailable(HostService.ENVIRONMENT)
                .markUnavailable(HostService.CERTIFICATES)
                .calls("a browser");
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
        sendToThePage("{" + field("type", kind) + "," + field("text", text) + "}");
    }

    private static String field(String name, String value) {
        return JsonConverter.asString(name) + ":" + JsonConverter.asString(value);
    }

    private static final class ThePageOnTheOtherSide implements BrowserScreen.Viewer {

        private boolean thePageHasSpoken;

        @Override
        public boolean isConnected() {
            return thePageHasSpoken;
        }

        @Override
        public void paint(List<DrawingCommand> painting) {
            sendToThePage("{"
                    + field("type", "paint") + ","
                    + JsonConverter.asString("picture") + ":"
                    + JsonConverter.write(painting,
                            (int) Math.round(screen.viewport().x()),
                            (int) Math.round(screen.viewport().y()))
                    + "}");
        }

        @Override
        public void takePostedLines() {
            for (String posted = takeWhatThePageSent(); posted != null; posted = takeWhatThePageSent()) {
                thePageHasSpoken = true;
                PageEvent said = new PageEvent(posted);
                if (said.isMeasurement()) {
                    if (said.isWholeMeasurement()) {
                        screen.measure(said.width(), said.height());
                    }
                } else if (said.isLine()) {
                    console.takeTypedLine(said.readLine());
                } else if (said.isFile()) {
                    said.readFileName().ifPresent(name -> said.readFileText().ifPresent(text ->
                            putAFileThePageHandedOver(name, text)));
                } else {
                    said.asEvent().ifPresent(kind ->
                            said.readDetails(kind).ifPresent(detail ->
                                    screen.report(kind, detail)));
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
