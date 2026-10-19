package org.jebol.domain.eval;

import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.value.Construction;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Maker;
import org.jebol.domain.value.MapValue;

import java.util.Set;

public final class Boot {

    private final MachinePort machine;
    private final BootDeclarations bootDeclarations = new BootDeclarations();
    private final MapValue registeredStructLayouts = MapValue.empty();
    private final MakingWithoutEvaluating withoutEvaluating = new MakingWithoutEvaluating(registeredStructLayouts);
    private final Encodings encodings = new Encodings();
    private final GrantedServices grantedServices = new GrantedServices();
    private final Ports ports = new Ports(grantedServices);
    private final LocalFileSeparator localFileSeparator = new LocalFileSeparator();

    private Boot(MachinePort machine) {
        this.machine = machine;
        localFileSeparator.use(machine.fileSeparator());
    }

    public static Boot makeStandard(MachinePort machine) {
        return new Boot(machine);
    }

    public static Boot makeStandard(MachinePort machine, Set<HostService> granted) {
        Boot boot = makeStandard(machine);
        boot.grantOnly(granted);
        return boot;
    }

    public void useOperatorTable(String source) {
        bootDeclarations.useOperatorTable(source);
    }

    public void useErrorCatalogue(String source) {
        bootDeclarations.useErrorCatalogue(source);
    }

    public void useDatatypeSpecs(String source) {
        bootDeclarations.useDatatypeSpecs(source);
    }

    public void useModeTable(String source) {
        ports.useModeTable(source);
    }

    public void useFunctionDeclarations(String... sources) {
        bootDeclarations.useFunctionDeclarations(String.join("\n", sources));
    }

    public void grantOnly(Set<HostService> granted) {
        grantedServices.grantOnly(granted);
    }

    public void markOnlyUnavailable(Set<HostService> unavailable) {
        grantedServices.markOnlyUnavailable(unavailable);
    }

    public void useHostName(String name) {
        grantedServices.useHostName(name);
    }

    public SystemObject start() {
        LibContext lib = new LibContext(bootDeclarations, grantedServices, ports, encodings, localFileSeparator);
        return new SystemObject(lib, bootDeclarations, ports, encodings, registeredStructLayouts, machine);
    }

    public Construction construction() {
        return withoutEvaluating;
    }

    public Maker makeMakerFor(Evaluator evaluator, Context where) {
        return new InterpreterMaker(evaluator, where, withoutEvaluating);
    }
}
