package org.jebol.adapter.host;

import java.util.function.Supplier;

public interface TerminalLending {

    <T> T lendDuring(Supplier<T> program);

    static TerminalLending none() {
        return new TerminalLending() {
            @Override
            public <T> T lendDuring(Supplier<T> program) {
                return program.get();
            }
        };
    }
}
