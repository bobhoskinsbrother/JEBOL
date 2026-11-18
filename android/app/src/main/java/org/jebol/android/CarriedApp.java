package org.jebol.android;

import org.jebol.application.ListedFiles;
import org.jebol.domain.host.FilePort;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public record CarriedApp(List<String> files, String entry) {

    private static final String FOLDER = "/jebol-app/";

    public static CarriedApp read() {
        String described = new String(readResource("app.json"), StandardCharsets.UTF_8);
        try {
            JSONObject app = new JSONObject(described);
            JSONArray listed = app.getJSONArray("files");
            List<String> named = new ArrayList<>();
            for (int at = 0; at < listed.length(); at++) {
                named.add(listed.getString(at));
            }
            return new CarriedApp(List.copyOf(named), app.getString("entry"));
        } catch (JSONException unreadable) {
            throw new IllegalStateException("the APK's app.json is not one assembleSite would write: " + described,
                    unreadable);
        }
    }

    public FilePort asFiles() {
        return ListedFiles.of(files, CarriedApp::readResource);
    }

    public String readEntry() {
        return new String(readResource(entry), StandardCharsets.UTF_8);
    }

    private static byte[] readResource(String name) {
        try (InputStream carried = CarriedApp.class.getResourceAsStream(FOLDER + name)) {
            if (carried == null) {
                throw new IllegalStateException("the APK carries no " + FOLDER + name);
            }
            return carried.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
