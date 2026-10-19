package org.jebol.application;

import org.jebol.domain.eval.Postbox;

@FunctionalInterface
public interface StartExtension {

    void start(Postbox postbox);
}
