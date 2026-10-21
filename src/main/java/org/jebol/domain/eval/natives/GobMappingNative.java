package org.jebol.domain.eval.natives;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;

public abstract class GobMappingNative extends DefaultNative {

    protected static final int DEEPEST_GOB_WALK = 1000;

    protected Value mapInwards(GobValue from, PairValue point) {
        GobValue reached = from;
        double takenX = 0;
        double takenY = 0;
        for (int depth = 0; depth < DEEPEST_GOB_WALK; depth++) {
            Optional<GobValue> entered = findTheTopmostChildHolding(reached, point, takenX, takenY);
            if (entered.isEmpty()) {
                break;
            }
            reached = entered.get();
            takenX += reached.storage().offset().x();
            takenY += reached.storage().offset().y();
        }
        return pairGobWithPoint(reached, PairValue.of(point.x() - takenX, point.y() - takenY));
    }

    private Optional<GobValue> findTheTopmostChildHolding(
            GobValue parent, PairValue point, double takenX, double takenY) {

        for (int at = parent.storage().length(); at >= 1; at--) {
            if (parent.storage().readChildAt(at) instanceof GobValue child
                    && holds(child, point, takenX, takenY)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private boolean holds(GobValue child, PairValue point, double takenX, double takenY) {
        double left = takenX + child.storage().offset().x();
        double top = takenY + child.storage().offset().y();
        return point.x() >= left
                && point.x() < left + child.storage().size().x()
                && point.y() >= top
                && point.y() < top + child.storage().size().y();
    }

    protected Value pairGobWithPoint(GobValue reached, PairValue point) {
        return BlockValue.of(List.of(reached, point));
    }
}
