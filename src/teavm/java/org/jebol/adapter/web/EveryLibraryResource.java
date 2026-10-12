package org.jebol.adapter.web;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("deprecation")
public class EveryLibraryResource implements ResourceSupplier {

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        try (InputStream listing =
                EveryLibraryResource.class.getResourceAsStream("/jebol-resources.txt")) {
            String names = new String(listing.readAllBytes(), StandardCharsets.UTF_8);
            return names.isEmpty() ? new String[0] : names.split("\n");
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
