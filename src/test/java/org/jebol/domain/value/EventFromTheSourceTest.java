package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The event datatype, read out of {@code t-event.c} and {@code reb-event.h}.
 *
 * <p>A thing that happened, waiting to be answered: a click, a key, a connection
 * opening, a file dropped on a window. Not a series and not a container -- the C
 * keeps one in a single twelve-byte value cell, and says why in a comment:
 * "events are kept compact in order to fit into normal 128 bit values cells. This
 * provides high performance for high frequency events". So an event has MAKE, TO
 * and nothing else, and every field a script reads is unpacked from those bytes.
 *
 * <p>The packing is where the surprises are, and there are three.
 *
 * <p><b>The type is an index into a block a script can read.</b>
 * {@code system/catalog/event-types} lists 47 words and the event stores the
 * position of one. Which means the catalogue is the authority on what type words
 * exist, and index 0 -- the word {@code ignore} -- reads back as none rather than
 * as that word.
 *
 * <p><b>Offset, key and code are one field seen three ways.</b> Four bytes, with
 * two flags saying how to read them: an offset packs two signed shorts and raises
 * HAS_XY, while a key or a code puts a number there and raises HAS_CODE. Writing
 * either takes the other's flag down, so an event with a key has no offset --
 * which Rebol's own test asserts.
 *
 * <p><b>And port, gob and data share one slot.</b> The model byte says which is
 * in it, {@code window} and {@code gob} are two names for the same field, and a
 * GUI event with nothing in the slot answers {@code system/ports/event} for its
 * port rather than none.
 *
 * <p>Rebol's own {@code event-test.r3} settles seven of these and is quoted where
 * it does. Specified in {@code spec/values.allium} as {@code EventValue}.
 */
class EventFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    /**
     * A port to hang an event on.
     *
     * <p>{@code make port! system/standard/port} is the way to one here: a scheme
     * has to be granted before OPEN will answer a port, and none of the schemes a
     * test may open has anything to do with events.
     */
    private static final String A_PORT = "p: make port! system/standard/port ";

    @Nested
    @DisplayName("the type, which is a position in a catalogue")
    class TheType {

        @Test
        @DisplayName("a type word is found in system/catalog/event-types and kept as its index")
        void aTypeWordIsAnIndex() {
            // `for (n = 0, arg = VAL_BLK(arg); NOT_END(arg); arg++, n++) { if
            // (IS_WORD(arg) && VAL_WORD_CANON(arg) == w) { VAL_EVENT_TYPE(value) =
            // n; return TRUE; } }` -- Rebol's own test uses `connect`.
            assertThat(answerTo("event? make event! [type: 'connect]")).isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [type: 'connect] e/type"))
                    .isEqualTo("connect");
            assertThat(answerTo("e: make event! [type: 'close] e/type"))
                    .isEqualTo("close");
        }

        @Test
        @DisplayName("and the catalogue is what a script can read to find out which words those are")
        void theCatalogueIsReadable() {
            // The list lives in `sysobj.reb` and nowhere else, so a type word is
            // only a type word because the catalogue says so. 47 of them, and the
            // comment above them says the order cannot change after release.
            assertThat(answerTo("block? system/catalog/event-types")).isEqualTo(TRUE);
            assertThat(answerTo("first system/catalog/event-types")).isEqualTo("ignore");
            assertThat(answerTo("length? system/catalog/event-types")).isEqualTo("47");
        }

        @Test
        @DisplayName("a word the catalogue has not got is the wrong argument")
        void anUnknownTypeIsRefused() {
            // `Trap_Arg(val)` after the loop runs off the end, which is
            // `invalid-arg` -- and not the `bad-field-set` a field the event has
            // not got would give.
            assertThat(errorIdFrom("make event! [type: 'nonsense]"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and something that is not a word at all is a bad field set")
        void aNonWordTypeIsRefused() {
            // `if (!IS_WORD(val) && !IS_LIT_WORD(val)) return FALSE;`, and FALSE
            // is what `Set_Event_Vars` turns into `bad-field-set`. So the two
            // refusals are different errors and a script can tell them apart.
            assertThat(errorIdFrom("make event! [type: 1]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make event! [type: \"connect\"]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("and both a word and a lit-word are accepted, which is why the spec can hold either")
        void aWordOrALitWordIsAccepted() {
            // `if (!IS_WORD(val) && !IS_LIT_WORD(val)) return FALSE;` -- two
            // datatypes, and the reason is `Get_Simple_Value`: every value in a
            // spec block goes through it, and it resolves a plain word to what the
            // word holds. So a literal `[type: 'connect]` reaches the setter as a
            // lit-word and a `[type: t]` reaches it as whatever t holds -- which
            // for `t: 'connect` is the plain word. Both spellings, one meaning.
            assertThat(answerTo("e: make event! [type: 'connect] e/type"))
                    .isEqualTo("connect");
            assertThat(answerTo("t: 'connect e: make event! [type: t] e/type"))
                    .isEqualTo("connect");
        }

        @Test
        @DisplayName("and a spec block may name its values rather than spell them out")
        void theSpecResolvesWordsAndPaths() {
            // `val = Get_Simple_Value(val);` -- "does easy lookup, else just
            // returns the value as is". A word or a path becomes what it holds, so
            // a spec can be built from values that were worked out elsewhere.
            // Rebol's own gob test relies on the same line: `make gob! [size:
            // g1/size]` under "simple paths inside GOB".
            assertThat(answerTo("where: 3x4 e: make event! [offset: where] e/offset"))
                    .isEqualTo("3x4");
            assertThat(answerTo(
                    "o: make object! [n: 7] e: make event! [code: o/n] e/code"))
                    .isEqualTo("7");
            assertThat(answerTo(
                    "g: make gob! [size: 2x3] h: make gob! [size: g/size] h/size"))
                    .isEqualTo("2x3");
        }

        @Test
        @DisplayName("the type nothing set reads as none, and so does the first word in the list")
        void typeZeroIsNone() {
            // `if (VAL_EVENT_TYPE(value) == 0) goto is_none;` -- so a fresh event
            // has no type, and `type: 'ignore` sets index 0 and is indistinguishable
            // from never having set one. The word `ignore` cannot be read back.
            assertThat(answerTo("e: make event! [] none? e/type")).isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [type: 'ignore] none? e/type"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("offset, key and code: one field, three readings")
    class TheDataField {

        @Test
        @DisplayName("an offset is two signed shorts packed into the one data field")
        void anOffsetIsPacked() {
            // `SET_EVENT_XY(v,x,y)` is `VAL_EVENT_DATA(v) = ((y << 16) | (x &
            // 0xffff))`, and reading takes each half back through `(short)`.
            assertThat(answerTo("e: make event! [offset: 1x2] e/offset"))
                    .isEqualTo("1x2");
            assertThat(answerTo("e: make event! [offset: -5x-7] e/offset"))
                    .isEqualTo("-5x-7");
        }

        @Test
        @DisplayName("a half that will not fit in a short is refused, not truncated")
        void theOffsetLimits() {
            // `if (fabs(f) > (REBD32)(0x7FFF)) { ... Trap_Range(DS_TOP); }` in
            // `Float_Int16`. Rebol's own test pins both ends of it, and the ON
            // point too: 32767 fits, 32768 does not.
            assertThat(errorIdFrom("make event! [offset: 32768x0]"))
                    .isEqualTo("out-of-range");
            assertThat(errorIdFrom("make event! [offset: 65536x1]"))
                    .isEqualTo("out-of-range");
            assertThat(answerTo("e: make event! [offset: 32767x-32767] e/offset"))
                    .isEqualTo("32767x-32767");
            assertThat(errorIdFrom("make event! [offset: -32769x0]"))
                    .isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("and an offset that is not a pair is a bad field set")
        void anOffsetMustBeAPair() {
            // The commented-out `else if (IS_NONE(val))` in the C is the C
            // wondering whether none should take an offset away. It decided not
            // to, so none is refused like anything else.
            assertThat(errorIdFrom("make event! [offset: 5]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make event! [offset: none]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a key is a character, and setting one takes the offset away")
        void aKeyClearsTheOffset() {
            // `CLR_FLAG(VAL_EVENT_FLAGS(value), EVF_HAS_XY); SET_FLAG(...,
            // EVF_HAS_CODE);` -- one field, so the two readings cannot both be
            // live. Rebol's own test asserts `none? e/offset` beside the key.
            assertThat(answerTo("e: make event! [type: 'key key: #\"A\"] e/key"))
                    .isEqualTo("#\"A\"");
            assertThat(answerTo(
                    "e: make event! [type: 'key key: #\"A\"] none? e/offset"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: make event! [offset: 1x2 key: #\"A\"] none? e/offset"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an offset takes the code away, the same way round")
        void anOffsetClearsTheCode() {
            // `CLR_FLAG(VAL_EVENT_FLAGS(value), EVF_HAS_CODE)` in the offset arm.
            assertThat(answerTo(
                    "e: make event! [type: 'custom code: 5 offset: 1x2] none? e/code"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: make event! [type: 'custom code: 5 offset: 1x2] e/offset"))
                    .isEqualTo("1x2");
        }

        @Test
        @DisplayName("a key with no type makes the event a key event")
        void aKeyImpliesTheType() {
            // `if(!VAL_EVENT_TYPE(value)) VAL_EVENT_TYPE(value) = EVT_KEY;`, which
            // Rebol's own test states as a comment: "added automaticaly if no type
            // is specified".
            assertThat(answerTo("e: make event! [key: #\"B\"] e/type"))
                    .isEqualTo("key");
            assertThat(answerTo("e: make event! [key: #\"B\"] e/key"))
                    .isEqualTo("#\"B\"");
        }

        @Test
        @DisplayName("and only a key or key-up event reads a key back, though any type keeps the code")
        void onlyKeyTypesReadAKey() {
            // Reading: `if (VAL_EVENT_TYPE(value) == EVT_KEY || ... EVT_KEY_UP)
            // SET_CHAR(val, n);` and none otherwise. The byte is still there --
            // `e/code` answers it -- so the type decides how the same number is
            // read, and Rebol's own test asserts all three facts together.
            assertThat(answerTo(
                    "e: make event! [type: 'custom key: #\"C\"] none? e/key"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [type: 'custom key: #\"C\"] e/code"))
                    .isEqualTo("67");
            assertThat(answerTo("e: make event! [type: 'key-up key: #\"C\"] e/key"))
                    .isEqualTo("#\"C\"");
            assertThat(answerTo("e: make event! [type: 'key-up key: #\"C\"] e/code"))
                    .isEqualTo("67");
        }

        @Test
        @DisplayName("a key given as a word is looked up in the key catalogue")
        void aWordKeyIsAnIndex() {
            // `VAL_EVENT_DATA(value) = (n+1) << 16` -- the position in
            // `system/catalog/event-keys`, shifted into the top half, so a named
            // key and a character never collide. `page-up` is the first, so it
            // becomes 1 << 16.
            assertThat(answerTo("e: make event! [type: 'custom key: 'page-up] e/code"))
                    .isEqualTo("65536");
            assertThat(answerTo("e: make event! [type: 'custom key: 'end] e/code"))
                    .isEqualTo("196608");
            assertThat(answerTo("block? system/catalog/event-keys")).isEqualTo(TRUE);
            assertThat(answerTo("first system/catalog/event-keys")).isEqualTo("page-up");
        }

        @Test
        @DisplayName("a control event reads its key back out of that catalogue, from a plain number")
        void aControlEventNamesItsKey() {
            // `else if (VAL_EVENT_TYPE(value) == EVT_CONTROL || ... EVT_CONTROL_UP)
            // { ... if (IS_BLOCK(arg) && n <= (REBINT)VAL_TAIL(arg)) *val =
            // *VAL_BLK_SKIP(arg, n-1); }` -- so the same stored number reads as a
            // character for a key event and as a word for a control event. The
            // number it wants is a plain position, 1 to 33.
            assertThat(answerTo("e: make event! [type: 'control code: 5] e/key"))
                    .isEqualTo("left");
            assertThat(answerTo("e: make event! [type: 'control-up code: 4] e/key"))
                    .isEqualTo("home");
        }

        @Test
        @DisplayName("but a control key set by name cannot be read back, because the two halves disagree")
        void theControlKeyRoundTripIsBroken() {
            // The write shifts and the read does not. `Set_Event_Var` stores
            // `(n+1) << 16` for a key word; the control arm of `Get_Event_Var`
            // wants `n <= VAL_TAIL(arg)`, which 327680 is not, so it falls to
            // `goto is_none`.
            //
            // Not a slip here and not one worth correcting: a control event's key
            // is written by the host, which puts the raw position in the field
            // directly, and the shifted form is what a *key* event wants. The two
            // readings of one field met in the middle and nobody joined them up.
            // A script that sets `key: 'left` on a control event reads none.
            assertThat(answerTo("e: make event! [type: 'control key: 'left] none? e/key"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [type: 'control key: 'left] e/code"))
                    .isEqualTo("327680");
        }

        @Test
        @DisplayName("a word the key catalogue has not got is refused")
        void anUnknownKeyIsRefused() {
            // `if (IS_END(arg)) return FALSE;` after the loop, so this is a bad
            // field set rather than the invalid-arg an unknown *type* gives. The
            // two loops fail differently and the C does not tidy that up.
            assertThat(errorIdFrom("make event! [key: 'nonsense]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a key nothing set reads as none, and a key that is neither char nor word is refused")
        void aKeyAtItsEdges() {
            // The read falls through to `goto is_none` for a type that is not one
            // of the four, and a fresh event has no type at all. Going in, the arm
            // takes a char, a word or a lit-word and `return FALSE` for the rest --
            // note that it has already written the model and the type by then, so
            // the refusal comes after two fields were changed.
            assertThat(answerTo("e: make event! [] none? e/key")).isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [key: 1]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make event! [key: \"A\"]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a code is an integer, and nothing else")
        void aCodeIsAnInteger() {
            // Rebol's own test: `make event! [type: 'custom code: 1]`.
            assertThat(answerTo("e: make event! [type: 'custom code: 1] e/code"))
                    .isEqualTo("1");
            assertThat(answerTo("e: make event! [type: 'custom code: 1] e/type"))
                    .isEqualTo("custom");
            assertThat(errorIdFrom("make event! [code: \"1\"]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("and a code nothing set reads as none")
        void noCodeIsNone() {
            // `if (GET_FLAG(VAL_EVENT_FLAGS(value), EVF_HAS_CODE)) ... goto
            // is_none;` -- the flag rather than the bytes, so a zero code that was
            // set reads as 0 and a code that was never set reads as none.
            assertThat(answerTo("e: make event! [] none? e/code")).isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [type: 'custom code: 0] e/code"))
                    .isEqualTo("0");
            assertThat(answerTo("e: make event! [] none? e/offset")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the shared slot: port, gob and data")
    class TheAttachedThing {

        @Test
        @DisplayName("a port goes in the slot and the model says it is there")
        void aPortIsHeld() {
            // `VAL_EVENT_MODEL(value) = EVM_PORT; VAL_EVENT_SER(value) =
            // VAL_PORT(val);`
            assertThat(answerTo(A_PORT + "e: make event! [port: p] same? p e/port"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(A_PORT + "e: make event! [port: p] port? e/port"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an object goes in the same slot under a different model")
        void anObjectIsHeld() {
            // `else if (IS_OBJECT(val)) { VAL_EVENT_MODEL(value) = EVM_OBJECT;
            // ... }` -- so `port:` is the way in for an object too, and `e/port`
            // reads it back as an object.
            assertThat(answerTo(
                    "o: make object! [a: 1] e: make event! [port: o] object? e/port"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "o: make object! [a: 1] e: make event! [port: o] e/port/a"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("and none means the event belongs to the GUI, whose port is the host's")
        void noneMeansTheGui() {
            // `else if (IS_NONE(val)) { VAL_EVENT_MODEL(value) = EVM_GUI; }` going
            // in, and `if (IS_EVENT_MODEL(value, EVM_GUI)) *val =
            // *Get_System(SYS_PORTS, PORTS_EVENT);` coming out. So the answer is
            // whatever that field holds, and it holds none until a window system
            // fills it -- in a stock console 3.22.1 as much as here. Rebol's own
            // event test guards its port case with `if system/ports/event [...]`
            // for exactly this reason.
            assertThat(answerTo("none? system/ports/event")).isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [port: none] none? e/port"))
                    .isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [port: 1]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a gob goes in the slot, and window is the same field under another name")
        void aGobIsHeld() {
            // `case SYM_WINDOW: case SYM_GOB:` share one arm going in and one
            // coming out, so the two words are one field and neither is an alias
            // of the other.
            assertThat(answerTo(
                    "g: make gob! [] e: make event! [gob: g] same? g e/gob"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] e: make event! [gob: g] same? g e/window"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "g: make gob! [] e: make event! [window: g] same? g e/gob"))
                    .isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [gob: 1]")).isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("and a gob and a port cannot both be there, because there is one slot")
        void theSlotHoldsOneThing() {
            // Both arms write `VAL_EVENT_SER(value)`, so the second write is the
            // one that stands -- and it moves the model with it, which is what
            // makes the first unreadable rather than merely overwritten.
            assertThat(answerTo(A_PORT + "g: make gob! [] "
                    + "e: make event! [gob: g port: p] none? e/gob")).isEqualTo(TRUE);
            assertThat(answerTo(A_PORT + "g: make gob! [] "
                    + "e: make event! [gob: g port: p] same? p e/port")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a gob nothing set reads as none")
        void noGobIsNone() {
            assertThat(answerTo("e: make event! [] none? e/gob")).isEqualTo(TRUE);
            // And an event holding a port has no gob, because the model is wrong
            // for one: `if (IS_EVENT_MODEL(value, EVM_GUI))` guards the read.
            assertThat(answerTo(A_PORT + "e: make event! [port: p] none? e/gob"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and data is the dropped file, which only a drop-file event has")
        void dataIsForDroppedFiles() {
            // `if (!GET_FLAG(..., EVF_HAS_DATA)) goto is_none; if
            // (VAL_EVENT_TYPE(value) != EVT_DROP_FILE) goto is_none;` -- two
            // guards, and only the host raises that flag. So a script always reads
            // none here, and that is the answer rather than a gap.
            assertThat(answerTo("e: make event! [] none? e/data")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: make event! [type: 'drop-file] none? e/data")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("flags, which read as words and are written by the host")
    class TheFlags {

        @Test
        @DisplayName("three of them read back as a block, and the rest are the C's bookkeeping")
        void flagsReadAsWords() {
            // `if (VAL_EVENT_FLAGS(value) & (1<<EVF_DOUBLE | 1<<EVF_CONTROL |
            // 1<<EVF_SHIFT))` -- only those three, and none when none of them is
            // up. Nothing in `Set_Event_Var` writes them, so a script can read
            // them and never set them: they come from the window system.
            assertThat(answerTo("e: make event! [] none? e/flags")).isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [flags: [double]]"))
                    .isEqualTo("bad-field-set");
        }
    }

    @Nested
    @DisplayName("making one, and what an event will not do")
    class MakingOne {

        @Test
        @DisplayName("a block of set-words fills the fields, and an empty one makes an empty event")
        void aBlockOfFields() {
            // `CLEARS(out); Set_Event_Vars(out, VAL_BLK_DATA(data));`
            assertThat(answerTo("event? make event! []")).isEqualTo(TRUE);
            assertThat(answerTo("event? make event! [type: 'connect]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a field name it does not know is a bad field set")
        void anUnknownFieldIsRefused() {
            // `default: return FALSE;` in `Set_Event_Var`, and `if
            // (!Set_Event_Var(evt, var, val)) Trap2(RE_BAD_FIELD_SET, ...)`.
            assertThat(errorIdFrom("make event! [nonsense: 1]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a set-word with nothing after it is read as none, not refused")
        void aTrailingSetWordIsNone() {
            // `if (IS_END(val)) val = NONE_VALUE;` -- which is the opposite of what
            // a gob does with the same shape, where it is `need-value`. So
            // `make event! [port:]` is `make event! [port: none]` and works, while
            // `make event! [type:]` fails because none is not a word.
            assertThat(answerTo("event? make event! [port:]")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: make event! [port:] same? system/ports/event e/port"))
                    .isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [type:]")).isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("MAKE from an event and a block starts from nothing, not from that event")
        void makeFromAnEventDoesNotCopyIt() {
            // `CLEARS(&(D_RET->data.event));` runs whether the first argument was
            // the datatype or an event, so `make e [offset: 0x0]` is a fresh event
            // with an offset and not a copy of e with one. Rebol's own test only
            // checks the offset, and this checks what the clear did to the rest.
            assertThat(answerTo(
                    "e: make event! [type: 'connect] "
                    + "e2: make e [offset: 0x0] e2/offset")).isEqualTo("0x0");
            assertThat(answerTo(
                    "e: make event! [type: 'connect] "
                    + "e2: make e [offset: 0x0] none? e2/type")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and MAKE given an event alone answers that very event")
        void makeFromAnEventAlone() {
            // `if (IS_EVENT(arg)) return R_ARG2;` -- the same event, not a copy,
            // which for a value held in a cell is a distinction without a
            // difference until something writes through a path.
            assertThat(answerTo(
                    "e: make event! [type: 'connect] f: make event! e f/type"))
                    .isEqualTo("connect");
        }

        @Test
        @DisplayName("and anything else is the wrong argument")
        void anythingElseIsRefused() {
            // `Trap_Types(RE_EXPECT_VAL, REB_EVENT, VAL_TYPE(arg))`.
            assertThat(errorIdFrom("make event! 1")).isEqualTo("expect-val");
            assertThat(errorIdFrom("make event! \"connect\"")).isEqualTo("expect-val");
        }

        @Test
        @DisplayName("an event has two arms and everything else is an operation it cannot do")
        void twoArmsAndNoMore() {
            // `else Trap_Action(REB_EVENT, action);` -- so an event is not a
            // series, not a container, and answers none of the questions one of
            // those would.
            assertThat(errorIdFrom("length? make event! []")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("append make event! [] 1")).isEqualTo("cannot-use");
            assertThat(errorIdFrom("copy make event! []")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("reading a field it has not got is an invalid path")
        void readingAnUnknownFieldRaises() {
            // `if (!Get_Event_Var(...)) return PE_BAD_SELECT;` -- so an unknown
            // field raises where a known one that is empty answers none. Which is
            // the opposite way round from what it looks like: `e/data` is none and
            // `e/date` is an error.
            assertThat(errorIdFrom("e: make event! [] e/nonsense"))
                    .isEqualTo("invalid-path");
            assertThat(errorIdFrom("e: make event! [] e/1")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("TO does exactly what MAKE does, sharing the one arm")
        void toIsMake() {
            // `if (action == A_MAKE || action == A_TO)` -- one branch for both, so
            // there is nothing TO does differently. Worth a test because for most
            // datatypes the two part company.
            assertThat(answerTo("e: to event! [type: 'connect] e/type"))
                    .isEqualTo("connect");
            assertThat(errorIdFrom("to event! 1")).isEqualTo("expect-val");
        }

        @Test
        @DisplayName("and a field can be written through a path")
        void aFieldIsWrittenThroughAPath() {
            // `if (!Set_Event_Var(pvs->value, pvs->select, pvs->setval)) return
            // PE_BAD_SET;` -- the same setter MAKE uses, so a path write refuses
            // exactly what a spec block refuses, under a different error name.
            assertThat(answerTo("e: make event! [] e/type: 'connect e/type"))
                    .isEqualTo("connect");
            assertThat(answerTo("e: make event! [] e/offset: 3x4 e/offset"))
                    .isEqualTo("3x4");
            assertThat(errorIdFrom("e: make event! [] e/nonsense: 1"))
                    .isEqualTo("bad-path-set");
        }
    }

    @Nested
    @DisplayName("molding and comparing")
    class MoldingAndComparing {

        @Test
        @DisplayName("an event molds as the fields that answer something")
        void itMoldsItsSetFields() {
            // `Mold_Event` walks a fixed list -- type, port, gob, offset, key,
            // flags, code, data -- and writes each one that is not none, with a
            // quote before a word. So the mold shows what can be read rather than
            // what was written, and a GUI port always appears.
            assertThat(answerTo("mold/flat make event! []")).isEqualTo("\"make event! []\"");
            assertThat(answerTo("mold/flat make event! [offset: 1x2]"))
                    .isEqualTo("\"make event! [offset: 1x2]\"");
            // `if (IS_WORD(&val)) Append_Byte(mold->series, '\'');` -- a type
            // molds with a quote in front, so the mold reads back as itself.
            assertThat(answerTo("mold/flat make event! [type: 'connect]"))
                    .isEqualTo("\"make event! [type: 'connect]\"");
        }

        @Test
        @DisplayName("two events are equal when their model, type and data agree")
        void equalityIsThreeFields() {
            // `Cmp_Event` compares exactly those three and nothing else, so two
            // events holding different ports are equal as long as both hold one.
            assertThat(answerTo(
                    "equal? (make event! [type: 'connect]) (make event! [type: 'connect])"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "equal? (make event! [type: 'connect]) (make event! [type: 'close])"))
                    .isEqualTo("#(false)");
            assertThat(answerTo(
                    "equal? (make event! [offset: 1x2]) (make event! [offset: 3x4])"))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and the flags take no part in it")
        void flagsAreNotCompared() {
            // Which is worth pinning because it is a field the C could have
            // compared and chose not to: `Cmp_Event` names model, type and xy.
            assertThat(answerTo(
                    "equal? (make event! [type: 'key key: #\"A\"]) "
                    + "(make event! [type: 'key key: #\"A\"])")).isEqualTo(TRUE);
        }
    }
}
