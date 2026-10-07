package org.jebol.domain.eval.definition;

import org.jebol.application.Interpreter;
import org.jebol.domain.eval.BootDeclarations;
import org.jebol.domain.eval.CryptPort;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachSettingNativeDeclaresTest {

    private static final Set<String> NOTHING = Set.of();

    private static final Set<Datatype> ANY_TYPE = Typeset.ANY_TYPE.members();

    private static final Set<Datatype> AN_INTEGER = Set.of(Datatype.INTEGER);

    private Value answerOf(NativeDefinition definition, Set<String> refinements,
            Value... arguments) {
        return definition.behaviour().call(List.of(arguments), null, null, refinements);
    }

    private Value answerOf(NativeDefinition definition, Value... arguments) {
        return answerOf(definition, NOTHING, arguments);
    }

    private String ran(String source) {
        Interpreter interpreter = Interpreter.create();
        return interpreter.display(interpreter.run(source));
    }

    private Set<Datatype> whatSetTakes() {
        return Typeset.ANY_PATH.membersAnd(
                Datatype.WORD, Datatype.LIT_WORD, Datatype.BLOCK, Datatype.OBJECT);
    }

    private Set<Datatype> whatPokeTakes() {
        return Typeset.SERIES.membersAnd(
                Datatype.PORT, Datatype.MAP, Datatype.GOB, Datatype.BITSET);
    }

    private Set<Datatype> whatDifferenceTakes() {
        return Set.of(Datatype.BITSET, Datatype.TYPESET, Datatype.STRING, Datatype.MAP,
                Datatype.BLOCK, Datatype.DATE);
    }

    private Set<Datatype> whatBoundsAPart() {
        return Stream.concat(
                        Typeset.NUMBER.membersAnd(Datatype.PAIR).stream(),
                        Arrays.stream(Datatype.values()).filter(Datatype::isSeries))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Context holding(String name, Value value) {
        Context fields = Context.root();
        fields.set(name, value);
        return fields;
    }

    Stream<Arguments> whatEachDeclares() {
        return Stream.of(
                Arguments.of(new SetNative(), "set",
                        List.of(Parameter.required("word", whatSetTakes()),
                                Parameter.required("value", ANY_TYPE)),
                        Set.of("any", "only", "some")),
                Arguments.of(new GetNative(), "get",
                        List.of(Parameter.required("word")), Set.of("any")),
                Arguments.of(new TakeAction(new CryptPort()), "take",
                        List.of(Parameter.required("series"),
                                Parameter.belongingTo("part", "count", Set.of())),
                        Set.of("part", "last", "deep", "all")),
                Arguments.of(new AjoinNative(), "ajoin",
                        List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                                Parameter.belongingTo("with", "separator", ANY_TYPE)),
                        Set.of("all", "with")),
                Arguments.of(new PokeAction(), "poke",
                        List.of(Parameter.required("series", whatPokeTakes()),
                                Parameter.required("index"),
                                Parameter.required("value", ANY_TYPE)),
                        NOTHING),
                Arguments.of(new DifferenceNative(), "difference",
                        List.of(Parameter.required("first", whatDifferenceTakes()),
                                Parameter.required("second", whatDifferenceTakes()),
                                Parameter.belongingTo("skip", "size", AN_INTEGER)),
                        Set.of("case", "skip")),
                Arguments.of(new ReflectAction(new BootDeclarations()), "reflect",
                        List.of(Parameter.required("value"),
                                Parameter.required("field", Set.of(Datatype.WORD))),
                        NOTHING),
                Arguments.of(new PutAction(), "put",
                        List.of(Parameter.required("target"),
                                Parameter.required("key", ANY_TYPE),
                                Parameter.required("value", ANY_TYPE),
                                Parameter.belongingTo("skip", "size", AN_INTEGER)),
                        Set.of("case", "skip")),
                Arguments.of(new SelectAction(), "select",
                        List.of(Parameter.required("series"),
                                Parameter.required("value", ANY_TYPE),
                                Parameter.belongingTo("part", "range", whatBoundsAPart()),
                                Parameter.belongingTo("with", "wild", Set.of(Datatype.STRING)),
                                Parameter.belongingTo("skip", "size", AN_INTEGER)),
                        Set.of("case", "skip", "any", "only", "last", "part", "same", "with",
                                "reverse")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("whatEachDeclares")
    @DisplayName("each declares exactly what the inline definition declared")
    void declaresWhatItDeclaredInline(NativeDefinition definition, String name,
            List<Parameter> parameters, Set<String> refinements) {
        assertThat(definition.name()).isEqualTo(name);
        assertThat(definition.parameters()).isEqualTo(parameters);
        assertThat(definition.refinements()).isEqualTo(refinements);
    }

    @Nested
    @DisplayName("set and get write and read a word")
    class SetAndGet {

        @Test
        @DisplayName("set writes a bound word and answers what it wrote")
        void setAWord() {
            Context fields = holding("a", NoneValue.none());

            Value answer = answerOf(new SetNative(),
                    WordValue.of("a").boundTo(fields), IntegerValue.of(7));

            assertThat(answer).isEqualTo(IntegerValue.of(7));
            assertThat(fields.slotFor("a").value()).isEqualTo(IntegerValue.of(7));
        }

        @Test
        @DisplayName("set refuses unset without /any")
        void setRefusesUnset() {
            Context fields = holding("a", NoneValue.none());

            assertThatThrownBy(() -> answerOf(new SetNative(),
                    WordValue.of("a").boundTo(fields), UnsetValue.unset()))
                    .isInstanceOf(Raised.class);
        }

        @Test
        @DisplayName("set of a block of words spreads a block of values, padding with none")
        void setABlockOfWords() {
            Context fields = holding("a", NoneValue.none());
            fields.set("b", IntegerValue.of(9));

            answerOf(new SetNative(),
                    BlockValue.block(List.of(WordValue.of("a").boundTo(fields),
                            WordValue.of("b").boundTo(fields))),
                    BlockValue.block(List.of(IntegerValue.of(1))));

            assertThat(fields.slotFor("a").value()).isEqualTo(IntegerValue.of(1));
            assertThat(fields.slotFor("b").value()).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("get reads a word, and refuses unset unless asked /any")
        void getAWord() {
            Context fields = holding("a", IntegerValue.of(3));
            fields.set("u", UnsetValue.unset());

            assertThat(answerOf(new GetNative(), WordValue.of("a").boundTo(fields)))
                    .isEqualTo(IntegerValue.of(3));
            assertThatThrownBy(() -> answerOf(new GetNative(),
                    WordValue.of("u").boundTo(fields)))
                    .isInstanceOf(Raised.class);
            assertThat(answerOf(new GetNative(), Set.of("any"),
                    WordValue.of("u").boundTo(fields)))
                    .isInstanceOf(UnsetValue.class);
        }
    }

    @Nested
    @DisplayName("the actions on series and maps")
    class SeriesAndMaps {

        @Test
        @DisplayName("put adds a key a map has not got, and select reads it back")
        void putAndSelectOnAMap() {
            assertThat(ran("""
                    m: make map! [] put m 'k 1 select m 'k""")).isEqualTo("1");
        }

        @Test
        @DisplayName("put on a block replaces the value after a key it finds, and appends one it does not")
        void putOnABlock() {
            assertThat(ran("""
                    b: [a 1] put b 'a 2 put b 'c 3 b""")).isEqualTo("[a 2 c 3]");
        }

        @Test
        @DisplayName("poke replaces the item at a position")
        void pokeABlock() {
            assertThat(ran("""
                    b: [1 2] poke b 2 9 b""")).isEqualTo("[1 9]");
        }

        @Test
        @DisplayName("poke one past the end, or at nought, is out of range")
        void pokeOutOfRange() {
            assertThat(ran("""
                    error? try [poke [1 2] 3 9]""")).isEqualTo("#(true)");
            assertThat(ran("""
                    error? try [poke [1 2] 0 9]""")).isEqualTo("#(true)");
            assertThat(ran("""
                    b: [1 2] poke b 1 9 b""")).isEqualTo("[9 2]");
        }

        @Test
        @DisplayName("poke writes a character or a codepoint into a string")
        void pokeAString() {
            assertThat(ran("""
                    s: copy {abc} poke s 2 #"x" poke s 3 65 equal? s {axA}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("poke writes a byte into a binary, and refuses 256")
        void pokeABinary() {
            assertThat(ran("""
                    b: #{0000} poke b 1 255 b""")).isEqualTo("#{FF00}");
            assertThat(ran("""
                    error? try [poke #{00} 1 256]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("select answers the item after a match, or none past the end")
        void selectFromABlock() {
            assertThat(ran("""
                    select [a 1 b 2] 'b""")).isEqualTo("2");
            assertThat(ran("""
                    none? select [a 1 b] 'b""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("select/skip looks only at the first of each record")
        void selectByRecord() {
            assertThat(ran("""
                    select/skip [1 2 2 3] 2 2""")).isEqualTo("3");
        }

        @Test
        @DisplayName("select of an object's field answers its value, and none for a field it lacks")
        void selectFromAnObject() {
            assertThat(ran("""
                    select make object! [a: 1] 'a""")).isEqualTo("1");
            assertThat(ran("""
                    none? select make object! [a: 1] 'self""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("find/tail lands after the whole match, which select reads past")
        void findTailAfterAMatch() {
            assertThat(ran("""
                    equal? find/tail {abcd} {bc} {d}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("find/any matches a wildcard run")
        void findAnyWithWildcards() {
            assertThat(ran("""
                    equal? find/any {abcd} {b*d} {bcd}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("take removes the first item and answers it")
        void takeFromABlock() {
            BlockValue block = BlockValue.block(List.of(IntegerValue.of(1), IntegerValue.of(2)));

            assertThat(answerOf(new TakeAction(new CryptPort()), block)).isEqualTo(IntegerValue.of(1));
            assertThat(block.remaining()).containsExactly(IntegerValue.of(2));
        }

        @Test
        @DisplayName("take of none is none")
        void takeFromNone() {
            assertThat(answerOf(new TakeAction(new CryptPort()), NoneValue.none()))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("reflect lists an object's words")
        void reflectAnObject() {
            ObjectValue object = new ObjectValue(holding("a", IntegerValue.of(1)));

            Value words = answerOf(new ReflectAction(new BootDeclarations()),
                    object, WordValue.of("words"));

            assertThat(((BlockValue) words).remaining()).containsExactly(WordValue.of("a"));
        }

        @Test
        @DisplayName("difference keeps what only one side holds")
        void differenceOfBlocks() {
            Value kept = answerOf(new DifferenceNative(),
                    BlockValue.block(List.of(IntegerValue.of(1), IntegerValue.of(2))),
                    BlockValue.block(List.of(IntegerValue.of(2), IntegerValue.of(3))));

            assertThat(((BlockValue) kept).remaining())
                    .containsExactly(IntegerValue.of(1), IntegerValue.of(3));
        }
    }
}
