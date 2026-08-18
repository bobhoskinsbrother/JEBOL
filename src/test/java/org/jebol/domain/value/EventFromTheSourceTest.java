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
            assertThat(answerTo("event? make event! [type: 'connect]")).isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [type: 'connect] e/type"))
                    .isEqualTo("connect");
            assertThat(answerTo("e: make event! [type: 'close] e/type"))
                    .isEqualTo("close");
        }

        @Test
        @DisplayName("and the catalogue is what a script can read to find out which words those are")
        void theCatalogueIsReadable() {
            assertThat(answerTo("block? system/catalog/event-types")).isEqualTo(TRUE);
            assertThat(answerTo("first system/catalog/event-types")).isEqualTo("ignore");
            assertThat(answerTo("length? system/catalog/event-types")).isEqualTo("47");
        }

        @Test
        @DisplayName("a word the catalogue has not got is the wrong argument")
        void anUnknownTypeIsRefused() {
            assertThat(errorIdFrom("make event! [type: 'nonsense]"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and something that is not a word at all is a bad field set")
        void aNonWordTypeIsRefused() {
            assertThat(errorIdFrom("make event! [type: 1]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make event! [type: \"connect\"]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("and both a word and a lit-word are accepted, which is why the spec can hold either")
        void aWordOrALitWordIsAccepted() {
            assertThat(answerTo("e: make event! [type: 'connect] e/type"))
                    .isEqualTo("connect");
            assertThat(answerTo("t: 'connect e: make event! [type: t] e/type"))
                    .isEqualTo("connect");
        }

        @Test
        @DisplayName("and a spec block may name its values rather than spell them out")
        void theSpecResolvesWordsAndPaths() {
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
            assertThat(answerTo("e: make event! [offset: 1x2] e/offset"))
                    .isEqualTo("1x2");
            assertThat(answerTo("e: make event! [offset: -5x-7] e/offset"))
                    .isEqualTo("-5x-7");
        }

        @Test
        @DisplayName("a half that will not fit in a short is refused, not truncated")
        void theOffsetLimits() {
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
            assertThat(errorIdFrom("make event! [offset: 5]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make event! [offset: none]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a key is a character, and setting one takes the offset away")
        void aKeyClearsTheOffset() {
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
            assertThat(answerTo("e: make event! [key: #\"B\"] e/type"))
                    .isEqualTo("key");
            assertThat(answerTo("e: make event! [key: #\"B\"] e/key"))
                    .isEqualTo("#\"B\"");
        }

        @Test
        @DisplayName("and only a key or key-up event reads a key back, though any type keeps the code")
        void onlyKeyTypesReadAKey() {
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
            assertThat(answerTo("e: make event! [type: 'control code: 5] e/key"))
                    .isEqualTo("left");
            assertThat(answerTo("e: make event! [type: 'control-up code: 4] e/key"))
                    .isEqualTo("home");
        }

        @Test
        @DisplayName("but a control key set by name cannot be read back, because the two halves disagree")
        void theControlKeyRoundTripIsBroken() {
            assertThat(answerTo("e: make event! [type: 'control key: 'left] none? e/key"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("e: make event! [type: 'control key: 'left] e/code"))
                    .isEqualTo("327680");
        }

        @Test
        @DisplayName("a word the key catalogue has not got is refused")
        void anUnknownKeyIsRefused() {
            assertThat(errorIdFrom("make event! [key: 'nonsense]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a key nothing set reads as none, and a key that is neither char nor word is refused")
        void aKeyAtItsEdges() {
            assertThat(answerTo("e: make event! [] none? e/key")).isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [key: 1]")).isEqualTo("bad-field-set");
            assertThat(errorIdFrom("make event! [key: \"A\"]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a code is an integer, and nothing else")
        void aCodeIsAnInteger() {
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
            assertThat(answerTo(A_PORT + "e: make event! [port: p] same? p e/port"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(A_PORT + "e: make event! [port: p] port? e/port"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an object goes in the same slot under a different model")
        void anObjectIsHeld() {
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
            // Writing none is not clearing the field. `else if (IS_NONE(val))
            // VAL_EVENT_MODEL(value) = EVM_GUI;` -- it says which of the
            // seven models this event uses, and reading the field back then
            // answers the one port every GUI event belongs to: `if
            // (IS_EVENT_MODEL(value, EVM_GUI)) *val = *Get_System(SYS_PORTS,
            // PORTS_EVENT);`.
            //
            // This asserted `none? system/ports/event` until that port
            // existed, which pinned a gap in JEBOL rather than anything the C
            // does. A real 3.22.1 has the port even in a console build.
            assertThat(answerTo("port? system/ports/event")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: make event! [port: none] same? e/port system/ports/event"))
                    .isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [port: 1]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("and a gob puts the event in the same model, so it answers the same port")
        void agobMeansTheGuiToo() {
            assertThat(answerTo(
                    "g: make gob! [] e: make event! [gob: g] "
                            + "same? e/port system/ports/event"))
                    .as("`case SYM_WINDOW: case SYM_GOB:` sets EVM_GUI as well")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a gob goes in the slot, and window is the same field under another name")
        void aGobIsHeld() {
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
            assertThat(answerTo(A_PORT + "g: make gob! [] "
                    + "e: make event! [gob: g port: p] none? e/gob")).isEqualTo(TRUE);
            assertThat(answerTo(A_PORT + "g: make gob! [] "
                    + "e: make event! [gob: g port: p] same? p e/port")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a gob nothing set reads as none")
        void noGobIsNone() {
            assertThat(answerTo("e: make event! [] none? e/gob")).isEqualTo(TRUE);
            assertThat(answerTo(A_PORT + "e: make event! [port: p] none? e/gob"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and data is the dropped file, which only a drop-file event has")
        void dataIsForDroppedFiles() {
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
            assertThat(answerTo("event? make event! []")).isEqualTo(TRUE);
            assertThat(answerTo("event? make event! [type: 'connect]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a field name it does not know is a bad field set")
        void anUnknownFieldIsRefused() {
            assertThat(errorIdFrom("make event! [nonsense: 1]"))
                    .isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("a set-word with nothing after it is read as none, not refused")
        void aTrailingSetWordIsNone() {
            assertThat(answerTo("event? make event! [port:]")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: make event! [port:] same? system/ports/event e/port"))
                    .isEqualTo(TRUE);
            assertThat(errorIdFrom("make event! [type:]")).isEqualTo("bad-field-set");
        }

        @Test
        @DisplayName("MAKE from an event and a block starts from nothing, not from that event")
        void makeFromAnEventDoesNotCopyIt() {
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
            assertThat(answerTo(
                    "e: make event! [type: 'connect] f: make event! e f/type"))
                    .isEqualTo("connect");
        }

        @Test
        @DisplayName("and anything else is the wrong argument")
        void anythingElseIsRefused() {
            assertThat(errorIdFrom("make event! 1")).isEqualTo("expect-val");
            assertThat(errorIdFrom("make event! \"connect\"")).isEqualTo("expect-val");
        }

        @Test
        @DisplayName("an event has two arms and everything else is an operation it cannot do")
        void twoArmsAndNoMore() {
            assertThat(errorIdFrom("length? make event! []")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("append make event! [] 1")).isEqualTo("cannot-use");
            assertThat(errorIdFrom("copy make event! []")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("reading a field it has not got is an invalid path")
        void readingAnUnknownFieldRaises() {
            assertThat(errorIdFrom("e: make event! [] e/nonsense"))
                    .isEqualTo("invalid-path");
            assertThat(errorIdFrom("e: make event! [] e/1")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("TO does exactly what MAKE does, sharing the one arm")
        void toIsMake() {
            assertThat(answerTo("e: to event! [type: 'connect] e/type"))
                    .isEqualTo("connect");
            assertThat(errorIdFrom("to event! 1")).isEqualTo("expect-val");
        }

        @Test
        @DisplayName("and a field can be written through a path")
        void aFieldIsWrittenThroughAPath() {
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
            assertThat(answerTo("mold/flat make event! []")).isEqualTo("\"make event! []\"");
            assertThat(answerTo("mold/flat make event! [offset: 1x2]"))
                    .isEqualTo("\"make event! [offset: 1x2]\"");
            assertThat(answerTo("mold/flat make event! [type: 'connect]"))
                    .isEqualTo("\"make event! [type: 'connect]\"");
        }

        @Test
        @DisplayName("two events are equal when their model, type and data agree")
        void equalityIsThreeFields() {
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
            assertThat(answerTo(
                    "equal? (make event! [type: 'key key: #\"A\"]) "
                    + "(make event! [type: 'key key: #\"A\"])")).isEqualTo(TRUE);
        }
    }
}
