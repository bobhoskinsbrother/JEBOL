package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;

public class UpdateAction extends ActorFirstPortAction {

    public UpdateAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "update";
    }

    @Override
    Value answeredHere(PortValue port) {
        return ports.updated(port);
    }
}
