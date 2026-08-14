package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The gob datatype, read out of {@code t-gob.c}.
 *
 * <p>A graphical object: somewhere to be, one piece of content, and children.
 * Three things about it are not what its shape suggests, and all three come out
 * of {@code Make_Gob}, {@code Set_GOB_Var} and {@code REBTYPE(Gob)}.
 *
 * <p><b>A fresh one is not empty.</b> {@code Make_Gob} clears the struct and then
 * writes three fields: {@code GOB_W(gob) = 100; GOB_H(gob) = 100; GOB_ALPHA(gob)
 * = 255;}. So {@code make gob! []} is a hundred by a hundred and opaque, and only
 * the offset starts at nothing.
 *
 * <p><b>The content is a union.</b> {@code image}, {@code draw}, {@code text},
 * {@code effect} and {@code color} all write {@code GOB_CONTENT} and set one type
 * tag beside it, so giving a gob an image takes away the draw block it had.
 * Reading the field it has not got answers none.
 *
 * <p><b>The pane is the series.</b> All 24 of the gob's arms work on its list of
 * children, not on the gob: {@code length? gob} counts children, {@code append
 * gob child} adds one, {@code pick gob 1} is the first. Which is why the gob's own
 * fields are reached through a path and never through a position.
 *
 * <p>Rebol's own {@code gob-test.r3} settles several of these, and it is quoted
 * where it does. Specified in {@code spec/values.allium} as {@code GobStorage}
 * and {@code GobValue}.
 */
class GobFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("making one")
    class Making {

        @Test
        @DisplayName("a fresh gob is a hundred square and opaque, at no offset")
        void theFreshGob() {
            assertThat(answerTo("gob? make gob! []")).isEqualTo(TRUE);
            assertThat(answerTo("g: make gob! [] g/offset")).isEqualTo("0x0");
            assertThat(answerTo("g: make gob! [] g/size")).isEqualTo("100x100");
            assertThat(answerTo("g: make gob! [] g/alpha")).isEqualTo("255");
        }

        @Test
        @DisplayName("a block of set-words fills the fields")
        void aBlockOfFields() {
            assertThat(answerTo("g: make gob! [offset: 10x20 size: 30x40] g/offset"))
                    .isEqualTo("10x20");
            assertThat(answerTo("g: make gob! [offset: 10x20 size: 30x40] g/size"))
                    .isEqualTo("30x40");
        }

        @Test
        @DisplayName("a pair on its own is a size")
        void aPairIsASize() {
            assertThat(answerTo("g: make gob! 1x1 g/size")).isEqualTo("1x1");
            assertThat(answerTo("g: make gob! 1x1 g/offset")).isEqualTo("0x0");
        }

        @Test
        @DisplayName("and anything that is not a set-word is refused")
        void aBadBlockIsRefused() {
            assertThat(errorIdFrom("make gob! [color 127.0.127]")).isEqualTo("expect-val");
            assertThat(answerTo(
                    "e: try [make gob! [color 127.0.127]] e/arg1 = set-word!"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a set-word with nothing usable after it needs a value")
        void aSetWordNeedsAValue() {
            assertThat(errorIdFrom("make gob! [data:]")).isEqualTo("need-value");
            assertThat(errorIdFrom("make gob! [data: size: 10x10]"))
                    .isEqualTo("need-value");
        }

        @Test
        @DisplayName("a field it does not know is a bad field set")
        void anUnknownFieldIsRefused() {
            assertThat(errorIdFrom("make gob! [nonsense: 1]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make gob! [offset: \"here\"]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("and nothing else makes a gob at all")
        void anythingElseIsRefused() {
            assertThat(errorIdFrom("make gob! \"10x20\"")).isEqualTo("bad-make-arg");
            assertThat(errorIdFrom("make gob! 5")).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("and a gob is cloned without its pane or its parent")
        void aGobIsCloned() {
            assertThat(answerTo(
                    "a: make gob! [offset: 5x5] append a make gob! [] "
                    + "b: make gob! a reduce [b/offset length? b]"))
                    .isEqualTo("[5x5 0]");
        }
    }

    @Nested
    @DisplayName("the fields")
    class Fields {

        @Test
        @DisplayName("alpha is a byte, clipped on the way in")
        void alphaIsClipped() {
            assertThat(answerTo("g: make gob! [alpha: 128] g/alpha")).isEqualTo("128");
            assertThat(answerTo("g: make gob! [alpha: 300] g/alpha")).isEqualTo("255");
            assertThat(answerTo("g: make gob! [alpha: -1] g/alpha")).isEqualTo("0");
        }

        @Test
        @DisplayName("the offset and size keep fractions, and one number sets both halves")
        void theShapeIsFloating() {
            assertThat(answerTo("g: make gob! [offset: 1.5x2.5] g/offset"))
                    .isEqualTo("1.5x2.5");
            assertThat(answerTo("g: make gob! [size: 7] g/size")).isEqualTo("7x7");
            assertThat(answerTo("g: make gob! [size: 2.5] g/size")).isEqualTo("2.5x2.5");
        }

        @Test
        @DisplayName("content is one thing at a time: an image takes the place of a draw block")
        void contentIsAUnion() {
            assertThat(answerTo(
                    "g: make gob! [draw: [1 2]] g/image: make image! 1x1 none? g/draw"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [draw: [1 2]] g/image: make image! 1x1 image? g/image"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a field the gob has not got answers none rather than raising")
        void anAbsentContentFieldIsNone() {
            assertThat(answerTo("g: make gob! [] none? g/image")).isEqualTo(TRUE);
            assertThat(answerTo("g: make gob! [] none? g/text")).isEqualTo(TRUE);
            assertThat(answerTo("g: make gob! [] none? g/color")).isEqualTo(TRUE);
            assertThat(answerTo("g: make gob! [] none? g/data")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("text takes a string or a block, and reads back as whichever went in")
        void textIsTwoKinds() {
            assertThat(answerTo("g: make gob! [text: \"A\"] g/text")).isEqualTo("\"A\"");
            assertThat(answerTo("g: make gob! [text: [1 2]] mold g/text"))
                    .isEqualTo("\"[1 2]\"");
        }

        @Test
        @DisplayName("an image sets the gob's size to the image's")
        void anImageSetsTheSize() {
            assertThat(answerTo("g: make gob! [] g/image: make image! 20x10 g/size"))
                    .isEqualTo("20x10");
        }

        @Test
        @DisplayName("a colour is kept as a pixel and read back with its alpha")
        void aColourRoundTrips() {
            assertThat(answerTo("g: make gob! [color: 255.128.0] g/color"))
                    .isEqualTo("255.128.0.255");
            assertThat(answerTo("g: make gob! [color: 1.2.3.4] g/color"))
                    .isEqualTo("1.2.3.4");
        }

        @Test
        @DisplayName("and none takes the content away again")
        void noneEmptiesTheContent() {
            assertThat(answerTo(
                    "g: make gob! [] g/color: 255.0.0 g/color: none none? g/color"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("data holds one of five kinds of thing, and refuses the rest")
        void dataIsWhateverYouLike() {
            assertThat(answerTo("g: make gob! [data: [1 2 3]] mold g/data"))
                    .isEqualTo("\"[1 2 3]\"");
            assertThat(answerTo("g: make gob! [data: 42] g/data")).isEqualTo("42");
            assertThat(errorIdFrom("make gob! [data: 1x1]")).isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("draw and effect are blocks, and each is a content of its own")
        void drawAndEffectAreBlocks() {
            assertThat(answerTo("g: make gob! [effect: [1 2]] mold g/effect"))
                    .isEqualTo("\"[1 2]\"");
            assertThat(answerTo(
                    "g: make gob! [effect: [1 2]] g/draw: [3 4] none? g/effect"))
                    .isEqualTo(TRUE);
            assertThat(errorIdFrom("make gob! [effect: \"nope\"]"))
                    .isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make gob! [draw: \"nope\"]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("data takes an object, a string and a binary too")
        void dataTakesFiveKinds() {
            assertThat(answerTo("g: make gob! [] g/data: make object! [a: 1] g/data/a"))
                    .isEqualTo("1");
            assertThat(answerTo("g: make gob! [data: \"hi\"] g/data"))
                    .isEqualTo("\"hi\"");
            assertThat(answerTo("g: make gob! [data: #{FF00}] mold g/data"))
                    .isEqualTo("\"#{FF00}\"");
        }

        @Test
        @DisplayName("data does not join the content union")
        void dataStandsApart() {
            assertThat(answerTo(
                    "g: make gob! [draw: [1 2] data: [3 4]] mold reduce [g/draw g/data]"))
                    .isEqualTo("\"[[1 2] [3 4]]\"");
        }

        @Test
        @DisplayName("parent is none until the gob is in someone's pane")
        void parentFollowsThePane() {
            assertThat(answerTo("g: make gob! [] none? g/parent")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "p: make gob! [] c: make gob! [] append p c same? p c/parent"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "p: make gob! [] append p make gob! [] c: make gob! [] "
                    + "change p c same? p c/parent")).isEqualTo(TRUE);
            assertThat(errorIdFrom("change make gob! [] make gob! []"))
                    .isEqualTo("past-end");
            assertThat(errorIdFrom("g: make gob! [] g/parent: make gob! []"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("and flags answer as a block, which a block of them replaces wholesale")
        void flagsReadAsABlock() {
            assertThat(answerTo("g: make gob! [flags: [hidden]] mold g/flags"))
                    .isEqualTo("\"[hidden]\"");
            assertThat(answerTo("g: make gob! [] mold g/flags")).isEqualTo("\"[]\"");
            assertThat(answerTo(
                    "g: make gob! [] g/flags: [resize] g/flags: [popup] mold g/flags"))
                    .isEqualTo("\"[popup]\"");
        }

        @Test
        @DisplayName("a single flag word adds to what is there, and an unknown one is ignored")
        void oneFlagWordAtATime() {
            assertThat(answerTo(
                    "g: make gob! [] g/flags: 'resize g/flags: 'popup mold g/flags"))
                    .isEqualTo("\"[resize popup]\"");
            assertThat(answerTo("g: make gob! [flags: [nonsense]] mold g/flags"))
                    .isEqualTo("\"[]\"");
            assertThat(answerTo("g: make gob! [flags: 1] mold g/flags"))
                    .isEqualTo("\"[]\"");
        }

        @Test
        @DisplayName("and the flags come back in the table's order, not the order they were set")
        void flagsComeBackInTheTablesOrder() {
            assertThat(answerTo(
                    "g: make gob! [flags: [hidden resize modal]] mold g/flags"))
                    .isEqualTo("\"[resize modal hidden]\"");
        }

        @Test
        @DisplayName("owner can be written and cannot be read")
        void ownerIsWriteOnly() {
            assertThat(errorIdFrom("g: make gob! [] g/owner: make gob! []"))
                    .isEqualTo("no-error");
            assertThat(errorIdFrom("make gob! [owner: 1]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("g: make gob! [] g/owner")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("and a half of a pair field can be written through the path")
        void oneHalfOfAPairField() {
            assertThat(answerTo("g: make gob! [size: 10x20] g/size/x: 5 g/size"))
                    .isEqualTo("5x20");
            assertThat(answerTo("g: make gob! [offset: 1x2] g/offset/y: 9 g/offset"))
                    .isEqualTo("1x9");
        }
    }

    @Nested
    @DisplayName("the pane is the series")
    class ThePane {

        @Test
        @DisplayName("length counts children, and an empty gob has none")
        void lengthCountsChildren() {
            assertThat(answerTo("length? make gob! []")).isEqualTo("0");
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] length? g")).isEqualTo("1");
        }

        @Test
        @DisplayName("append and insert put children in, and append answers the gob")
        void childrenGoIn() {
            assertThat(answerTo(
                    "g: make gob! [] a: make gob! 1x1 b: make gob! 2x2 "
                    + "append g a append g b c: pick g 2 c/size"))
                    .isEqualTo("2x2");
            assertThat(answerTo(
                    "g: make gob! [] a: make gob! 1x1 b: make gob! 2x2 "
                    + "append g a insert g b c: pick g 1 c/size"))
                    .isEqualTo("2x2");
            assertThat(answerTo(
                    "a: make gob! 1x1 b: make gob! 2x2 same? a append a b"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "a: make gob! 1x1 b: make gob! 2x2 append a b append a b length? a"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("a pane given as a block puts them all in")
        void aPaneFromABlock() {
            assertThat(answerTo(
                    "g: make gob! reduce [to set-word! 'pane "
                    + "reduce [make gob! [] make gob! []]] length? g")).isEqualTo("2");
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] g/pane: none length? g"))
                    .isEqualTo("0");
            assertThat(answerTo(
                    "g: make gob! [] g/pane: make gob! [] length? g")).isEqualTo("1");
            assertThat(errorIdFrom("make gob! [pane: 1]")).isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("APPEND takes a block of children, and refuses anything but a gob")
        void appendTakesABlock() {
            assertThat(answerTo(
                    "g: make gob! [] append g reduce [make gob! [] make gob! []] "
                    + "length? g")).isEqualTo("2");
            assertThat(errorIdFrom("append make gob! [] 1")).isEqualTo("expect-val");
            assertThat(errorIdFrom("insert make gob! [] \"child\""))
                    .isEqualTo("expect-val");
        }

        @Test
        @DisplayName("PICK answers a child, and none past the end")
        void pickAnswersAChild() {
            assertThat(answerTo("g: make gob! [] none? pick g 1")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] gob? pick g 1")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] none? pick g 2")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] a: make gob! [] append g a same? a g/1"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a field name is the wrong argument for PICK, not a field read")
        void pickWillNotReadAField() {
            assertThat(errorIdFrom("pick make gob! [] 'offset")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("poke make gob! [] 'offset 1x1"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("PICK with none, and past the end, both answer none")
        void pickAtTheEdges() {
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] none? pick g none"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] none? pick g 0"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] none? pick tail g -1"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("g: make gob! [] none? g/1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("POKE puts a child at a position and answers the child")
        void pokePutsAChildIn() {
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! 1x1 c: make gob! 2x2 "
                    + "same? c poke g 1 c")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! 1x1 c: make gob! 2x2 "
                    + "poke g 1 c same? g c/parent")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an arm the gob has not got is an operation it cannot do")
        void anArmItHasNotGot() {
            assertThat(errorIdFrom("swap make gob! [] make gob! []"))
                    .isEqualTo("cannot-use");
            assertThat(errorIdFrom("copy make gob! []")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("sort make gob! []")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("REMOVE and CLEAR take children out")
        void childrenComeOut() {
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] append g make gob! [] "
                    + "remove g length? g")).isEqualTo("1");
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] append g make gob! [] "
                    + "clear g length? g")).isEqualTo("0");
            assertThat(answerTo(
                    "g: make gob! [] c: make gob! [] append g c remove g none? c/parent"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] loop 3 [append g make gob! []] "
                    + "remove/part g 2 length? g")).isEqualTo("1");
            assertThat(answerTo(
                    "g: make gob! [] loop 2 [append g make gob! []] "
                    + "remove/part g 99 length? g")).isEqualTo("0");
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] "
                    + "remove tail g length? g")).isEqualTo("1");
        }

        @Test
        @DisplayName("TAKE answers one child, or a block of them with /PART")
        void takeAnswersChildren() {
            assertThat(answerTo(
                    "g: make gob! [] a: make gob! 1x1 b: make gob! 2x2 "
                    + "append g a append g b c: take next g c/size")).isEqualTo("2x2");
            assertThat(answerTo(
                    "g: make gob! [] a: make gob! 1x1 b: make gob! 2x2 "
                    + "append g a append g b take next g length? g")).isEqualTo("1");
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! 1x1 append g make gob! 2x2 "
                    + "block? take/part g 2")).isEqualTo(TRUE);
            assertThat(answerTo("none? take tail make gob! []")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("REVERSE puts the children in the other order")
        void reverseTurnsThePaneRound() {
            assertThat(answerTo(
                    "c: make gob! [] a: make gob! 1x1 b: make gob! 2x2 "
                    + "append c a append c b reverse c same? b c/1")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "c: make gob! [] append c make gob! 1x1 append c make gob! 2x2 "
                    + "gob? reverse c")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("FIND answers the pane standing at the child it found")
        void findLooksThroughThePane() {
            assertThat(answerTo(
                    "g: make gob! [] a: make gob! [] b: make gob! [] "
                    + "append g a append g b index? find g b")).isEqualTo("2");
            assertThat(answerTo(
                    "g: make gob! [] none? find g make gob! []")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the gob navigates its own pane")
        void itNavigatesThePane() {
            assertThat(answerTo(
                    "g: make gob! [] loop 2 [append g make gob! []] index? g"))
                    .isEqualTo("1");
            assertThat(answerTo(
                    "g: make gob! [] loop 2 [append g make gob! []] index? back g"))
                    .isEqualTo("1");
            assertThat(answerTo(
                    "g: make gob! [] loop 2 [append g make gob! []] index? next g"))
                    .isEqualTo("2");
            assertThat(answerTo(
                    "g: make gob! [] loop 2 [append g make gob! []] index? tail g"))
                    .isEqualTo("3");
            assertThat(answerTo(
                    "g: make gob! [] loop 2 [append g make gob! []] index? at g 2"))
                    .isEqualTo("2");
            assertThat(answerTo(
                    "g: make gob! [] loop 2 [append g make gob! []] indexz? tail g"))
                    .isEqualTo("2");
            assertThat(answerTo("tail? tail make gob! []")).isEqualTo(TRUE);
            assertThat(answerTo("head? make gob! []")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("SKIP steps the position, and PAST? asks whether it went too far")
        void skipAndPast() {
            assertThat(answerTo(
                    "g: make gob! [] loop 3 [append g make gob! []] index? skip g 2"))
                    .isEqualTo("3");
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] past? tail g"))
                    .isEqualTo("#(false)");
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] head? back g"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a child knows its parent, and moving it moves the knowledge")
        void aChildHasOneParent() {
            assertThat(answerTo(
                    "one: make gob! [] two: make gob! [] c: make gob! [] "
                    + "append one c append two c same? two c/parent"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "one: make gob! [] two: make gob! [] c: make gob! [] "
                    + "append one c append two c length? one"))
                    .isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("molding and comparing")
    class MoldingAndComparing {

        @Test
        @DisplayName("a gob molds as the spec block that would remake it")
        void itMoldsAsASpec() {
            assertThat(answerTo("mold make gob! []"))
                    .isEqualTo("\"make gob! [offset: 0x0 size: 100x100]\"");
            assertThat(answerTo("mold/flat make gob! 2x2"))
                    .isEqualTo("\"make gob! [offset: 0x0 size: 2x2]\"");
            assertThat(answerTo("mold/flat make gob! [text: \"A\"]"))
                    .isEqualTo("{make gob! [offset: 0x0 size: 100x100 text: \"A\"]}");
        }

        @Test
        @DisplayName("and the alpha it molds is the opposite of the alpha it holds")
        void theMoldedAlphaIsInverted() {
            assertThat(answerTo("mold/flat make gob! [alpha: 200]"))
                    .isEqualTo("\"make gob! [offset: 0x0 size: 100x100 alpha: 55]\"");
            assertThat(answerTo("mold/flat make gob! [alpha: 255]"))
                    .isEqualTo("\"make gob! [offset: 0x0 size: 100x100]\"");
        }

        @Test
        @DisplayName("two gobs are equal only when they are the same gob")
        void equalityIsIdentity() {
            assertThat(answerTo("equal? (make gob! []) (make gob! [])"))
                    .isEqualTo("#(false)");
            assertThat(answerTo("g: make gob! [] equal? g g")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] append g make gob! [] equal? g next g"))
                    .isEqualTo("#(false)");
        }
    }
}
