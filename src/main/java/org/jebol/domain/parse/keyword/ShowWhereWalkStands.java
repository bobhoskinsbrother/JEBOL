package org.jebol.domain.parse.keyword;

import org.jebol.domain.parse.ParseWalk;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;

import java.util.List;

final class ShowWhereWalkStands extends SameForBlockAndString {

    private static final String END_OF_THE_RULES = "#(end)";
    private static final int LONGEST_RULE = 74;
    private static final int LONGEST_INPUT = 72;
    private static final char FIRST_PRINTABLE = ' ';

    ShowWhereWalkStands() {
        super("??", 0, false, WhenNothingFollowsIt.DOES_NOT_MATCH);
    }

    @Override
    int apply(ParseWalk walk, List<Value> rules, int at) {
        String rule = at + 1 < rules.size()
                ? Molder.moldWithin(rules.get(at + 1), LONGEST_RULE, Molder::moldFlat)
                : END_OF_THE_RULES;
        String input = Molder.moldWithin(walk.inputPositionedHere(), LONGEST_INPUT, Molder::moldFlat);
        walk.printLine(onOneLine(rule) + ": " + onOneLine(input));
        return 1;
    }

    private String onOneLine(String molded) {
        StringBuilder printable = new StringBuilder(molded.length());
        molded.chars().forEach(each -> printable.append(each < FIRST_PRINTABLE ? FIRST_PRINTABLE : (char) each));
        return printable.toString();
    }
}
