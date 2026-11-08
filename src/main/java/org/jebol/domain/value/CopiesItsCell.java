package org.jebol.domain.value;

interface CopiesItsCell {

    Value copyCell(boolean lineMarked);

    boolean isLineMarked();

    void markInPlace();
}
