package org.jebol.adapter.host;

import org.jebol.application.Bounds;
import org.jebol.application.Extension;
import org.jebol.application.ExtensionProvider;
import org.jebol.application.ExtensionReport;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.Value;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class ExtensionDescriptors {

    private static final String DESCRIPTOR = "META-INF/jebol/extension.reb";

    private static final String JVM_SETUP = "jvm";

    private static final String UNNAMED = "extension";

    private static final String BAD_EXTENSION = "bad-extension";

    private final ClassLoader loader;

    public ExtensionDescriptors(ClassLoader loader) {
        this.loader = loader;
    }

    public Bounds addJvmSetupsTo(Bounds bounds) {
        Bounds added = bounds;
        for (URL descriptor : listDescriptors()) {
            added = addJvmSetup(added, readText(descriptor));
        }
        return added;
    }

    private List<URL> listDescriptors() {
        try {
            return Collections.list(loader.getResources(DESCRIPTOR));
        } catch (IOException unlisted) {
            throw new UncheckedIOException(unlisted);
        }
    }

    private static String readText(URL descriptor) {
        try (InputStream in = descriptor.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return "";
        }
    }

    private Bounds addJvmSetup(Bounds bounds, String descriptor) {
        Optional<List<Value>> fields = Transcoder.transcode(descriptor).values().map(AnyBlockValue::remaining);
        String name = fields.flatMap(read -> findWord(read, "name")).orElse(UNNAMED);
        Optional<Extension> extension = fields
                .flatMap(read -> findBlock(read, "setups"))
                .flatMap(setups -> findSetup(setups, JVM_SETUP))
                .flatMap(setup -> findWord(setup, "provider"))
                .flatMap(this::provide);
        return extension.map(bounds::add)
                .orElseGet(() -> bounds.refuse(new ExtensionReport(name, Optional.of(BAD_EXTENSION))));
    }

    private Optional<Extension> provide(String providerClass) {
        try {
            Class<?> found = Class.forName(providerClass, true, loader);
            if (!ExtensionProvider.class.isAssignableFrom(found)) {
                return Optional.empty();
            }
            return Optional.of(((ExtensionProvider) found.getDeclaredConstructor().newInstance()).provide());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unprovided) {
            return Optional.empty();
        }
    }

    private static Optional<String> findWord(List<Value> fields, String field) {
        return findField(fields, field)
                .filter(AnyWordValue.class::isInstance)
                .map(word -> ((AnyWordValue) word).spelling());
    }

    private static Optional<List<Value>> findBlock(List<Value> fields, String field) {
        return findField(fields, field)
                .filter(AnyBlockValue.class::isInstance)
                .map(block -> ((AnyBlockValue) block).remaining());
    }

    private static Optional<Value> findField(List<Value> fields, String field) {
        for (int at = 0; at + 1 < fields.size(); at++) {
            if (fields.get(at) instanceof SetWordValue named && named.canonical().equals(field)) {
                return Optional.of(fields.get(at + 1));
            }
        }
        return Optional.empty();
    }

    private static Optional<List<Value>> findSetup(List<Value> setups, String setup) {
        for (int at = 0; at + 1 < setups.size(); at++) {
            if (setups.get(at) instanceof AnyWordValue named && !(named instanceof SetWordValue)
                    && named.canonical().equals(setup) && setups.get(at + 1) instanceof AnyBlockValue body) {
                return Optional.of(body.remaining());
            }
        }
        return Optional.empty();
    }
}
