package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.PortRequest;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.natives.HostNative;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public abstract class PortAction extends HostNative implements ActionValue {

    protected final Ports ports;

    protected PortAction(GrantedServices granted, Ports ports) {
        super(granted);
        this.ports = ports;
    }

    protected PortRequest readRequest(List<Value> arguments, Set<String> refinements) {
        return new PortRequest(
                readTheArgumentOf("part", 0, arguments, refinements),
                readTheArgumentOf("seek", 0, arguments, refinements),
                refinements);
    }
}
