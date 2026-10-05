package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public record StringValue(StringStorage storage, int index, Datatype datatype)
        implements RebolSeries {

    public StringValue {
        if (storage == null) {
            throw new IllegalArgumentException("a string value needs storage");
        }
        if (!datatype.isAnyString()) {
            throw new IllegalArgumentException(
                    datatype.literalSpelling() + " is not an any-string! datatype");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    public static StringValue of(String text) {
        return new StringValue(StringStorage.of(text), 1, Datatype.STRING);
    }

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        return StringValue.of(text(), datatype);
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
        return StringValue.of(whole.substring(0, whole.offsetByCodePoints(0, taking)), datatype);
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

    public StringValue rewrittenFromHere(UnaryOperator<String> change) {
        int[] replacement = change.apply(text()).codePoints().toArray();
        for (int at = storageLength(); at >= index; at--) {
            storage.removeAt(at);
        }
        for (int at = replacement.length; at > 0; at--) {
            storage.insertAt(index, replacement[at - 1]);
        }
        return this;
    }

    public StringValue withOneLineFeedPerEnding() {
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

    public StringValue frontRewritten(int howMany, UnaryOperator<String> change) {
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

    public StringValue swapFirstItemWith(StringValue there) {
        if (!atTail() && !there.atTail()) {
            int mine = storage.at(index);
            storage.set(index, there.storage.at(there.index));
            there.storage.set(there.index, mine);
        }
        return this;
    }

    @Override
    public List<Value> items() {
        return text().codePoints()
                .<Value>mapToObj(CharacterValue::of)
                .toList();
    }

    @Override
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof StringValue theirs
                && (how.theyWereBroughtTogetherFirst() || datatype == theirs.datatype)
                && equalsIgnoringCase(theirs);
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return other.datatype().isAnyString() ? both(this, other) : Optional.empty();
    }

    @Override
    public Value asASetWith(Value other, MembersKept keeping, boolean mindingCase) {
        return textOf(keeping.from(charactersOf(this), charactersOf(other)), datatype);
    }

    static StringValue textOf(List<Value> letters, Datatype datatype) {
        StringBuilder written = new StringBuilder();
        letters.forEach(letter ->
                written.appendCodePoint(((CharacterValue) letter).codepoint()));
        return StringValue.of(written.toString(), datatype);
    }

    static List<Value> charactersOf(Value value) {
        if (!(value instanceof StringValue text)) {
            return value instanceof BlockValue block ? block.remaining() : List.of(value);
        }
        return text.text().codePoints().<Value>mapToObj(CharacterValue::of).toList();
    }

    public static StringValue of(String text, Datatype datatype) {
        return new StringValue(StringStorage.of(text), 1, datatype);
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public StringValue atIndex(int oneBasedIndex) {
        return new StringValue(storage, oneBasedIndex, datatype);
    }

    @Override
    public StringValue head() {
        return atIndex(1);
    }

    @Override
    public StringValue tail() {
        return atIndex(storage.length() + 1);
    }

    public StringValue as(Datatype otherDatatype) {
        return new StringValue(storage, index, otherDatatype);
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
        return other instanceof StringValue string && string.storage == storage;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StringValue string
                && string.datatype == datatype
                && string.text().equals(text());
    }

    @Override
    public int hashCode() {
        return datatype.hashCode() * 31 + text().hashCode();
    }

    public boolean equalsIgnoringCase(StringValue other) {
        return other.text().equalsIgnoreCase(text());
    }

    @Override
    public String toString() {
        return datatype.literalSpelling() + "@" + index + " " + text();
    }
}
