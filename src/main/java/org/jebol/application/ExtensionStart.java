package org.jebol.application;

import org.jebol.domain.eval.Postbox;

@FunctionalInterface
public interface ExtensionStart {

    void start(Postbox postbox);
}
