package org.jebol.android;

import org.jebol.adapter.android.AndroidHost;
import org.jebol.adapter.host.PropertiesMachine;
import org.jebol.application.PlatformUnderTest;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.host.ProcessPort;

public final class TheAndroidPlatform implements PlatformUnderTest {

    @Override
    public HostPorts ports() {
        return new AndroidHost().ports();
    }

    @Override
    public MachinePort machine() {
        return new PropertiesMachine();
    }

    @Override
    public ProcessPort processes() {
        return ProcessPort.none();
    }

    @Override
    public ImagePort images() {
        return ImagePort.none();
    }

    @Override
    public String toString() {
        return "Android";
    }
}
