package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParseVocabularyIsOneListFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("NOT succeeds where the rule fails, over a block, consuming nothing")
    void notSucceedsWhereTheRuleFailsOverABlock() {
        assertThat(answerTo("""
                parse [a] [not integer! 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NOT fails where the rule matches, over a block")
    void notFailsWhereTheRuleMatchesOverABlock() {
        assertThat(answerTo("""
                parse [1] [not integer! integer!]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("NOT succeeds where the rule fails, over a string, consuming nothing")
    void notSucceedsWhereTheRuleFailsOverAString() {
        assertThat(answerTo("""
                parse "a" [not #"x" #"a"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NOT fails where the rule matches, over a string")
    void notFailsWhereTheRuleMatchesOverAString() {
        assertThat(answerTo("""
                parse "a" [not #"a" #"a"]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("NOT NOT succeeds over a block even when the rule it flags fails")
    void doubledNotSucceedsEvenWhenTheRuleFailsOverABlock() {
        assertThat(answerTo("""
                parse [1] [not not word! integer!]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NOT NOT succeeds over a string even when the rule it flags fails")
    void doubledNotSucceedsEvenWhenTheRuleFailsOverAString() {
        assertThat(answerTo("""
                parse "a" [not not #"x" #"a"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NOT flags the whole counted rule after it, over a block")
    void notFlagsACountedRuleOverABlock() {
        assertThat(answerTo("""
                parse [a] [not 2 'a 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NOT flags the whole counted rule after it, over a string")
    void notFlagsACountedRuleOverAString() {
        assertThat(answerTo("""
                parse "a" [not 2 #"a" #"a"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("A bare NOT answers false over a block rather than raising")
    void aBareNotIsFalseOverABlock() {
        assertThat(answerTo("""
                parse [a] [not]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("A bare NOT answers false over a string rather than raising")
    void aBareNotIsFalseOverAString() {
        assertThat(answerTo("""
                parse "a" [not]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("NOT before a count with no rule to repeat raises parse-end")
    void notWithACountAndNoRuleRaisesParseEnd() {
        assertThat(answerTo("""
                e: try [parse [a] [not 1]] e/id = 'parse-end""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THEN gives up the remaining alternatives over a block")
    void thenGivesUpTheAlternativeOverABlock() {
        assertThat(answerTo("""
                parse [a] ['a then 'x | 'a]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("Without THEN the alternative is tried over a block")
    void withoutThenTheAlternativeIsTriedOverABlock() {
        assertThat(answerTo("""
                parse [a] ['a 'x | 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THEN gives up the remaining alternatives over a string")
    void thenGivesUpTheAlternativeOverAString() {
        assertThat(answerTo("""
                parse "a" [#"a" then #"x" | #"a"]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("Without THEN the alternative is tried over a string")
    void withoutThenTheAlternativeIsTriedOverAString() {
        assertThat(answerTo("""
                parse "a" [#"a" #"x" | #"a"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THEN does nothing when the rule after it matches, over a block")
    void thenDoesNothingWhenTheRuleAfterItMatchesOverABlock() {
        assertThat(answerTo("""
                parse [a a] ['a then 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THEN does nothing when the rule after it matches, over a string")
    void thenDoesNothingWhenTheRuleAfterItMatchesOverAString() {
        assertThat(answerTo("""
                parse "aa" [#"a" then #"a"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THEN commits only the block it is written in")
    void thenCommitsOnlyTheBlockItIsIn() {
        assertThat(answerTo("""
                parse [a] [['a then 'x | 'a] | 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("A bare THEN answers false rather than raising")
    void aBareThenIsFalse() {
        assertThat(answerTo("""
                parse [a] [then]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("BREAK stops a repeat and the repeat succeeds, over a block")
    void breakStopsARepeatOverABlock() {
        assertThat(answerTo("""
                parse [a a] [some ['a break] 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("BREAK stops a repeat and the repeat succeeds, over a string")
    void breakStopsARepeatOverAString() {
        assertThat(answerTo("""
                parse "aa" [some [#"a" break] #"a"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("BREAK ends the block it is in, over a block that is not repeated")
    void breakEndsAPlainBlockOverABlock() {
        assertThat(answerTo("""
                parse [a a] [['a break] 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("A BREAK that leaves input over is still a failed parse")
    void breakThatLeavesInputOverIsAFailure() {
        assertThat(answerTo("""
                parse [a a] [some ['a break]]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("A bare BREAK matches empty block input")
    void aBareBreakMatchesEmptyBlockInput() {
        assertThat(answerTo("""
                parse [] [break]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("A bare BREAK matches empty string input")
    void aBareBreakMatchesEmptyStringInput() {
        assertThat(answerTo("""
                parse "" [break]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ACCEPT is BREAK under another name")
    void acceptIsBreakUnderAnotherName() {
        assertThat(answerTo("""
                parse [a a] [['a accept] 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REJECT gives up the alternatives in its own block")
    void rejectGivesUpTheAlternativesInItsOwnBlock() {
        assertThat(answerTo("""
                parse [a] [['a reject | 'b]]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("REJECT leaves the enclosing block's alternatives to run")
    void rejectLeavesTheEnclosingBlocksAlternatives() {
        assertThat(answerTo("""
                parse [a] [['a reject | 'b] | 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("A bare REJECT answers false rather than raising")
    void aBareRejectIsFalse() {
        assertThat(answerTo("""
                parse [a] [reject]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("QUOTE over a string answers false rather than raising")
    void quoteOverAStringAnswersFalseRatherThanRaising() {
        assertThat(answerTo("""
                parse "a" [quote #"a"]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("QUOTE still matches the item after it over a block")
    void quoteStillWorksOverABlock() {
        assertThat(answerTo("""
                parse [a] [quote 'a]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("INTO over a string answers false rather than raising")
    void intoOverAStringAnswersFalseRatherThanRaising() {
        assertThat(answerTo("""
                parse "a" [into [#"a"]]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("INTO still descends into a nested block over a block")
    void intoStillWorksOverABlock() {
        assertThat(answerTo("""
                parse [[a]] [into ['a]]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("CHANGE takes both its rule and its replacement over a string")
    void changeTakesItsReplacementOverAString() {
        assertThat(answerTo("""
                parse copy "ac" [change #"a" #"b" #"c"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("A bare keyword answers what R3 answers, over a block and over a string")
    void aBareKeywordAnswersWhatTheReferenceAnswers() {
        assertThat(answerTo("""
                wrong: copy []
                expected: [
                    any #(false) while #(false) some #(false) opt #(false)
                    to parse-end thru parse-end end #(false) skip #(true)
                    into parse-end quote parse-end
                    set parse-variable copy parse-variable
                    collect parse-end keep parse-end
                    and #(false) ahead #(false) not #(false)
                    reject #(false) break #(false) accept #(false)
                    return #(false) then #(false) if parse-end
                    remove #(false) change #(false) insert parse-end
                    case #(false) no-case #(false) fail #(false)
                    limit not-done
                ]
                check: func [label input] [
                    foreach [keyword wanted] expected [
                        e: try [parse input reduce [to word! keyword]]
                        got: either error? e [e/id] [e]
                        unless got = wanted [
                            append wrong rejoin [label " " keyword " " mold got]
                        ]
                    ]
                ]
                check "block" [a]
                check "string" "a"
                wrong""")).isEqualTo("[]");
    }

    @Test
    @DisplayName("A bare count raises parse-end where a bare keyword does not")
    void aBareCountStillRaisesParseEnd() {
        assertThat(answerTo("""
                e: try [parse [a] [1]] e/id = 'parse-end""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                e: try [parse "a" [1]] e/id = 'parse-end""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SET and COPY stop raising once their variable is there")
    void setAndCopyStopRaisingOnceTheirVariableIsThere() {
        assertThat(answerTo("""
                parse [a] [set x]""")).isEqualTo("#(false)");
        assertThat(answerTo("""
                parse "a" [set x]""")).isEqualTo("#(false)");
        assertThat(answerTo("""
                parse [a] [copy x]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("No keyword of the dialect raises parse-rule in either engine")
    void noKeywordRaisesParseRuleInEitherEngine() {
        assertThat(answerTo("""
                gaps: copy []
                check: func [label input rules] [
                    foreach [name rule] rules [
                        e: try [parse copy input rule]
                        if all [error? e  e/id = 'parse-rule] [
                            append gaps rejoin [label " " name]
                        ]
                    ]
                ]
                check "block" [a] [
                    any     [any 'a]
                    while   [while 'a]
                    some    [some 'a]
                    opt     [opt 'a]
                    to      [to end]
                    thru    [thru 'a]
                    end     ['a end]
                    skip    [skip]
                    into    [into ['a]]
                    quote   [quote 'a]
                    set     [set x 'a]
                    copy    [copy x 'a]
                    collect [collect keep 'a]
                    keep    [collect keep 'a]
                    ahead   [ahead 'a 'a]
                    and     [and 'a 'a]
                    not     [not 'b 'a]
                    reject  [['a reject] | 'a]
                    break   [['a break]]
                    accept  [['a accept]]
                    return  [return 'a]
                    then    ['a then 'a | 'a]
                    if      [if (true) 'a]
                    remove  [remove 'a]
                    change  [change 'a 'b]
                    insert  [insert 'b]
                    case    [case 'a]
                    no-case [no-case 'a]
                    fail    [fail | 'a]
                    limit   [limit]
                ]
                check "string" "a" [
                    any     [any #"a"]
                    while   [while #"a"]
                    some    [some #"a"]
                    opt     [opt #"a"]
                    to      [to end]
                    thru    [thru #"a"]
                    end     [#"a" end]
                    skip    [skip]
                    into    [into [#"a"]]
                    quote   [quote #"a"]
                    set     [set x #"a"]
                    copy    [copy x #"a"]
                    collect [collect keep #"a"]
                    keep    [collect keep #"a"]
                    ahead   [ahead #"a" #"a"]
                    and     [and #"a" #"a"]
                    not     [not #"b" #"a"]
                    reject  [[#"a" reject] | #"a"]
                    break   [[#"a" break]]
                    accept  [[#"a" accept]]
                    return  [return #"a"]
                    then    [#"a" then #"a" | #"a"]
                    if      [if (true) #"a"]
                    remove  [remove #"a"]
                    change  [change #"a" #"b"]
                    insert  [insert #"b"]
                    case    [case #"a"]
                    no-case [no-case #"a"]
                    fail    [fail | #"a"]
                    limit   [limit]
                ]
                gaps""")).isEqualTo("[]");
    }
}
