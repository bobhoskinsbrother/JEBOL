package org.jebol.devtools;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public record SigningKey(String storeFile, String storePassword, String keyAlias, String keyPassword) {

    public static final String FILE = "android-signing.properties";
    public static final List<String> FIELDS = List.of("storeFile", "storePassword", "keyAlias", "keyPassword");
    public static final String KEYTOOL_COMMAND = "keytool -genkeypair -v -keystore upload.jks -keyalg RSA "
            + "-keysize 2048 -validity 10000 -alias upload";

    public static String variableFor(String field) {
        return switch (field) {
            case "storeFile" -> "JEBOL_ANDROID_STORE_FILE";
            case "storePassword" -> "JEBOL_ANDROID_STORE_PASSWORD";
            case "keyAlias" -> "JEBOL_ANDROID_KEY_ALIAS";
            case "keyPassword" -> "JEBOL_ANDROID_KEY_PASSWORD";
            default -> throw new IllegalArgumentException(field);
        };
    }

    public static Optional<SigningKey> read(Path appDirectory, Map<String, String> environment) {
        Map<String, String> fields = new LinkedHashMap<>();
        Path file = appDirectory.resolve(FILE);
        if (Files.isRegularFile(file)) {
            Properties written = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                written.load(reader);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
            FIELDS.forEach(field -> Optional.ofNullable(written.getProperty(field)).ifPresent(value -> fields.put(field, value)));
        }
        FIELDS.forEach(field -> Optional.ofNullable(environment.get(variableFor(field)))
                .filter(value -> !value.isEmpty())
                .ifPresent(value -> fields.put(field, value)));
        if (!fields.keySet().containsAll(FIELDS)) {
            return Optional.empty();
        }
        Path store = appDirectory.resolve(fields.get("storeFile")).normalize();
        return Optional.of(new SigningKey(store.toString(), fields.get("storePassword"), fields.get("keyAlias"),
                fields.get("keyPassword")));
    }
}
