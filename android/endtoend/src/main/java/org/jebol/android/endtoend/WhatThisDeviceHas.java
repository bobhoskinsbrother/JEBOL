package org.jebol.android.endtoend;

import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.testing.Needs;
import org.jebol.testing.RequirementCheck;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WhatThisDeviceHas implements RequirementCheck {

    private static final String CHROME = "com.android.chrome";
    private static final int FIRST_CHROME_WITH_EXNREF = 137;
    private static final Pattern VERSION_NAME = Pattern.compile("versionName=(\\d+)\\.");
    private static final String MUST_BE_MET = "requirementsMustBeMet";

    @Override
    public Set<Needs> answers() {
        return EnumSet.of(Needs.CHROME_WITH_EXNREF);
    }

    @Override
    public Optional<String> shortfall(Needs need) {
        int chromes = readChromesMajorVersion();
        return chromes >= FIRST_CHROME_WITH_EXNREF
                ? Optional.empty()
                : Optional.of("this device's Chrome is " + (chromes == 0 ? "missing" : String.valueOf(chromes)));
    }

    @Override
    public boolean requirementsMustBeMet() {
        return Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString(MUST_BE_MET, "false"));
    }

    private static int readChromesMajorVersion() {
        ParcelFileDescriptor output = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("dumpsys package " + CHROME);
        try (FileInputStream reading = new ParcelFileDescriptor.AutoCloseInputStream(output)) {
            Matcher version = VERSION_NAME.matcher(new String(reading.readAllBytes(), StandardCharsets.UTF_8));
            return version.find() ? Integer.parseInt(version.group(1)) : 0;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
