package org.jebol.domain.host;

import org.jebol.domain.render.Transform;
import org.jebol.domain.value.PairValue;

public record ShownWindow(int across, int down, double scale, int wide, int high) {

    public static ShownWindow of(PairValue space, PairValue size, PairValue offset, int raisedBy) {
        double spaceWide = space.x();
        double spaceHigh = space.y();
        double ownWide = size.x();
        double ownHigh = size.y();
        if (ownWide > spaceWide || ownHigh > spaceHigh) {
            double scale = Math.min(spaceWide / ownWide, spaceHigh / ownHigh);
            return new ShownWindow(0, -raisedBy, scale,
                    (int) Math.floor(ownWide * scale), (int) Math.floor(ownHigh * scale));
        }
        int across = (int) Math.round(Math.clamp(offset.x(), 0, spaceWide - ownWide));
        int down = (int) Math.round(Math.clamp(offset.y(), 0, spaceHigh - ownHigh));
        return new ShownWindow(across, down - raisedBy, 1.0,
                (int) Math.round(ownWide), (int) Math.round(ownHigh));
    }

    public boolean covers(int pointerAcross, int pointerDown) {
        return pointerAcross >= across && pointerAcross < across + wide
                && pointerDown >= down && pointerDown < down + high;
    }

    public int[] mapBack(int pointerAcross, int pointerDown) {
        return new int[] {
                (int) Math.round((pointerAcross - across) / scale),
                (int) Math.round((pointerDown - down) / scale)};
    }

    public Transform asTransform() {
        return Transform.moveBy(across, down).combineWith(Transform.scaleBy(scale, scale));
    }
}
