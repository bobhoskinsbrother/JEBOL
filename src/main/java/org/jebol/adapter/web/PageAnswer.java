package org.jebol.adapter.web;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

public final class PageAnswer {

    private static final String GIVEN = "given";
    private static final String DECLINED = "declined";
    private static final String REFUSED = "refused";
    private static final String DEFAULT_REFUSAL = "the page refused";

    private final EventFields fields;

    public PageAnswer(String posted) {
        this.fields = new EventFields(posted);
    }

    public Optional<Integer> answering() {
        return fields.readWholeNumber("ask");
    }

    public boolean isGiven() {
        return answerWord().filter(GIVEN::equals).isPresent();
    }

    public boolean isDeclined() {
        return answerWord().filter(DECLINED::equals).isPresent();
    }

    public boolean isRefused() {
        return !isGiven() && !isDeclined();
    }

    public String why() {
        if (answerWord().filter(REFUSED::equals).isPresent()) {
            return fields.text("why").orElse(DEFAULT_REFUSAL);
        }
        return answerWord()
                .map(word -> "the page answered " + word + ", which is not an answer this interpreter knows")
                .orElse("the page answered something this interpreter cannot read");
    }

    public Optional<String> text(String field) {
        return fields.text(field);
    }

    public Optional<Integer> wholeNumber(String field) {
        return fields.readWholeNumber(field);
    }

    public Optional<byte[]> octets(String field) {
        try {
            return fields.text(field).map(Base64.getDecoder()::decode);
        } catch (IllegalArgumentException notBase64) {
            return Optional.empty();
        }
    }

    public List<PageAnswer> listed(String field) {
        return fields.itemsIn(field).stream()
                .filter(item -> item.startsWith("{"))
                .map(PageAnswer::new)
                .toList();
    }

    public List<String> texts(String field) {
        return fields.itemsIn(field).stream()
                .flatMap(item -> fields.unquote(item).stream())
                .toList();
    }

    private Optional<String> answerWord() {
        return fields.text("answer");
    }
}
