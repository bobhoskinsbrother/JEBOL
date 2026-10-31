package org.jebol.domain.eval.blend2d;

interface MonoCurve {

    double firstX();

    double firstY();

    double lastX();

    double lastY();

    double midX();

    double midY();

    boolean isLeftToRight();

    void boundLeftToRight();

    void boundRightToLeft();

    boolean isFlat();

    void split();

    boolean canPush();

    void push();

    boolean canPop();

    void pop();

    void discardAndAdvance();
}
