package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.stream.Collectors;

public final class StringActions extends SeriesActions {

    private final StringValue text;

    public StringActions(StringValue text) {
        this.text = text;
    }

    @Override
    StringValue held() {
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
        return StringValue.of(items.stream()
                .map(Molder::form).collect(Collectors.joining()), text.datatype());
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
        StringValue held = (StringValue) RebolNativeWords.clampedToTail(text);
        int[] added = contributedBy(asked).codePoints().toArray();
        for (int at = 0; at < added.length; at++) {
            held.storage().insertAt(held.index() + at, added[at]);
        }
        return held.atIndex(held.index() + added.length);
    }

    static String contributedBy(Asked asked) {
        Value adding = asked.duplicated();
        String written = adding instanceof BlockValue added
                && added.datatype() == Datatype.BLOCK
                ? added.runTogether()
                : Molder.form(adding);
        return asked.howMuchOfIt()
                .map(count -> theFirstCodePointsOf(written, count.intValue()))
                .orElse(written);
    }

    static String theFirstCodePointsOf(String written, int wanted) {
        int taking = Math.min(wanted, written.codePointCount(0, written.length()));
        return written.substring(0, written.offsetByCodePoints(0, taking));
    }
}
