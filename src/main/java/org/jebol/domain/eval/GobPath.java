package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

final class GobPath {

    private GobPath() {
    }

    static Value read(GobValue gob, Value selector) {
        if (selector instanceof WordValue asked) {
            return field(gob, asked);
        }
        if (!(selector instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "cannot select " + selector.datatype().literalSpelling()
                            + " from a gob");
        }
        return childOf(gob, magnitude);
    }

    static Value childOf(GobValue gob, long count) {
        long at = (gob.index() - 1) + count;
        if (aCountBelowOneDoesNotReachBehindTheGob(at, gob)) {
            return NoneValue.none();
        }
        return gob.storage().childAt((int) at);
    }

    private static boolean aCountBelowOneDoesNotReachBehindTheGob(
            long at, GobValue gob) {
        return at < 1 || at > gob.storage().length();
    }

    static Value field(GobValue gob, WordValue asked) {
        GobStorage storage = gob.storage();
        return switch (asked.canonical()) {
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
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH, asked.spelling());
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

    static void write(GobValue gob, WordValue asked, Value written) {
        if (!accepted(gob.storage(), asked.canonical(), written)) {
            throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                    asked.spelling() + " will not hold "
                            + written.datatype().literalSpelling());
        }
    }

    static boolean accepted(GobStorage storage, String field, Value written) {
        return switch (field) {
            case "offset" -> asPairWhereALoneNumberIsBothHalves(written).map(pair -> {
                storage.offset(pair);
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
                storage.alpha(magnitude);
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

    private static java.util.Optional<PairValue> asPairWhereALoneNumberIsBothHalves(
            Value written) {
        if (written instanceof PairValue pair) {
            return java.util.Optional.of(pair);
        }
        if (written instanceof IntegerValue(long magnitude)) {
            return java.util.Optional.of(PairValue.square(magnitude));
        }
        if (written instanceof DecimalValue fraction) {
            return java.util.Optional.of(PairValue.square(fraction.quantity()));
        }
        return java.util.Optional.empty();
    }

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

    private static boolean writtenColour(GobStorage storage, Value written) {
        if (written instanceof TupleValue colour) {
            int[] parts = colour.segments();
            storage.content(GobStorage.Content.COLOUR, TupleValue.of(
                    partOr(parts, 0), partOr(parts, 1), partOr(parts, 2),
                    parts.length > 3 ? parts[3] : OPAQUE_WHERE_NO_FOURTH_PART));
            return true;
        }
        emptied(storage, written);
        return anythingElseIsAcceptedAndIgnored();
    }

    private static final int OPAQUE_WHERE_NO_FOURTH_PART = 0xFF;

    private static boolean anythingElseIsAcceptedAndIgnored() {
        return true;
    }

    private static int partOr(int[] parts, int at) {
        return at < parts.length ? parts[at] : 0;
    }

    private static boolean emptied(GobStorage storage, Value written) {
        if (written instanceof NoneValue) {
            storage.content(GobStorage.Content.NONE, NoneValue.none());
            return true;
        }
        return false;
    }

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
        replaceRatherThanAddTo(storage);
        for (Value child : children) {
            storage.insertChild(storage.length() + 1, (GobValue) child);
        }
        return true;
    }

    private static void replaceRatherThanAddTo(GobStorage storage) {
        storage.removeChildren(1, storage.length());
    }

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

    private static boolean writtenFlags(GobStorage storage, Value written) {
        if (written instanceof WordValue aLoneWordAddsToWhatIsThere) {
            raiseUnlessTheWordIsNoFlag(storage, aLoneWordAddsToWhatIsThere);
            return true;
        }
        if (written instanceof BlockValue aBlockStartsFromNothing) {
            storage.lowerEveryFlag();
            for (Value item : aBlockStartsFromNothing.remaining()) {
                if (item instanceof WordValue word) {
                    raiseUnlessTheWordIsNoFlag(storage, word);
                }
            }
            return true;
        }
        return anythingElseIsAcceptedAndIgnored();
    }

    private static void raiseUnlessTheWordIsNoFlag(
            GobStorage storage, WordValue word) {
        GobStorage.Flag flag = GobStorage.Flag.named(word.canonical());
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
