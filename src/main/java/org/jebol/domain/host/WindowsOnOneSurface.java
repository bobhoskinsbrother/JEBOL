package org.jebol.domain.host;

import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WindowsOnOneSurface {

    private Optional<GobValue> pressedIn = Optional.empty();
    private Optional<GobValue> keyboardWith = Optional.empty();

    public List<ScreenEvent> address(ScreenEventType kind, ScreenEventDetail detail,
                                     List<GobValue> showingFromBottomToTop) {
        forgetWindowsNoLongerShowing(showingFromBottomToTop);
        List<GobValue> heardBy = switch (kind) {
            case DOWN -> findWhoHearsAPress(detail, showingFromBottomToTop);
            case MOVE, UP -> findWhoHearsThePointer(kind, detail, showingFromBottomToTop);
            case KEY, KEY_UP, CONTROL, CONTROL_UP -> findWhoHearsAKey(showingFromBottomToTop);
            case CLOSE -> showingFromBottomToTop;
            case RESIZE, OFFSET -> List.of();
        };
        return makeTheEvents(heardBy, kind, detail);
    }

    private void forgetWindowsNoLongerShowing(List<GobValue> showing) {
        pressedIn = pressedIn.filter(window -> isShowing(window, showing));
        keyboardWith = keyboardWith.filter(window -> isShowing(window, showing));
    }

    private boolean isShowing(GobValue window, List<GobValue> showing) {
        return showing.stream().anyMatch(window::sharesStorageWith);
    }

    private List<GobValue> findWhoHearsAPress(ScreenEventDetail detail, List<GobValue> showing) {
        Optional<GobValue> hit = findTheTopmostWindowUnder(detail, showing);
        if (hit.isPresent()) {
            pressedIn = hit;
            keyboardWith = hit;
        }
        return hit.stream().toList();
    }

    private List<GobValue> findWhoHearsThePointer(ScreenEventType kind, ScreenEventDetail detail,
                                                  List<GobValue> showing) {
        Optional<GobValue> heardBy = pressedIn.or(() -> findTheTopmostWindowUnder(detail, showing));
        if (kind == ScreenEventType.UP) {
            pressedIn = Optional.empty();
        }
        return heardBy.stream().toList();
    }

    private List<GobValue> findWhoHearsAKey(List<GobValue> showing) {
        return keyboardWith.or(() -> findTheTopmostWindow(showing)).stream().toList();
    }

    private Optional<GobValue> findTheTopmostWindow(List<GobValue> showing) {
        return showing.isEmpty() ? Optional.empty() : Optional.of(showing.getLast());
    }

    private Optional<GobValue> findTheTopmostWindowUnder(ScreenEventDetail detail, List<GobValue> showing) {
        if (!(detail instanceof ScreenEventDetail.At(int across, int down))) {
            return Optional.empty();
        }
        for (GobValue window : showing.reversed()) {
            if (covers(window, across, down)) {
                return Optional.of(window);
            }
        }
        return Optional.empty();
    }

    private boolean covers(GobValue window, int across, int down) {
        PairValue place = window.storage().offset();
        PairValue size = window.storage().size();
        return across >= place.x() && across < place.x() + size.x()
                && down >= place.y() && down < place.y() + size.y();
    }

    private List<ScreenEvent> makeTheEvents(List<GobValue> heardBy, ScreenEventType kind, ScreenEventDetail detail) {
        List<ScreenEvent> addressed = new ArrayList<>();
        for (GobValue window : heardBy) {
            addressed.add(new ScreenEvent(kind, window, countFromTheWindow(detail, window)));
        }
        return addressed;
    }

    private ScreenEventDetail countFromTheWindow(ScreenEventDetail detail, GobValue window) {
        if (!(detail instanceof ScreenEventDetail.At(int across, int down))) {
            return detail;
        }
        PairValue place = window.storage().offset();
        return new ScreenEventDetail.At(
                across - (int) Math.round(place.x()), down - (int) Math.round(place.y()));
    }
}
