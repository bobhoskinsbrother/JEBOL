package org.jebol.domain.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The compressions one interpreter knows: the three every build has, the optional ones this build carries, and those extensions have registered over either. */
public final class CompressionMethods {

    public static final List<String> BUILT_IN = List.of("deflate", "zlib", "gzip");

    private final Map<String, CompressionMethod> optional = new LinkedHashMap<>();

    private final Map<String, CompressionMethod> registered = new HashMap<>();

    public CompressionMethods() {
        this(OptionalCompressions.found());
    }

    public CompressionMethods(List<OptionalCompression> found) {
        found.stream()
                .sorted(Comparator.comparingInt(OptionalCompression::placeInRebolsOrder))
                .forEach(each -> optional.put(each.name(), each.method()));
    }

    public void register(String name, CompressionMethod method) {
        registered.put(name, method);
    }

    /** Puts back what the build itself serves under the name, as registering the built-in's own functions would. */
    public void restoreBuiltIn(String name) {
        registered.remove(name);
    }

    public Optional<CompressionMethod> find(String name) {
        return Optional.ofNullable(registered.getOrDefault(name, optional.get(name)));
    }

    public boolean isKnown(String name) {
        return BUILT_IN.contains(name) || optional.containsKey(name) || registered.containsKey(name);
    }

    public List<String> catalogue() {
        List<String> names = new ArrayList<>(BUILT_IN);
        names.addAll(optional.keySet());
        return List.copyOf(names);
    }
}
