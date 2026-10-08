package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;

final class WritingIntoText {

    private static final int GREATEST_CODEPOINT = 0x10FFFF;

    private static final int GREATEST_OCTET = 0xFF;

    private static final int WRITES_NOWHERE = 0;

    private final RebolSeries text;

    WritingIntoText(RebolSeries text) {
        this.text = text;
    }

    void write(IntegerValue selector, Value written) {
        int counted = (int) selector.magnitude();
        if (counted == WRITES_NOWHERE) {
            return;
        }
        int fromHere = counted < 0 ? counted + 1 : counted;
        int oneBased = text.index() + fromHere - 1;
        if (oneBased < 1 || oneBased > text.storageLength()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE);
        }
        int replacement = theCodepointOf(written);
        text.requireChangeable();
        switch (text) {
            case BinaryValue octets -> octets.storage().set(oneBased, replacement & GREATEST_OCTET);
            case AnyStringValue characters -> characters.storage().set(oneBased, replacement);
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        }
    }

    private int theCodepointOf(Value written) {
        return switch (written) {
            case CharacterValue(int codepoint) when codepoint > GREATEST_CODEPOINT ->
                    throw Raised.of(EvaluationFailure.BAD_PATH_SET);
            case CharacterValue(int codepoint) -> codepoint;
            case IntegerValue(long wanted) when wanted < 0 || wanted > GREATEST_CODEPOINT ->
                    throw Raised.of(EvaluationFailure.BAD_PATH_SET);
            case IntegerValue(long wanted) when text instanceof BinaryValue
                    && wanted > GREATEST_OCTET -> throw Raised.of(EvaluationFailure.OUT_OF_RANGE, written);
            case IntegerValue(long wanted) -> (int) wanted;
            case RebolSeries other when other instanceof AnyStringValue || other instanceof BinaryValue ->
                    theFirstCodepointOf(other);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH);
        };
    }

    private int theFirstCodepointOf(RebolSeries source) {
        if (source.atTail()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        }
        return switch (source) {
            case BinaryValue octets -> octets.storage().at(octets.index());
            case AnyStringValue characters -> characters.storage().at(characters.index());
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        };
    }
}
