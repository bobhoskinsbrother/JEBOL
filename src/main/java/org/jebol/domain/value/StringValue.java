package org.jebol.domain.value;

/**
 * A position into string storage, reported as one of the {@code any-string!}
 * datatypes: {@code string!}, {@code file!}, {@code url!}, {@code email!} or
 * {@code tag!}.
 *
 * <p>These share a representation and differ in how they are written and
 * printed. They are lexical shapes rather than validated types, per
 * {@code docs/decisions.md} item 7, so nothing here checks that an
 * {@code email!} is a deliverable address.
 */
public record StringValue(StringStorage storage, int index, Datatype datatype)
        implements SeriesValue {

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

    /** The same storage and position, read as a different any-string! type. */
    public StringValue as(Datatype otherDatatype) {
        return new StringValue(storage, index, otherDatatype);
    }

    /** The text from this position to the tail. */
    public String text() {
        return storage.textFrom(index);
    }

    /** The character at this position. Fails at the tail. */
    public CharacterValue first() {
        if (atTail()) {
            throw new IllegalStateException("nothing to read at the tail");
        }
        return CharacterValue.of(storage.at(index));
    }

    @Override
    public boolean sharesStorageWith(SeriesValue other) {
        return other instanceof StringValue string && string.storage == storage;
    }

    /**
     * REBOL's {@code ==}: same datatype, same remaining contents, case
     * sensitive. Contents rather than storage, so two separately built
     * strings holding the same text are equal; {@link #sharesStorageWith} is
     * the identity question, REBOL's {@code same?}.
     *
     * <p>REBOL's looser {@code =} is {@link #equalsIgnoringCase}.
     */
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

    /**
     * REBOL's {@code =}: the same contents, ignoring case and ignoring which
     * of the string datatypes each side is.
     *
     * <p>The datatype really does drop out. {@code Compare_Values} sends any
     * string against any other string to {@code CT_String}, which compares
     * the contents and nothing else, so {@code equal? "a" %a} is true and so
     * is {@code equal? "a" <a>}. Only {@code ==} minds the datatype, and it
     * minds it before reaching here.
     *
     * <p>This method used to insist on the datatype, which read as the
     * obvious rule and made those two assertions false.
     */
    public boolean equalsIgnoringCase(StringValue other) {
        return other.text().equalsIgnoreCase(text());
    }

    @Override
    public String toString() {
        return datatype.literalSpelling() + "@" + index + " " + text();
    }
}
