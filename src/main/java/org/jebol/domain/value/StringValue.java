package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.util.List;
import java.util.Optional;

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
