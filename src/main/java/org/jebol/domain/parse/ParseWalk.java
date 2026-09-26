package org.jebol.domain.parse;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

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

    int giveUpTheNextAlternative(List<Value> rules, int at);

    int matchWithoutConsuming(List<Value> rules, int at);

    int mindCaseFromHereOn(boolean minding);

    ParseWalk walkingOver(SeriesValue nested);

    boolean matchesTheWholeOf(BlockValue rule);

    Value answerFor(BlockValue rule);

    Value evaluateParen(BlockValue paren);

    Value whatTheWordHolds(Value wanted);

    Value theValueToInsert(Value written);

    Value replacementFor(Value replacement);

    WordValue theWordToWriteInto(List<Value> rules, int at);

    void assign(WordValue word, Value value);

    Integer sameStorageOffset(Value item);

    Value sliceBetween(int from, int to);

    Value textSliceBetween(int from, int to);

    Value firstItemBetween(int from, int to);

    Value firstCharacterMatchedFrom(int before);

    Optional<Value> whatMatchedBetween(int from, int to);

    int firstMatchFrom(int[] needle, int from);

    String theTextOf(Value value);

    void removeBetween(int from, int howMany);

    void putItemsIntoTheBlockAt(int where, List<Value> putting);

    int putValueIntoTheTextAt(int where, Value added);

    boolean noCollectionIsOpen();

    void startCollecting();

    List<Value> stopCollecting();

    void deliverTheCollected(List<Value> mine);

    void keep(Value gathering);

    void keepEachBetween(int from, int to);

    void deliverTheCollectedTo(WordValue target, List<Value> mine, boolean appending);
}
