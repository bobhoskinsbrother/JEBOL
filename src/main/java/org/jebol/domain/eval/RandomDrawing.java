package org.jebol.domain.eval;

import org.jebol.domain.value.RandomDraw;

import java.util.List;

public abstract class RandomDrawing implements RandomDraw {

    @Override
    public int below(int limit) {
        return limit <= 0 ? 0 : (int) (Integer.toUnsignedLong((int) next()) % limit);
    }

    @Override
    public int belowWithoutNarrowing(int limit) {
        return limit <= 0 ? 0 : (int) (next() % limit);
    }

    @Override
    public <T> void shuffle(List<T> items) {
        for (int remaining = items.size(); remaining > 1;) {
            int chosen = below(remaining);
            remaining--;
            T held = items.get(chosen);
            items.set(chosen, items.get(remaining));
            items.set(remaining, held);
        }
    }
}
