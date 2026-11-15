package org.jebol.adapter.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class AScriptedPage implements PageRoundTrip {

    private final Deque<String> answers = new ArrayDeque<>();
    private final List<PageAsk> asked = new ArrayList<>();

    public AScriptedPage answering(String... posted) {
        answers.addAll(List.of(posted));
        return this;
    }

    @Override
    public PageAnswer ask(PageAsk ask) {
        asked.add(ask);
        String next = answers.poll();
        if (next == null) {
            throw new AssertionError("the page was asked " + ask.writeNumbered(asked.size())
                    + " and no answer was scripted");
        }
        return new PageAnswer(next);
    }

    public List<String> kindsAsked() {
        return asked.stream().map(PageAsk::kind).toList();
    }

    public String writeAsk(int numberFromOne) {
        return asked.get(numberFromOne - 1).writeNumbered(numberFromOne);
    }

    public String writeLastAsk() {
        return writeAsk(asked.size());
    }

    public boolean wasAskedNothing() {
        return asked.isEmpty();
    }

    public boolean hasAnswersLeft() {
        return !answers.isEmpty();
    }
}
