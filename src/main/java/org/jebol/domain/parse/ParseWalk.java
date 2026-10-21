package org.jebol.domain.parse;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Optional;

public interface ParseWalk {

    int NO_MATCH = -1;

    int position();

    void moveTo(int where);

    int inputLength();

    boolean atEnd();

    boolean advanceOne();

    Value current();

    Value inputPositionedHere();

    int matchOne(List<Value> rules, int at);

    boolean matchValue(Value rule);

    boolean matchesLiteral(Value wanted);

    int ruleSpan(List<Value> rules, int at);

    int repeat(List<Value> rules, int at, int leastNeeded);

    int optional(List<Value> rules, int at);

    int negate(List<Value> rules, int at);

    int giveUpNextAlternative(List<Value> rules, int at);

    int matchWithoutConsuming(List<Value> rules, int at);

    int mindCaseFromHereOn(boolean minding);

    ParseWalk walkOver(RebolSeries nested);

    boolean matchesWhole(AnyBlockValue rule);

    Value answerFor(AnyBlockValue rule);

    Value evaluateParen(AnyBlockValue paren);

    Value resolveWord(Value wanted);

    Value chooseValueToInsert(Value written);

    Value replacementFor(Value replacement);

    AnyWordValue findWordToWriteInto(List<Value> rules, int at);

    void assign(AnyWordValue word, Value value);

    Integer sameStorageOffset(Value item);

    Value sliceBetween(int from, int to);

    Value textSliceBetween(int from, int to);

    Value readFirstItemBetween(int from, int to);

    Value readFirstCharacterMatchedFrom(int before);

    Optional<Value> readWhatMatchedBetween(int from, int to);

    int findFirstMatchFrom(int[] needle, int from);

    String textFrom(Value value);

    void removeBetween(int from, int howMany);

    void putItemsIntoBlockAt(int where, List<Value> putting);

    int putValueIntoTextAt(int where, Value added);

    boolean hasNoCollectionOpen();

    void startCollecting();

    List<Value> stopCollecting();

    void deliverCollected(List<Value> mine);

    void keep(Value gathering);

    void keepEachBetween(int from, int to);

    void deliverCollectedTo(AnyWordValue target, List<Value> mine, boolean appending);
}
