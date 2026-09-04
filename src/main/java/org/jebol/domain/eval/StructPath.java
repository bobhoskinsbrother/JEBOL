package org.jebol.domain.eval;

import org.jebol.domain.value.*;

/**
 * Reading and writing a struct through a path.
 *
 * <p>{@code PD_Struct} takes a word and nothing else -- "struct allows only
 * named field access (so far)" is the C's own comment -- so a number selects
 * nothing and is a bad path rather than an index.
 *
 * <p>Depth needs no special case here. A field of struct type answers a value
 * sharing the parent's bytes, so {@code s/pos/x: 22} is an ordinary write into
 * the struct the first step handed back, and it lands in the parent because
 * there is only one run of bytes underneath both.
 */
public final class StructPath {

    private StructPath() {
    }

    public static Value read(StructValue struct, Value selector) {
        return struct.valueOf(fieldChosenBy(struct, selector));
    }

    public static void write(StructValue struct, Value selector, Value written) {
        try {
            struct.writeField(fieldChosenBy(struct, selector), written);
        } catch (StructLayoutRefused refused) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(written));
        }
    }

    private static StructSpec.StructField fieldChosenBy(StructValue struct, Value selector) {
        if (!(selector instanceof WordValue asked)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Molder.mold(selector));
        }
        return struct.fieldCalled(asked.spelling())
                .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                        asked.spelling()));
    }
}
