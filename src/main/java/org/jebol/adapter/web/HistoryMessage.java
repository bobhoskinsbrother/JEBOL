package org.jebol.adapter.web;

import org.jebol.domain.host.HistoryEntry;

final class HistoryMessage {

    String navigate(String move, HistoryEntry entry) {
        return "{" + text("type", "navigate") + "," + text("move", move) + ","
                + index(entry.index()) + "," + text("address", entry.address()) + "}";
    }

    String guard(boolean unsavedWork) {
        return "{" + text("type", "guard") + "," + JsonConverter.asString("unsaved") + ":" + unsavedWork + "}";
    }

    String title(String shown) {
        return "{" + text("type", "title") + "," + text("text", shown) + "}";
    }

    private String text(String name, String value) {
        return JsonConverter.asString(name) + ":" + JsonConverter.asString(value);
    }

    private String index(int value) {
        return JsonConverter.asString("index") + ":" + value;
    }
}
