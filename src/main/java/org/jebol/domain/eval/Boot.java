package org.jebol.domain.eval;

import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.host.DigestPort;
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
    private final GrantedServices grantedServices = new GrantedServices();
    private final DigestPort digests;
    private final Encodings encodings;
    private final Ports ports;
    private final LocalFileSeparator localFileSeparator = new LocalFileSeparator();

    private Boot(MachinePort machine, DigestPort digests, BootSources sources) {
        this.machine = machine;
        this.digests = digests;
        this.encodings = new Encodings(digests);
        this.ports = new Ports(grantedServices, digests);
        localFileSeparator.use(machine.fileSeparator());
        bootDeclarations.useErrorCatalogue(sources.errorCatalogue());
        bootDeclarations.useDatatypeSpecs(sources.datatypeSpecs());
        bootDeclarations.useOperatorTable(sources.operatorTable());
        bootDeclarations.useFunctionDeclarations(String.join("\n", sources.functionDeclarations()));
        ports.useModeTable(sources.modeTable());
    }

    public static Boot makeStandard(MachinePort machine, DigestPort digests, BootSources sources) {
        return new Boot(machine, digests, sources);
    }

    public static Boot makeStandard(
            MachinePort machine, DigestPort digests, BootSources sources, Set<HostService> granted) {
        Boot boot = makeStandard(machine, digests, sources);
        boot.grantOnly(granted);
        return boot;
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
        LibContext lib = new LibContext(
                bootDeclarations, grantedServices, ports, encodings, digests, localFileSeparator);
        return new SystemObject(lib, bootDeclarations, ports, encodings, registeredStructLayouts, machine);
    }

    public Construction construction() {
        return withoutEvaluating;
    }

    public Maker makeMakerFor(Evaluator evaluator, Context where) {
        return new InterpreterMaker(evaluator, where, withoutEvaluating);
    }
}
