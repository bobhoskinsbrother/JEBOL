package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A position into block storage, reported as one of the {@code any-block!}
 * datatypes: {@code block!}, {@code paren!}, or one of the four path types.
 *
 * <p>They share a representation because they are the same thing read
 * differently. A path is a block whose items are selectors, which is why
 * {@code first 'face/color} gives you a word.
 */
public record BlockValue(BlockStorage storage, int index, Datatype datatype)
        implements RebolSeries {

    public BlockValue {
        if (storage == null) {
            throw new IllegalArgumentException("a block value needs storage");
        }
        if (!datatype.isAnyBlock()) {
            throw new IllegalArgumentException(
                    datatype.literalSpelling() + " is not an any-block! datatype");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    @Override
    public boolean equalTo(Value other, Sameness how) {
        if (!(other instanceof BlockValue theirs) || datatype != theirs.datatype) {
            return false;
        }
        List<Value> ours = remaining();
        List<Value> yours = theirs.remaining();
        if (ours.size() != yours.size()) {
            return false;
        }
        for (int at = 0; at < ours.size(); at++) {
            if (!ours.get(at).equalTo(yours.get(at), Sameness.insideASeries())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Value asASetWith(Value other, MembersKept keeping, boolean mindingCase) {
        return switch (other) {
            case StringValue ignored -> StringValue.textOf(
                    keeping.from(StringValue.charactersOf(this),
                            StringValue.charactersOf(other)),
                    Datatype.STRING);
            case MapValue theirs -> MapValue.empty()
                    .combinedWith(theirs, keeping.how(), mindingCase);
            case BlockValue theirs -> BlockValue.block(
                    keeping.from(remaining(), theirs.remaining()));
            default -> throw Raised.cannotUse(other, "a set operation");
        };
    }

    public static BlockValue block(Value... items) {
        return new BlockValue(BlockStorage.of(items), 1, Datatype.BLOCK);
    }

    public static BlockValue block(List<Value> items) {
        return new BlockValue(new BlockStorage(items), 1, Datatype.BLOCK);
    }

    public static BlockValue paren(List<Value> items) {
        return new BlockValue(new BlockStorage(items), 1, Datatype.PAREN);
    }

    public static BlockValue path(List<Value> segments, Datatype pathDatatype) {
        if (!pathDatatype.isAnyPath()) {
            throw new IllegalArgumentException(
                    pathDatatype.literalSpelling() + " is not an any-path! datatype");
        }
        return new BlockValue(new BlockStorage(segments), 1, pathDatatype);
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public BlockValue atIndex(int oneBasedIndex) {
        return new BlockValue(storage, oneBasedIndex, datatype);
    }

    @Override
    public BlockValue head() {
        return atIndex(1);
    }

    @Override
    public BlockValue tail() {
        return atIndex(storage.length() + 1);
    }

    /** The same storage and position, read as a different any-block! type. */
    public BlockValue as(Datatype otherDatatype) {
        return new BlockValue(storage, index, otherDatatype);
    }

    /** The item at this position. Fails at the tail, which holds nothing. */
    public Value first() {
        if (atTail()) {
            throw new IllegalStateException("nothing to read at the tail");
        }
        return storage.at(index);
    }

    /** The items from this position to the tail. */
    public List<Value> remaining() {
        return storage.snapshot().subList(
                Math.min(index - 1, storage.length()), storage.length());
    }

    /**
     * The set-words from this position on, in the order they are written.
     *
     * <p>What a loader walks to give a name a slot before the body that
     * assigns it runs.
     */
    public List<WordValue> setWordsFromHere() {
        return remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .filter(word -> word.datatype() == Datatype.SET_WORD)
                .toList();
    }

    public List<Value> wordsWritten(boolean deeply, boolean settersOnly) {
        List<Value> found = new ArrayList<>();
        gatherWordsInto(found, deeply, settersOnly);
        return found;
    }

    private void gatherWordsInto(List<Value> found, boolean deeply, boolean settersOnly) {
        for (Value item : remaining()) {
            if (item instanceof BlockValue nested) {
                if (deeply) {
                    nested.gatherWordsInto(found, true, settersOnly);
                }
                continue;
            }
            if (item instanceof WordValue word
                    && (!settersOnly || word.datatype() == Datatype.SET_WORD)
                    && isNotYetAmong(found, word)) {
                found.add(WordValue.of(word.spelling()));
            }
        }
    }

    private static boolean isNotYetAmong(List<Value> found, WordValue word) {
        return found.stream().noneMatch(seen -> seen instanceof WordValue already
                && already.canonical().equals(word.canonical()));
    }

    public Optional<ContextSlot> fieldThePathNames() {
        List<Value> segments = remaining();
        if (!datatype.isAnyPath() || segments.size() < 2
                || !(segments.getFirst() instanceof WordValue start)
                || !start.isBound() || !start.binding().knows(start.canonical())) {
            return Optional.empty();
        }
        Value reached = start.binding().slotFor(start.canonical()).value();
        for (Value between : segments.subList(1, segments.size() - 1)) {
            Optional<ContextSlot> field = theFieldNamed(reached, between);
            if (field.isEmpty()) {
                return Optional.empty();
            }
            reached = field.get().value();
        }
        return theFieldNamed(reached, segments.getLast());
    }

    private static Optional<ContextSlot> theFieldNamed(Value holder, Value name) {
        return holder instanceof ObjectValue(Context context)
                && name instanceof WordValue word
                && context.holds(word.canonical())
                ? Optional.of(context.ownSlotFor(word.canonical()))
                : Optional.empty();
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof BlockValue block && block.storage == storage;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockValue block
                && block.datatype == datatype
                && block.remaining().equals(remaining());
    }

    @Override
    public int hashCode() {
        return datatype.hashCode() * 31 + remaining().hashCode();
    }

    @Override
    public String toString() {
        return datatype.literalSpelling() + "@" + index;
    }
}
