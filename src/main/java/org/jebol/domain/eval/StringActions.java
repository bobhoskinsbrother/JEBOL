package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.stream.Collectors;

public final class StringActions extends SeriesActions {

    private final AnyStringValue text;

    public StringActions(AnyStringValue text) {
        this.text = text;
    }

    @Override
    AnyStringValue held() {
        return text;
    }

    @Override
    public Value poked(Value position, Value written) {
        text.storage().set(pokedStoragePosition(position), codepointPokedFrom(written));
        return written;
    }

    private int codepointPokedFrom(Value written) {
        return switch (written) {
            case CharacterValue letter -> letter.codepoint();
            case IntegerValue number
                    when number.magnitude() >= 0
                    && number.magnitude() <= CharacterValue.MAXIMUM_CODEPOINT ->
                    (int) number.magnitude();
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "poke into a string takes a character or a codepoint, not a "
                            + written.datatype().literalSpelling());
        };
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        text.storage().removeAt(oneBasedIndex);
    }

    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        return text.holding(items.stream()
                .map(Molder::form).collect(Collectors.joining()));
    }

    @Override
    public Value cleared() {
        return clearedOneAtATime();
    }

    @Override
    public Value append(Asked asked) {
        contributedBy(asked).codePoints().forEach(text.storage()::append);
        return text.head();
    }

    @Override
    public Value insert(Asked asked) {
        AnyStringValue held = (AnyStringValue) text.clampedToTail();
        int[] added = contributedBy(asked).codePoints().toArray();
        for (int at = 0; at < added.length; at++) {
            held.storage().insertAt(held.index() + at, added[at]);
        }
        return held.atIndex(held.index() + added.length);
    }

    private String contributedBy(Asked asked) {
        Value adding = asked.duplicated();
        String written = adding instanceof BlockValue added
                ? added.runTogether()
                : Molder.form(adding);
        return asked.howMuchOfIt()
                .map(count -> theFirstCodePointsOf(written, count.intValue()))
                .orElse(written);
    }

    private String theFirstCodePointsOf(String written, int wanted) {
        int taking = Math.min(wanted, written.codePointCount(0, written.length()));
        return written.substring(0, written.offsetByCodePoints(0, taking));
    }
}
