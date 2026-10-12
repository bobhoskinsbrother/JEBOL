package org.jebol.adapter.web;

import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.value.EventCatalogue;

import java.util.Locale;
import java.util.Optional;

final class WhatThePageSaid {

    private final FieldsOfAPostedEvent said;

    WhatThePageSaid(String posted) {
        this.said = new FieldsOfAPostedEvent(posted);
    }

    boolean isAMeasurement() {
        return said.text("kind").filter("measure"::equals).isPresent();
    }

    boolean isAWholeMeasurement() {
        return said.wholeNumber("wide").isPresent() && said.wholeNumber("high").isPresent();
    }

    boolean isALine() {
        return said.text("kind").filter("line"::equals).isPresent();
    }

    String theLine() {
        return said.text("text").orElse("");
    }

    int wide() {
        return said.wholeNumber("wide").orElseThrow();
    }

    int high() {
        return said.wholeNumber("high").orElseThrow();
    }

    Optional<ScreenEventKind> asAnEvent() {
        return said.text("kind").flatMap(this::kindNamed);
    }

    Optional<ScreenEventDetail> carrying(ScreenEventKind kind) {
        return switch (kind) {
            case DOWN, UP, MOVE -> whereThePointerIs();
            case KEY, KEY_UP -> said.wholeNumber("code")
                    .filter(this::isACharacter)
                    .map(ScreenEventDetail.Typed::new);
            case CONTROL, CONTROL_UP -> said.text("named")
                    .filter(named -> EventCatalogue.keyIndexOf(named).isPresent())
                    .map(ScreenEventDetail.NamedKey::new);
            case CLOSE, RESIZE, OFFSET -> Optional.of(new ScreenEventDetail.NothingMore());
        };
    }

    private Optional<ScreenEventKind> kindNamed(String word) {
        for (ScreenEventKind kind : ScreenEventKind.values()) {
            if (kind.spelling().equals(word.toLowerCase(Locale.ROOT))) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private boolean isACharacter(int code) {
        return Character.isValidCodePoint(code)
                && (code < Character.MIN_SURROGATE || code > Character.MAX_SURROGATE);
    }

    private Optional<ScreenEventDetail> whereThePointerIs() {
        Optional<Integer> across = said.wholeNumber("across");
        Optional<Integer> down = said.wholeNumber("down");
        return across.isPresent() && down.isPresent()
                ? Optional.of(new ScreenEventDetail.At(across.get(), down.get()))
                : Optional.empty();
    }
}
