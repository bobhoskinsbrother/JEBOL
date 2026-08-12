package org.jebol.domain.eval;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Value;

/**
 * The two codecs the boot registers, from {@code Init_Codecs} in {@code b-init.c}.
 *
 * <p>{@code Register_Codec("text", Codec_Text)} and the same for markup, and each
 * puts a handle of type {@code codec} into {@code system/codecs}. Those handles
 * are what DO-CODEC is given, and registering them is what makes the datatype
 * reachable at all: nothing else in a build without the crypto family or an
 * extension API produces a handle.
 *
 * <p>Every other codec in the C -- png, gif, jpeg, bmp, qoi, wav -- sits behind an
 * {@code #ifdef INCLUDE_*_CODEC}, so these two are the ones a stock build always
 * has.
 *
 * <p>A codec answers one of a small set of results and DO-CODEC turns that into a
 * value. Three of them appear here: CODI_CHECK for an identify, CODI_STRING and
 * CODI_BLOCK for the two decodes.
 */
final class Codecs {

    private Codecs() {
    }

    /** What a codec was asked to do. {@code CODI_IDENTIFY}, and the other two. */
    enum Action { IDENTIFY, DECODE, ENCODE }

    /**
     * What a codec answered, and what DO-CODEC does with it.
     *
     * <p>{@code CODI_*} in reb-codec.h. A codec answers one of these and sets an
     * error code beside it, and the pair decides the result: a non-zero error is
     * {@code bad-media} unless the answer was CHECK, in which case it is the
     * <em>inverted</em> result -- "error code is inverted result", says the header,
     * so identify says yes by setting no error.
     */
    record Answer(Kind kind, Value value, int error) {

        enum Kind { ERROR, CHECK, BINARY, TEXT, IMAGE, SOUND, BLOCK, STRING }

        static Answer check(int error) {
            return new Answer(Kind.CHECK, LogicValue.of(error == 0), error);
        }

        static Answer string(Value text) {
            return new Answer(Kind.STRING, text, 0);
        }

        static Answer block(Value items) {
            return new Answer(Kind.BLOCK, items, 0);
        }

        static Answer binary(Value bytes) {
            return new Answer(Kind.BINARY, bytes, 0);
        }

        /** {@code codi->error = CODI_ERR_NA; return CODI_ERROR;} */
        static Answer notAvailable() {
            return new Answer(Kind.ERROR, LogicValue.no(), NOT_AVAILABLE);
        }
    }

    /** {@code CODI_ERR_NA = 1, // Feature not available}. */
    private static final int NOT_AVAILABLE = 1;

    /** The codec names {@code Init_Codecs} registers, in the order it does. */
    static final List<String> REGISTERED = List.of("text", "markup");

    /** Runs a registered codec, or nothing when the name is not one. */
    static Answer run(String codec, Action action, Value data) {
        return switch (codec) {
            case "text" -> text(action, data);
            case "markup" -> markup(action, data);
            default -> Answer.notAvailable();
        };
    }

    /**
     * {@code Codec_Text}: bytes in, a string out.
     *
     * <p>Identify answers yes for anything, because any bytes are text as far as
     * this is concerned -- `if (codi->action == CODI_IDENTIFY) return CODI_CHECK;`
     * with the error left at zero.
     *
     * <p>Encode answers an empty binary, and the C's own comment says why it does
     * not matter: "this does not happen as in n-system.c only image is allowed to
     * be encoded!". So the branch is reachable only through an image, and an image
     * is not text, and it returns nothing.
     */
    private static Answer text(Action action, Value data) {
        return switch (action) {
            case IDENTIFY -> Answer.check(0);
            case DECODE -> Answer.string(StringValue.of(
                    new String(bytesOf(data), StandardCharsets.UTF_8)));
            case ENCODE -> Answer.binary(BinaryValue.of());
        };
    }

