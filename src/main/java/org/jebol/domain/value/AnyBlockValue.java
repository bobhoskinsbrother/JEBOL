package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

public abstract sealed class AnyBlockValue implements RebolSeries
        permits BlockValue, ParenValue, HashValue, AnyPathValue {

    private final BlockStorage storage;
    private final int index;

    AnyBlockValue(BlockStorage storage, int index) {
        if (storage == null) {
            throw new IllegalArgumentException("a block value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
        this.storage = storage;
        this.index = index;
    }

    @Override
    public abstract Datatype datatype();

    abstract AnyBlockValue sameKindOver(BlockStorage storage, int index);

    public abstract static class AnyBlockDatatype extends PositionedSeriesDatatype {

        private static final int BYTES_A_SLOT_TAKES = 32;

        AnyBlockDatatype(String spelling) {
            super(spelling);
        }

        public abstract AnyBlockValue holding(BlockStorage storage, int index);

        public AnyBlockValue holding(List<Value> items) {
            return holding(new BlockStorage(items), 1);
        }

        boolean wrapsWhatItConverts() {
            return true;
        }

        boolean listsATypesetsMembers() {
            return false;
        }

        @Override
        protected int bytesAnItemTakes() {
            return BYTES_A_SLOT_TAKES;
        }

        @Override
        protected void refuseToBuildSomethingOutOfNothing(Value from) {
        }

        @Override
        protected Value withRoomFor(int asked) {
            return holding(List.of());
        }

        @Override
        public Value as(Value value) {
            return value instanceof AnyBlockValue block
                    ? holding(block.storage(), block.index())
                    : super.as(value);
        }

        @Override
        protected Value constructedFromOne(Value only, Construction construction) {
            if (!(only instanceof AnyBlockValue block)) {
                throw refusingConstruction(List.of(only));
            }
            return as(block);
        }

        @Override
        protected Value constructedFromMore(List<Value> contents, Construction construction) {
            return standingWhereItWasTold(
                    constructedFromOne(contents.getFirst(), construction), contents.get(1));
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return whatTheHostHadRoomFor(() -> blockBuilt(asking, from, maker));
        }

        private Value blockBuilt(Conversion asking, Value from, Maker maker) {
            if (from instanceof AnyBlockValue given) {
                BlockStorage built = new BlockStorage(given.remaining());
                built.takeLineBreaksFrom(given.storage(), given.index());
                return holding(built, 1);
            }
            if (from instanceof MapValue pairs) {
                return as(pairs.pairsOnLines());
            }
            if (from.isAnyObject()) {
                return as(from.fieldsAsAContext().orElseThrow().setWordsAndValuesOnLines());
            }
            if (from instanceof VectorValue numbers) {
                return holding(numbers.remaining());
            }
            if (asking.builds()) {
                if (from instanceof IntegerValue || from instanceof DecimalValue) {
                    return holding(List.of());
                }
            } else if (wrapsWhatItConverts()) {
                return from instanceof TypesetValue kinds && listsATypesetsMembers()
                        ? holding(Catalogue.DATATYPES.where(kinds::holds).stream()
                                .<Value>map(datatype -> datatype).toList())
                        : holding(List.of(from));
            }
            if (from instanceof StringValue text) {
                return sourceReadStoppingAtANoughtByte(text.text(), maker);
            }
            if (from instanceof BinaryValue octets) {
                return sourceReadStoppingAtANoughtByte(octets.decodedAsText(), maker);
            }
            if (from instanceof PairValue) {
                return holding(List.of());
            }
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(from));
        }

        private Value sourceReadStoppingAtANoughtByte(String source, Maker maker) {
            int endsAt = source.indexOf('\0');
            return as(maker.sourceRead(endsAt < 0 ? source : source.substring(0, endsAt)));
        }
    }

    public AnyBlockValue holding(BlockStorage another) {
        return sameKindOver(another, 1);
    }

    public AnyBlockValue holding(List<Value> items) {
        return sameKindOver(new BlockStorage(items), 1);
    }

    String opensWith() {
        return "[";
    }

    String closesWith() {
        return "]";
    }

    boolean moldsInBrackets() {
        return true;
    }

    String moldedWhenAlreadyInsideItself() {
        return opensWith() + "..." + closesWith();
    }

    public BlockStorage storage() {
        return storage;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public Value randomised(RandomDraw draw) {
        throw Raised.cannotUseTheAction(this, "random");
    }

    @Override
    public Value pickedAtRandom(RandomDraw draw) {
        throw Raised.cannotUseTheAction(this, "random");
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        throw Raised.of(EvaluationFailure.BAD_REFINES);
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
        return sameKindOver(built, 1);
    }

    @Override
    public Value itemAt(int positionFromTheHead) {
        return storage.at(positionFromTheHead);
    }

    @Override
    public Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds) {
        BlockStorage built = new BlockStorage(remaining().subList(0, howMany).stream()
                .map(item -> deeply && kinds.contains(item.datatype())
                        ? item.copied(true, kinds)
                        : item)
                .toList());
        built.takeLineBreaksFrom(storage, index);
        return sameKindOver(built, 1);
    }

    @Override
    public RebolSeries reversedFront(int howMany) {
        List<Value> items = new ArrayList<>(howMany);
        List<Boolean> breaks = new ArrayList<>(howMany);
        for (int at = 0; at < howMany; at++) {
            items.add(storage.at(index + at));
            breaks.add(storage.breaksLineAt(index + at));
        }
        for (int at = 0; at < howMany; at++) {
            int from = howMany - 1 - at;
            storage.set(index + at, items.get(from));
            storage.setLineBreakAt(index + at, breaks.get(from));
        }
        return this;
    }

    public void removeTheFirstPairWhoseKey(Predicate<Value> matches) {
        List<Value> items = remaining();
        for (int at = 0; at + 1 < items.size(); at += 2) {
            if (matches.test(items.get(at))) {
                storage.removeAt(index + at);
                storage.removeAt(index + at);
                return;
            }
        }
    }

    @Override
    public void putItemAt(int positionFromTheHead, Value item) {
        storage.set(positionFromTheHead, item);
    }

    public void putEachPairOnALine() {
        startALineEvery(2);
    }

    public void putEachItemOnALine() {
        startALineEvery(1);
    }

    private void startALineEvery(int stride) {
        for (int at = 1; at <= storageLength(); at += stride) {
            storage.setLineBreakAt(at, true);
        }
    }

    public BlockValue declaredParameters() {
        return BlockValue.block(remaining().stream()
                .filter(AnyBlockValue::declaresAParameter)
                .toList());
    }

    private static boolean declaresAParameter(Value item) {
        return item instanceof AnyWordValue word
                && !(word instanceof SetWordValue || word instanceof IssueValue);
    }

    @Override
    public String runTogether() {
        return remaining().stream()
                .map(Value::runTogether)
                .collect(Collectors.joining());
    }

    @Override
    public boolean equalTo(Value other, Sameness how) {
        if (!(other instanceof AnyBlockValue theirs) || datatype() != theirs.datatype()) {
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
            case AnyStringValue ignored -> StringValue.of(AnyStringValue.textOf(
                    keeping.from(AnyStringValue.charactersOf(this),
                            AnyStringValue.charactersOf(other))));
            case MapValue theirs -> MapValue.empty()
                    .combinedWith(theirs, keeping.how(), mindingCase);
            case AnyBlockValue theirs -> BlockValue.block(
                    keeping.from(remaining(), theirs.remaining()));
            default -> throw Raised.cannotUse(other, "a set operation");
        };
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public AnyBlockValue atIndex(int oneBasedIndex) {
        return sameKindOver(storage, oneBasedIndex);
    }

    @Override
    public AnyBlockValue head() {
        return atIndex(1);
    }

    @Override
    public AnyBlockValue tail() {
        return atIndex(storage.length() + 1);
    }

    public boolean looksUpItsDeclaration() {
        return false;
    }

    public BlockValue asBlock() {
        return new BlockValue(storage, index);
    }

    public PathValue asPath() {
        return new PathValue(storage, index);
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

    @Override
    public Value trimmed(Trimming trimming) {
        trimming.refuseWhatOnlyTextServes();
        List<Value> items = new ArrayList<>(remaining());
        if (trimming.everywhere()) {
            items.removeIf(NoneValue.class::isInstance);
        } else {
            while (trimming.fromTheHead() && !items.isEmpty() && items.getFirst() instanceof NoneValue) {
                items.removeFirst();
            }
            while (trimming.fromTheTail() && !items.isEmpty() && items.getLast() instanceof NoneValue) {
                items.removeLast();
            }
        }
        for (int at = storageLength(); at >= index; at--) {
            storage.removeAt(at);
        }
        for (int at = items.size(); at > 0; at--) {
            storage.insertAt(index, items.get(at - 1));
        }
        return this;
    }

    public List<AnyWordValue> setWordsFromHere() {
        return remaining().stream()
                .filter(SetWordValue.class::isInstance)
                .map(AnyWordValue.class::cast)
                .toList();
    }

    public List<Value> wordsWritten(boolean deeply, boolean settersOnly) {
        List<Value> found = new ArrayList<>();
        gatherWordsInto(found, deeply, settersOnly);
        return found;
    }

    private void gatherWordsInto(List<Value> found, boolean deeply, boolean settersOnly) {
        for (Value item : remaining()) {
            if (item instanceof AnyBlockValue nested) {
                if (deeply) {
                    nested.gatherWordsInto(found, true, settersOnly);
                }
                continue;
            }
            if (item instanceof AnyWordValue word
                    && (!settersOnly || word instanceof SetWordValue)
                    && isNotYetAmong(found, word)) {
                found.add(WordValue.of(word.spelling()));
            }
        }
    }

    private static boolean isNotYetAmong(List<Value> found, AnyWordValue word) {
        return found.stream().noneMatch(seen -> seen instanceof AnyWordValue already
                && already.canonical().equals(word.canonical()));
    }

    public Optional<ContextSlot> fieldThePathNames() {
        return Optional.empty();
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof AnyBlockValue block && block.storage == storage;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnyBlockValue block
                && block.datatype() == datatype()
                && block.remaining().equals(remaining());
    }

    @Override
    public int hashCode() {
        return datatype().hashCode() * 31 + remaining().hashCode();
    }

    @Override
    public String toString() {
        return datatype().literalSpelling() + "@" + index;
    }
}
