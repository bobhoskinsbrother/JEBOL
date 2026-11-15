package org.jebol.adapter.web;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class EveryLibraryResource implements ResourceSupplier {

    private static final String CORE_LIST = "jebol-resources.txt";

    private static final String EXTENSION_LIST = "META-INF/jebol/web-resources.txt";

    @Override
    public String[] supplyResources(ResourceSupplierContext context) {
        try {
            List<String> names = new ArrayList<>();
            names.addAll(readNames(EveryLibraryResource.class.getResource("/" + CORE_LIST)));
            for (URL list : Collections.list(context.getClassLoader().getResources(EXTENSION_LIST))) {
                names.addAll(readNames(list));
            }
            return names.toArray(String[]::new);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static List<String> readNames(URL list) throws IOException {
        try (InputStream listing = list.openStream()) {
            String names = new String(listing.readAllBytes(), StandardCharsets.UTF_8);
            return names.isEmpty() ? List.of() : List.of(names.split("\n"));
        }
    }
}
