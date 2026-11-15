package org.jebol.adapter.web;

record TypingMessage(boolean held, String heading) {

    String write() {
        return "{" + JsonConverter.asString("type") + ":" + JsonConverter.asString("typing")
                + "," + JsonConverter.asString("held") + ":" + held
                + "," + JsonConverter.asString("heading") + ":" + JsonConverter.asString(heading) + "}";
    }
}
