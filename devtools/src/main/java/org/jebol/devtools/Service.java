package org.jebol.devtools;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum Service {
    NOTIFICATIONS(List.of("android.permission.POST_NOTIFICATIONS"), Optional.empty()),
    LOCATION(List.of("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"),
            Optional.of("NSLocationWhenInUseUsageDescription")),
    CAMERA(List.of("android.permission.CAMERA"), Optional.of("NSCameraUsageDescription"));

    private final List<String> androidPermissions;
    private final Optional<String> macUsageKey;

    Service(List<String> androidPermissions, Optional<String> macUsageKey) {
        this.androidPermissions = androidPermissions;
        this.macUsageKey = macUsageKey;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    public List<String> androidPermissions() {
        return androidPermissions;
    }

    public Optional<String> macUsageKey() {
        return macUsageKey;
    }

    public static Optional<Service> named(String word) {
        return Arrays.stream(values()).filter(service -> service.word().equals(word)).findFirst();
    }
}
