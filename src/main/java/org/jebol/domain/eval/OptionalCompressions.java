package org.jebol.domain.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class OptionalCompressions {

    private OptionalCompressions() {
    }

    private static final class FoundOncePerProcess {
        static final List<OptionalCompression> FOUND = findOnClasspath();
    }

    public static List<OptionalCompression> found() {
        return FoundOncePerProcess.FOUND;
    }

    private static List<OptionalCompression> findOnClasspath() {
        List<OptionalCompression> found = new ArrayList<>();
        for (OptionalCompression each : ServiceLoader.load(OptionalCompression.class)) {
            found.add(each);
        }
        return List.copyOf(found);
    }
}
