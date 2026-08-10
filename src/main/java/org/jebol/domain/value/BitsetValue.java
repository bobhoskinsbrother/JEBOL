package org.jebol.domain.value;

import java.util.Arrays;

/**
 * A set of character codes, held as bits.
 *
 * <p>Membership is a lookup rather than a search, which is what a bitset is
 * for: a PARSE rule saying "any of these characters" without listing them
 * as alternatives, and a delimiter set that does not care how many
 * delimiters there are.
 *
 * <p>Not a series. It has no position and no order, so FIND answers whether
 * something is in it rather than where -- the one place FIND gives a logic.
 */
public final class BitsetValue implements Value {

    private static final int BITS_PER_OCTET = 8;

    private byte[] octets;

    /**
     * Whether the set means everything except what its bits name.
     *
     * <p>A flag rather than every bit flipped. Flipping them gives a set
     * that answers the same questions and molds as a wall of FF, and it
     * loses the fact that a caller asked for a complement -- which
     * COMPLEMENT? has to answer and MOLD has to print.
     */
    private boolean complemented;

    private BitsetValue(byte[] octets) {
        this.octets = octets;
    }

    /** Whether this set means everything except what its bits name. */
    public boolean isComplemented() {
        return complemented;
    }

    /**
     * A set with the same members, holding its own octets.
     *
     * <p>COPY on a bitset has to duplicate the octets. A bitset can be
     * written through a path, and a shallow copy means that writing to the
     * copy writes to the original: Rebol's own url-parser copies the URI set
     * from the catalogue and adds a percent sign to the copy, and with a
     * shallow copy the catalogue's own set gained the percent sign as well.
     * Every later use of it was then wrong, and nothing pointed at COPY.
     */
    public BitsetValue duplicate() {
        BitsetValue same = new BitsetValue(octets.clone());
        same.complemented = complemented;
        return same;
    }

    /** A set of everything this one leaves out. */
    public BitsetValue complemented() {
        BitsetValue turned = new BitsetValue(octets.clone());
        turned.complemented = !complemented;
        return turned;
    }

    /**
     * Adds every member of another set to this one, in place.
     *
     * <p>In place because APPEND on a bitset changes the set the caller
     * holds, the way APPEND on a series does. Answering a new set would
     * leave the caller's unchanged and look like the call did nothing.
     */
    public void addAll(BitsetValue others) {
        byte[] theirs = others.octets;
        if (theirs.length > octets.length) {
            byte[] wider = new byte[theirs.length];
            System.arraycopy(octets, 0, wider, 0, octets.length);
            octets = wider;
        }
        for (int at = 0; at < theirs.length; at++) {
            octets[at] = (byte) (octets[at] | theirs[at]);
        }
    }

    public static BitsetValue of(byte[] octets) {
        return new BitsetValue(octets.clone());
    }

    /** A bitset holding every code in the text. */
    public static BitsetValue ofCharacters(int... codes) {
        int widest = 0;
        for (int code : codes) {
            widest = Math.max(widest, code);
        }
        BitsetValue built = new BitsetValue(new byte[widest / BITS_PER_OCTET + 1]);
        for (int code : codes) {
            built.add(code);
        }
        return built;
    }

    public void add(int code) {
        int octet = code / BITS_PER_OCTET;
        if (octet >= octets.length) {
            octets = Arrays.copyOf(octets, octet + 1);
        }
        // The highest bit of an octet is the lowest code in it, which is
        // the order the molded form reads in.
        octets[octet] |= (byte) (1 << (7 - code % BITS_PER_OCTET));
    }

    public boolean holds(int code) {
        return complemented != namesDirectly(code);
    }

    /**
     * Puts a character in the set, or takes it out.
     *
     * <p>{@code PD_Bitset} writes a bit through a path, and it minds the
     * complement flag: {@code t = IS_TRUE(val); if (BITS_NOT(ser)) t = !t;}.
     * A complemented set holds every character its octets do not name, thus
     * to put a character into one, the octet for that character is cleared.
     *
     * <p>The inversion changes nothing for an ordinary set, which is why it
     * is easy to leave out and hard to notice afterwards.
     *
     * <p>Changes this set rather than answering a new one, because a path
     * writes through to the value the word holds and a parse rule that
     * already names that word has to see the change.
     */
    public void hold(int code, boolean wanted) {
        if (complemented == wanted) {
            clearDirectly(code);
        } else {
            add(code);
        }
    }

    private void clearDirectly(int code) {
        int octet = code / BITS_PER_OCTET;
        if (octet < octets.length) {
            octets[octet] &= (byte) ~(1 << (7 - code % BITS_PER_OCTET));
        }
    }

    /** Whether the bits themselves name this character. */
    private boolean namesDirectly(int code) {
        int octet = code / BITS_PER_OCTET;
        return octet < octets.length
                && (octets[octet] & (1 << (7 - code % BITS_PER_OCTET))) != 0;
    }

    public byte[] octets() {
        return octets.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BitsetValue bitset && Arrays.equals(octets, bitset.octets);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octets);
    }

    @Override
    public Datatype datatype() {
        return Datatype.BITSET;
    }

    @Override
    public String toString() {
        return "bitset of " + octets.length + " octets";
    }
}
