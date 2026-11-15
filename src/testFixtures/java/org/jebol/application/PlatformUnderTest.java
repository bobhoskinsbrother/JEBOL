package org.jebol.application;

import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.host.ProcessPort;

import java.util.List;
import java.util.ServiceLoader;

public interface PlatformUnderTest {

    HostPorts ports();

    MachinePort machine();

    ProcessPort processes();

    ImagePort images();

    static PlatformUnderTest current() {
        return TheOneFound.PLATFORM;
    }

    final class TheOneFound {

        private static final PlatformUnderTest PLATFORM = findExactlyOne();

        private TheOneFound() {
        }

        private static PlatformUnderTest findExactlyOne() {
            List<PlatformUnderTest> found = ServiceLoader.load(PlatformUnderTest.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .toList();
            if (found.size() != 1) {
                throw new IllegalStateException("a test run needs exactly one platform to build its interpreters on, "
                        + "named in META-INF/services/" + PlatformUnderTest.class.getName() + ", and found "
                        + found.size() + ": " + found);
            }
            return found.getFirst();
        }
    }
}
