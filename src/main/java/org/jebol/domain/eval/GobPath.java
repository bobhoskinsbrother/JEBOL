package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A gob's fields and its children, from {@code PD_Gob}, {@code Get_GOB_Var} and
 * {@code Set_GOB_Var}.
 *
 * <p>Two kinds of selector, and they ask about different things. A word names a
 * field of the gob. A number names a child in its pane, counted from where the
 * gob stands, and answers none rather than raising when there is no such child.
 *
 * <p>The five content fields -- {@code image}, {@code draw}, {@code text},
 * {@code effect}, {@code color} -- share one slot and one type tag, so writing
 * any of them takes away whatever was there and reading the one it has not got
 * answers none. {@code data} looks like a sixth and is not: it has a slot and a
 * tag of its own, which is why a gob can carry a draw block and a block of data
 * at the same time.
 *
 * <p>Three fields break the symmetry between reading and writing. {@code parent}
 * can be read and not written, because being in a pane is what sets it.
 * {@code owner} can be written and not read, because {@code Get_GOB_Var} has no
 * case for it. And {@code flags} answers a block built from a fixed table, so it
 * comes back in the table's order whatever order it was written in.
 */
final class GobPath {

    private GobPath() {
    }

    /** What a path segment answers, or none where the child is not there. */
    static Value read(GobValue gob, Value selector) {
        if (selector instanceof WordValue named) {
            return field(gob, named);
        }
        if (!(selector instanceof IntegerValue position)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "cannot select " + selector.datatype().literalSpelling()
                            + " from a gob");
        }
        return childOf(gob, position.magnitude());
    }

    /**
     * The child a count names, or none.
     *
     * <p>{@code index += Int32(pvs->select) - 1; if (index >= tail) return
     * PE_NONE;} with {@code index} unsigned, so a zero or a negative count wraps
     * to something enormous and fails that test. A gob is the one series here
     * where a negative position does not reach behind where it stands.
     */
    static Value childOf(GobValue gob, long count) {
        long at = (gob.index() - 1) + count;
        if (at < 1 || at > gob.storage().length()) {
            return NoneValue.none();
        }
        return gob.storage().childAt((int) at);
    }

    /** What {@code Get_GOB_Var} answers, raising for a name it has not got. */
    static Value field(GobValue gob, WordValue named) {
        GobStorage storage = gob.storage();
        return switch (named.canonical()) {
            case "offset" -> storage.offset();
            case "size" -> storage.size();
            case "alpha" -> IntegerValue.of(storage.alpha());
            case "image" -> storage.contentIfKind(GobStorage.Content.IMAGE);
            case "draw" -> storage.contentIfKind(GobStorage.Content.DRAW);
            case "text" -> storage.contentKind() == GobStorage.Content.STRING
                    ? storage.contentIfKind(GobStorage.Content.STRING)
                    : storage.contentIfKind(GobStorage.Content.TEXT);
            case "effect" -> storage.contentIfKind(GobStorage.Content.EFFECT);
            case "color" -> storage.contentIfKind(GobStorage.Content.COLOUR);
            case "pane" -> BlockValue.block(storage.pane());
            case "parent" -> parentOf(storage);
            case "data" -> storage.data();
            case "flags" -> flagsOf(storage);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, named.spelling());
        };
    }

    private static Value parentOf(GobStorage storage) {
        return storage.parent() == null
                ? NoneValue.none()
                : new GobValue(storage.parent(), 1);
    }

    private static Value flagsOf(GobStorage storage) {
        List<Value> words = new ArrayList<>();
        for (GobStorage.Flag flag : storage.raisedFlags()) {
            words.add(WordValue.of(flag.spelling()));
        }
        return BlockValue.block(words);
    }

    /**
     * Writes one field, raising for a name or a value the gob will not take.
     *
     * <p>{@code if (!Set_GOB_Var(gob, pvs->select, pvs->setval)) return
     * PE_BAD_SET;}, and the same FALSE from the same function is what
     * {@code Set_GOB_Vars} turns into {@code bad-field-set} while making one. The
     * two errors differ in name and not in cause, so both come through here.
     */
    static void write(GobValue gob, WordValue named, Value written) {
        if (!accepted(gob.storage(), named.canonical(), written)) {
            throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                    named.spelling() + " will not hold "
                            + written.datatype().literalSpelling());
        }
    }

    /**
     * One field written, answering whether the gob took it.
     *
     * <p>FALSE where {@code Set_GOB_Var} returns FALSE, which is a field it has
     * not got or a value that field will not hold. Two of the arms have no such
     * return and so accept anything: a colour that is not a tuple and a flags
     * that is neither a word nor a block are quietly ignored, which reads like an
     * oversight in the C and is what a script sees.
     */
    static boolean accepted(GobStorage storage, String field, Value written) {
        return switch (field) {
            case "offset" -> asPair(written).map(pair -> {
                storage.offset(pair);
                return true;
            }).orElse(false);
            case "size" -> asPair(written).map(pair -> {
                storage.size(pair);
                return true;
            }).orElse(false);
            case "alpha" -> {
                if (!(written instanceof IntegerValue given)) {
                    yield false;
                }
                storage.alpha(given.magnitude());
                yield true;
            }
            case "image" -> writtenImage(storage, written);
            case "draw" -> oneBlockContent(storage, GobStorage.Content.DRAW, written);
            case "effect" -> oneBlockContent(storage, GobStorage.Content.EFFECT, written);
            case "text" -> writtenText(storage, written);
            case "color" -> writtenColour(storage, written);
            case "pane" -> writtenPane(storage, written);
            case "data" -> writtenData(storage, written);
            case "flags" -> writtenFlags(storage, written);
            case "owner" -> {
                if (!(written instanceof GobValue owner)) {
                    yield false;
                }
                storage.owner(owner.storage());
                yield true;
            }
            default -> false;
        };
    }

    /**
     * A pair, or a lone number as both halves.
     *
     * <p>{@code Set_Pair} takes three things: a pair, an integer, a decimal.
     * {@code pair->x = pair->y = (REBD32)VAL_INT64(val)} is the shorthand, so
     * {@code size: 7} is seven square.
     */
    private static java.util.Optional<PairValue> asPair(Value written) {
        if (written instanceof PairValue pair) {
            return java.util.Optional.of(pair);
        }
        if (written instanceof IntegerValue whole) {
            return java.util.Optional.of(PairValue.square(whole.magnitude()));
        }
        if (written instanceof DecimalValue fraction) {
            return java.util.Optional.of(PairValue.square(fraction.quantity()));
        }
        return java.util.Optional.empty();
    }

    /** An image also sets the gob's shape: `GOB_W(gob) = VAL_IMAGE_WIDE(val)`. */
    private static boolean writtenImage(GobStorage storage, Value written) {
        if (written instanceof ImageValue image) {
            storage.content(GobStorage.Content.IMAGE, image);
            storage.size(PairValue.of(image.storage().wide(), image.storage().high()));
            return true;
        }
        return emptied(storage, written);
    }

    private static boolean oneBlockContent(
            GobStorage storage, GobStorage.Content kind, Value written) {
        if (written instanceof BlockValue block && block.datatype() == Datatype.BLOCK) {
            storage.content(kind, block);
            return true;
        }
        return emptied(storage, written);
    }

    private static boolean writtenText(GobStorage storage, Value written) {
        if (written instanceof BlockValue block && block.datatype() == Datatype.BLOCK) {
            storage.content(GobStorage.Content.TEXT, block);
            return true;
        }
        if (written instanceof StringValue text && text.datatype() == Datatype.STRING) {
            storage.content(GobStorage.Content.STRING, text);
            return true;
        }
        return emptied(storage, written);
    }

    /**
     * A colour, kept as a pixel.
     *
     * <p>{@code Set_Pixel_Tuple} writes four bytes and puts 0xFF in the fourth
     * when the tuple was shorter, and {@code Set_Tuple_Pixel} reads all four
     * back. So the alpha a script never wrote is the alpha it reads.
     *
     * <p>Anything that is neither a tuple nor none is accepted and ignored: that
     * arm of the C ends in a plain {@code break} where every other content arm
     * ends in {@code return FALSE}.
     */
    private static boolean writtenColour(GobStorage storage, Value written) {
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            storage.content(GobStorage.Content.COLOUR, TupleValue.of(
                    partOr(parts, 0), partOr(parts, 1), partOr(parts, 2),
                    parts.length > 3 ? parts[3] : 0xFF));
            return true;
        }
        emptied(storage, written);
        return true;
    }

    private static int partOr(int[] parts, int at) {
        return at < parts.length ? parts[at] : 0;
    }

    /** None takes the content away: `SET_GOB_TYPE(gob, GOBT_NONE)`. */
    private static boolean emptied(GobStorage storage, Value written) {
        if (written instanceof NoneValue) {
            storage.content(GobStorage.Content.NONE, NoneValue.none());
            return true;
        }
        return false;
    }

    /**
     * The whole pane at once.
     *
     * <p>{@code if (GOB_PANE(gob)) Clear_Series(GOB_PANE(gob));} first, so this
     * replaces the children rather than adding to them.
     */
    private static boolean writtenPane(GobStorage storage, Value written) {
        List<Value> children;
        if (written instanceof BlockValue block) {
            children = block.remaining();
        } else if (written instanceof GobValue only) {
            children = List.of(only);
        } else if (written instanceof NoneValue) {
            children = List.of();
        } else {
            return false;
        }
        for (Value child : children) {
            if (!(child instanceof GobValue)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "a pane holds gobs, not "
                                + child.datatype().literalSpelling());
            }
        }
        storage.removeChildren(1, storage.length());
        for (Value child : children) {
            storage.insertChild(storage.length() + 1, (GobValue) child);
        }
        return true;
    }

    /** The five things `data` holds, each with its own {@code GOBD} tag. */
    private static boolean writtenData(GobStorage storage, Value written) {
        GobStorage.Held kind = switch (written) {
            case ObjectValue ignored -> GobStorage.Held.OBJECT;
            case BinaryValue ignored -> GobStorage.Held.BINARY;
            case IntegerValue ignored -> GobStorage.Held.INTEGER;
            case BlockValue block when block.datatype() == Datatype.BLOCK ->
                    GobStorage.Held.BLOCK;
            case StringValue text when text.datatype() == Datatype.STRING ->
                    GobStorage.Held.STRING;
            default -> GobStorage.Held.NONE;
        };
        if (kind == GobStorage.Held.NONE) {
            return emptied(storage, written);
        }
        storage.data(kind, written);
        return true;
    }

    /**
     * The flag words.
     *
     * <p>A block starts from nothing -- {@code gob->flags = 0;} before the loop
     * -- and a lone word adds to what is there. {@code Set_Gob_Flag} walks a
     * table of nine and stops at the end, so a word that is not a flag does
     * nothing at all.
     */
    private static boolean writtenFlags(GobStorage storage, Value written) {
        if (written instanceof WordValue only) {
            raise(storage, only);
            return true;
        }
        if (written instanceof BlockValue block) {
            storage.lowerEveryFlag();
            for (Value item : block.remaining()) {
                if (item instanceof WordValue word) {
                    raise(storage, word);
                }
            }
            return true;
        }
        return true;
    }

    private static void raise(GobStorage storage, WordValue word) {
        GobStorage.Flag flag = GobStorage.Flag.named(word.canonical());
        if (flag != null) {
            storage.raise(flag);
        }
    }

    /**
     * Puts a child at a position, which is what POKE and CHANGE both do.
     *
     * <p>They insert rather than replace. The C has the replacing code beside it
     * and commented out, so {@code poke g 1 child} makes the pane one longer.
     */
    static void poke(GobValue gob, int oneBasedIndex, Value written) {
        if (!(written instanceof GobValue child)) {
            throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    "a pane holds gobs, not " + written.datatype().literalSpelling());
        }
        if (oneBasedIndex < 1 || oneBasedIndex > gob.storage().length()) {
            throw Raised.of(EvaluationFailure.PAST_END,
                    "there is no child at " + oneBasedIndex + " to change");
        }
        gob.storage().insertChild(oneBasedIndex, child);
    }

}
