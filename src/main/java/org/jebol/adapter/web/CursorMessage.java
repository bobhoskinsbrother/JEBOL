package org.jebol.adapter.web;

import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.SystemCursor;

import java.util.Base64;

record CursorMessage(ScreenCursor cursor) {

    String write() {
        return "{" + field("type", "cursor") + "," + switch (cursor) {
            case ScreenCursor.Hidden ignored -> field("css", "none");
            case ScreenCursor.FromSystem(SystemCursor system) -> field("css", cssNameFor(system));
            case ScreenCursor.Picture(var pixels) -> JsonConverter.asString("wide") + ":" + pixels.wide()
                    + "," + JsonConverter.asString("high") + ":" + pixels.high()
                    + "," + field("rgba", Base64.getEncoder().encodeToString(pixels.rgba()));
        } + "}";
    }

    private static String field(String name, String value) {
        return JsonConverter.asString(name) + ":" + JsonConverter.asString(value);
    }

    private static String cssNameFor(SystemCursor system) {
        return switch (system) {
            case ARROW -> "default";
            case I_SHAPE -> "text";
            case WAIT -> "wait";
            case CROSS -> "crosshair";
            case UP_ARROW -> "n-resize";
            case SIZE_NWSE -> "nwse-resize";
            case SIZE_NESW -> "nesw-resize";
            case SIZE_WE -> "ew-resize";
            case SIZE_NS -> "ns-resize";
            case SIZE_ALL -> "move";
            case NO -> "not-allowed";
            case HAND -> "pointer";
            case APP_START -> "progress";
            case HELP -> "help";
        };
    }
}
