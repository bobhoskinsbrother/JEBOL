package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The handle datatype, and the two functions it unblocked: DO-CODEC and RELEASE.
 *
 * <p>Read out of {@code t-handle.c}, {@code c-handle.c}, {@code b-init.c}'s
 * {@code Init_Codecs}, and the two natives in {@code n-system.c}.
 *
 * <p>A handle is an opaque thing the runtime owns and a script can only pass
 * around. {@code make handle!} cannot build one -- the Make column of
 * {@code boot/types.reb} is a dash -- so the only handles that exist are the ones
 * the runtime hands out, and in a build with no crypto family and no extension API
 * there are exactly two: {@code system/codecs/text/entry} and
 * {@code system/codecs/markup/entry}, put there by {@code Register_Codec} at boot.
 *
 * <p>There are two kinds and nearly everything depends on which. A <b>function</b>
 * handle wraps something callable; a <b>context</b> handle owns a resource with a
 * lifetime. A codec is the first kind, and the first kind publishes nothing about
 * itself and cannot be released.
 *
 * <p>Which makes two of the comparisons read like bugs. {@code equal?} needs both
 * sides to be context handles, so a codec is equal to nothing at all, including
 * itself. And a path on a function handle answers none for every name, because
 * {@code PD_Handle} ends "for the data handles, return NONE on get".
 *
 * <p>Rebol's own {@code handle-test.r3} exercises the other kind, through
 * {@code rc4/key} and {@code aes/key}, and guards the whole file with
 * {@code if not error? try [...]} so a build without those natives runs none of
 * it. It is quoted below for the rules it settles, not for its cases.
 */
class HandleFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("where a handle comes from")
    class WhereTheyComeFrom {

        @Test
        @DisplayName("the boot registers two codecs as handles, and REBOL wraps each in an object")
        void theBootRegistersTwo() {
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
        void theSuffixesReachTheTable() {
            assertThat(answerTo("select system/catalog/file-types %.txt"))
                    .isEqualTo("text");
            assertThat(answerTo("select system/catalog/file-types %.html"))
                    .isEqualTo("markup");
        }

        @Test
        @DisplayName("and the kinds of handle are a catalogue, as an event's types are")
        void theKindsAreACatalogue() {
            // Named rather than counted, so a kind arriving is progress
            // instead of a failure. This asserted "[codec]" exactly, written
            // when codecs were the only kind here; RC4 registers its cipher
            // context beside them and a real 3.22.1 lists six.
            assertThat(answerTo("true? find system/catalog/handles 'codec"))
                    .isEqualTo("#(true)");
            assertThat(answerTo("true? find system/catalog/handles 'rc4"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and every kind in it is a word, as Register_Handle appends one")
        void everyKindIsAWord() {
            assertThat(answerTo("block? system/catalog/handles")).isEqualTo("#(true)");
            assertThat(answerTo("""
                    empty? remove-each k copy system/catalog/handles [word? k]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("MAKE cannot build one")
        void makeCannotBuildOne() {
            assertThat(errorIdFrom("make handle! []")).isNotEqualTo("no-error");
            assertThat(errorIdFrom("make handle! \"codec\"")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("and it molds as its kind, which is all there is to say about it")
        void itMoldsAsItsKind() {
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
        void sameIsIdentity() {
            assertThat(answerTo("same? system/codecs/text/entry system/codecs/text/entry"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("same? system/codecs/text/entry system/codecs/markup/entry"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry same? h system/codecs/text/entry"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and EQUAL? needs two context handles, so a codec equals nothing at all")
        void equalNeedsContextHandles() {
            assertThat(answerTo("equal? system/codecs/text/entry system/codecs/text/entry"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry equal? h h")).isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry h = h")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("two codecs can be put in an order, by identity")
        void theyCanBeOrdered() {
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
        void aFunctionHandleAnswersNone() {
            assertThat(answerTo("none? system/codecs/text/entry/type")).isEqualTo(TRUE);
            assertThat(answerTo("none? system/codecs/text/entry/nonsense")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a selector that is not a name is refused")
        void aNonWordSelectorIsRefused() {
            assertThat(errorIdFrom("system/codecs/text/entry/1")).isEqualTo("invalid-path");
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
        void markupIdentifiesNothing() {
            assertThat(answerTo("do-codec system/codecs/markup/entry 'identify #{3C623E}"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and it decodes markup into a block of strings and tags")
        void markupDecodes() {
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
            assertThat(errorIdFrom("do-codec system/codecs/text/entry 'decode #{41}"))
                    .isEqualTo("no-error");
        }

        @Test
        @DisplayName("and an action word it does not know is refused")
        void anUnknownActionIsRefused() {
            assertThat(errorIdFrom("do-codec system/codecs/text/entry 'nonsense #{41}"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("decoding wants a binary and encoding wants an image, whatever the spec allows")
        void eachActionWantsItsOwnDatatype() {
            assertThat(errorIdFrom("do-codec system/codecs/text/entry 'decode \"AB\""))
                    .isEqualTo("invalid-arg");
            assertThat(errorIdFrom("do-codec system/codecs/text/entry 'encode #{41}"))
                    .isEqualTo("invalid-arg");
            assertThat(errorIdFrom(
                    "do-codec system/codecs/text/entry 'encode make image! 1x1"))
                    .isEqualTo("no-error");
        }

        @Test
        @DisplayName("and a codec asked to do what it cannot is bad media")
        void whatACodecCannotDo() {
            assertThat(errorIdFrom(
                    "do-codec system/codecs/markup/entry 'encode make image! 1x1"))
                    .isEqualTo("bad-media");
        }

        @Test
        @DisplayName("and it takes a handle, a word and data, in that order")
        void itsArguments() {
            assertThat(errorIdFrom("do-codec 1 'decode #{41}")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("do-codec system/codecs/text/entry \"decode\" #{41}"))
                    .isEqualTo("expect-arg");
            assertThat(errorIdFrom("do-codec system/codecs/text/entry 'decode 1"))
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
        void encodingAsksEachCodec() {
            assertThat(answerTo("word? encoding? #{4142}")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("RELEASE")
    class Releasing {

        @Test
        @DisplayName("a function handle has nothing to release, and says so")
        void aFunctionHandleIsNotReleased() {
            assertThat(answerTo("release system/codecs/text/entry")).isEqualTo(FALSE);
            assertThat(answerTo("release system/codecs/markup/entry")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("releasing does not spoil the handle, because there was nothing to spoil")
        void itChangesNothing() {
            assertThat(answerTo(
                    "release system/codecs/text/entry "
                    + "do-codec system/codecs/text/entry 'decode #{41}")).isEqualTo("\"A\"");
        }

        @Test
        @DisplayName("and it takes a handle and nothing else")
        void itsArgument() {
            assertThat(errorIdFrom("release 1")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("release none")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("Load_Markup, which is the markup codec's whole body")
    class SplittingMarkup {

        private static String decoded(String markup) {
            return answerTo("mold do-codec system/codecs/markup/entry 'decode to binary! "
                    + "\"" + markup + "\"");
        }

        @Test
        @DisplayName("text with no tags in it is one string")
        void plainText() {
            assertThat(decoded("hello")).isEqualTo("{[\"hello\"]}");
            assertThat(decoded("")).isEqualTo("\"[]\"");
        }

        @Test
        @DisplayName("a less-than sign that could not start a tag is ordinary text")
        void aLoneLessThan() {
            assertThat(decoded("a < b")).isEqualTo("{[\"a < b\"]}");
            assertThat(decoded("a<1>b")).isEqualTo("{[\"a<1>b\"]}");
        }

        @Test
        @DisplayName("and a slash, a question mark or an exclamation mark can start one")
        void theThreeOtherStarters() {
            assertThat(decoded("</b>")).isEqualTo("\"[</b>]\"");
            assertThat(decoded("<?xml?>")).isEqualTo("\"[<?xml?>]\"");
        }

        @Test
        @DisplayName("a quoted attribute may hold a closing bracket")
        void quotesProtectABracket() {
            assertThat(decoded("<a href='x>y'>"))
                    .isEqualTo("\"[<a href='x>y'>]\"");
        }

        @Test
        @DisplayName("a comment runs to its own ending and not to the first bracket")
        void aCommentRunsToItsEnd() {
            assertThat(decoded("<!-- a > b -->after"))
                    .isEqualTo("{[<!-- a > b --> \"after\"]}");
        }

        @Test
        @DisplayName("and a tag that never closes is treated as text")
        void anUnclosedTagIsText() {
            assertThat(decoded("a<b")).isEqualTo("{[\"a\" \"<b\"]}");
        }
    }
}
