package org.jebol.adapter.web;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public final class PageAsk {

    private static final int FIRST_NUMBER = 1;

    private static final Set<String> ENVELOPE = Set.of("type", "ask", "kind", "gesture");

    private final String kind;
    private final boolean needsGesture;
    private final List<String> fields;

    public PageAsk(String kind, boolean needsGesture) {
        this(kind, needsGesture, List.of());
    }

    private PageAsk(String kind, boolean needsGesture, List<String> fields) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("an ask names its kind");
        }
        this.kind = kind;
        this.needsGesture = needsGesture;
        this.fields = List.copyOf(fields);
    }

    public String kind() {
        return kind;
    }

    public boolean needsGesture() {
        return needsGesture;
    }

    public PageAsk with(String field, String text) {
        return withWritten(field, JsonConverter.asString(text));
    }

    public PageAsk with(String field, boolean flag) {
        return withWritten(field, Boolean.toString(flag));
    }

    public PageAsk with(String field, int number) {
        return withWritten(field, Integer.toString(number));
    }

    public PageAsk withOctets(String field, byte[] octets) {
        return with(field, Base64.getEncoder().encodeToString(octets));
    }

    public String writeNumbered(int number) {
        if (number < FIRST_NUMBER) {
            throw new IllegalArgumentException("an ask is numbered from one, and " + number + " is not");
        }
        StringBuilder written = new StringBuilder("{")
                .append(JsonConverter.asString("type")).append(':').append(JsonConverter.asString("ask"))
                .append(',').append(JsonConverter.asString("ask")).append(':').append(number)
                .append(',').append(JsonConverter.asString("kind")).append(':').append(JsonConverter.asString(kind))
                .append(',').append(JsonConverter.asString("gesture")).append(':').append(needsGesture);
        for (String field : fields) {
            written.append(',').append(field);
        }
        return written.append('}').toString();
    }

    private PageAsk withWritten(String field, String value) {
        if (field == null || field.isBlank() || ENVELOPE.contains(field)) {
            throw new IllegalArgumentException(
                    "an ask's field needs a name of its own, and " + field + " is not one");
        }
        List<String> grown = new ArrayList<>(fields);
        grown.add(JsonConverter.asString(field) + ":" + value);
        return new PageAsk(kind, needsGesture, grown);
    }
}
