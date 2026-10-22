package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.Navigation;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;

import java.util.Locale;

public abstract class NavigationNative extends HostNative {

    private static final String NO_SERVICE = "no-service";
    private static final String NO_SCREEN = "this interpreter has no screen to keep a history on";

    protected NavigationNative(GrantedServices granted) {
        super(granted);
    }

    protected Navigation requireNavigation(Evaluator evaluator) {
        granted.require(HostService.NAVIGATION);
        ScreenPort screen = evaluator.ports().screen();
        if (!screen.hasDisplay()) {
            throw refuseAsNotPresent();
        }
        try {
            return screen.navigation();
        } catch (ScreenPort.Denied noHistory) {
            throw refuseAsNotPresent();
        }
    }

    private Raised refuseAsNotPresent() {
        String reason = NO_SCREEN + ", which is "
                + ServiceRefusal.NOT_PRESENT.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return new Raised(ErrorValue.of(ErrorCategory.ACCESS, NO_SERVICE, reason, StringValue.of(reason)));
    }
}
