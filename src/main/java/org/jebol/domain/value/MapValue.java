package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A map: key-to-value storage, written {@code #[key: value ...]}.
 *
 * <p>Not a series. It has no position, so NEXT and FIRST mean nothing on
 * one, and LENGTH? counts pairs rather than items. Reading a key it has not
 * got gives NONE rather than raising, which is the opposite of an object
 * and the same as a series position past the end: a map is asked about keys
 * it may not have, and an object is asked for fields it should have.
 *
 * <p>Keys are values rather than names, so {@code #[1 2]} has the integer
 * one as a key. That is the whole difference from an object, and it is why
 * this does not reuse {@link Context}.
 *
 * <p>Mutable and shared, as series storage is: two references to the same
 * map see each other's changes. {@link LinkedHashMap} keeps insertion order
 * so that MOLD and KEYS-OF produce a stable answer, which the tests depend
 * on even though order is not part of equality.
 */
public final class MapValue implements Value {

    private final Map<Value, Value> entries;
    private boolean protectedFromChange;

    private MapValue(Map<Value, Value> entries) {
        this.entries = entries;
    }

    public static MapValue empty() {
        return new MapValue(new LinkedHashMap<>());
    }

    /**
     * A map from a block of alternating keys and values.
     *
     * <p>An odd number of items is a mistake rather than a key with no
     * value: {@code #[none]} is malformed, not a map holding NONE. Padding
     * it would make a typo into a map nobody meant.
     */
    public static MapValue of(List<Value> pairs) {
        if (pairs.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "a map needs a value for every key, and got " + pairs.size() + " items");
        }
        Map<Value, Value> built = new LinkedHashMap<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            built.put(lockedIfItIsText(keyOf(pairs.get(at))), pairs.get(at + 1));
        }
        return new MapValue(built);
    }

    /**
     * Any kind of word is stored as the set-word it names.
     *
     * <p>{@code if (ANY_WORD(key) && VAL_TYPE(key) != REB_SET_WORD) ...
     * VAL_SET(set, REB_SET_WORD);}. So {@code #[a: 1]} and
     * {@code make map! [a 1]} hold one key, and so do a lit-word, a get-word
     * and a refinement of the same spelling: a map is keyed by what a word
     * names rather than by how it was written.
     *
     * <p>The direction matters as much as the fact. Storing the set-word is
     * what makes a molded map read back as an equal map -- the colon comes from
     * the key and not from the molder, which is why a map keyed by an integer
     * molds as {@code #[1 2]} with no colon anywhere. KEYS-OF turns them back,
     * and so does the walk; nothing else does.
     */
    private static Value keyOf(Value written) {
        return written instanceof WordValue word && word.datatype() != Datatype.SET_WORD
                ? word.as(Datatype.SET_WORD)
                : written;
    }

    /**
     * A text key taken as the map's own, copied and locked.
     *
     * <p>A key is what the map is hashed on, so a caller who keeps hold of it
     * and appends to it would move the entry out from under its own hash. The
     * map therefore stores a copy and protects that, which is why
     * {@code append first keys-of #["key" 1] "x"} is a {@code protected} error
     * and why appending to the block the keys came from is not: the two are no
     * longer the same series.
     *
     * <p>Only text. A block key is stored as it stands and is not locked, and
     * Rebol's own suite says so in a comment -- "note that keys are not
     * implicitly protected!" -- and then shows what follows: poking with a
     * block a caller still holds, and then emptying it, gives a map with two
     * keys that are both {@code [b]}. Copying every key would have been the
     * tidier rule and is not the one REBOL has.
     */
    private static Value lockedIfItIsText(Value key) {
        if (!(key instanceof StringValue text)) {
            return key;
        }
        StringValue own = StringValue.of(text.text(), text.datatype());
        own.storage().protectFromChange(true);
        return own;
    }

    /** A stored key as KEYS-OF and the walk hand it out: a word, not a set-word. */
    private static Value keyAsAskedAbout(Value stored) {
        return stored instanceof WordValue word && word.datatype() == Datatype.SET_WORD
                ? word.as(Datatype.WORD)
                : stored;
    }

    /**
     * The key this map holds that matches the one asked about, or none.
     *
     * <p>{@code Find_Entry} takes a {@code cased} flag and its callers do not
     * agree about it: a path read, SELECT, FIND, PUT and POKE pass false, while
     * MAKE and REMOVE/KEY pass true. So the two ends of a map behave
     * differently on purpose -- building one keeps {@code "k"} and {@code "K"}
     * apart, and looking one up does not -- and that is what lets a caller use
     * whatever case is to hand while the map can still hold both.
     *
     * <p>Whichever was stored first, and not the exact one. A map holding both
     * {@code &lt;a&gt;} and {@code &lt;A&gt;} answers the first of them to
     * either spelling, so {@code select m &lt;A&gt;} is the value under
     * {@code &lt;a&gt;}. Preferring the exact key reads as the friendlier
     * answer and is not the one a real 3.22.1 gives -- and it made an uncased
     * SELECT indistinguishable from SELECT/CASE on exactly the maps where the
     * difference matters.
     */
    private Value theKeyMatching(Value asked, boolean mindingCase) {
        Value wanted = keyOf(asked);
        if (mindingCase) {
            return entries.containsKey(wanted) ? wanted : NoneValue.none();
        }
        return entries.keySet().stream()
                .filter(held -> held.equals(wanted) || alikeApartFromCase(held, wanted))
                .findFirst()
                .orElseGet(NoneValue::none);
    }

    /**
     * Whether two keys are the same but for case.
     *
     * <p>Only something with letters in it can be, which is why this is not a
     * general comparison: an integer key and a pair key have no case to differ
     * by, and asking whether they match without minding it is asking whether
     * they are equal.
     *
     * <p>A binary is not among them although it holds the same bytes a string
     * would. {@code #\{61}} and {@code #\{41}} are two keys however they are
     * asked for, because a binary is bytes rather than letters and nothing
     * says which of them stand for text.
     */
    private static boolean alikeApartFromCase(Value held, Value wanted) {
        if (held instanceof StringValue one && wanted instanceof StringValue other) {
            return one.datatype() == other.datatype()
                    && one.text().equalsIgnoreCase(other.text());
        }
        if (held instanceof WordValue one && wanted instanceof WordValue other) {
            return one.datatype() == other.datatype()
                    && one.canonical().equals(other.canonical());
        }
        if (held instanceof CharacterValue one && wanted instanceof CharacterValue other) {
            return Character.toLowerCase(one.codepoint())
                    == Character.toLowerCase(other.codepoint());
        }
        return false;
    }

    /** What a key holds, or NONE when the map has not got it. */
    public Value select(Value key) {
        return select(key, false);
    }

    public Value select(Value key, boolean mindingCase) {
        Value found = theKeyMatching(key, mindingCase);
        return found instanceof NoneValue
                ? NoneValue.none()
                : entries.getOrDefault(found, NoneValue.none());
    }

    public boolean holds(Value key) {
        return holds(key, false);
    }

    public boolean holds(Value key, boolean mindingCase) {
        return !(theKeyMatching(key, mindingCase) instanceof NoneValue);
    }

    /**
     * The key as the map holds it, or NONE when the map has not got it.
     *
     * <p>What FIND on a map answers, and the C says why with a comment on the
     * line itself: {@code // `find` returns the key}. The key it answers with
     * is not always the key that was asked for -- a word goes in and comes back
     * as a set-word, because that is how the entry is stored -- and that
     * difference is the only thing FIND on a map can tell a caller that SELECT
     * cannot.
     */
    public Value storedKeyLike(Value asked) {
        return storedKeyLike(asked, false);
    }

    public Value storedKeyLike(Value asked, boolean mindingCase) {
        return theKeyMatching(asked, mindingCase);
    }

    /** Adds or replaces a key, in place. */
    public void put(Value key, Value value) {
        put(key, value, false);
    }

    /**
     * The same, told whether to mind the case of an existing key.
     *
     * <p>A write that matches without minding case replaces the value and
     * <em>keeps the key that was already there</em>: writing {@code "K"} into a
     * map holding {@code "k"} leaves the key spelled {@code "k"}. The C does it
     * by finding the entry and setting only the slot after the key, and it
     * matters because FIND answers the stored key -- a lookup that quietly
     * respelled it would change what a later FIND says.
     */
    public void put(Value key, Value value, boolean mindingCase) {
        refuseIfProtected();
        Value existing = theKeyMatching(key, mindingCase);
        entries.put(existing instanceof NoneValue
                ? lockedIfItIsText(keyOf(key))
                : existing, value);
    }

    /** Empties the map, as CLEAR on a series empties it. */
    public void clear() {
        refuseIfProtected();
        entries.clear();
    }

    public void remove(Value key) {
        refuseIfProtected();
        entries.remove(keyOf(key));
    }

    /**
     * Refuses a change to a map somebody protected.
     *
     * <p>{@code TRAP_PROTECT(VAL_SERIES(value))} is the first line of every
     * branch of MT_Map that writes, and the error it raises is `protected`.
     * JEBOL kept the flag and let every write through, so PROTECT on a map
     * was a word that did nothing.
     */
    private void refuseIfProtected() {
        if (protectedFromChange) {
            throw new ProtectedFromChange();
        }
    }

    /** Pairs, not items: {@code #[a: 1 b: 2]} is two long. */
    public int pairCount() {
        return entries.size();
    }

    /**
     * The keys as a caller asks about them: plain words, not set-words.
     *
     * <p>{@code Map_To_Block} takes a flag for which question is asking, and
     * this is the only one that turns a word key back:
     * {@code if (ANY_WORD(val)) VAL_SET(out - 1, REB_WORD);} under
     * {@code what < 0}. So KEYS-OF answers `[a]` where BODY-OF answers
     * `[a: 1]`, and a caller can compare a key it was handed against a word it
     * wrote.
     */
    public List<Value> keys() {
        return entries.keySet().stream().map(MapValue::keyAsAskedAbout).toList();
    }

    public List<Value> values() {
        return List.copyOf(entries.values());
    }

    /**
     * The pairs in order, as a flat list, keys as they are stored.
     *
     * <p>What MOLD, BODY-OF and {@code to block!} all walk -- {@code what == 0}
     * in {@code Map_To_Block} -- and none of them normalises a word key. That is
     * what makes a molded map read back as an equal map: the colon is in the
     * key, so the molder writes the pairs out and nothing more.
     */
    public List<Value> flattened() {
        List<Value> flat = new ArrayList<>();
        entries.forEach((key, value) -> {
            flat.add(key);
            flat.add(value);
        });
        return List.copyOf(flat);
    }

    /**
     * The pairs as the walk hands them out: keys as plain words.
     *
     * <p>{@code if (IS_SET_WORD(vars)) SET_TYPE(vars, REB_WORD);} inside
     * {@code Loop_Each}. So FOREACH agrees with KEYS-OF rather than with
     * BODY-OF, and a walk that compares its key against a word finds what it
     * is looking for.
     */
    public List<Value> walkable() {
        List<Value> flat = new ArrayList<>();
        entries.forEach((key, value) -> {
            flat.add(keyAsAskedAbout(key));
            flat.add(value);
        });
        return List.copyOf(flat);
    }

    /**
     * Whether the map refuses to be changed, having been PROTECTed.
     *
     * <p>A map is protected as a series is, which is one line of the C:
     * {@code if (ANY_SERIES(value) || IS_MAP(value) || IS_BITSET(value))
     * Protect_Series(value, flags);}. It is not a block, so PROTECT/DEEP
     * stops here rather than reaching what the values hold.
     */
    public boolean isProtected() {
        return protectedFromChange;
    }

    public void protectFromChange(boolean refusing) {
        protectedFromChange = refusing;
    }

    /**
     * A copy, which is free to be changed even when the original was not.
     *
     * <p>{@code Copy_Map} builds a new series and copies the pairs into it,
     * and the protection lives on the series rather than on the pairs. So a
     * copy of a protected map is the way to get a changeable one, and a caller
     * that expected the protection to travel would be protecting nothing.
     */
    public MapValue copy() {
        return new MapValue(new LinkedHashMap<>(entries));
    }

    /** Same keys with equal values. Insertion order plays no part. */
    @Override
    public boolean equals(Object other) {
        return other instanceof MapValue map && entries.equals(map.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public Datatype datatype() {
        return Datatype.MAP;
    }

    @Override
    public String toString() {
        return "map of " + entries.size() + " pairs";
    }
}
