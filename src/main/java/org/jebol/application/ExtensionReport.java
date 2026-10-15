package org.jebol.application;

import java.util.Optional;

public record ExtensionReport(String name, Optional<String> refusal) {

    public boolean installed() {
        return refusal.isEmpty();
    }
}
