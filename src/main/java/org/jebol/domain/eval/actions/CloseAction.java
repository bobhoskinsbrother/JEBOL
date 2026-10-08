package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

public class CloseAction extends ActorFirstPortAction {

    public CloseAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "close";
    }

    @Override
    Value answeredHere(PortValue port) {
        return ports.closed(port);
    }
}
