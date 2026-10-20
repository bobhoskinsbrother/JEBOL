package org.jebol.adapter.web;

import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.value.EventCatalogue;

import java.util.Locale;
import java.util.Optional;

final class PageEvent {

    private final EventFields eventFields;

    PageEvent(String posted) {
        this.eventFields = new EventFields(posted);
    }

    boolean isMeasurement() {
        return eventFields.text("type").filter("measure"::equals).isPresent();
    }

    boolean isWholeMeasurement() {
        return eventFields.readWholeNumber("wide").isPresent() && eventFields.readWholeNumber("high").isPresent();
    }

    boolean isLine() {
        return eventFields.text("type").filter("line"::equals).isPresent();
    }

    String readLine() {
        return eventFields.text("text").orElse("");
    }

    boolean isFile() {
        return eventFields.text("type").filter("file"::equals).isPresent();
    }

    Optional<String> readFileName() {
        return eventFields.text("path");
    }

    Optional<String> readFileText() {
        return eventFields.text("text");
    }

    int width() {
        return eventFields.readWholeNumber("wide").orElseThrow();
    }

    int height() {
        return eventFields.readWholeNumber("high").orElseThrow();
    }

    Optional<ScreenEventType> asEvent() {
        return eventFields.text("type").flatMap(this::findTypeNamed);
    }

    Optional<ScreenEventDetail> readDetails(ScreenEventType eventType) {
        return switch (eventType) {
            case DOWN, UP, MOVE -> pointerLocation();
            case KEY, KEY_UP -> eventFields.readWholeNumber("code")
                    .filter(this::isCharacter)
                    .map(ScreenEventDetail.Typed::new);
            case CONTROL, CONTROL_UP -> eventFields.text("named")
                    .filter(named -> EventCatalogue.findTheKeyIndexOf(named).isPresent())
                    .map(ScreenEventDetail.NamedKey::new);
            case CLOSE, RESIZE, OFFSET -> Optional.of(new ScreenEventDetail.NothingMore());
            case SCROLL_LINE, SCROLL_PAGE -> wheelTurn();
        };
    }

    private Optional<ScreenEventDetail> wheelTurn() {
        Optional<Integer> across = eventFields.readWholeNumber("across");
        Optional<Integer> down = eventFields.readWholeNumber("down");
        Optional<Integer> amount = eventFields.readWholeNumber("amount");
        return across.isPresent() && down.isPresent() && amount.isPresent()
                ? Optional.of(new ScreenEventDetail.Scrolled(across.get(), down.get(), amount.get()))
                : Optional.empty();
    }

    private Optional<ScreenEventType> findTypeNamed(String word) {
        for (ScreenEventType eventType : ScreenEventType.values()) {
            if (eventType.spelling().equals(word.toLowerCase(Locale.ROOT))) {
                return Optional.of(eventType);
            }
        }
        return Optional.empty();
    }

    private boolean isCharacter(int code) {
        return Character.isValidCodePoint(code)
                && (code < Character.MIN_SURROGATE || code > Character.MAX_SURROGATE);
    }

    private Optional<ScreenEventDetail> pointerLocation() {
        Optional<Integer> across = eventFields.readWholeNumber("across");
        Optional<Integer> down = eventFields.readWholeNumber("down");
        return across.isPresent() && down.isPresent()
                ? Optional.of(new ScreenEventDetail.At(across.get(), down.get()))
                : Optional.empty();
    }
}
