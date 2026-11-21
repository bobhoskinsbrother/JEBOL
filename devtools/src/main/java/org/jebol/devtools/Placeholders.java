package org.jebol.devtools;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Placeholders {

    public enum Escaping {
        PLAIN,
        JSON,
        XML
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{jebol\\.([A-Za-z]+)}");

    private final Map<String, String> values;
    private final Set<String> written;

    private Placeholders(Map<String, String> values, Set<String> written) {
        this.values = values;
        this.written = written;
    }

    public static Placeholders of(AppDeclaration declaration) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", declaration.title());
        values.put("identity", declaration.identity());
        values.put("version", declaration.version());
        values.put("build", Integer.toString(declaration.build()));
        values.put("summary", declaration.summary());
        values.put("publisher", declaration.publisher());
        values.put("contact", declaration.contact());
        values.put("website", declaration.website().orElse(""));
        values.put("category", declaration.category().word());
        values.put("linuxCategory", declaration.category().freedesktopCategory());
        values.put("macCategory", declaration.category().macCategory());
        values.put("colour", declaration.colour().hex());
        values.put("entry", declaration.entry());
        values.put("package", packageName(declaration));
        values.put("launcher", launcherName(declaration));
        return new Placeholders(values, Set.of());
    }

    public static String packageName(AppDeclaration declaration) {
        return declaration.identity().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String launcherName(AppDeclaration declaration) {
        String identity = declaration.identity();
        return identity.substring(identity.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public Placeholders withWritten(String name, String alreadyEscaped) {
        Map<String, String> more = new LinkedHashMap<>(values);
        more.put(name, alreadyEscaped);
        Set<String> moreWritten = new HashSet<>(written);
        moreWritten.add(name);
        return new Placeholders(more, Set.copyOf(moreWritten));
    }

    public static Escaping escapingFor(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".json") || name.endsWith(".webmanifest")) {
            return Escaping.JSON;
        }
        if (name.endsWith(".xml") || name.endsWith(".plist") || name.endsWith(".html") || name.endsWith(".wxs")) {
            return Escaping.XML;
        }
        return Escaping.PLAIN;
    }

    public String fill(String text, Escaping escaping) {
        Matcher found = PLACEHOLDER.matcher(text);
        StringBuilder filled = new StringBuilder();
        while (found.find()) {
            String name = found.group(1);
            String value = values.get(name);
            String replacement = value == null
                    ? found.group()
                    : written.contains(name) ? value : escape(value, escaping);
            found.appendReplacement(filled, Matcher.quoteReplacement(replacement));
        }
        found.appendTail(filled);
        return filled.toString();
    }

    private static String escape(String value, Escaping escaping) {
        return switch (escaping) {
            case JSON -> Json.escape(value);
            case XML -> value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;");
            case PLAIN -> value.replace('\n', ' ');
        };
    }
}
