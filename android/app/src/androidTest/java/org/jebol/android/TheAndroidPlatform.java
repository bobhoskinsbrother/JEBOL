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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class TheAndroidPlatform implements PlatformUnderTest {

    private static final String SUITE_ENTRIES = "/not-on-a-device-in-rebols-suite.txt";

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
    public int timeLimitFactor() {
        return EMULATED_SLOWNESS;
    }

    @Override
    public String toString() {
        return "Android";
    }
}
