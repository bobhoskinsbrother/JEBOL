package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What a value contributes when it goes into a series.
 *
 * <p>The series decides, not the caller and not the value: a binary takes
 * bytes, so a binary laid into one contributes its own, a character its UTF-8
 * and a whole number the single byte it names. A string takes characters, so
 * every one of those contributes the text it FORMs to instead -- and
 * {@code #{FFFF}} is honestly two bytes in the first and four letters in the
 * second.
 *
 * <p>Here rather than beside either caller because there are two, and they had
 * drifted. INSERT and CHANGE reach it through the natives, and PARSE's own
 * INSERT and CHANGE reach the same rule through {@code Modify_String} in the
 * C; JEBOL's PARSE had written its own, which formed everything into text
 * whatever it was going into. Rebol's own quoted-printable encoder folds long
 * lines with {@code insert #{3D0D0A}} inside a PARSE over a binary, and got
 * the six letters of that hex in the message instead of a soft line break.
 */
public final class SeriesContents {

    private SeriesContents() {
    }

    /** Every octet, with no bound on how many are taken. */
    public static int[] octetsContributedBy(Value value) {
        return octetsContributedBy(value, EVERY_ONE);
    }

    /** {@code /PART} asking for no particular number of them. */
    public static final int EVERY_ONE = -1;

    /**
     * The bytes a value contributes when it goes into a binary.
     *
     * <p>{@code Join_Binary} in {@code s-make.c}, and the branches of
     * {@code Modify_String} that run when the target is a binary. One rule
     * underneath all of it: text becomes its UTF-8 bytes, so a character above
     * the ASCII range contributes several bytes rather than one. Writing the
     * code point straight in gives one byte and is right for every ASCII
     * character, which is what makes it hard to notice.
     *
     * <p>{@code howMany} is a {@code /part} count of the source, or
     * {@link #EVERY_ONE} for all of it. It counts characters of the source and
     * the encoding happens afterwards, so one character of U+2190 still
     * contributes three bytes. A character value is not a series and ignores
     * the count entirely.
     */
    public static int[] octetsContributedBy(Value value, int howMany) {
        List<Integer> octets = new ArrayList<>();
        gatherOctets(value, howMany, octets);
        int[] gathered = new int[octets.size()];
        for (int at = 0; at < gathered.length; at++) {
            gathered[at] = octets.get(at);
        }
        return gathered;
    }

    /**
     * The characters a value contributes to a string, which is what it FORMs
     * to.
     *
     * <p>A block runs its items together with spaces between, the way FORM
     * does everywhere, so {@code insert [1 2]} into text puts "1 2" in.
     */
    public static int[] charactersContributedBy(Value value) {
        return Molder.form(value).codePoints().toArray();
    }

    private static void gatherOctets(Value value, int howMany, List<Integer> into) {
        switch (value) {
            case BinaryValue source -> {
                int taking = howMany < 0
                        ? source.lengthFromHere()
                        : Math.min(howMany, source.lengthFromHere());
                for (int at = 0; at < taking; at++) {
                    into.add(source.storage().at(source.index() + at) & 0xFF);
                }
            }
            case StringValue text -> {
                String held = text.text();
                int taking = howMany < 0
                        ? held.length()
                        : Math.min(howMany, held.length());
                addUtf8(held.substring(0, taking), into);
            }
            case CharacterValue letter ->
                    addUtf8(Character.toString(letter.codepoint()), into);
            case IntegerValue whole -> {
                if (whole.magnitude() < 0 || whole.magnitude() > 255) {
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                            "a byte is 0 to 255, not " + whole.magnitude());
                }
                into.add((int) whole.magnitude());
            }
            case TupleValue tuple -> {
                for (int at = 1; at <= tuple.segmentCount(); at++) {
                    into.add(tuple.octetAt(at));
                }
            }
            case BlockValue several -> {
                List<Value> items = several.remaining();
                int taking = howMany < 0 ? items.size() : Math.min(howMany, items.size());
                for (int at = 0; at < taking; at++) {
                    if (items.get(at) instanceof BlockValue nested) {
                        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                nested.datatype().literalSpelling()
                                        + " cannot go into a binary");
                    }
                    gatherOctets(items.get(at), EVERY_ONE, into);
                }
            }
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " cannot go into a binary");
        }
    }

    private static void addUtf8(String text, List<Integer> into) {
        for (byte encoded : text.getBytes(StandardCharsets.UTF_8)) {
            into.add(encoded & 0xFF);
        }
    }
}
