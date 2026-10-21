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

    abstract AnyBlockValue makeSameKindOver(BlockStorage storage, int index);

    public abstract static class AnyBlockDatatype extends PositionedSeriesDatatype {

        private static final int BYTES_A_SLOT_TAKES = 32;

        AnyBlockDatatype(String spelling) {
            super(spelling);
        }

        public abstract AnyBlockValue wrap(BlockStorage storage, int index);

        public AnyBlockValue wrap(List<Value> items) {
            return wrap(new BlockStorage(items), 1);
        }

        boolean wrapsWhatItConverts() {
            return true;
        }

        boolean listsTypesetsMembers() {
            return false;
        }

        @Override
        protected int bytesItemTakes() {
            return BYTES_A_SLOT_TAKES;
        }

        @Override
        protected void refuseToBuildSomethingOutOfNothing(Value from) {
        }

        @Override
        protected Value withRoomFor(int asked) {
            return wrap(List.of());
        }

        @Override
        public Value as(Value value) {
            return value instanceof AnyBlockValue block
                    ? wrap(block.storage(), block.index())
                    : super.as(value);
        }

        @Override
        protected Value constructFromOne(Value only, Construction construction) {
            if (!(only instanceof AnyBlockValue block)) {
                throw refuseConstruction(List.of(only));
            }
            return as(block);
        }

        @Override
        protected Value constructFromMore(List<Value> contents, Construction construction) {
            return standWhereItWasTold(
                    constructFromOne(contents.getFirst(), construction), contents.get(1));
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            return allocateWithinHost(() -> buildBlock(asking, from, maker));
        }

        private Value buildBlock(Conversion asking, Value from, Maker maker) {
            if (from instanceof AnyBlockValue given) {
                BlockStorage built = new BlockStorage(given.remaining());
                built.takeLineBreaksFrom(given.storage(), given.index());
                return wrap(built, 1);
            }
            if (from instanceof MapValue pairs) {
                return as(pairs.layPairsOnLines());
            }
            if (from.isAnyObject()) {
                return as(from.fieldsAsContext().orElseThrow().setWordsAndValuesOnLines());
            }
            if (from instanceof VectorValue numbers) {
                return wrap(numbers.listRemaining());
            }
            if (asking.builds()) {
                if (from instanceof IntegerValue || from instanceof DecimalValue) {
                    return wrap(List.of());
                }
            } else if (wrapsWhatItConverts()) {
                return from instanceof TypesetValue kinds && listsTypesetsMembers()
                        ? BlockValue.of(Catalogue.DATATYPES.selectWhere(kinds::holds).stream()
                                .<Value>map(datatype -> datatype).toList())
                        : wrap(List.of(from));
            }
            if (from instanceof StringValue text) {
                return readSourceToNoughtByte(text.text(), maker);
            }
            if (from instanceof BinaryValue octets) {
                return readSourceToNoughtByte(octets.decodeAsText(), maker);
            }
            if (from instanceof PairValue) {
                return wrap(List.of());
            }
            throw Raised.of(EvaluationFailure.INVALID_ARG, from);
        }

        private Value readSourceToNoughtByte(String source, Maker maker) {
            int endsAt = source.indexOf('\0');
            return as(maker.readSource(endsAt < 0 ? source : source.substring(0, endsAt)));
        }
    }

    public AnyBlockValue wrap(BlockStorage another) {
        return makeSameKindOver(another, 1);
    }

    public AnyBlockValue wrap(List<Value> items) {
        return makeSameKindOver(new BlockStorage(items), 1);
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
    public Value randomise(RandomDraw draw) {
        throw Raised.cannotUseAction(this, "random");
    }

    @Override
    public Value pickAtRandom(RandomDraw draw) {
        throw Raised.cannotUseAction(this, "random");
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
    public List<Value> listTheItems() {
        return remaining();
    }

    @Override
    public Value copy(boolean deeply, Set<Datatype> kinds) {
        List<Value> items = remaining();
        List<Value> members = new ArrayList<>(items.size());
        for (Value item : items) {
            members.add(item.copyAsMember(deeply, kinds));
        }
        BlockStorage built = new BlockStorage(members);
        built.takeLineBreaksFrom(storage, index);
        return makeSameKindOver(built, 1);
    }

    @Override
    public Value readItemAt(int positionFromTheHead) {
        return storage.at(positionFromTheHead);
    }

    @Override
    public Value copyFront(int howMany, boolean deeply, Set<Datatype> kinds) {
        List<Value> taken = remaining().subList(0, howMany);
        List<Value> members = new ArrayList<>(taken.size());
        for (Value item : taken) {
            members.add(deeply && kinds.contains(item.datatype())
                    ? item.copy(true, kinds)
                    : item);
        }
        BlockStorage built = new BlockStorage(members);
        built.takeLineBreaksFrom(storage, index);
        return makeSameKindOver(built, 1);
    }

    @Override
    public RebolSeries reverseFront(int howMany) {
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

    public void putEachPairOnLine() {
        startLineEvery(2);
    }

    public void putEachItemOnLine() {
        startLineEvery(1);
    }

    private void startLineEvery(int stride) {
        for (int at = 1; at <= storageLength(); at += stride) {
            storage.setLineBreakAt(at, true);
        }
    }

    public BlockValue declaredParameters() {
        return BlockValue.of(remaining().stream()
                .filter(AnyBlockValue::declaresParameter)
                .toList());
    }

    private static boolean declaresParameter(Value item) {
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
    public boolean isEqualTo(Value other, Sameness how) {
        if (!(other instanceof AnyBlockValue theirs) || datatype() != theirs.datatype()) {
            return false;
        }
        List<Value> ours = remaining();
        List<Value> yours = theirs.remaining();
        if (ours.size() != yours.size()) {
            return false;
        }
        for (int at = 0; at < ours.size(); at++) {
            if (!ours.get(at).isEqualTo(yours.get(at), Sameness.insideSeries())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Value asSetWith(Value other, MembersKept keeping, boolean mindingCase) {
        return switch (other) {
            case AnyStringValue ignored -> StringValue.of(AnyStringValue.textOf(
                    keeping.from(AnyStringValue.listTheCharactersOf(this),
                            AnyStringValue.listTheCharactersOf(other))));
            case MapValue theirs -> MapValue.empty()
                    .combineWith(theirs, keeping.how(), mindingCase);
            case AnyBlockValue theirs -> BlockValue.of(
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
        return makeSameKindOver(storage, oneBasedIndex);
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

    public Value pickFirst() {
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
    public Value trim(Trimming trimming) {
        trimming.refuseWhatOnlyTextServes();
        List<Value> items = new ArrayList<>(remaining());
        if (trimming.everywhere()) {
            items.removeIf(NoneValue.class::isInstance);
        } else {
            while (trimming.fromHead() && !items.isEmpty() && items.getFirst() instanceof NoneValue) {
                items.removeFirst();
            }
            while (trimming.fromTail() && !items.isEmpty() && items.getLast() instanceof NoneValue) {
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

    public List<Value> listTheWordsWritten(boolean deeply, boolean settersOnly) {
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
