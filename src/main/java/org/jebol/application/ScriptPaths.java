package org.jebol.application;

import java.util.ArrayDeque;
import java.util.Deque;

final class ScriptPaths {

    private static final String ROOT = "/";

    private ScriptPaths() {
    }

    static String absolute(String workingDirectory, String path) {
        Deque<String> segments = new ArrayDeque<>();
        if (!path.startsWith(ROOT)) {
            addEach(workingDirectory, segments);
        }
        addEach(path, segments);
        String joined = ROOT + String.join(ROOT, segments);
        return path.endsWith(ROOT) && !joined.equals(ROOT) ? joined + ROOT : joined;
    }

    static String directory(String absolute) {
        return absolute.endsWith(ROOT) ? absolute : absolute + ROOT;
    }

    static String withoutTrailingSlash(String absolute) {
        return absolute.length() > 1 && absolute.endsWith(ROOT)
                ? absolute.substring(0, absolute.length() - 1)
                : absolute;
    }

    static String parent(String absolute) {
        String bare = withoutTrailingSlash(absolute);
        int lastSlash = bare.lastIndexOf('/');
        return lastSlash <= 0 ? ROOT : bare.substring(0, lastSlash) + ROOT;
    }

    static String lastName(String absolute) {
        String bare = withoutTrailingSlash(absolute);
        return bare.substring(bare.lastIndexOf('/') + 1);
    }

    static String relativeTo(String workingDirectory, String absolute) {
        String[] from = workingDirectory.substring(1).split(ROOT, -1);
        String[] to = absolute.substring(1).split(ROOT, -1);
        int fromCount = from.length - 1;
        int shared = 0;
        while (shared < fromCount && shared < to.length - 1 && from[shared].equals(to[shared])) {
            shared++;
        }
        StringBuilder relative = new StringBuilder();
        relative.append("../".repeat(fromCount - shared));
        for (int at = shared; at < to.length; at++) {
            relative.append(to[at]);
            if (at < to.length - 1) {
                relative.append(ROOT);
            }
        }
        return relative.toString();
    }

    private static void addEach(String path, Deque<String> segments) {
        for (String segment : path.split(ROOT)) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                segments.pollLast();
            } else {
                segments.addLast(segment);
            }
        }
    }
}
