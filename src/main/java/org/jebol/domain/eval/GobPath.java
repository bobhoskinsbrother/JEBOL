package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

public final class GobPath {

    private GobPath() {
    }

    static Value read(GobValue gob, Value selector) {
        if (selector instanceof AnyWordValue asked) {
            return readField(gob, asked);
        }
        if (!(selector instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "cannot select " + selector.datatype().literalSpelling()
                            + " from a gob");
        }
        return childAt(gob, magnitude);
    }

    static Value childAt(GobValue gob, long count) {
        long at = (gob.index() - 1) + count;
        if (isACountBelowOne(at, gob)) {
            return NoneValue.none();
        }
        return gob.storage().readChildAt((int) at);
    }

    private static boolean isACountBelowOne(
            long at, GobValue gob) {
        return at < 1 || at > gob.storage().length();
    }

    static Value readField(GobValue gob, AnyWordValue asked) {
        GobStorage storage = gob.storage();
        return switch (asked.canonical()) {
            case "offset" -> storage.offset();
            case "size" -> storage.size();
            case "alpha" -> IntegerValue.of(storage.alpha());
            case "image" -> storage.readContentIfKind(GobStorage.Content.IMAGE);
            case "draw" -> storage.readContentIfKind(GobStorage.Content.DRAW);
            case "text" -> storage.contentKind() == GobStorage.Content.STRING
                    ? storage.readContentIfKind(GobStorage.Content.STRING)
                    : storage.readContentIfKind(GobStorage.Content.TEXT);
            case "effect" -> storage.readContentIfKind(GobStorage.Content.EFFECT);
            case "color" -> storage.readContentIfKind(GobStorage.Content.COLOUR);
            case "pane" -> BlockValue.of(storage.pane());
            case "parent" -> findTheParentOf(storage);
            case "data" -> storage.data();
            case "flags" -> raisedFlagWords(storage);
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, asked.spelling());
        };
    }

    private static Value findTheParentOf(GobStorage storage) {
        return storage.parent() == null
                ? NoneValue.none()
                : new GobValue(storage.parent(), 1);
    }

    private static Value raisedFlagWords(GobStorage storage) {
        List<Value> words = new ArrayList<>();
        for (GobStorage.Flag flag : storage.listTheRaisedFlags()) {
            words.add(WordValue.of(flag.spelling()));
        }
        return BlockValue.of(words);
    }

    static void write(GobValue gob, AnyWordValue asked, Value written) {
        if (!accepts(gob.storage(), asked.canonical(), written)) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        }
    }

    public static boolean accepts(GobStorage storage, String field, Value written) {
        return switch (field) {
            case "offset" -> asPairWhereALoneNumberIsBothHalves(written).map(pair -> {
                storage.setTheOffset(pair);
                return true;
            }).orElse(false);
            case "size" -> asPairWhereALoneNumberIsBothHalves(written).map(pair -> {
                storage.size(pair);
                return true;
            }).orElse(false);
            case "alpha" -> {
                if (!(written instanceof IntegerValue(long magnitude))) {
                    yield false;
                }
                storage.setAlpha(magnitude);
                yield true;
            }
            case "image" -> writeImage(storage, written);
            case "draw" -> writeOneBlockContent(storage, GobStorage.Content.DRAW, written);
            case "effect" -> writeOneBlockContent(storage, GobStorage.Content.EFFECT, written);
            case "text" -> writeText(storage, written);
            case "color" -> writeColour(storage, written);
            case "pane" -> writePane(storage, written);
            case "data" -> writeData(storage, written);
            case "flags" -> writeFlags(storage, written);
            case "owner" -> {
                if (!(written instanceof GobValue owner)) {
                    yield false;
                }
                storage.setOwner(owner.storage());
                yield true;
            }
            default -> false;
        };
    }

    private static java.util.Optional<PairValue> asPairWhereALoneNumberIsBothHalves(
            Value written) {
        if (written instanceof PairValue pair) {
            return java.util.Optional.of(pair);
        }
        if (written instanceof IntegerValue(long magnitude)) {
            return java.util.Optional.of(PairValue.square(magnitude));
        }
        if (written instanceof AnyDecimalValue fraction) {
            return java.util.Optional.of(PairValue.square(fraction.quantity()));
        }
        return java.util.Optional.empty();
    }

    private static boolean writeImage(GobStorage storage, Value written) {
        if (written instanceof ImageValue image) {
            storage.setContent(GobStorage.Content.IMAGE, image);
            storage.size(PairValue.of(image.storage().wide(), image.storage().high()));
            return true;
        }
        return empty(storage, written);
    }

    private static boolean writeOneBlockContent(
            GobStorage storage, GobStorage.Content kind, Value written) {
        if (written instanceof BlockValue block) {
            storage.setContent(kind, block);
            return true;
        }
        return empty(storage, written);
    }

    private static boolean writeText(GobStorage storage, Value written) {
        if (written instanceof BlockValue block) {
            storage.setContent(GobStorage.Content.TEXT, block);
            return true;
        }
        if (written instanceof StringValue text) {
            storage.setContent(GobStorage.Content.STRING, text);
            return true;
        }
        return empty(storage, written);
    }

    private static boolean writeColour(GobStorage storage, Value written) {
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            storage.setContent(GobStorage.Content.COLOUR, TupleValue.of(
                    partOr(parts, 0), partOr(parts, 1), partOr(parts, 2),
                    parts.length > 3 ? parts[3] : OPAQUE_WHERE_NO_FOURTH_PART));
            return true;
        }
        empty(storage, written);
        return acceptAndIgnoreAnythingElse();
    }

    private static final int OPAQUE_WHERE_NO_FOURTH_PART = 0xFF;

    private static boolean acceptAndIgnoreAnythingElse() {
        return true;
    }

    private static int partOr(int[] parts, int at) {
        return at < parts.length ? parts[at] : 0;
    }

    private static boolean empty(GobStorage storage, Value written) {
        if (written instanceof NoneValue) {
            storage.setContent(GobStorage.Content.NONE, NoneValue.none());
            return true;
        }
        return false;
    }

    private static boolean writePane(GobStorage storage, Value written) {
        List<Value> children;
        if (written instanceof AnyBlockValue block) {
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
        replaceRatherThanAddTo(storage);
        for (Value child : children) {
            storage.insertChild(storage.length() + 1, (GobValue) child);
        }
        return true;
    }

    private static void replaceRatherThanAddTo(GobStorage storage) {
        storage.removeChildren(1, storage.length());
    }

    private static boolean writeData(GobStorage storage, Value written) {
        GobStorage.Held kind = switch (written) {
            case ObjectValue ignored -> GobStorage.Held.OBJECT;
            case BinaryValue ignored -> GobStorage.Held.BINARY;
            case IntegerValue ignored -> GobStorage.Held.INTEGER;
            case BlockValue block ->
                    GobStorage.Held.BLOCK;
            case StringValue ignored ->
                    GobStorage.Held.STRING;
            default -> GobStorage.Held.NONE;
        };
        if (kind == GobStorage.Held.NONE) {
            return empty(storage, written);
        }
        storage.setData(kind, written);
        return true;
    }

    private static boolean writeFlags(GobStorage storage, Value written) {
        if (written instanceof AnyWordValue aLoneWordAddsToWhatIsThere) {
            raiseUnlessWordIsNoFlag(storage, aLoneWordAddsToWhatIsThere);
            return true;
        }
        if (written instanceof AnyBlockValue aBlockStartsFromNothing) {
            storage.lowerEveryFlag();
            for (Value item : aBlockStartsFromNothing.remaining()) {
                if (item instanceof AnyWordValue word) {
                    raiseUnlessWordIsNoFlag(storage, word);
                }
            }
            return true;
        }
        return acceptAndIgnoreAnythingElse();
    }

    private static void raiseUnlessWordIsNoFlag(
            GobStorage storage, AnyWordValue word) {
        GobStorage.Flag flag = GobStorage.Flag.findFlagNamed(word.canonical());
        if (flag != null) {
            storage.raise(flag);
        }
    }

    static void pokeWhichInsertsRatherThanReplaces(
            GobValue gob, int oneBasedIndex, Value written) {
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
