package org.jebol.domain.value;

import java.util.*;

/**
 * What a {@code gob!} holds: a place on a screen, one piece of content, and a
 * list of children.
 *
 * <p>Three things about it are not what the shape suggests, and all three come
 * out of {@code t-gob.c}.
 *
 * <p><b>A fresh one is not empty.</b> {@code Make_Gob} clears the struct and
 * then writes three fields: {@code GOB_W(gob) = 100; GOB_H(gob) = 100;
 * GOB_ALPHA(gob) = 255;}. So {@code make gob! []} is a hundred by a hundred and
 * fully opaque, and only the offset starts at nothing.
 *
 * <p><b>The content is a union.</b> {@code image}, {@code draw}, {@code text},
 * {@code effect} and {@code color} all write {@code GOB_CONTENT} and set one
 * type tag beside it, so giving a gob an image takes away whatever draw block it
 * had, and reading the field it has not got answers none. {@code data} is a
 * second slot with a tag of its own and does not join that union.
 *
 * <p><b>The pane is a series.</b> All 24 arms in {@code REBTYPE(Gob)} work on
 * the children rather than on the gob. The pane is allocated when the first
 * child arrives -- {@code tail = GOB_PANE(gob) ? GOB_TAIL(gob) : 0} -- which is
 * why a gob with no pane and a gob with an empty one answer the same to
 * everything.
 */
public final class GobStorage {

    /**
     * The kinds of content a gob holds, one at a time.
     *
     * <p>{@code GOBT_*}, and there are two for {@code text} rather than one: a
     * block is {@code GOBT_TEXT} and a string is {@code GOBT_STRING}. Both read
     * back through the same field name.
     */
    public enum Content { NONE, COLOUR, IMAGE, DRAW, TEXT, STRING, EFFECT, WIDGET }

    /**
     * The kinds of thing {@code data} holds. {@code GOBD_*}.
     *
     * <p>A separate tag from the content's, which is what lets a gob carry a
     * draw block and a block of data at the same time.
     */
    public enum Held { NONE, OBJECT, BLOCK, STRING, BINARY, INTEGER }

    /**
     * The flag words {@code flags} accepts.
     *
     * <p>Nine of them, in the order {@code Gob_Flag_Words} lists them, because
     * {@code Flags_To_Block} walks that same array and the block it builds comes
     * out in this order whatever order a script set them in.
     */
    public enum Flag {
        RESIZE, NO_TITLE, NO_BORDER, DROPABLE, TRANSPARENT, POPUP, MODAL, ON_TOP, HIDDEN;

        /** The word a script writes: the name lowercased, underscores as hyphens. */
        public String spelling() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /** The flag a word names, or nothing. {@code Set_Gob_Flag} ignores the rest. */
        public static Flag named(String word) {
            for (Flag flag : values()) {
                if (flag.spelling().equals(word)) {
                    return flag;
                }
            }
            return null;
        }
    }

    /** What {@code Make_Gob} writes over the cleared struct. */
    private static final double FRESH_SIDE = 100;
    private static final int OPAQUE = 255;

    private PairValue offset = PairValue.of(0, 0);
    private PairValue size = PairValue.of(FRESH_SIDE, FRESH_SIDE);
    private int alpha = OPAQUE;
    private Content contentKind = Content.NONE;
    private Value content = NoneValue.none();
    private Held dataKind = Held.NONE;
    private Value data = NoneValue.none();
    private GobStorage parent;
    private GobStorage owner;
    private final List<Value> pane = new ArrayList<>();
    private final Set<Flag> flags = EnumSet.noneOf(Flag.class);

    public PairValue offset() {
        return offset;
    }

    public void offset(PairValue where) {
        this.offset = where;
    }

    public PairValue size() {
        return size;
    }

    public void size(PairValue how) {
        this.size = how;
    }

    /** {@code GOB_ALPHA(gob) = Clip_Int(Int32(val), 0, 255)}. */
    public int alpha() {
        return alpha;
    }

    public void alpha(long given) {
        this.alpha = (int) Math.max(0, Math.min(OPAQUE, given));
    }

    public Content contentKind() {
        return contentKind;
    }

    /** The content, or none when the kind asked for is not the kind it has. */
    public Value contentIfKind(Content wanted) {
        return contentKind == wanted ? content : NoneValue.none();
    }

    /**
     * Puts one piece of content in, taking out whatever was there.
     *
     * <p>One slot and one tag: {@code CLR_GOB_OPAQUE(gob); SET_GOB_TYPE(gob,
     * ...); GOB_CONTENT(gob) = ...} on every one of the five field names.
     */
    public void content(Content kind, Value given) {
        this.contentKind = kind;
        this.content = given;
    }

    public Held dataKind() {
        return dataKind;
    }

    /** What {@code data} holds, or none when it holds nothing. */
    public Value data() {
        return dataKind == Held.NONE ? NoneValue.none() : data;
    }

    public void data(Held kind, Value given) {
        this.dataKind = kind;
        this.data = given;
    }

    public GobStorage parent() {
        return parent;
    }

