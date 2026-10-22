package org.jebol.adapter.web;

import org.jebol.adapter.host.JavaHost;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.render.DrawingCommand;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.function.JSConsumer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

final class WorkerSession implements BrowserScreen.Viewer {

    private static final String PICK_FOLDER = "/picked/";

    private final PickedFiles files;
    private final PageMidiDevices midi;
    private final BrowserScreen screen;
    private final Interpreter interpreter;
    private Optional<ScriptHandOver> waitingForScript = Optional.empty();
    private boolean pageHasSpoken;

    WorkerSession() {
        WorkerRoundTrip page = new WorkerRoundTrip();
        files = new PickedFiles(new WebFiles(BrowserStore.openForPage()), PICK_FOLDER, page);
        midi = new PageMidiDevices(page, this::takePostedLines);
        HostPorts ports = new JavaHost().ports()
                .withDigests(new BrowserDigests())
                .withConsole(new WebConsole())
                .withFiles(files)
                .withHttp(new WebHttp())
                .withWindows(new PageWindows(page, files))
                .withClipboard(new PageClipboard(page))
                .withMidiDevices(midi)
                .withImages(new BrowserImageCodec(new BrowserImages()));
        screen = BrowserScreen.showTo(this, ports.carriedFiles());
        interpreter = Interpreter.createOn(ports.withScreen(screen), this::relayPrinted,
                new ShippedPageBounds().bounds());
    }

    void serve() {
        say("booted", "");
        while (true) {
            String source = awaitScript().await();
            say("finished", interpreter.display(interpreter.run(source)));
        }
    }

    void handOver(String source) {
        Optional<ScriptHandOver> waiting = waitingForScript;
        waitingForScript = Optional.empty();
        waiting.ifPresentOrElse(
                handOver -> handOver.resolve().accept(source),
                () -> say("failed", "a script is already running in this worker"));
    }

    @Override
    public boolean isConnected() {
        takePostedLines();
        return pageHasSpoken;
    }

    @Override
    public void paint(List<DrawingCommand> painting) {
        postToPage("{" + field("type", "paint") + ","
                + JsonConverter.asString("picture") + ":"
                + JsonConverter.write(painting,
                        (int) Math.round(screen.viewport().x()),
                        (int) Math.round(screen.viewport().y()))
                + "}");
    }

    @Override
    public void takeTyping(String heading) {
        postToPage(new TypingMessage(true, heading).write());
    }

    @Override
    public void releaseTyping() {
        postToPage(new TypingMessage(false, "").write());
    }

    @Override
    public void takePostedLines() {
        for (String posted = takeInbox(); posted != null; posted = takeInbox()) {
            pageHasSpoken = true;
            route(new PageEvent(posted));
        }
    }

    private JSPromise<String> awaitScript() {
        return new JSPromise<>((handedOver, refused) ->
                waitingForScript = Optional.of(new ScriptHandOver(handedOver)));
    }

    private record ScriptHandOver(JSConsumer<String> resolve) {
    }

    private void route(PageEvent said) {
        if (said.isMeasurement()) {
            if (said.isWholeMeasurement()) {
                screen.measure(said.width(), said.height());
            }
        } else if (said.isFile()) {
            said.readFileName().ifPresent(name -> said.readFileText().ifPresent(text -> storeHandedFile(name, text)));
        } else if (said.isMidiArrival()) {
            said.readConnection().ifPresent(connection ->
                    said.readOctets().ifPresent(octets -> midi.takeArrival(connection, octets)));
        } else {
            said.asEvent().ifPresent(kind -> said.readDetails(kind).ifPresent(detail -> screen.report(kind, detail)));
        }
    }

    private void storeHandedFile(String name, String text) {
        try {
            int lastSlash = name.lastIndexOf('/');
            if (lastSlash > 0) {
                files.makeDirectory(name.substring(0, lastSlash), true);
            }
            files.write(name, text.getBytes(StandardCharsets.UTF_8));
        } catch (FilePort.Denied refused) {
            say("failed", "the page's file " + name + " would not go in: " + refused.getMessage());
        }
    }

    private void relayPrinted(String text) {
        say("printed", text);
    }

    private void say(String kind, String text) {
        postToPage("{" + field("type", kind) + "," + field("text", text) + "}");
    }

    private String field(String name, String value) {
        return JsonConverter.asString(name) + ":" + JsonConverter.asString(value);
    }

    @JSBody(params = "message", script = "self.postMessage(message);")
    private static native void postToPage(String message);

    @JSBody(script = "return inbox.length > 0 ? inbox.shift() : null;")
    private static native String takeInbox();
}
