package org.jebol.domain.value;

/** A position into binary storage, written {@code #{DEADBEEF}}. */
public record BinaryValue(BinaryStorage storage, int index) implements SeriesValue {

    public BinaryValue {
        if (storage == null) {
            throw new IllegalArgumentException("a binary value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    public static BinaryValue of(int... octets) {
        return new BinaryValue(BinaryStorage.of(octets), 1);
    }

    /**
     * The octets from here on, read as UTF-8 text.
     *
     * <p>What TRANSCODE needs: a script that has read a file holds a
     * binary, and reading source out of it means deciding an encoding.
     * UTF-8 is what REBOL 3 sources are.
     */
    public String asText() {
        return new String(octetsFromHere(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * The octets from here on, as octets.
     *
     * <p>What anything that wants the bytes themselves needs. Going through
     * {@link #asText()} and back loses every byte above 0x7F, because
     * decoding as UTF-8 turns an octet that is not valid UTF-8 into the
     * replacement character. That is how {@code make bitset! #{FF}} came out
     * holding {@code #{3F}}, which is a question mark.
     */
    public byte[] octetsFromHere() {
        byte[] octets = new byte[storageLength() - index + 1];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) storage.at(index + at);
        }
        return octets;
    }

    @Override
    public Datatype datatype() {
        return Datatype.BINARY;
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public BinaryValue atIndex(int oneBasedIndex) {
        return new BinaryValue(storage, oneBasedIndex);
    }

    @Override
    public BinaryValue head() {
        return atIndex(1);
    }

    @Override
    public BinaryValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(SeriesValue other) {
        return other instanceof BinaryValue binary && binary.storage == storage;
    }

    /** REBOL's {@code ==}: the same remaining octets, compared in turn. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BinaryValue binary)) {
            return false;
        }
        if (binary.lengthFromHere() != lengthFromHere()) {
            return false;
        }
        for (int offset = 0; offset < lengthFromHere(); offset++) {
            if (binary.storage.at(binary.index + offset) != storage.at(index + offset)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        for (int offset = 0; offset < lengthFromHere(); offset++) {
            hash = hash * 31 + storage.at(index + offset);
        }
        return hash;
    }

    @Override
    public String toString() {
        return "binary!@" + index;
    }
}
