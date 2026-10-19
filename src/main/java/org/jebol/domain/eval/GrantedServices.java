package org.jebol.domain.eval;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

import java.util.Locale;
import java.util.Set;

public final class GrantedServices {

    private Set<HostService> granted = Set.of();

    private Set<HostService> unavailable = Set.of();

    private String hostName = "this host";

    public void grantOnly(Set<HostService> services) {
        this.granted = Set.copyOf(services);
    }

    public void markOnlyUnavailable(Set<HostService> services) {
        this.unavailable = Set.copyOf(services);
    }

    public void useHostName(String name) {
        this.hostName = name;
    }

    public boolean allow(HostService service) {
        return granted.contains(service);
    }

    public void require(HostService service) {
        if (allow(service)) {
            return;
        }
        throw Raised.of(EvaluationFailure.NO_SERVICE, explainWhyNot(service));
    }

    private String explainWhyNot(HostService service) {
        String serviceName = service.name().toLowerCase(Locale.ROOT);
        if (unavailable.contains(service)) {
            return hostName + " does not allow " + serviceName
                    + ", which is " + putIntoWords(ServiceRefusal.NOT_PRESENT);
        }
        return serviceName + " is " + putIntoWords(ServiceRefusal.NOT_GRANTED);
    }

    private static String putIntoWords(ServiceRefusal reason) {
        return reason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
