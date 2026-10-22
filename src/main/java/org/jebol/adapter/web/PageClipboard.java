package org.jebol.adapter.web;

import org.jebol.domain.host.ClipboardPort;

import java.util.Optional;

public final class PageClipboard implements ClipboardPort {

    private final PageRoundTrip page;

    public PageClipboard(PageRoundTrip page) {
        this.page = page;
    }

    @Override
    public Optional<String> read() {
        PageAnswer answer = page.ask(new PageAsk("clipboard-read", true));
        if (answer.isDeclined()) {
            return Optional.empty();
        }
        requireNotRefused(answer);
        return Optional.of(answer.text("text").orElseThrow(() ->
                new Unreachable("the page read the clipboard and sent no text")));
    }

    @Override
    public boolean write(String text) {
        PageAnswer answer = page.ask(new PageAsk("clipboard-write", false).with("text", text));
        requireNotRefused(answer);
        return answer.isGiven();
    }

    private void requireNotRefused(PageAnswer answer) {
        if (answer.isRefused()) {
            throw new Unreachable(answer.why());
        }
    }
}
