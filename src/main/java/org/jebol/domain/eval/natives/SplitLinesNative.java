package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SplitLinesNative extends DefaultNative {

    private static final int LINE_FEED = '\n';

    private static final int CARRIAGE_RETURN = '\r';

    @Override
    public String nativeName() {
        return "split-lines";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("value", Set.of(StringValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue lines = BlockValue.of(linesOf((AnyStringValue) arguments.getFirst()));
            lines.putEachItemOnALine();
            return lines;
        };
    }

    private List<Value> linesOf(AnyStringValue text) {
        List<Value> lines = new ArrayList<>();
        int stopsAtTheLengthFromHereCountedFromTheHead = text.lengthFromHere();
        int start = text.index() - 1;
        int at = start;
        while (at < stopsAtTheLengthFromHereCountedFromTheHead) {
            int letter = readCodePointAfter(text, at);
            at++;
            if (endsLine(letter)) {
                lines.add(lineBetween(text, start, at - 1));
                at = skipALineFeedFollowing(text, letter, at);
                start = at;
            }
        }
        if (at > start) {
            lines.add(lineBetween(text, start, at));
        }
        return lines;
    }

    private boolean endsLine(int letter) {
        return letter == LINE_FEED || letter == CARRIAGE_RETURN;
    }

    private int skipALineFeedFollowing(AnyStringValue text, int ending, int at) {
        boolean aLineFeedFollows = at < text.storageLength() && readCodePointAfter(text, at) == LINE_FEED;
        return ending == CARRIAGE_RETURN && aLineFeedFollows ? at + 1 : at;
    }

    private int readCodePointAfter(AnyStringValue text, int charactersFromTheHead) {
        return text.storage().at(charactersFromTheHead + 1);
    }

    private Value lineBetween(AnyStringValue text, int from, int to) {
        StringBuilder line = new StringBuilder();
        for (int at = from; at < to; at++) {
            line.appendCodePoint(readCodePointAfter(text, at));
        }
        return StringValue.of(line.toString());
    }
}
