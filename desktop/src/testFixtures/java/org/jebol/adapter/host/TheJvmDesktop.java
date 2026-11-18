package org.jebol.adapter.host;

import org.jebol.application.PlatformUnderTest;
import org.jebol.domain.host.HostNames;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.host.ProcessPort;

public final class TheJvmDesktop implements PlatformUnderTest {

    @Override
    public HostPorts ports() {
        return new JavaHost().ports();
    }

    @Override
    public MachinePort machine() {
        return new JavaMachine();
    }

    @Override
    public ProcessPort processes() {
        return new JavaProcess();
    }

    @Override
    public ImagePort images() {
        return new JavaImage();
    }

    @Override
    public HostNames hostNames() {
        return Uts46::toAsciiHostName;
    }

    @Override
    public String toString() {
        return "the JVM's desktop";
    }
}
