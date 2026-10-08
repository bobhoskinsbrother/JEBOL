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

    abstract AnyStringValue sameKindOver(StringStorage storage, int index);

    public abstract String mold();

    public String form() {
        return text();
    }

    public boolean isALocation() {
        return false;
    }

    public abstract static class AnyStringDatatype extends SeriesDatatype {

        AnyStringDatatype(String spelling) {
            super(spelling, Typeset.SERIES, Typeset.ANY_STRING);
        }

        public abstract AnyStringValue holding(StringStorage storage, int index);

        public AnyStringValue holding(String text) {
            return holding(StringStorage.of(text), 1);
        }

        @Override
        protected Value withRoomFor(int asked) {
            return holding(StringStorage.withRoomFor(asked), 1);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return holding(textOf(from));
        }

        protected String textOf(Value value) {
            return switch (value) {
                case BinaryValue octets -> octets.decodedAsText();
                case AnyStringValue already -> already.text();
                default -> value.runTogether();
            };
        }

        @Override
        public Value as(Value value) {
            return value instanceof AnyStringValue text
                    ? holding(text.storage(), text.index())
                    : super.as(value);
        }

        @Override
        protected Value constructedFromOne(Value only, Construction construction) {
            return switch (only) {
                case AnyStringValue text -> as(text);
                case BinaryValue _ -> construction.madeOf(this, only);
                default -> throw refusingConstruction(List.of(only));
            };
        }
    }

    public AnyStringValue holding(String text) {
        return sameKindOver(StringStorage.of(text), 1);
    }

    public StringStorage storage() {
        return storage;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public Value randomised(RandomDraw draw) {
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
    public Value pickedAtRandom(RandomDraw draw) {
        byte[] octets = text().getBytes(StandardCharsets.UTF_8);
        if (octets.length == 0) {
            return NoneValue.none();
        }
        int at = new CharacterBoundary().drawnIn(octets, draw);
        return CharacterValue.of(
                new String(octets, at, octets.length - at, StandardCharsets.UTF_8).codePointAt(0));
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return checksumOfTheOctets.applyAsLong(text().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        return holding(text());
    }

    @Override
    public byte[] asOctets() {
        return text().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Value itemAt(int positionFromTheHead) {
        return CharacterValue.of(storage.at(positionFromTheHead));
    }

    @Override
    public Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds) {
        String whole = text();
        int taking = Math.min(howMany, whole.codePointCount(0, whole.length()));
        return holding(whole.substring(0, whole.offsetByCodePoints(0, taking)));
    }

    @Override
    public RebolSeries reversedFront(int howMany) {
        return rewrittenFromHere(whole -> new StringBuilder(whole.substring(0, howMany)).reverse()
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

    public AnyStringValue rewrittenFromHere(UnaryOperator<String> change) {
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
        return rewrittenFromHere(this::oneLineFeedPerEnding);
    }

    private String oneLineFeedPerEnding(String text) {
        StringBuilder standardised = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            char here = text.charAt(at++);
            if (here == '\n' || here == '\r') {
                if (at < text.length() && text.charAt(at) == theOtherEnding(here)) {
                    at++;
                }
                here = '\n';
            }
            standardised.append(here);
        }
        return standardised.toString();
    }

    private char theOtherEnding(char one) {
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

    public AnyStringValue frontRewritten(int howMany, UnaryOperator<String> change) {
        return rewrittenFromHere(whole -> {
            int taking = Math.min(howMany, whole.codePointCount(0, whole.length()));
            int split = whole.offsetByCodePoints(0, taking);
            return change.apply(whole.substring(0, split)) + whole.substring(split);
        });
    }

    @Override
    public Value trimmed(Trimming trimming) {
        trimming.refuseContradictions();
        return rewrittenFromHere(text -> trimmedText(text, trimming));
    }

    private String trimmedText(String text, Trimming trimming) {
        if (trimming.ofTheGivenCharacters()) {
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
        String indented = trimming.ofTheCommonIndent() ? withoutCommonIndent(text) : text;
        if (trimming.atOneEndOnly()) {
            return trimming.fromTheHead() ? indented.stripLeading() : indented.stripTrailing();
        }
        return trimming.ofTheCommonIndent() || trimming.atBothNamedEnds()
                ? indented.strip()
                : trimmedEachLine(indented);
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

    private String trimmedEachLine(String text) {
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
    public void putItemAt(int positionFromTheHead, Value item) {
        storage.set(positionFromTheHead, ((CharacterValue) item).codepoint());
    }

    @Override
    public List<Value> items() {
        return text().codePoints()
                .<Value>mapToObj(CharacterValue::of)
                .toList();
    }

    @Override
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof AnyStringValue theirs
                && (how.theyWereBroughtTogetherFirst() || datatype() == theirs.datatype())
                && equalsIgnoringCase(theirs);
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return other instanceof AnyStringValue ? both(this, other) : Optional.empty();
    }

    @Override
    public Value asASetWith(Value other, MembersKept keeping, boolean mindingCase) {
        return holding(textOf(keeping.from(charactersOf(this), charactersOf(other))));
    }

    static String textOf(List<Value> letters) {
        StringBuilder written = new StringBuilder();
        letters.forEach(letter ->
                written.appendCodePoint(((CharacterValue) letter).codepoint()));
        return written.toString();
    }

    static List<Value> charactersOf(Value value) {
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
        return sameKindOver(storage, oneBasedIndex);
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

    public CharacterValue first() {
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
        return "#(" + datatype().literalSpelling() + " " + Molder.moldedText(head().text())
                + (index > 1 ? " " + index : "") + ")";
    }

    String moldedUnlessItWouldNotReadBackWhenMarkedBy(char required) {
        return wouldNotReadBackAsItselfWhenMarkedBy(required) ? constructed() : text();
    }

    private boolean wouldNotReadBackAsItselfWhenMarkedBy(char required) {
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
