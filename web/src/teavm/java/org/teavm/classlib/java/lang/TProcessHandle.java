package org.teavm.classlib.java.lang;

import java.util.Optional;

public interface TProcessHandle {

    TProcessHandle CURRENT = new TProcessHandle() {
        @Override
        public long pid() {
            return 0L;
        }

        @Override
        public Info info() {
            return () -> Optional.empty();
        }

        @Override
        public boolean destroy() {
            return false;
        }

        @Override
        public boolean destroyForcibly() {
            return false;
        }
    };

    static TProcessHandle current() {
        return CURRENT;
    }

    static Optional<TProcessHandle> of(long pid) {
        return Optional.empty();
    }

    long pid();

    Info info();

    boolean destroy();

    boolean destroyForcibly();

    interface Info {
        Optional<String> command();
    }
}
