package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

public abstract sealed class AnyStringValue implements RebolSeries
        permits StringValue, FileValue, EmailValue, RefValue, UrlValue, TagValue {

    private final StringStorage storage;
    private final int index;

    AnyStringValue(StringStorage storage, int index) {
        if (storage == null) {
            throw new IllegalArgumentException("a string value needs storage");
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

    abstract AnyStringValue makeSameKindOver(StringStorage storage, int index);

    public abstract String mold();

    public String form() {
        return text();
    }

    public boolean isALocation() {
        return false;
    }

    public abstract static class AnyStringDatatype extends PositionedSeriesDatatype {

        AnyStringDatatype(String spelling) {
            super(spelling);
        }

        public abstract AnyStringValue wrap(StringStorage storage, int index);

        public AnyStringValue wrap(String text) {
            return wrap(StringStorage.of(text), 1);
        }

        @Override
        protected Value withRoomFor(int asked) {
            return wrap(StringStorage.withRoomFor(asked), 1);
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            return wrap(textOf(from));
        }

        protected String textOf(Value value) {
            return switch (value) {
                case BinaryValue octets -> octets.decodeAsText();
                case AnyStringValue already -> already.text();
                default -> value.runTogether();
            };
        }

        @Override
        public Value as(Value value) {
            return value instanceof AnyStringValue text
                    ? wrap(text.storage(), text.index())
                    : super.as(value);
        }

        @Override
        protected Value constructFromOne(Value only, Construction construction) {
            return switch (only) {
                case AnyStringValue text -> as(text);
                case BinaryValue _ -> construction.make(this, only);
                default -> throw refuseConstruction(List.of(only));
            };
        }
    }

    public AnyStringValue wrap(String text) {
        return makeSameKindOver(StringStorage.of(text), 1);
    }

    public StringStorage storage() {
        return storage;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public Value randomise(RandomDraw draw) {
        List<Integer> letters = new ArrayList<>();
        for (int at = index; at <= storageLength(); at++) {
            letters.add(storage.at(at));
        }
        draw.shuffle(letters);
        for (int at = 0; at < letters.size(); at++) {
            storage.set(index + at, letters.get(at));
        }
        return this;
    }

    @Override
    public Value pickAtRandom(RandomDraw draw) {
        byte[] octets = text().getBytes(StandardCharsets.UTF_8);
        if (octets.length == 0) {
            return NoneValue.none();
        }
        int at = new CharacterBoundary().drawIn(octets, draw);
        return CharacterValue.of(
                new String(octets, at, octets.length - at, StandardCharsets.UTF_8).codePointAt(0));
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> octetChecksum) {
        return octetChecksum.applyAsLong(text().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public Value copy(boolean deeply, Set<Datatype> kinds) {
        return wrap(text());
    }

    @Override
    public byte[] asOctets() {
        return text().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Value readItemAt(int positionFromHead) {
        return CharacterValue.of(storage.at(positionFromHead));
    }

    @Override
    public Value copyFront(int howMany, boolean deeply, Set<Datatype> kinds) {
        String whole = text();
        int taking = Math.min(howMany, whole.codePointCount(0, whole.length()));
        return wrap(whole.substring(0, whole.offsetByCodePoints(0, taking)));
    }

    @Override
    public RebolSeries reverseFront(int howMany) {
        return rewriteFromHere(whole -> new StringBuilder(whole.substring(0, howMany)).reverse()
                + whole.substring(howMany));
    }

    @Override
    public RebolSeries reversedFromHere() {
        int[] forwards = text().codePoints().toArray();
        for (int at = 0; at < forwards.length; at++) {
            storage.set(index + at, forwards[forwards.length - 1 - at]);
        }
        return this;
    }

    public AnyStringValue rewriteFromHere(UnaryOperator<String> change) {
        int[] replacement = change.apply(text()).codePoints().toArray();
        for (int at = storageLength(); at >= index; at--) {
            storage.removeAt(at);
        }
        for (int at = replacement.length; at > 0; at--) {
            storage.insertAt(index, replacement[at - 1]);
        }
        return this;
    }

    public AnyStringValue withOneLineFeedPerEnding() {
        return rewriteFromHere(this::standardiseLineEndings);
    }

    private String standardiseLineEndings(String text) {
        StringBuilder standardised = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            char here = text.charAt(at++);
            if (here == '\n' || here == '\r') {
                if (at < text.length() && text.charAt(at) == findOtherEnding(here)) {
                    at++;
                }
                here = '\n';
            }
            standardised.append(here);
        }
        return standardised.toString();
    }

    private char findOtherEnding(char one) {
        return one == '\n' ? '\r' : '\n';
    }

    public List<Value> linesDroppingOneTrailingEmptyLine() {
        String text = text();
        if (text.isEmpty()) {
            return List.of();
        }
        String[] split = text.replace("\r\n", "\n").split("\n", -1);
        int howMany = split[split.length - 1].isEmpty() ? split.length - 1 : split.length;
        List<Value> lines = new ArrayList<>(howMany);
        for (int at = 0; at < howMany; at++) {
            lines.add(StringValue.of(split[at]));
        }
        return lines;
    }

    public AnyStringValue rewriteFront(int howMany, UnaryOperator<String> change) {
        return rewriteFromHere(whole -> {
            int taking = Math.min(howMany, whole.codePointCount(0, whole.length()));
            int split = whole.offsetByCodePoints(0, taking);
            return change.apply(whole.substring(0, split)) + whole.substring(split);
        });
    }

    @Override
    public Value trim(Trimming trimming) {
        trimming.refuseContradictions();
        return rewriteFromHere(text -> trimText(text, trimming));
    }

    private String trimText(String text, Trimming trimming) {
        if (trimming.ofGivenCharacters()) {
            StringBuilder kept = new StringBuilder();
            text.codePoints()
                    .filter(letter -> !trimming.unwantedCodePoints().contains(letter))
                    .forEach(kept::appendCodePoint);
            return kept.toString();
        }
        if (trimming.everywhere()) {
            return text.replaceAll("\\s", "");
        }
        if (trimming.intoOneLine()) {
            return text.strip().replaceAll("\\s+", " ");
        }
        String indented = trimming.ofCommonIndent() ? withoutCommonIndent(text) : text;
        if (trimming.atOneEndOnly()) {
            return trimming.fromHead() ? indented.stripLeading() : indented.stripTrailing();
        }
        return trimming.ofCommonIndent() || trimming.atBothNamedEnds()
                ? indented.strip()
                : trimEachLine(indented);
    }

    private String withoutCommonIndent(String text) {
        String[] lines = text.split("\n", -1);
        int firstContentLine = 0;
        while (firstContentLine < lines.length && lines[firstContentLine].isBlank()) {
            firstContentLine++;
        }
        int indent = firstContentLine < lines.length
                ? lines[firstContentLine].length()
                        - lines[firstContentLine].stripLeading().length()
                : 0;
        StringBuilder trimmed = new StringBuilder();
        for (int at = firstContentLine; at < lines.length; at++) {
            String line = lines[at];
            int take = Math.min(indent, line.length() - line.stripLeading().length());
            trimmed.append(line.substring(take));
            if (at + 1 < lines.length) {
                trimmed.append('\n');
            }
        }
        return trimmed.toString();
    }

    private String trimEachLine(String text) {
        String afterLead = text.stripLeading();
        String core = afterLead.stripTrailing();
        boolean endedWithLineFeed =
                afterLead.substring(core.length()).indexOf('\n') >= 0;
        String[] lines = core.split("\n", -1);
        StringBuilder joined = new StringBuilder();
        for (int at = 0; at < lines.length; at++) {
            if (at > 0) {
                joined.append('\n');
            }
            joined.append(lines[at].strip());
        }
        if (endedWithLineFeed) {
            joined.append('\n');
        }
        return joined.toString();
    }

    @Override
    public void putItemAt(int positionFromHead, Value item) {
        storage.set(positionFromHead, ((CharacterValue) item).codepoint());
    }

    @Override
    public List<Value> listItems() {
        return text().codePoints()
                .<Value>mapToObj(CharacterValue::of)
                .toList();
    }

    @Override
    public boolean isEqualTo(Value other, Sameness how) {
        return other instanceof AnyStringValue theirs
                && (how.theyWereBroughtTogetherFirst() || datatype() == theirs.datatype())
                && equalsIgnoringCase(theirs);
    }

    @Override
    public Optional<Value[]> combineWith(Value other) {
        return other instanceof AnyStringValue ? pairUp(this, other) : Optional.empty();
    }

    @Override
    public Value asSetWith(Value other, MembersKept keeping, boolean mindingCase) {
        return wrap(textOf(keeping.from(listCharactersFrom(this), listCharactersFrom(other))));
    }

    static String textOf(List<Value> letters) {
        StringBuilder written = new StringBuilder();
        letters.forEach(letter ->
                written.appendCodePoint(((CharacterValue) letter).codepoint()));
        return written.toString();
    }

    static List<Value> listCharactersFrom(Value value) {
        if (!(value instanceof AnyStringValue text)) {
            return value instanceof AnyBlockValue block ? block.remaining() : List.of(value);
        }
        return text.text().codePoints().<Value>mapToObj(CharacterValue::of).toList();
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public AnyStringValue atIndex(int oneBasedIndex) {
        return makeSameKindOver(storage, oneBasedIndex);
    }

    @Override
    public AnyStringValue head() {
        return atIndex(1);
    }

    @Override
    public AnyStringValue tail() {
        return atIndex(storage.length() + 1);
    }

    public String text() {
        return storage.textFrom(index);
    }

    public CharacterValue pickFirst() {
        if (atTail()) {
            throw new IllegalStateException("nothing to read at the tail");
        }
        return CharacterValue.of(storage.at(index));
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof AnyStringValue string && string.storage == storage;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AnyStringValue string
                && string.datatype() == datatype()
                && string.text().equals(text());
    }

    @Override
    public int hashCode() {
        return datatype().hashCode() * 31 + text().hashCode();
    }

    public boolean equalsIgnoringCase(AnyStringValue other) {
        return other.text().equalsIgnoreCase(text());
    }

    @Override
    public String toString() {
        return datatype().literalSpelling() + "@" + index + " " + text();
    }

    String constructed() {
        return "#(" + datatype().literalSpelling() + " " + Molder.moldText(head().text())
                + (index > 1 ? " " + index : "") + ")";
    }

    String moldUnlessItFailsToReadBack(char required) {
        return failsToReadBackWhenMarkedBy(required) ? constructed() : text();
    }

    private boolean failsToReadBackWhenMarkedBy(char required) {
        String remaining = text();
        String whole = head().text();
        if (remaining.isEmpty() || whole.isEmpty() || remaining.charAt(0) == '%') {
            return true;
        }
        int found = -1;
        for (int at = 0; at < remaining.length(); at++) {
            char letter = remaining.charAt(at);
            if (letter <= 0x20 || letter == 0x7F
                    || "()[]{}\";".indexOf(letter) >= 0
                    || (letter == '/' && required == '@')) {
                return true;
            }
            if (letter == required) {
                if (at == 0) {
                    return true;
                }
                if (found >= 0 && (required == '@' || at == 1)) {
                    return true;
                }
                if (found < 0) {
                    found = at;
                }
            }
        }
        return found < 0 || found == remaining.length() - 1;
    }
}
