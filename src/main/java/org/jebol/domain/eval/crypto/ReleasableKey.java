package org.jebol.domain.eval.crypto;

public interface ReleasableKey {

    void release();

    boolean released();
}
