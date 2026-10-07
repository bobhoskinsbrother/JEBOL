package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

public class IsOpenAction extends ActorFirstPortAction {

    public IsOpenAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String name() {
        return "open?";
    }

    @Override
    Value answeredHere(PortValue port) {
        return ports.isOpen(port);
    }
}
