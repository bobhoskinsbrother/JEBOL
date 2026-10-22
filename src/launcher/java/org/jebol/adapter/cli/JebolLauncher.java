package org.jebol.adapter.cli;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

public final class JebolLauncher {

    static final int OLDEST_JAVA = 25;

    private static final String JAVA_VERSION_PROPERTY = "java.specification.version";
    private static final String CONSOLE = "org.jebol.adapter.cli.Repl";
    private static final int REFUSED = 1;

    private JebolLauncher() {
    }

    public static void main(String[] arguments) throws Throwable {
        Optional<String> refusal = new JavaVersionCheck(OLDEST_JAVA)
                .refusalFor(System.getProperty(JAVA_VERSION_PROPERTY));
        if (refusal.isPresent()) {
            System.err.println(refusal.get());
            System.exit(REFUSED);
        }
        try {
            Class.forName(CONSOLE).getDeclaredMethod("main", String[].class).invoke(null, (Object) arguments);
        } catch (InvocationTargetException failedInside) {
            throw failedInside.getCause();
        }
    }
}
