package org.jebol.domain.eval.crypto;

public interface AKeyThatCanBeReleased {

    void release();

    boolean released();
}
