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
            // `Register_Codec("text", Codec_Text); Register_Codec("markup",
            // Codec_Markup);` puts a handle in `system/codecs` for each. Then
            // Rebol's own `base-defs.reb` walks that object and wraps every handle
            // it finds:
            //
            //     foreach [codec handler] system/codecs [
            //         if handle? handler [
            //             ; Change boot handle (for internal native codecs) into object:
            //             codec: set codec make object! [ ... entry: handler ]
            //
            // So the handle ends up at `system/codecs/text/entry`, and the layer
            // rule is doing the work: the C registers a handle, REBOL gives it a
            // name, a title and a list of suffixes. That loop had nothing to walk
            // until these two handles existed.
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
            // The same loop: `append append system/catalog/file-types
            // codec/suffixes codec/name`. So registering a codec is what makes a
            // file extension mean something.
            assertThat(answerTo("select system/catalog/file-types %.txt"))
                    .isEqualTo("text");
            assertThat(answerTo("select system/catalog/file-types %.html"))
                    .isEqualTo("markup");
        }

        @Test
        @DisplayName("and the kinds of handle are a catalogue, as an event's types are")
        void theKindsAreACatalogue() {
            // `Register_Handle` appends the type word to `system/catalog/handles`,
            // up to `MAX_HANDLE_TYPES` of 64, and a handle stores its position.
            // Only `codec` registers in a build with no ciphers.
            assertThat(answerTo("mold system/catalog/handles"))
                    .isEqualTo("\"[codec]\"");
        }

        @Test
        @DisplayName("MAKE cannot build one")
        void makeCannotBuildOne() {
            // The Make column of `boot/types.reb` is `-` for handle, and
            // `MT_Handle` is `return FALSE;` and nothing else. So a handle can only
            // be given to a script, never asked for.
            assertThat(errorIdFrom("make handle! []")).isNotEqualTo("no-error");
            assertThat(errorIdFrom("make handle! \"codec\"")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("and it molds as its kind, which is all there is to say about it")
        void itMoldsAsItsKind() {
            // `Append_Bytes(mold->series, "#(handle! "); Append_Bytes(mold->series,
            // cs_cast(name)); ... Append_Byte(mold->series, ')');`
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
            // `(VAL_HANDLE_FLAGS(a) == VAL_HANDLE_FLAGS(b)) && (VAL_HANDLE_DATA(a)
            // == VAL_HANDLE_DATA(b))` -- so reading the field twice gives the same
            // handle, and two different codecs are not.
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
            // `return (IS_CONTEXT_HANDLE(a) && IS_CONTEXT_HANDLE(b) &&
            // (VAL_HANDLE_SYM(a) == VAL_HANDLE_SYM(b)));` -- both conditions, and a
            // codec fails the first. So `equal? h h` is false, which is the one
            // place a value here is not equal to itself.
            //
            // Not a slip. Rebol's own test says handles "are equal, if they have
            // same type", and a function handle has no type a script can ask for:
            // `h/type` on one answers none. There is nothing to compare.
            assertThat(answerTo("equal? system/codecs/text/entry system/codecs/text/entry"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry equal? h h")).isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry h = h")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("two codecs can be put in an order, by identity")
        void theyCanBeOrdered() {
            // `return (VAL_HANDLE_I32(a) - VAL_HANDLE_I32(b));` for two function
            // handles. Which order that is depends on the runtime rather than on
            // anything a script can see -- so what is worth pinning is that the
            // question has an answer, and that it is consistent.
            assertThat(answerTo(
                    "a: system/codecs/text/entry b: system/codecs/markup/entry "
                    + "one: a < b other: b < a one <> other")).isEqualTo(TRUE);
            // And a handle is neither below nor above itself, which is what makes
            // the ordering usable for a search: `Cmp_Handle` answers zero.
            assertThat(answerTo("h: system/codecs/text/entry h < h")).isEqualTo(FALSE);
            assertThat(answerTo("h: system/codecs/text/entry h > h")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and FIND finds one in a block, because SAME? can tell them apart")
        void findFindsOne() {
            // Rebol's own test walks a block of four handles with FIND and expects
            // each at its own position, which needs identity and not equality.
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
            // `// for the data handles, return NONE on get` and `return NZ(val) ?
            // PE_BAD_SET : PE_NONE;` -- so even `type`, the one word a context
            // handle answers, is none here. A codec tells a script nothing about
            // itself.
            assertThat(answerTo("none? system/codecs/text/entry/type")).isEqualTo(TRUE);
            assertThat(answerTo("none? system/codecs/text/entry/nonsense")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a selector that is not a name is refused")
        void aNonWordSelectorIsRefused() {
            // `if (!ANY_WORD(arg)) return PE_BAD_SELECT;` -- which comes before the
            // none, so a number raises where a name answers nothing.
            assertThat(errorIdFrom("system/codecs/text/entry/1")).isEqualTo("invalid-path");
        }
    }

    @Nested
    @DisplayName("DO-CODEC")
    class RunningACodec {

        @Test
        @DisplayName("the text codec decodes bytes into a string")
        void textDecodes() {
            // `codi->other = Decode_UTF_String(codi->data, codi->len, -1, TRUE,
            // &codi->error); return CODI_STRING;`
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
            // `if (codi->action == CODI_IDENTIFY) { return CODI_CHECK; }` with the
            // error left at zero, and "error code is inverted result" -- so no error
            // means yes.
            assertThat(answerTo("do-codec system/codecs/text/entry 'identify #{4142}"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("do-codec system/codecs/text/entry 'identify #{00FF}"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the markup codec identifies nothing, and says so by setting an error")
        void markupIdentifiesNothing() {
            // `codi->error = 1; // never identified... would require markup
            // validator` and then `return CODI_CHECK`. The error is the inverted
            // result, so this is the one identify that cannot answer yes -- and it
            // answers false rather than raising, because CHECK is exempt from the
            // bad-media test.
            assertThat(answerTo("do-codec system/codecs/markup/entry 'identify #{3C623E}"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("and it decodes markup into a block of strings and tags")
        void markupDecodes() {
            // `codi->other = Load_Markup(codi->data, codi->len); return
            // CODI_BLOCK;`
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
            // `if (VAL_HANDLE_TYPE(hnd) != SYM_CODEC) Trap0(RE_INVALID_HANDLE);` --
            // its own error id, so a caller can tell this from a codec that failed.
            // Nothing here makes a non-codec handle, so the branch is stated and
            // reached only when one exists.
            assertThat(errorIdFrom("do-codec system/codecs/text/entry 'decode #{41}"))
                    .isEqualTo("no-error");
        }

        @Test
        @DisplayName("and an action word it does not know is refused")
        void anUnknownActionIsRefused() {
            // `default: Trap1(RE_INVALID_ARG, D_ARG(2));`
            assertThat(errorIdFrom("do-codec system/codecs/text/entry 'nonsense #{41}"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("decoding wants a binary and encoding wants an image, whatever the spec allows")
        void eachActionWantsItsOwnDatatype() {
            // `data [binary! image! string!]` is the declaration, and then every arm
            // narrows it: `if (!IS_BINARY(val)) Trap1(RE_INVALID_ARG, val);` for
            // identify and decode, and `if (IS_IMAGE(val)) {...} else
            // Trap1(RE_INVALID_ARG, val);` for encode.
            //
            // So a string is declared and refused by all three, which is the
            // declaration and the arms disagreeing on purpose: the spec was widened
            // for a codec that could take one and none of them does.
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
            // `codi->error = CODI_ERR_NA; return CODI_ERROR;` in the markup codec's
            // fall-through, and `if (codi.error != 0) { ... Trap0(RE_BAD_MEDIA); }`.
            // Markup can be read and not written.
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
            // `sys-codec.reb`:
            //
            //     cod: select system/codecs type
            //     data: either handle? try [cod/entry] [
            //         ; original codecs were only natives
            //         do-codec cod/entry 'decode data
            //
            // So DO-CODEC was never a function without a caller. DECODE and ENCODE
            // have been reaching for it since the codec files were imported, and
            // took this branch to a word that did not exist.
            assertThat(answerTo("decode 'text #{4142}")).isEqualTo("\"AB\"");
            assertThat(answerTo(
                    "mold decode 'markup to binary! \"a<b>c\""))
                    .isEqualTo("{[\"a\" <b> \"c\"]}");
        }

        @Test
        @DisplayName("and ENCODING? asks every codec to identify the data")
        void encodingAsksEachCodec() {
            // Also `sys-codec.reb`, and it walks the whole registry asking each
            // codec whether it recognises the bytes. The text codec says yes to
            // anything, so it is the answer for anything no earlier codec claimed.
            assertThat(answerTo("word? encoding? #{4142}")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("RELEASE")
    class Releasing {

        @Test
        @DisplayName("a function handle has nothing to release, and says so")
        void aFunctionHandleIsNotReleased() {
            // The whole native: `if (IS_CONTEXT_HANDLE(val)) { Free_Hob(...); return
            // R_TRUE; } return R_FALSE;`. A codec wraps a dispatcher rather than
            // owning anything, so there is nothing to free and the answer is false.
            //
            // The true branch waits on a handle that owns a resource -- a cipher's
            // key schedule is the C's own example -- which waits on a cipher. The
            // function is complete; what is missing is a producer for the other
            // kind.
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
            // `handle [handle!]`
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
            // `if (!IS_LEX_WORD(cp[1]) && cp[1] != '/' && cp[1] != '?' && cp[1] !=
            // '!') { cp++; len--; continue; }` -- which is what lets arithmetic
            // through: `a < b` holds no tag.
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
            // `if (*cp == '"' || *cp == '\\'') { quote = *cp++; for (len--; len > 0
            // && *cp != quote; len--, cp++); }`
            assertThat(decoded("<a href='x>y'>"))
                    .isEqualTo("\"[<a href='x>y'>]\"");
        }

        @Test
        @DisplayName("a comment runs to its own ending and not to the first bracket")
        void aCommentRunsToItsEnd() {
            // `if (*cp == '!' && len > 7 && cp[1] == '-' && cp[2] == '-')` and then
            // a scan for `-->`. So a `>` inside a comment does not end it.
            assertThat(decoded("<!-- a > b -->after"))
                    .isEqualTo("{[<!-- a > b --> \"after\"]}");
        }

        @Test
        @DisplayName("and a tag that never closes is treated as text")
        void anUnclosedTagIsText() {
            // "Note: if final tag does not end, then it is treated as text." The
            // loop runs out and the trailing `if (cp != bp) Append_Markup(series,
            // REB_STRING, ...)` picks up what is left.
            assertThat(decoded("a<b")).isEqualTo("{[\"a\" \"<b\"]}");
        }
    }
}
