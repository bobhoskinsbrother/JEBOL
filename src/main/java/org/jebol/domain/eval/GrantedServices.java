package org.jebol.domain.eval;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

import java.util.Locale;
import java.util.Set;

public final class GrantedServices {

    private Set<HostService> granted = Set.of();

    public void grantOnly(Set<HostService> services) {
        this.granted = Set.copyOf(services);
    }

    public boolean allow(HostService service) {
        return granted.contains(service);
    }

    public void require(HostService service) {
        if (allow(service)) {
            return;
        }
        throw Raised.of(EvaluationFailure.NO_SERVICE,
                service.name().toLowerCase(Locale.ROOT) + " is "
                        + ServiceRefusal.NOT_GRANTED.name()
                                .toLowerCase(Locale.ROOT).replace('_', ' '));
    }
}
