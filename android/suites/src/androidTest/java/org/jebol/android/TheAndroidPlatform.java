package org.jebol.android;

import org.jebol.adapter.android.AndroidHost;
import org.jebol.adapter.host.PropertiesMachine;
import org.jebol.application.PlatformUnderTest;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.MachinePort;
import org.jebol.domain.host.ProcessPort;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TheAndroidPlatform implements PlatformUnderTest {

    private static final String SUITE_ENTRIES = "/not-on-a-device-in-rebols-suite.txt";

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
    public List<String> suiteEntriesItCannotRun() {
        try (InputStream listed = TheAndroidPlatform.class.getResourceAsStream(SUITE_ENTRIES)) {
            if (listed == null) {
                throw new IllegalStateException(SUITE_ENTRIES + " is not in the test APK");
            }
            return new String(listed.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Override
    public String toString() {
        return "Android";
    }
}
