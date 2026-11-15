package org.jebol.adapter.android;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.adapter.host.PropertiesMachine;
import org.jebol.application.ThreadPause;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.JvmDigests;

public final class AndroidHost {

    public HostPorts ports() {
        return HostPorts.none()
                .withCarriedFiles(new ClasspathFiles())
                .withDigests(new JvmDigests())
                .withMachine(new PropertiesMachine())
                .withPause(new ThreadPause());
    }
}
