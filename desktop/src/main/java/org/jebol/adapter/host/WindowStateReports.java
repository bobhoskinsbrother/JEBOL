package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;

import java.awt.Frame;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

final class WindowStateReports {

    static final long MILLISECONDS_A_STATE_WAITS_FOR_ITS_RESIZE = 500;

    record Report(ScreenEventType kind, ScreenEventDetail detail) {
    }

    private record Size(int wide, int high) {
    }

    private final LongSupplier millisecondsNow;
    private Optional<Size> lastResize = Optional.empty();
    private long lastResizeAt = Long.MIN_VALUE;
    private Optional<ScreenEventType> awaitingResize = Optional.empty();
    private long awaitingSince;
    private boolean remapping;

    WindowStateReports(LongSupplier millisecondsNow) {
        this.millisecondsNow = millisecondsNow;
    }

    synchronized void beginRemap() {
        remapping = true;
        awaitingResize = Optional.empty();
    }

    synchronized List<Report> endRemap(int settledIn, int wide, int high) {
        remapping = false;
        if (isIconified(settledIn)) {
            return List.of();
        }
        return List.of(recordResize(new Size(wide, high)), said(kindSettledIn(settledIn)));
    }

    synchronized List<Report> resized(int wide, int high) {
        if (remapping) {
            return List.of();
        }
        Size now = new Size(wide, high);
        if (awaitingResize.isPresent()) {
            ScreenEventType kind = awaitingResize.get();
            awaitingResize = Optional.empty();
            return List.of(recordResize(now), said(kind));
        }
        if (lastResize.filter(now::equals).isPresent()) {
            return List.of();
        }
        return List.of(recordResize(now));
    }

    synchronized List<Report> stateChanged(int was, int now, int wide, int high) {
        if (remapping) {
            return List.of();
        }
        Optional<ScreenEventType> kind = findChangeBetween(was, now);
        if (kind.isEmpty()) {
            return List.of();
        }
        if (kind.get() == ScreenEventType.MINIMIZE) {
            awaitingResize = Optional.empty();
            return List.of(said(ScreenEventType.MINIMIZE));
        }
        if (isIconified(was)) {
            awaitingResize = Optional.empty();
            return List.of(recordResize(new Size(wide, high)), said(kind.get()));
        }
        awaitingResize = kind;
        awaitingSince = millisecondsNow.getAsLong();
        return List.of();
    }

    synchronized List<Report> takeOverdue(int wide, int high) {
        if (awaitingResize.isEmpty()) {
            return List.of();
        }
        ScreenEventType kind = awaitingResize.get();
        awaitingResize = Optional.empty();
        Size now = new Size(wide, high);
        if (lastResize.filter(now::equals).isPresent() && isResizeJustBeforeState()) {
            return List.of(said(kind));
        }
        return List.of(recordResize(now), said(kind));
    }

    private boolean isResizeJustBeforeState() {
        return awaitingSince - lastResizeAt <= MILLISECONDS_A_STATE_WAITS_FOR_ITS_RESIZE;
    }

    private Report recordResize(Size size) {
        lastResize = Optional.of(size);
        lastResizeAt = millisecondsNow.getAsLong();
        return new Report(ScreenEventType.RESIZE, new ScreenEventDetail.At(size.wide(), size.high()));
    }

    private static Report said(ScreenEventType kind) {
        return new Report(kind, new ScreenEventDetail.NothingMore());
    }

    private static ScreenEventType kindSettledIn(int state) {
        return isMaximized(state) ? ScreenEventType.MAXIMIZE : ScreenEventType.RESTORE;
    }

    private static Optional<ScreenEventType> findChangeBetween(int was, int now) {
        if (isIconified(now)) {
            return isIconified(was) ? Optional.empty() : Optional.of(ScreenEventType.MINIMIZE);
        }
        if (isMaximized(now) && (isIconified(was) || !isMaximized(was))) {
            return Optional.of(ScreenEventType.MAXIMIZE);
        }
        if (!isMaximized(now) && (isIconified(was) || isMaximized(was))) {
            return Optional.of(ScreenEventType.RESTORE);
        }
        return Optional.empty();
    }

    private static boolean isIconified(int state) {
        return (state & Frame.ICONIFIED) != 0;
    }

    private static boolean isMaximized(int state) {
        return (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }
}