    /**
     * The gob that owns this one for the host's purposes.
     *
     * <p>{@code GOB_TMP_OWNER} -- writable and not readable, because
     * {@code Set_GOB_Var} answers {@code owner} and {@code Get_GOB_Var} has no
     * case for it. So {@code g/owner: other} is accepted and {@code g/owner}
     * raises.
     */
    public GobStorage owner() {
        return owner;
    }

    public void owner(GobStorage given) {
        this.owner = given;
    }

    /** The flags that are set, in the order {@code Flags_To_Block} lists them. */
    public List<Flag> raisedFlags() {
        List<Flag> raised = new ArrayList<>();
        for (Flag flag : Flag.values()) {
            if (flags.contains(flag)) {
                raised.add(flag);
            }
        }
        return raised;
    }

    public void raise(Flag flag) {
        flags.add(flag);
    }

    /** Takes every flag down, which a block of flag words does before setting. */
    public void lowerEveryFlag() {
        flags.clear();
    }

    /** How many children there are. Zero for a gob that has no pane at all. */
    public int length() {
        return pane.size();
    }

    public List<Value> pane() {
        return List.copyOf(pane);
    }

    public Value childAt(int oneBasedIndex) {
        return pane.get(oneBasedIndex - 1);
    }

    /**
     * Puts a child in, and tells it who its parent is.
     *
     * <p>{@code Insert_Gobs} calls {@code Detach_Gob} first, so a gob appended
     * somewhere else moves rather than being shared: one gob has one parent, and
     * {@code Trap_Temp()} is what waits for the code that forgets.
     */
    public void insertChild(int oneBasedIndex, GobValue child) {
        GobStorage had = child.storage().parent;
        int goesAt = oneBasedIndex;
        if (had != null) {
            int wasAt = had.pane.indexOf(child) + 1;
            had.pane.remove(child);
            if (had == this && wasAt > 0 && goesAt > wasAt) {
                goesAt--;
            }
        }
        pane.add(Math.min(Math.max(1, goesAt), pane.size() + 1) - 1, child);
        child.storage().parent = this;
    }

    /** Takes children out, and forgets it was their parent. */
    public void removeChildren(int oneBasedIndex, int howMany) {
        for (int gone = 0; gone < howMany && oneBasedIndex <= pane.size(); gone++) {
            Value child = pane.remove(oneBasedIndex - 1);
            if (child instanceof GobValue gob && gob.storage().parent == this) {
                gob.storage().parent = null;
            }
        }
    }

    /** Puts the children back in the other order, which is what REVERSE does. */
    public void turnRound() {
        java.util.Collections.reverse(pane);
    }

    /** Where a child sits in the pane, or zero when it is not in it. */
    public int positionOf(GobStorage child) {
        for (int at = 1; at <= pane.size(); at++) {
            if (pane.get(at - 1) instanceof GobValue held && held.storage() == child) {
                return at;
            }
        }
        return 0;
    }

    /**
     * The spec block a gob molds as: {@code Gob_To_Block}.
     *
     * <p>Offset and size always, then the alpha when the gob is see-through, then
     * the one content field it has. Which fields appear is not "the ones a script
     * set" -- a gob that was never given a size still molds one.
     *
     * <p>The alpha is written as {@code 255 - alpha}, so molding a gob does not
     * read back as the gob it molded. That is {@code SET_INTEGER(val, 255 -
     * GOB_ALPHA(gob))} in the C rather than a slip here.
     */
    public List<Value> moldingSpec() {
        List<Value> spec = new ArrayList<>();
        spec.add(WordValue.of("offset", Datatype.SET_WORD));
        spec.add(offset);
        spec.add(WordValue.of("size", Datatype.SET_WORD));
        spec.add(size);
        if (alpha < OPAQUE) {
            spec.add(WordValue.of("alpha", Datatype.SET_WORD));
            spec.add(IntegerValue.of(OPAQUE - alpha));
        }
        String named = moldedContentName();
        if (!named.isEmpty()) {
            spec.add(WordValue.of(named, Datatype.SET_WORD));
            spec.add(content);
        }
        return spec;
    }

    /** Which field name the content molds under. No content molds no name. */
    private String moldedContentName() {
        return switch (contentKind) {
            case COLOUR -> "color";
            case IMAGE -> "image";
            case TEXT, STRING -> "text";
            case DRAW -> "draw";
            case EFFECT -> "effect";
            case NONE, WIDGET -> "";
        };
    }

    /**
     * A gob with the same fields and no children.
     *
     * <p>{@code *ngob = *gob; ngob->pane = 0; ngob->parent = 0;} -- the pane is
     * not copied, because a child has one parent and a clone cannot give it two.
     */
    public GobStorage copyWithoutPane() {
        GobStorage made = new GobStorage();
        made.offset = offset;
        made.size = size;
        made.alpha = alpha;
        made.contentKind = contentKind;
        made.content = content;
        made.dataKind = dataKind;
        made.data = data;
        made.owner = owner;
        made.flags.addAll(flags);
        return made;
    }

    @Override
    public String toString() {
        return "GobStorage(" + offset + " " + size + ", " + pane.size() + " children)";
    }
}
