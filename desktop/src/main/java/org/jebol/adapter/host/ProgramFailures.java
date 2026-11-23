package org.jebol.adapter.host;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProgramFailures {

    private static final Pattern SYSTEMS_REASON =
            Pattern.compile("(?:\\(([^()]*)\\)|error=\\d+, (.+?))\\s*$");

    private static final String FORMAT_MESSAGES_END = ".";

    private ProgramFailures() {
    }

    static String reasonIn(String exceptionMessage, boolean onWindows) {
        Matcher matcher = SYSTEMS_REASON.matcher(exceptionMessage);
        if (!matcher.find()) {
            return exceptionMessage;
        }
        if (matcher.group(1) != null) {
            return matcher.group(1);
        }
        String reason = matcher.group(2);
        return onWindows && !reason.endsWith(FORMAT_MESSAGES_END) ? reason + FORMAT_MESSAGES_END : reason;
    }
}
