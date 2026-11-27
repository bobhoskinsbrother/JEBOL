package org.jebol.android;

import org.jebol.adapter.android.AndroidHost;
import org.jebol.adapter.android.AndroidHostNames;
import org.jebol.adapter.android.AndroidImage;
import org.jebol.adapter.android.AndroidMachine;
import org.jebol.application.PlatformUnderTest;
import org.jebol.domain.host.HostNames;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.host.ProcessPort;

public final class TheAndroidPlatform implements PlatformUnderTest {

    private static final int EMULATED_SLOWNESS = 10;

    @Override
    public HostPorts ports() {
        return new AndroidHost().ports();
    }

    @Override
    public MachinePort machine() {
        return new AndroidMachine();
    }

    @Override
    public ProcessPort processes() {
        return ProcessPort.none();
    }

    @Override
    public ImagePort images() {
        return new AndroidImage();
    }

    @Override
    public HostNames hostNames() {
        return new AndroidHostNames();
    }

    @Override
    public int timeLimitFactor() {
        return EMULATED_SLOWNESS;
    }

    @Override
    public String toString() {
        return "Android";
    }
}
