package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.GobText;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

public abstract class TextMeasuringNative extends ScreenNative {

    protected TextMeasuringNative(GrantedServices granted) {
        super(granted);
    }

    protected GobText readTheTextOf(GobValue gob, Evaluator evaluator) {
        granted.require(HostService.WINDOWS);
        return askThroughTheScreen(() -> new GobText(gob, evaluator.screen().textMeasurement()));
    }

    protected PairValue roundToWholePixels(double across, double down) {
        return PairValue.of(Math.round(across), Math.round(down));
    }
}
