package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record BlockValue(BlockStorage storage, int index, Datatype datatype)
        implements RebolSeries {

    private static final Set<Datatype> DECLARES_A_PARAMETER = Set.of(
            Datatype.WORD, Datatype.REFINEMENT, Datatype.LIT_WORD, Datatype.GET_WORD);

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
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public List<Value> items() {
        return remaining();
    }

    @Override
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        BlockStorage built = new BlockStorage(remaining().stream()
                .map(item -> item.copiedAsAMember(deeply, kinds))
                .toList());
        built.takeLineBreaksFrom(storage, index);
        return new BlockValue(built, 1, datatype);
    }

    public void putEachPairOnALine() {
        for (int at = 1; at <= storageLength(); at += 2) {
            storage.setLineBreakAt(at, true);
        }
    }

    public BlockValue declaredParameters() {
        return block(remaining().stream()
                .filter(item -> item instanceof WordValue word
                        && DECLARES_A_PARAMETER.contains(word.datatype()))
                .toList());
    }

    @Override
    public String runTogether() {
        return remaining().stream()
                .map(Value::runTogether)
                .collect(Collectors.joining(datatype.isAnyPath() ? "/" : ""));
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

    public BlockValue as(Datatype otherDatatype) {
        return new BlockValue(storage, index, otherDatatype);
    }

    public Value first() {
        if (atTail()) {
            throw new IllegalStateException("nothing to read at the tail");
        }
        return storage.at(index);
    }

    public List<Value> remaining() {
        return storage.snapshot().subList(
                Math.min(index - 1, storage.length()), storage.length());
    }

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
