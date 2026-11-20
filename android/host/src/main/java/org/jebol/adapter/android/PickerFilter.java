package org.jebol.adapter.android;

import android.webkit.MimeTypeMap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PickerFilter {

    public static final String EVERYTHING = "*/*";

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("\\*\\.([^*?/\\\\;.]+)");
    private static final Set<String> CATCH_ALL_PATTERNS = Set.of("*", "*.*");

    private PickerFilter() {
    }

    public static List<String> mimeTypes(List<String> filterPairs) {
        Set<String> types = new LinkedHashSet<>();
        for (int at = 1; at < filterPairs.size(); at += 2) {
            for (String pattern : filterPairs.get(at).split(";")) {
                Optional<String> type = mimeTypeFor(pattern.trim());
                if (type.isEmpty()) {
                    return List.of(EVERYTHING);
                }
                types.add(type.get());
            }
        }
        return types.isEmpty() ? List.of(EVERYTHING) : List.copyOf(types);
    }

    public static String mimeTypeForName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        return Optional.ofNullable(MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT)))
                .orElse("application/octet-stream");
    }

    private static Optional<String> mimeTypeFor(String pattern) {
        if (CATCH_ALL_PATTERNS.contains(pattern)) {
            return Optional.empty();
        }
        Matcher suffix = SUFFIX_PATTERN.matcher(pattern);
        if (!suffix.matches()) {
            return Optional.empty();
        }
        return Optional.ofNullable(MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(suffix.group(1).toLowerCase(Locale.ROOT)));
    }
}
