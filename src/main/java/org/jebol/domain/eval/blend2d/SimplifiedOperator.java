package org.jebol.domain.eval.blend2d;

import java.util.Optional;

import static org.jebol.domain.eval.blend2d.CompositingOperator.DESTINATION_ATOP;
import static org.jebol.domain.eval.blend2d.CompositingOperator.DESTINATION_IN;
import static org.jebol.domain.eval.blend2d.CompositingOperator.DESTINATION_OUT;
import static org.jebol.domain.eval.blend2d.CompositingOperator.DESTINATION_OVER;
import static org.jebol.domain.eval.blend2d.CompositingOperator.SOURCE_ATOP;
import static org.jebol.domain.eval.blend2d.CompositingOperator.SOURCE_COPY;
import static org.jebol.domain.eval.blend2d.CompositingOperator.SOURCE_IN;
import static org.jebol.domain.eval.blend2d.CompositingOperator.SOURCE_OUT;

record SimplifiedOperator(CompositingOperator operator, boolean replacesSource, int replacement,
                          boolean hasSourceAlpha) {

    private static final int TRANSPARENT = 0x00000000;

    enum SourceFormat {
        ZERO,
        OPAQUE,
        PREMULTIPLIED
    }

    static SourceFormat solidFormat(int argb) {
        if (argb == 0) {
            return SourceFormat.ZERO;
        }
        return Integer.compareUnsigned(argb, 0xFF000000) >= 0 ? SourceFormat.OPAQUE : SourceFormat.PREMULTIPLIED;
    }

    static Optional<SimplifiedOperator> forSolid(CompositingOperator operator, int argb) {
        return forFormat(operator, solidFormat(argb));
    }

    static Optional<SimplifiedOperator> forFormat(CompositingOperator operator, SourceFormat format) {
        boolean isZero = format == SourceFormat.ZERO;
        boolean isOpaque = format == SourceFormat.OPAQUE;
        return switch (operator) {
            case SOURCE_COPY -> simplifyTo(SOURCE_COPY);
            case SOURCE_OVER -> isZero ? drawNothing() : simplifyTo(isOpaque ? SOURCE_COPY : operator);
            case SOURCE_IN -> simplifyTo(SOURCE_IN);
            case SOURCE_OUT -> isZero ? simplifyToClear() : simplifyTo(SOURCE_OUT);
            case SOURCE_ATOP -> isZero ? drawNothing() : simplifyTo(isOpaque ? SOURCE_IN : SOURCE_ATOP);
            case DESTINATION_COPY -> drawNothing();
            case DESTINATION_OVER -> isZero ? drawNothing() : simplifyTo(DESTINATION_OVER);
            case DESTINATION_IN -> isZero ? simplifyTo(SOURCE_COPY) : isOpaque ? drawNothing() : simplifyTo(DESTINATION_IN);
            case DESTINATION_OUT -> isZero ? drawNothing() : isOpaque ? simplifyToClear() : simplifyTo(DESTINATION_OUT);
            case DESTINATION_ATOP -> isZero ? simplifyToClear() : simplifyTo(isOpaque ? DESTINATION_OVER : DESTINATION_ATOP);
            case XOR -> isZero ? drawNothing() : simplifyTo(isOpaque ? SOURCE_OUT : operator);
            case CLEAR -> simplifyToClear();
            case MODULATE -> isZero ? simplifyToClear() : simplifyTo(operator);
            case PLUS, MINUS, SCREEN, COLOR_DODGE, DIFFERENCE, EXCLUSION -> isZero ? drawNothing() : simplifyTo(operator);
            default -> isZero ? drawNothing() : Optional.of(new SimplifiedOperator(operator, false, 0, !isOpaque));
        };
    }

    private static Optional<SimplifiedOperator> simplifyTo(CompositingOperator operator) {
        return Optional.of(new SimplifiedOperator(operator, false, 0, true));
    }

    private static Optional<SimplifiedOperator> simplifyToClear() {
        return Optional.of(new SimplifiedOperator(SOURCE_COPY, true, TRANSPARENT, true));
    }

    private static Optional<SimplifiedOperator> drawNothing() {
        return Optional.empty();
    }
}
