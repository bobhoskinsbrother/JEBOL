package org.jebol.adapter.android;

import org.jebol.domain.host.EnvironmentPort;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AndroidEnvironment implements EnvironmentPort {

    private static final String ROOT = "/";

    private static final String TEMPORARY_FOLDER = "/tmp";

    private static final String ENCODING = ".UTF-8";

    private static final String NO_LANGUAGE = "C";

    private final Map<String, String> variables = new LinkedHashMap<>();

    public AndroidEnvironment(String applicationId, Locale locale) {
        variables.put("HOME", ROOT);
        variables.put("PWD", ROOT);
        variables.put("TMPDIR", TEMPORARY_FOLDER);
        variables.put("LANG", posixLanguage(locale) + ENCODING);
        variables.put("USER", applicationId);
        variables.put("LOGNAME", applicationId);
    }

    private AndroidEnvironment(Map<String, String> copied) {
        variables.putAll(copied);
    }

    private static String posixLanguage(Locale locale) {
        if (locale.getLanguage().isEmpty()) {
            return NO_LANGUAGE;
        }
        return locale.getCountry().isEmpty() ? locale.getLanguage() : locale.getLanguage() + "_" + locale.getCountry();
    }

    @Override
    public String valueOf(String name) {
        return variables.get(name);
    }

    @Override
    public Map<String, String> readAll() {
        return Map.copyOf(variables);
    }

    @Override
    public void assign(String name, String value) {
        if (value == null) {
            variables.remove(name);
        } else {
            variables.put(name, value);
        }
    }

    public AndroidEnvironment copy() {
        return new AndroidEnvironment(variables);
    }
}