    /**
     * {@code Codec_Markup}: bytes in, a block of strings and tags out.
     *
     * <p>Identify always answers <em>no</em>, and the C says why in a comment:
     * "never identified... would require markup validator". It sets
     * {@code codi->error = 1} and returns CODI_CHECK, and the error is the inverted
     * result -- so this is the one codec whose identify cannot say yes.
     *
     * <p>Encode is not implemented and answers {@code CODI_ERR_NA}, which DO-CODEC
     * turns into {@code bad-media}.
     */
    private static Answer markup(Action action, Value data) {
        return switch (action) {
            case IDENTIFY -> Answer.check(1);
            case DECODE -> Answer.block(BlockValue.block(
                    markupOf(new String(bytesOf(data), StandardCharsets.UTF_8))));
            case ENCODE -> Answer.notAvailable();
        };
    }

    private static byte[] bytesOf(Value data) {
        BinaryValue binary = (BinaryValue) data;
        byte[] bytes = new byte[binary.lengthFromHere()];
        for (int at = 0; at < bytes.length; at++) {
            bytes[at] = (byte) binary.storage().at(binary.index() + at);
        }
        return bytes;
    }

    /**
     * HTML or XML split into strings and tags. {@code Load_Markup}.
     *
     * <p>Not a parser. It looks for a less-than sign, and takes what follows as a
     * tag only if the next character could start one: a word character, a slash, a
     * question mark or an exclamation mark. Anything else and the sign is ordinary
     * text -- which is what lets `a < b` through unharmed.
     *
     * <p>Two details past that. A comment runs to `-->` rather than to the first
     * `>`, and a quoted attribute value can hold a `>` without ending the tag. And
     * a tag that never closes is text, because the loop runs out and the trailing
     * `if (cp != bp)` appends what is left as a string.
     */
    static List<Value> markupOf(String source) {
        List<Value> parts = new ArrayList<>();
        int textFrom = 0;
        int at = 0;
        while (at < source.length()) {
            int opened = source.indexOf('<', at);
            if (opened < 0) {
                break;
            }
            if (!couldStartATag(source, opened + 1)) {
                at = opened + 1;
                continue;
            }
            if (opened > textFrom) {
                parts.add(StringValue.of(source.substring(textFrom, opened)));
            }
            int closed = endOfTag(source, opened + 1);
            if (closed < 0) {
                // "Note: if final tag does not end, then it is treated as text."
                textFrom = opened;
                at = source.length();
                break;
            }
            parts.add(StringValue.of(
                    source.substring(opened + 1, closed), Datatype.TAG));
            textFrom = closed + 1;
            at = textFrom;
        }
        if (textFrom < source.length()) {
            parts.add(StringValue.of(source.substring(textFrom)));
        }
        return parts;
    }

    /**
     * Whether what follows a less-than sign could begin a tag.
     *
     * <p>{@code if (!IS_LEX_WORD(cp[1]) && cp[1] != '/' && cp[1] != '?' && cp[1] !=
     * '!') { cp++; len--; continue; }} -- so `<` at the very end, or before a space
     * or a digit, is text.
     */
    private static boolean couldStartATag(String source, int at) {
        if (at >= source.length()) {
            return false;
        }
        char next = source.charAt(at);
        return Character.isLetter(next) || next == '/' || next == '?' || next == '!';
    }

    /**
     * Where the tag that starts here ends, or -1 when it does not.
     *
     * <p>A comment is looked for first and runs to `-->`. Otherwise the scan walks
     * to the closing `>`, skipping any quoted run so an attribute value may hold
     * one.
     */
    private static int endOfTag(String source, int from) {
        if (source.startsWith("!--", from)) {
            int ended = source.indexOf("-->", from + 3);
            return ended < 0 ? -1 : ended + 2;
        }
        for (int at = from; at < source.length(); at++) {
            char here = source.charAt(at);
            if (here == '>') {
                return at;
            }
            if (here == '"' || here == '\'') {
                int closed = source.indexOf(here, at + 1);
                if (closed < 0) {
                    return -1;
                }
                at = closed;
            }
        }
        return -1;
    }
}
