package org.jebol.domain.host;

import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class WindowsOnOneSurface {

    private Optional<GobValue> pressedIn = Optional.empty();
    private Optional<GobValue> keyboardWith = Optional.empty();

    private Function<GobValue, ShownWindow> shownAt = WindowsOnOneSurface::showWhereItIs;

    public List<ScreenEvent> address(ScreenEventType kind, ScreenEventDetail detail,
                                     List<GobValue> showingFromBottomToTop) {
        return address(kind, detail, showingFromBottomToTop, WindowsOnOneSurface::showWhereItIs);
    }

    public List<ScreenEvent> address(ScreenEventType kind, ScreenEventDetail detail,
                                     List<GobValue> showingFromBottomToTop,
                                     Function<GobValue, ShownWindow> whereShown) {
        shownAt = whereShown;
        forgetWindowsNoLongerShowing(showingFromBottomToTop);
        List<GobValue> heardBy = switch (kind) {
            case DOWN -> findWhoHearsPress(detail, showingFromBottomToTop);
            case MOVE, UP, SCROLL_LINE, SCROLL_PAGE ->
                    findWhoHearsPointer(kind, detail, showingFromBottomToTop);
            case KEY, KEY_UP, CONTROL, CONTROL_UP -> findWhoHearsKey(showingFromBottomToTop);
            case CLOSE -> showingFromBottomToTop;
            case RESIZE, OFFSET, BACK, FORWARD, MINIMIZE, MAXIMIZE, RESTORE -> List.of();
        };
        return makeEvents(heardBy, kind, detail);
    }

    private static ShownWindow showWhereItIs(GobValue window) {
        PairValue offset = window.storage().offset();
        PairValue size = window.storage().size();
        return new ShownWindow((int) Math.round(offset.x()), (int) Math.round(offset.y()), 1.0,
                (int) Math.round(size.x()), (int) Math.round(size.y()));
    }

    public void giveKeyboardTo(GobValue window) {
        keyboardWith = Optional.of(window);
    }

    private void forgetWindowsNoLongerShowing(List<GobValue> showing) {
        pressedIn = pressedIn.filter(window -> isShowing(window, showing));
        keyboardWith = keyboardWith.filter(window -> isShowing(window, showing));
    }

    private boolean isShowing(GobValue window, List<GobValue> showing) {
        return showing.stream().anyMatch(window::sharesStorageWith);
    }

    private List<GobValue> findWhoHearsPress(ScreenEventDetail detail, List<GobValue> showing) {
        Optional<GobValue> hit = findTopmostWindowUnder(detail, showing);
        if (hit.isPresent()) {
            pressedIn = hit;
            keyboardWith = hit;
        }
        return hit.stream().toList();
    }

    private List<GobValue> findWhoHearsPointer(ScreenEventType kind, ScreenEventDetail detail,
                                               List<GobValue> showing) {
        Optional<GobValue> heardBy = pressedIn.or(() -> findTopmostWindowUnder(detail, showing));
        if (kind == ScreenEventType.UP) {
            pressedIn = Optional.empty();
        }
        return heardBy.stream().toList();
    }

    private List<GobValue> findWhoHearsKey(List<GobValue> showing) {
        return keyboardWith.or(() -> findTopmostWindow(showing)).stream().toList();
    }

    private Optional<GobValue> findTopmostWindow(List<GobValue> showing) {
        return showing.isEmpty() ? Optional.empty() : Optional.of(showing.getLast());
    }

    private Optional<GobValue> findTopmostWindowUnder(ScreenEventDetail detail, List<GobValue> showing) {
        int across;
        int down;
        switch (detail) {
            case ScreenEventDetail.At(int atAcross, int atDown) -> {
                across = atAcross;
                down = atDown;
            }
            case ScreenEventDetail.Touched(int atAcross, int atDown) -> {
                across = atAcross;
                down = atDown;
            }
            case ScreenEventDetail.Scrolled(int atAcross, int atDown, int amount) -> {
                across = atAcross;
                down = atDown;
            }
            default -> {
                return Optional.empty();
            }
        }
        for (GobValue window : showing.reversed()) {
            if (covers(window, across, down)) {
                return Optional.of(window);
            }
        }
        return Optional.empty();
    }

    private boolean covers(GobValue window, int across, int down) {
        return shownAt.apply(window).covers(across, down);
    }

    private List<ScreenEvent> makeEvents(List<GobValue> heardBy, ScreenEventType kind, ScreenEventDetail detail) {
        List<ScreenEvent> addressed = new ArrayList<>();
        for (GobValue window : heardBy) {
            addressed.add(new ScreenEvent(kind, window, countFromWindow(detail, window)));
        }
        return addressed;
    }

    private ScreenEventDetail countFromWindow(ScreenEventDetail detail, GobValue window) {
        return switch (detail) {
            case ScreenEventDetail.At(int across, int down) -> {
                int[] within = shownAt.apply(window).mapBack(across, down);
                yield new ScreenEventDetail.At(within[0], within[1]);
            }
            case ScreenEventDetail.Touched(int across, int down) -> {
                int[] within = shownAt.apply(window).mapBack(across, down);
                yield new ScreenEventDetail.Touched(within[0], within[1]);
            }
            default -> detail;
        };
    }
}
