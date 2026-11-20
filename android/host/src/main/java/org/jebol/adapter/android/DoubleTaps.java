package org.jebol.adapter.android;

import android.content.Context;
import android.view.ViewConfiguration;

public final class DoubleTaps {

    private final long timeoutMilliseconds;
    private final float touchSlop;
    private final float doubleTapSlop;

    private boolean pressedTwice;
    private boolean stayedInPlace;
    private boolean lastWasATap;
    private long liftedAt;
    private float pressedAcross;
    private float pressedDown;

    public DoubleTaps(long timeoutMilliseconds, float touchSlop, float doubleTapSlop) {
        this.timeoutMilliseconds = timeoutMilliseconds;
        this.touchSlop = touchSlop;
        this.doubleTapSlop = doubleTapSlop;
    }

    public static DoubleTaps from(Context context) {
        ViewConfiguration configuration = ViewConfiguration.get(context);
        return new DoubleTaps(ViewConfiguration.getDoubleTapTimeout(),
                configuration.getScaledTouchSlop(), configuration.getScaledDoubleTapSlop());
    }

    public void press(long at, float across, float down) {
        pressedTwice = lastWasATap && !pressedTwice
                && at - liftedAt <= timeoutMilliseconds
                && isCloserThan(across - pressedAcross, down - pressedDown, doubleTapSlop);
        pressedAcross = across;
        pressedDown = down;
        stayedInPlace = true;
        lastWasATap = false;
    }

    public void move(float across, float down) {
        if (!isWithin(across - pressedAcross, down - pressedDown, touchSlop)) {
            stayedInPlace = false;
        }
    }

    public void lift(long at) {
        liftedAt = at;
        lastWasATap = stayedInPlace;
    }

    public boolean wasPressedTwice() {
        return pressedTwice;
    }

    private static boolean isWithin(float across, float down, float slop) {
        return across * across + down * down <= slop * slop;
    }

    private static boolean isCloserThan(float across, float down, float slop) {
        return across * across + down * down < slop * slop;
    }
}
