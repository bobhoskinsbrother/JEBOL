package org.jebol.domain.host;

import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class APlaceAndItsAddressTest {

    private Value read(String source) {
        return Transcoder.transcode(source).values().orElseThrow().listItems().getFirst();
    }

    private String written(Place place) {
        return Molder.moldAll(place.asBlock());
    }

    private String errorIdRaisedBy(Runnable attempt) {
        try {
            attempt.run();
        } catch (Raised raised) {
            return raised.error().errorId();
        }
        throw new AssertionError("nothing was raised");
    }

    private String wordOfLength(int letters) {
        return "a".repeat(letters);
    }

    @Nested
    @DisplayName("what a place may be")
    class WhatAPlaceMayBe {

        @Test
        @DisplayName("a word is a place of one word, and its address is the word")
        void aWordIsAPlaceOfOne() {
            Place survey = Place.of(read("survey"));

            assertThat(written(survey)).isEqualTo("[survey]");
            assertThat(survey.address()).isEqualTo("survey");
        }

        @Test
        @DisplayName("a block is the place, its items separated by an escaped space")
        void aBlockIsThePlace() {
            Place question = Place.of(read("[survey 3]"));

            assertThat(written(question)).isEqualTo("[survey 3]");
            assertThat(question.address()).isEqualTo("survey%203");
        }

        @Test
        @DisplayName("a string is a place of one string, its quotes escaped")
        void aStringIsAPlaceOfOne() {
            Place named = Place.of(read("{a b}"));

            assertThat(written(named)).isEqualTo("""
                    ["a b"]""");
            assertThat(named.address()).isEqualTo("%22a%20b%22");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0", "-1", "1", "9223372036854775807", "-9223372036854775808"})
        @DisplayName("a whole number is a place of one, at its boundaries too")
        void aWholeNumberIsAPlaceOfOne(String number) {
            assertThat(written(Place.of(read(number)))).isEqualTo("[" + number + "]");
        }

        @Test
        @DisplayName("a decimal stays a decimal, apart from the whole number of the same size")
        void aDecimalStaysADecimal() {
            assertThat(Place.of(read("3.0")).address()).isNotEqualTo(Place.of(read("3")).address());
            assertThat(written(Place.readAddress(Place.of(read("1.5")).address()).orElseThrow()))
                    .isEqualTo("[1.5]");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"true", "false"})
        @DisplayName("a logic is written as a logic, so it reads back as one and not as a word")
        void aLogicReadsBackAsALogic(String which) {
            Place flagged = Place.of(read("#(" + which + ")"));

            Place readBack = Place.readAddress(flagged.address()).orElseThrow();

            assertThat(written(readBack)).isEqualTo("[#(" + which + ")]");
            assertThat(readBack).isEqualTo(flagged);
            assertThat(readBack).isNotEqualTo(Place.of(read(which)));
        }

        @Test
        @DisplayName("a block may hold blocks, to any depth")
        void blocksNest() {
            Place deep = Place.of(read("[a [b [c [d]]]]"));

            assertThat(written(deep)).isEqualTo("[a [b [c [d]]]]");
            assertThat(Place.readAddress(deep.address())).contains(deep);
        }

        @Test
        @DisplayName("an empty block is the start, and the start has no address")
        void anEmptyBlockIsTheStart() {
            assertThat(Place.of(read("[]"))).isEqualTo(Place.start());
            assertThat(Place.start().address()).isEmpty();
            assertThat(written(Place.start())).isEqualTo("[]");
        }

        @Test
        @DisplayName("none is not a place, and is refused as an invalid argument")
        void noneIsRefused() {
            assertThat(errorIdRaisedBy(() -> Place.of(read("#(none)")))).isEqualTo("invalid-arg");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"survey:", "'survey", ":survey", "/survey", "a/b", "(a)", "1x2",
                "1.2.3", "%file", "<tag>", "http://x.example", "#{00}", "22-Oct-2026",
                "$1", "#issue", "@ref", "a@b.c", "10:00"})
        @DisplayName("any other type is refused, and so is a block holding it")
        void otherTypesAreRefused(String source) {
            assertThat(errorIdRaisedBy(() -> Place.of(read(source)))).isEqualTo("invalid-arg");
            assertThat(errorIdRaisedBy(() -> Place.of(read("[ok [" + source + "]]"))))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a character is refused, alone or in a block")
        void aCharacterIsRefused() {
            assertThat(errorIdRaisedBy(() -> Place.of(read("""
                    [#"a" ok]""")))).isEqualTo("invalid-arg");
            assertThat(errorIdRaisedBy(() -> Place.of(read("#{61}")))).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a block holding none is refused")
        void aBlockHoldingNoneIsRefused() {
            assertThat(errorIdRaisedBy(() -> Place.of(read("[a #(none)]")))).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("the place is a copy: changing the block it came from changes nothing")
        void thePlaceIsACopy() {
            Value given = read("[a [b]]");
            Place copied = Place.of(given);

            Place.of(given).asBlock().storage().removeAt(1);
            copied.asBlock().storage().removeAt(1);

            assertThat(written(copied)).isEqualTo("[a [b]]");
        }

        @Test
        @DisplayName("two places are the same when their addresses are, and different data is a different place")
        void samenessIsTheAddress() {
            assertThat(Place.of(read("[survey 3]"))).isEqualTo(Place.of(read("[survey 3]")));
            assertThat(Place.of(read("[survey 3]")).hashCode()).isEqualTo(Place.of(read("[survey 3]")).hashCode());
            assertThat(Place.of(read("[survey 3]"))).isNotEqualTo(Place.of(read("[survey 4]")));
            assertThat(Place.of(read("survey"))).isEqualTo(Place.of(read("[survey]")));
        }
    }

    @Nested
    @DisplayName("how long an address may be")
    class HowLongAnAddressMayBe {

        @Test
        @DisplayName("one short of the longest is a place")
        void oneShortIsAPlace() {
            assertThat(Place.of(read(wordOfLength(Place.LONGEST_ADDRESS - 1))).address())
                    .hasSize(Place.LONGEST_ADDRESS - 1);
        }

        @Test
        @DisplayName("exactly the longest is a place")
        void exactlyTheLongestIsAPlace() {
            assertThat(Place.LONGEST_ADDRESS).isEqualTo(2048);
            assertThat(Place.of(read(wordOfLength(Place.LONGEST_ADDRESS))).address())
                    .hasSize(Place.LONGEST_ADDRESS);
        }

        @Test
        @DisplayName("one past the longest is refused as too long")
        void onePastIsRefused() {
            assertThat(errorIdRaisedBy(() -> Place.of(read(wordOfLength(Place.LONGEST_ADDRESS + 1)))))
                    .isEqualTo("too-long");
        }

        @Test
        @DisplayName("the length counted is the escaped address, not the text written")
        void theEscapedLengthCounts() {
            String spacedOut = "a ".repeat(Place.LONGEST_ADDRESS / 4).trim();

            Place place = Place.of(read("[" + spacedOut + "]"));

            assertThat(place.address()).contains("%20");
            assertThat(place.address().length()).isGreaterThan(spacedOut.length());
        }
    }

    @Nested
    @DisplayName("reading an address, which anyone can write")
    class ReadingAnAddress {

        @Test
        @DisplayName("no address at all is the start")
        void nothingIsTheStart() {
            assertThat(Place.readAddress("")).contains(Place.start());
        }

        @Test
        @DisplayName("a written place reads back as itself")
        void aWrittenPlaceReadsBack() {
            assertThat(Place.readAddress("survey%203")).contains(Place.of(read("[survey 3]")));
        }

        @Test
        @DisplayName("characters a browser leaves unescaped are read as themselves")
        void unescapedCharactersAreRead() {
            assertThat(Place.readAddress("survey 3")).contains(Place.of(read("[survey 3]")));
            assertThat(Place.readAddress("caf%C3%A9").map(Place::address))
                    .isEqualTo(Place.readAddress("café").map(Place::address));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"%", "%2", "%zz", "a%", "a%G0", "%%20", "%٣٣", "%２０"})
        @DisplayName("an escape that is not two ASCII hex digits is unreadable")
        void aBrokenEscapeIsUnreadable(String address) {
            assertThat(Place.readAddress(address)).isEmpty();
        }

        @Test
        @DisplayName("bytes that are not UTF-8 are unreadable")
        void notUtf8IsUnreadable() {
            assertThat(Place.readAddress("%FF")).isEmpty();
            assertThat(Place.readAddress("%C3")).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"%5B", "%22open", "%7B", "a%5D"})
        @DisplayName("text that does not read as REBOL is unreadable")
        void aSyntaxErrorIsUnreadable(String address) {
            assertThat(Place.readAddress(address)).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"a%3A", "%23(none)", "%28print%201%29", "%2Fa", "a%2Fb", "1x2",
                "%5Bok%20%27quoted%5D"})
        @DisplayName("text that reads but holds something no place may is unreadable")
        void theWrongKindIsUnreadable(String address) {
            assertThat(Place.readAddress(address)).isEmpty();
        }

        @Test
        @DisplayName("a paren in an address is never evaluated, only refused")
        void aParenIsNeverEvaluated() {
            assertThat(Place.readAddress("%28quit%29")).isEmpty();
        }

        @Test
        @DisplayName("an address exactly the longest reads, and one past it does not")
        void theLongestAddressReads() {
            assertThat(Place.readAddress(wordOfLength(Place.LONGEST_ADDRESS))).isPresent();
            assertThat(Place.readAddress(wordOfLength(Place.LONGEST_ADDRESS + 1))).isEmpty();
        }

        @Test
        @DisplayName("the word none is a word, and reads as a place")
        void theWordNoneIsAWord() {
            assertThat(Place.readAddress("none").map(Place::address)).isEqualTo(Optional.of("none"));
        }
    }
}
