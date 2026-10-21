package org.jebol.domain.value;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandleFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("where a handle comes from")
    class WhereTheyComeFrom {

        @Test
        @DisplayName("the boot registers two codecs as handles, and REBOL wraps each in an object")
        void includesTheTwoTheBootRegisters() {
            assertThat(answerTo("handle? system/codecs/text/entry")).isEqualTo(TRUE);
            assertThat(answerTo("handle? system/codecs/markup/entry")).isEqualTo(TRUE);
            assertThat(answerTo("object? system/codecs/text")).isEqualTo(TRUE);
            assertThat(answerTo("system/codecs/text/name")).isEqualTo("text");
            assertThat(answerTo("system/codecs/text/type")).isEqualTo("text");
            assertThat(answerTo("mold system/codecs/text/suffixes"))
                    .isEqualTo("\"[%.txt %.cgi]\"");
        }

        @Test
        @DisplayName("and the suffixes reach the file-type table, which is what LOAD reads")
        void putsTheSuffixesInTheFileTypeTable() {
            assertThat(answerTo("select system/catalog/file-types %.txt"))
                    .isEqualTo("text");
            assertThat(answerTo("select system/catalog/file-types %.html"))
                    .isEqualTo("markup");
        }

        @Test
        @DisplayName("and the kinds of handle are a catalogue, as an event's types are")
        void keepsTheKindsInACatalogue() {
            assertThat(answerTo("true? find system/catalog/handles 'codec"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("true? find system/catalog/handles 'rc4"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and every type in it is a word, as Register_Handle appends one")
        void namesEveryKindWithAWord() {
            assertThat(answerTo("block? system/catalog/handles")).isEqualTo("#(true)");
            assertThat(answerTo("""
                    empty? remove-each k copy system/catalog/handles [word? k]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("MAKE cannot build one")
        void makeCannotBuildOne() {
            assertThat(readTheErrorIdFrom("make handle! []")).isNotEqualTo("no-error");
            assertThat(readTheErrorIdFrom("make handle! \"codec\"")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("and it molds as its type, which is all there is to say about it")
        void moldsAsItsKind() {
            assertThat(answerTo("mold system/codecs/text/entry"))
                    .isEqualTo("\"#(handle! codec)\"");
            assertThat(answerTo("mold system/codecs/markup/entry"))
                    .isEqualTo("\"#(handle! codec)\"");
        }
    }

    @Nested
    @DisplayName("comparing handles, where the two kinds part company")
    class Comparing {

        @Test
        @DisplayName("SAME? is identity, and a codec read twice is the same codec")
        void treatsSameAsIdentity() {
            assertThat(answerTo("same? system/codecs/text/entry system/codecs/text/entry"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("same? system/codecs/text/entry system/codecs/markup/entry"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry same? h system/codecs/text/entry"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and EQUAL? needs two context handles, so a codec equals nothing at all")
        void needsContextHandlesForEqual() {
            assertThat(answerTo("equal? system/codecs/text/entry system/codecs/text/entry"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry equal? h h")).isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry h = h")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("two codecs can be put in an order, by identity")
        void ordersByIdentity() {
            assertThat(answerTo(
                    "a: system/codecs/text/entry b: system/codecs/markup/entry "
                    + "one: a < b other: b < a one <> other")).isEqualTo(TRUE);
            assertThat(answerTo("h: system/codecs/text/entry h < h")).isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry h > h")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and FIND finds one in a block, because SAME? can tell them apart")
        void findFindsOne() {
            assertThat(answerTo(
                    "blk: reduce [system/codecs/text/entry system/codecs/markup/entry] "
                    + "index? find blk system/codecs/markup/entry")).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("what a path reads off one")
    class ReadingAField {

        @Test
        @DisplayName("a function handle answers none for every name")
        void answersNoneForAFunctionHandle() {
            assertThat(answerTo("none? system/codecs/text/entry/type")).isEqualTo(TRUE);
            assertThat(answerTo("none? system/codecs/text/entry/nonsense")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a selector that is not a name is refused")
        void refusesANonWordSelector() {
            assertThat(readTheErrorIdFrom("system/codecs/text/entry/1")).isEqualTo("invalid-path");
        }
    }

    @Nested
    @DisplayName("DO-CODEC")
    class RunningACodec {

        @Test
        @DisplayName("the text codec decodes bytes into a string")
        void textDecodes() {
            assertThat(answerTo("do-codec system/codecs/text/entry 'decode #{4142}"))
                    .isEqualTo("\"AB\"");
            assertThat(answerTo("string? do-codec system/codecs/text/entry 'decode #{41}"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("do-codec system/codecs/text/entry 'decode #{}"))
                    .isEqualTo("\"\"");
        }

        @Test
        @DisplayName("and it identifies anything, because any bytes are text")
        void textIdentifiesAnything() {
            assertThat(answerTo("do-codec system/codecs/text/entry 'identify #{4142}"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("do-codec system/codecs/text/entry 'identify #{00FF}"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the markup codec identifies nothing, and says so by setting an error")
        void identifiesNothingAsMarkup() {
            assertThat(answerTo("do-codec system/codecs/markup/entry 'identify #{3C623E}"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and it decodes markup into a block of strings and tags")
        void decodesMarkup() {
            assertThat(answerTo(
                    "mold do-codec system/codecs/markup/entry 'decode to binary! \"a<b>c\""))
                    .isEqualTo("{[\"a\" <b> \"c\"]}");
            assertThat(answerTo(
                    "block? do-codec system/codecs/markup/entry 'decode to binary! \"<b>\""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a handle that is not a codec's is refused by name")
        void onlyACodecHandle() {
            assertThat(readTheErrorIdFrom("do-codec system/codecs/text/entry 'decode #{41}"))
                    .isEqualTo("no-error");
        }

        @Test
        @DisplayName("and an action word it does not know is refused")
        void refusesAnUnknownAction() {
            assertThat(readTheErrorIdFrom("do-codec system/codecs/text/entry 'nonsense #{41}"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("decoding wants a binary and encoding wants an image, whatever the spec allows")
        void wantsEachActionsOwnDatatype() {
            assertThat(readTheErrorIdFrom("do-codec system/codecs/text/entry 'decode \"AB\""))
                    .isEqualTo("invalid-arg");
            assertThat(readTheErrorIdFrom("do-codec system/codecs/text/entry 'encode #{41}"))
                    .isEqualTo("invalid-arg");
            assertThat(readTheErrorIdFrom(
                    "do-codec system/codecs/text/entry 'encode make image! 1x1"))
                    .isEqualTo("no-error");
        }

        @Test
        @DisplayName("and a codec asked to do what it cannot is bad media")
        void reportsBadMediaForWhatACodecCannotDo() {
            assertThat(readTheErrorIdFrom(
                    "do-codec system/codecs/markup/entry 'encode make image! 1x1"))
                    .isEqualTo("bad-media");
        }

        @Test
        @DisplayName("and it takes a handle, a word and data, in that order")
        void takesAHandleAWordAndData() {
            assertThat(readTheErrorIdFrom("do-codec 1 'decode #{41}")).isEqualTo("expect-arg");
            assertThat(readTheErrorIdFrom("do-codec system/codecs/text/entry \"decode\" #{41}"))
                    .isEqualTo("expect-arg");
            assertThat(readTheErrorIdFrom("do-codec system/codecs/text/entry 'decode 1"))
                    .isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("what DO-CODEC is for: Rebol's own DECODE")
    class TheCaller {

        @Test
        @DisplayName("DECODE finds the codec, sees a handle, and calls DO-CODEC")
        void decodeCallsDoCodec() {
            assertThat(answerTo("decode 'text #{4142}")).isEqualTo("\"AB\"");
            assertThat(answerTo(
                    "mold decode 'markup to binary! \"a<b>c\""))
                    .isEqualTo("{[\"a\" <b> \"c\"]}");
        }

        @Test
        @DisplayName("and ENCODING? asks every codec to identify the data")
        void asksEachCodecForEncoding() {
            assertThat(answerTo("word? encoding? #{4142}")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("RELEASE")
    class Releasing {

        @Test
        @DisplayName("a function handle has nothing to release, and says so")
        void findsNothingToReleaseInAFunctionHandle() {
            assertThat(answerTo("release system/codecs/text/entry")).isEqualTo(FALSE);
            assertThat(answerTo("release system/codecs/markup/entry")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("releasing does not spoil the handle, because there was nothing to spoil")
        void leavesTheHandleUnspoiled() {
            assertThat(answerTo(
                    "release system/codecs/text/entry "
                    + "do-codec system/codecs/text/entry 'decode #{41}")).isEqualTo("\"A\"");
        }

        @Test
        @DisplayName("and it takes a handle and nothing else")
        void takesAHandleAndNothingElse() {
            assertThat(readTheErrorIdFrom("release 1")).isEqualTo("expect-arg");
            assertThat(readTheErrorIdFrom("release none")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("Load_Markup, which is the markup codec's whole body")
    class SplittingMarkup {

        private static String decode(String markup) {
            return answerTo("mold do-codec system/codecs/markup/entry 'decode to binary! "
                    + "\"" + markup + "\"");
        }

        @Test
        @DisplayName("text with no tags in it is one string")
        void keepsPlainTextAsOneString() {
            assertThat(decode("hello")).isEqualTo("{[\"hello\"]}");
            assertThat(decode("")).isEqualTo("\"[]\"");
        }

        @Test
        @DisplayName("a less-than sign that could not start a tag is ordinary text")
        void keepsALoneLessThanAsText() {
            assertThat(decode("a < b")).isEqualTo("{[\"a < b\"]}");
            assertThat(decode("a<1>b")).isEqualTo("{[\"a<1>b\"]}");
        }

        @Test
        @DisplayName("and a slash, a question mark or an exclamation mark can start one")
        void startsATagWithASlashQuestionOrExclamation() {
            assertThat(decode("</b>")).isEqualTo("\"[</b>]\"");
            assertThat(decode("<?xml?>")).isEqualTo("\"[<?xml?>]\"");
        }

        @Test
        @DisplayName("a quoted attribute may hold a closing bracket")
        void quotesProtectABracket() {
            assertThat(decode("<a href='x>y'>"))
                    .isEqualTo("\"[<a href='x>y'>]\"");
        }

        @Test
        @DisplayName("a comment runs to its own ending and not to the first bracket")
        void runsACommentToItsOwnEnd() {
            assertThat(decode("<!-- a > b -->after"))
                    .isEqualTo("{[<!-- a > b --> \"after\"]}");
        }

        @Test
        @DisplayName("and a tag that never closes is treated as text")
        void treatsAnUnclosedTagAsText() {
            assertThat(decode("a<b")).isEqualTo("{[\"a\" \"<b\"]}");
        }
    }
}
