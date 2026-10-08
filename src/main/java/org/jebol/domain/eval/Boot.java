package org.jebol.domain.eval;

import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.host.HostService;
import org.jebol.domain.read.Construction;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Maker;
import org.jebol.domain.value.MapValue;

import java.util.Set;

public final class Boot {

    private String operatingSystemName = "JVM";
    private final BootDeclarations bootDeclarations = new BootDeclarations();
    private final MapValue registeredStructLayouts = MapValue.empty();
    private final MakingAndConverting makingAndConverting = new MakingAndConverting(registeredStructLayouts);
    private final Encodings encodings = new Encodings();
    private final GrantedServices grantedServices = new GrantedServices();
    private final Ports ports = new Ports(grantedServices);
    private final LocalFileSeparator localFileSeparator = new LocalFileSeparator();

    private Boot() {
    }

    public static Boot standard() {
        return new Boot();
    }

    public static Boot standard(Set<HostService> granted) {
        Boot boot = standard();
        boot.grantOnly(granted);
        return boot;
    }

    public void useOperatorTable(String source) {
        bootDeclarations.useOperatorTable(source);
    }

    public void useFileSeparator(char separator) {
        localFileSeparator.use(separator);
    }

    public void useOperatingSystemNamed(String operatingSystem) {
        this.operatingSystemName = operatingSystem;
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

    public SystemObject start() {
        LibContext lib = new LibContext(bootDeclarations, grantedServices, ports, encodings, localFileSeparator);
        return new SystemObject(lib, bootDeclarations, ports, encodings, registeredStructLayouts, operatingSystemName);
    }

    public Construction construction() {
        return makingAndConverting;
    }

    public Maker makerFor(Evaluator evaluator, Context where) {
        return new InterpreterMaker(evaluator, where, makingAndConverting);
    }
}
