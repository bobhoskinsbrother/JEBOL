package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.domain.host.HostService;

public final class ShippedPageBounds {

    public Bounds bounds() {
        return Bounds.standard()
                .withoutTimeLimit()
                .grant(HostService.CONSOLE)
                .grant(HostService.FILES)
                .grant(HostService.WORKING_DIRECTORY)
                .grant(HostService.CLOCK)
                .grant(HostService.WINDOWS)
                .grant(HostService.CLIPBOARD)
                .grant(HostService.MIDI)
                .grant(HostService.WEB)
                .markUnavailable(HostService.NETWORK)
                .markUnavailable(HostService.PROCESSES)
                .markUnavailable(HostService.ENVIRONMENT)
                .markUnavailable(HostService.CERTIFICATES)
                .calls("a browser");
    }
}
