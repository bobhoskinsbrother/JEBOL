package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnErrorsArgumentsAreValuesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String raised(String source) {
        return answerTo("e: try [" + source + "] either error? e [reduce ["
                + "e/id type? get/any in e 'arg1 type? get/any in e 'arg2 type? get/any in e 'arg3]"
                + "] ['no-error]");
    }

    private static String arguments(String source) {
        return answerTo("e: try [" + source + "] reduce [e/arg1 e/arg2 e/arg3]");
    }

    private static String messageOf(String source) {
        String quoted = answerTo("e: try [" + source + "] pick split form e newline 2");
        return quoted.substring(1, quoted.length() - 1);
    }

    @Nested
    @DisplayName("no-value names the word, and only the word")
    class NoValue {

        @Test
        @DisplayName("get of an unset word carries that word, not a sentence about it")
        void getCarriesTheWord() {
            assertThat(raised("get first [zork]")).isEqualTo("[no-value #(word!) #(none!) #(none!)]");
            assertThat(arguments("get first [zork]")).isEqualTo("[zork _ _]");
        }

        @Test
        @DisplayName("and prints its template once")
        void printsItsTemplateOnce() {
            assertThat(messageOf("get first [zork]")).isEqualTo("** Script error: zork has no value");
        }

        @Test
        @DisplayName("a path whose head has no value names the head")
        void aPathNamesItsHead() {
            assertThat(arguments("zork/a")).isEqualTo("[zork _ _]");
            assertThat(arguments("set 'nowhere/zz 1")).isEqualTo("[nowhere _ _]");
        }

        @Test
        @DisplayName("a word met inside reduce is named as a word")
        void aWordInsideReduce() {
            assertThat(raised("reduce [zork]")).isEqualTo("[no-value #(word!) #(none!) #(none!)]");
        }

        @Test
        @DisplayName("bind into an unbound word is not-defined, naming that word")
        void bindIntoAnUnboundWord() {
            assertThat(raised("bind [a] to word! {q}")).isEqualTo("[not-defined #(word!) #(none!) #(none!)]");
            assertThat(arguments("bind [a] to word! {q}")).isEqualTo("[q _ _]");
        }
    }

    @Nested
    @DisplayName("not-related names the action as a set-word and the left operand's datatype")
    class NotRelated {

        @Test
        @DisplayName("a bitwise operation names its action, and~ for and")
        void aBitwiseOperationNamesItsAction() {
            assertThat(raised("1 and true")).isEqualTo("[not-related #(set-word!) #(datatype!) #(none!)]");
            assertThat(arguments("1 and true")).isEqualTo("[and~: #(integer!) _]");
            assertThat(arguments("1 xor 1x2")).isEqualTo("[xor~: #(integer!) _]");
            assertThat(arguments("1.2.3 or 1x2")).isEqualTo("[or~: #(tuple!) _]");
        }

        @Test
        @DisplayName("arithmetic names its action and the left datatype, whichever side refused")
        void arithmeticNamesTheLeftDatatype() {
            assertThat(arguments("1x2 + 1.2.3")).isEqualTo("[add: #(pair!) _]");
            assertThat(arguments("1.2.3 + 1x2")).isEqualTo("[add: #(tuple!) _]");
            assertThat(arguments("1 - 1x2")).isEqualTo("[subtract: #(integer!) _]");
            assertThat(arguments("1:00 / 1-Jan-2000")).isEqualTo("[divide: #(time!) _]");
            assertThat(arguments("$1 + 1:00")).isEqualTo("[add: #(money!) _]");
            assertThat(arguments("remainder 1x2 1.2.3")).isEqualTo("[remainder: #(pair!) _]");
        }

        @Test
        @DisplayName("a bit operation on a decimal vector names decimal!, not the vector's kind")
        void aDecimalVectorIsNamedAsDecimal() {
            assertThat(arguments("(make vector! [decimal! 64 [1.5]]) and 1")).isEqualTo("[and~: #(decimal!) _]");
        }

        @Test
        @DisplayName("and prints the catalogue's sentence with the action in it")
        void printsTheSentence() {
            assertThat(messageOf("1x2 + 1.2.3"))
                    .isEqualTo("** Script error: incompatible argument for add: of #(pair!)");
            assertThat(messageOf("1 and true"))
                    .isEqualTo("** Script error: incompatible argument for and~: of #(integer!)");
        }
    }

    @Nested
    @DisplayName("cannot-use names the action and the datatype it was given")
    class CannotUse {

        @Test
        @DisplayName("date arithmetic names the action, not the words date arithmetic")
        void dateArithmeticNamesTheAction() {
            assertThat(arguments("1-Jan-2000 + 1.2.3")).isEqualTo("[add: #(date!) _]");
            assertThat(arguments("1-Jan-2000 * 2")).isEqualTo("[multiply: #(date!) _]");
        }

        @Test
        @DisplayName("a bit operation on a datatype names or~")
        void aBitOperationOnADatatype() {
            assertThat(arguments("integer! or 1")).isEqualTo("[or~: #(datatype!) _]");
        }

        @Test
        @DisplayName("a vector given a pair refuses as R3 does, rather than crashing the host")
        void aVectorGivenAPair() {
            assertThat(arguments("(make vector! [1 2]) + 1x2")).isEqualTo("[add: #(vector!) _]");
        }

        @Test
        @DisplayName("reflect on a struct names the word asked about")
        void reflectNamesTheWord() {
            assertThat(arguments("reflect make struct! [a [uint8!]] 'zz")).isEqualTo("[zz #(struct!) _]");
        }

        @Test
        @DisplayName("make and to of a handle name datatype!, the type of the datatype given")
        void aHandleNamesDatatype() {
            assertThat(arguments("make handle! 0")).isEqualTo("[make: #(datatype!) _]");
            assertThat(arguments("to handle! 0")).isEqualTo("[to: #(datatype!) _]");
        }

        @Test
        @DisplayName("an image cannot be taken from, sorted, selected in or reversed")
        void anImageRefusesTakeSortSelectAndReverse() {
            assertThat(arguments("take/part make image! 2x2 2")).isEqualTo("[take: #(image!) _]");
            assertThat(arguments("sort make image! 2x2")).isEqualTo("[sort: #(image!) _]");
            assertThat(arguments("select make image! 2x2 1")).isEqualTo("[select: #(image!) _]");
            assertThat(arguments("reverse make image! 2x2")).isEqualTo("[reverse: #(image!) _]");
        }
    }

    @Nested
    @DisplayName("expect-val and type-mismatch name what they compared")
    class ExpectValAndTypeMismatch {

        @Test
        @DisplayName("a set operation over two datatypes names both, in order")
        void aSetOperationNamesBothDatatypes() {
            assertThat(raised("union make bitset! 8 [a]")).isEqualTo("[expect-val #(datatype!) #(datatype!) #(none!)]");
            assertThat(arguments("union [a] make bitset! 8")).isEqualTo("[#(block!) #(bitset!) _]");
            assertThat(arguments("union [a] make map! [a 1]")).isEqualTo("[#(block!) #(map!) _]");
        }

        @Test
        @DisplayName("a string and a block refuse rather than crash the host, either way round")
        void aStringAndABlockRefuse() {
            assertThat(arguments("difference {a} [a]")).isEqualTo("[#(string!) #(block!) _]");
            assertThat(arguments("intersect [a] {a}")).isEqualTo("[#(block!) #(string!) _]");
        }

        @Test
        @DisplayName("a gob takes a gob, and names what it was given")
        void aGobTakesAGob() {
            assertThat(arguments("g: make gob! [] append g 3")).isEqualTo("[#(gob!) #(integer!) _]");
            assertThat(arguments("g: make gob! [] append g make gob! [] change g 3"))
                    .isEqualTo("[#(gob!) #(integer!) _]");
        }

        @Test
        @DisplayName("a struct is changed by a binary, and names what it was given")
        void aStructIsChangedByABinary() {
            assertThat(arguments("s: make struct! [a [uint8!]] change s 1")).isEqualTo("[#(binary!) #(integer!) _]");
        }

        @Test
        @DisplayName("clamp and lerp name the two values that differ")
        void clampAndLerpNameBothValues() {
            assertThat(arguments("clamp 1 1.5 2")).isEqualTo("[1 1.5 _]");
            assertThat(arguments("clamp 1 1 2.5")).isEqualTo("[1 2.5 _]");
            assertThat(arguments("lerp 1.2.3 1x2 0.5")).isEqualTo("[1.2.3 1x2 _]");
        }
    }

    @Nested
    @DisplayName("invalid-path and bad-path-set name the path and the segment")
    class Paths {

        @Test
        @DisplayName("set through a path names the path as a value and the segment as a word")
        void setThroughAPath() {
            assertThat(raised("o: make object! [p: 1] set 'o/zz 1")).isEqualTo("[invalid-path #(path!) #(word!) #(none!)]");
            assertThat(arguments("o: make object! [p: 1] set 'o/zz 1")).isEqualTo("[o/zz zz _]");
        }

        @Test
        @DisplayName("a time written through a decimal refuses rather than truncating to the hour")
        void aTimeRefusesADecimal() {
            assertThat(arguments("t: 1:00 t/(1.5): 1")).isEqualTo("[t/(1.5): (1.5) _]");
            assertThat(answerTo("t: 1:00 attempt [t/(1.5): 9] t")).isEqualTo("1:00");
        }

        @Test
        @DisplayName("a time picked by a name it has not got names the time")
        void aTimePickedByAnUnknownName() {
            assertThat(arguments("pick 1:00 'zz")).isEqualTo("[1:00 zz _]");
            assertThat(answerTo("pick 1:00 'hour")).isEqualTo("1");
        }

        @Test
        @DisplayName("an image or a vector picked by an unknown word names none and the word")
        void anImageOrVectorPickedByAWord() {
            assertThat(arguments("pick make image! 2x2 'zz")).isEqualTo("[_ zz _]");
            assertThat(arguments("pick make vector! 3 'zz")).isEqualTo("[_ zz _]");
            assertThat(answerTo("pick make vector! 3 'size")).isEqualTo("32");
        }

        @Test
        @DisplayName("but picked by a string names the series itself")
        void pickedByAStringNamesTheSeries() {
            assertThat(arguments("pick make vector! 3 {a}")).isEqualTo("""
                    [#(int32! [0 0 0]) "a" _]""");
        }

        @Test
        @DisplayName("a refinement that is not a word is bad-refine, naming it")
        void aRefinementThatIsNotAWord() {
            assertThat(raised("append/1 [] 1")).isEqualTo("[bad-refine #(integer!) #(none!) #(none!)]");
        }
    }

    @Nested
    @DisplayName("out-of-range names the value that was out of range")
    class OutOfRange {

        @Test
        @DisplayName("a record width below one is named as the number")
        void aRecordWidthBelowOne() {
            assertThat(arguments("find/skip [1 2] 1 0")).isEqualTo("[0 _ _]");
            assertThat(arguments("select/skip [1 2] 1 0")).isEqualTo("[0 _ _]");
            assertThat(arguments("unique/skip [1 2] 0")).isEqualTo("[0 _ _]");
            assertThat(arguments("sort/skip [1 2 3] 2")).isEqualTo("[2 _ _]");
        }

        @Test
        @DisplayName("a byte that does not fit is named as written, a character as a character")
        void aByteThatDoesNotFit() {
            assertThat(arguments("poke #{00} 1 256")).isEqualTo("[256 _ _]");
            assertThat(raised("poke #{00} 1 to char! 256")).isEqualTo("[out-of-range #(char!) #(none!) #(none!)]");
            assertThat(arguments("append #{} 256")).isEqualTo("[256 _ _]");
        }

        @Test
        @DisplayName("a zone too far from UTC names the zone, or the word zone when it is written")
        void aZoneTooFar() {
            assertThat(arguments("make date! [1 1 2000 0:00 16:00]")).isEqualTo("[16:00 _ _]");
            assertThat(arguments("d: 1-Jan-2000 d/zone: 16:00")).isEqualTo("[zone _ _]");
        }

        @Test
        @DisplayName("a pair has two halves, and picking past them is out of range")
        void aPairHasTwoHalves() {
            assertThat(arguments("pick 1x2 3")).isEqualTo("[3 _ _]");
            assertThat(arguments("pick 1x2 3.5")).isEqualTo("[3.5 _ _]");
            assertThat(arguments("third 1x2")).isEqualTo("[3 _ _]");
            assertThat(answerTo("pick 1x2 'y")).isEqualTo("2.0");
        }

        @Test
        @DisplayName("an image's whole-picture byte too large names the field")
        void anImagesByteNamesTheField() {
            assertThat(arguments("i: make image! 2x2 i/rgb: 300")).isEqualTo("[rgb _ _]");
        }

        @Test
        @DisplayName("append/dup counted by a pair is out of range, naming the pair")
        void appendDupCountedByAPair() {
            assertThat(arguments("append/dup [] 1 1x2")).isEqualTo("[1x2 _ _]");
        }
    }

    @Nested
    @DisplayName("invalid-arg names the argument")
    class InvalidArg {

        @Test
        @DisplayName("a map is named by what it was given, or by itself where R3 names itself")
        void aMapNamedByItself() {
            assertThat(arguments("make map! [a b c]")).isEqualTo("[[a b c] _ _]");
            assertThat(answerTo("e: try [append make map! [x 1] 1] all [map? e/arg1 e/arg1/x = 1 none? e/arg2]"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a walk over more than one pair with a third word names that word")
        void aWalkWithAThirdWord() {
            assertThat(arguments("foreach [a b c] make map! [x 1 y 2] [a]")).isEqualTo("[c _ _]");
            assertThat(arguments("foreach [a b c] make object! [x: 1 y: 2] [a]")).isEqualTo("[c _ _]");
        }

        @Test
        @DisplayName("but a third word over a single pair is simply none")
        void aThirdWordOverOnePair() {
            assertThat(answerTo("foreach [a b c] make map! [x 1] [c]")).isEqualTo("_");
            assertThat(raised("foreach [a b c] make map! [] []")).isEqualTo("no-error");
        }

        @Test
        @DisplayName("a tuple or a pair picked by something that is not a position")
        void aTupleOrPairPickedBadly() {
            assertThat(arguments("pick 1.2.3 'zz")).isEqualTo("[zz _ _]");
            assertThat(arguments("pick 1x2 'zz")).isEqualTo("[zz _ _]");
            assertThat(arguments("t: 1.2.3 t/zz")).isEqualTo("[zz _ _]");
        }

        @Test
        @DisplayName("assert/type reads an unset word without raising, then names the bad type")
        void assertTypeNamesTheBadType() {
            assertThat(arguments("assert/type [aa 1]")).isEqualTo("[1 _ _]");
        }

        @Test
        @DisplayName("set over a block names the issue or refinement it cannot assign")
        void setNamesWhatItCannotAssign() {
            assertThat(arguments("set [#a] [1]")).isEqualTo("[#a _ _]");
            assertThat(arguments("set [/a] [1]")).isEqualTo("[/a _ _]");
        }

        @Test
        @DisplayName("a function spec names the type it could not read")
        void aFunctionSpecNamesTheType() {
            assertThat(arguments("func [a [1]] []")).isEqualTo("[1 _ _]");
            assertThat(raised("func [a [zz!]] []")).isEqualTo("[invalid-arg #(word!) #(none!) #(none!)]");
        }
    }

    @Nested
    @DisplayName("ids whose catalogue entry takes nothing carry nothing")
    class NoArguments {

        @Test
        @DisplayName("refinements that do not go together carry no sentence")
        void badRefinesCarriesNothing() {
            assertThat(raised("copy/deep make struct! [a [uint8!]]")).isEqualTo("[bad-refines #(none!) #(none!) #(none!)]");
            assertThat(raised("append/dup make map! [] [a 1] 2")).isEqualTo("[bad-refines #(none!) #(none!) #(none!)]");
        }

        @Test
        @DisplayName("overflow, zero-divide and not-same-type carry nothing")
        void mathCarriesNothing() {
            assertThat(raised("9223372036854775807 + 1")).isEqualTo("[overflow #(none!) #(none!) #(none!)]");
            assertThat(raised("1.2.3 / 0")).isEqualTo("[zero-divide #(none!) #(none!) #(none!)]");
            assertThat(raised("(make vector! [1]) / 0")).isEqualTo("[zero-divide #(none!) #(none!) #(none!)]");
            assertThat(raised("(make vector! [integer! 8 1]) < make vector! [decimal! 64 1]"))
                    .isEqualTo("[not-same-type #(none!) #(none!) #(none!)]");
        }

        @Test
        @DisplayName("past-end names a run's closing value, and nothing on a gob")
        void pastEnd() {
            assertThat(raised("charset reduce [to char! 122 '- to char! 97]"))
                    .isEqualTo("[past-end #(char!) #(none!) #(none!)]");
            assertThat(raised("g: make gob! [] poke g 1 make gob! []")).isEqualTo("[past-end #(none!) #(none!) #(none!)]");
        }

        @Test
        @DisplayName("a gob change with part, only or dup is not-done, once there is a child to change")
        void aGobChangeIsNotDone() {
            assertThat(raised("g: make gob! [] append g make gob! [] change/part g make gob! [] 1"))
                    .isEqualTo("[not-done #(none!) #(none!) #(none!)]");
            assertThat(raised("g: make gob! [] change/part g make gob! [] 1"))
                    .isEqualTo("[past-end #(none!) #(none!) #(none!)]");
            assertThat(raised("g: make gob! [] insert/only g make gob! []"))
                    .isEqualTo("[not-done #(none!) #(none!) #(none!)]");
        }

        @Test
        @DisplayName("get and set of an object with a hidden field are hidden")
        void hiddenFieldsRefuseGetAndSet() {
            assertThat(raised("o: make object! [p: 1] protect/hide in o 'p set o [1]"))
                    .isEqualTo("[hidden #(none!) #(none!) #(none!)]");
            assertThat(raised("o: make object! [p: 1 q: 2] protect/hide in o 'p get o"))
                    .isEqualTo("[hidden #(none!) #(none!) #(none!)]");
            assertThat(answerTo("o: make object! [p: 1] set o [5] o/p")).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("parse names the rule word at fault")
    class Parse {

        @Test
        @DisplayName("into, thru and only name themselves when what follows will not do")
        void keywordsNameThemselves() {
            assertThat(arguments("parse [a] [into 1]")).isEqualTo("[into _ _]");
            assertThat(arguments("parse {a} [thru 1.5]")).isEqualTo("[thru _ _]");
            assertThat(arguments("parse {a} [change only {a} {b}]")).isEqualTo("[only _ _]");
        }

        @Test
        @DisplayName("set and copy check their variable before looking for a rule")
        void setAndCopyCheckTheirVariable() {
            assertThat(arguments("parse {a} [set 1]")).isEqualTo("[1 _ _]");
            assertThat(arguments("parse [a] [copy 1]")).isEqualTo("[1 _ _]");
            assertThat(answerTo("parse {a} [set x]")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("set and copy with nothing after are parse-variable, naming the keyword where R3 holds #(end)")
        void setWithNothingAfter() {
            assertThat(raised("parse {a} [set]")).isEqualTo("[parse-variable #(word!) #(none!) #(none!)]");
            assertThat(arguments("parse {a} [copy]")).isEqualTo("[copy _ _]");
        }

        @Test
        @DisplayName("change matches first and only then wants a replacement")
        void changeMatchesFirst() {
            assertThat(answerTo("parse {a} [change {b}]")).isEqualTo("#(false)");
            assertThat(raised("parse {a} [change {a}]")).isEqualTo("[parse-end #(word!) #(none!) #(none!)]");
            assertThat(raised("parse [a] [change 'a]")).isEqualTo("[parse-end #(word!) #(none!) #(none!)]");
        }

        @Test
        @DisplayName("to over text with none, an unset word or a logic is parse-rule, naming what it found")
        void toOverTextWithNothingToFind() {
            assertThat(raised("parse {a} [to none]")).isEqualTo("[parse-rule #(none!) #(none!) #(none!)]");
            assertThat(raised("parse {a} [thru zork]")).isEqualTo("[parse-rule #(unset!) #(none!) #(none!)]");
            assertThat(answerTo("parse [a] [to none]")).isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("behaviour R3 has that the arguments work turned up")
    class Behaviour {

        @Test
        @DisplayName("for with a step of zero runs nothing forward and until a break backward")
        void forWithAStepOfZero() {
            assertThat(answerTo("for i 1 3 0 [1]")).isEqualTo("_");
            assertThat(answerTo("n: 0 for i 3 1 0 [n: n + 1 if n = 4 [break]] n")).isEqualTo("4");
        }

        @Test
        @DisplayName("a gob written through an integer path changes nothing")
        void aGobPathWriteChangesNothing() {
            assertThat(answerTo("g: make gob! [] append g make gob! [] g/1: 3 length? g")).isEqualTo("1");
            assertThat(answerTo("g: make gob! [] g/1: make gob! [] length? g")).isEqualTo("0");
        }

        @Test
        @DisplayName("reverse/part of a tuple with a negative count reverses the whole tuple")
        void aNegativeCountReversesTheWholeTuple() {
            assertThat(answerTo("reverse/part 1.2.3 -1")).isEqualTo("3.2.1");
            assertThat(answerTo("reverse/part 1.2.3 0")).isEqualTo("1.2.3");
            assertThat(answerTo("reverse/part 1.2.3.4.5 3")).isEqualTo("3.2.1.4.5");
        }

        @Test
        @DisplayName("the four bytes UTF-8 never uses scan as a control character, between two words")
        void neverUtf8BytesScanAsAControl() {
            assertThat(answerTo("load #{41FF42}")).isEqualTo("[A B]");
            assertThat(answerTo("load #{41C042}")).isEqualTo("[A B]");
            assertThat(answerTo("load #{41F542}")).isEqualTo("[A B]");
            assertThat(answerTo("load #{FF}")).isEqualTo("[]");
        }

        @Test
        @DisplayName("but a lead byte UTF-8 uses, with no continuation after it, is invalid-chars")
        void aBrokenSequenceIsInvalidChars() {
            assertThat(raised("load #{41C342}")).isEqualTo("[invalid-chars #(none!) #(none!) #(none!)]");
            assertThat(raised("load #{41F642}")).isEqualTo("[invalid-chars #(none!) #(none!) #(none!)]");
        }

        @Test
        @DisplayName("a struct's change ignores part and dup, as the C does")
        void aStructsChangeIgnoresRefinements() {
            assertThat(answerTo("s: make struct! [a [uint8!]] change/part s #{05} 1 s/a")).isEqualTo("5");
        }
    }
}
