package org.jebol.domain.value;

import org.jebol.domain.eval.natives.PrintNative;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AValueMakesItsOwnKindTest {

    private static final Value WHAT_WAS_MADE = IntegerValue.of(42);

    private static final class RecordingMaker implements Maker {

        private final List<String> asked = new ArrayList<>();

        @Override
        public Value make(Datatype kind, Value spec) {
            asked.add("made " + kind.literalSpelling());
            return WHAT_WAS_MADE;
        }

        @Override
        public Value makeAnotherFrom(Datatype kind, Value spec) {
            asked.add("made from a " + kind.literalSpelling());
            return WHAT_WAS_MADE;
        }

        @Override
        public Value makeObjectFrom(ObjectValue prototype, Value spec) {
            asked.add("object like");
            return WHAT_WAS_MADE;
        }

        @Override
        public Value makeFunctionFrom(Value function, BlockValue spec) {
            asked.add("derived from " + function.datatype().literalSpelling());
            return WHAT_WAS_MADE;
        }

        @Override
        public Value makeErrorFrom(Value spec) {
            asked.add("error from");
            return WHAT_WAS_MADE;
        }

        @Override
        public Value makeStructFrom(StructValue prototype, Value spec) {
            asked.add("struct like");
            return WHAT_WAS_MADE;
        }

        @Override
        public Value convertedTo(DatatypeValue wanted, Value value) {
            asked.add("converted to " + wanted.represents().literalSpelling());
            return WHAT_WAS_MADE;
        }

        @Override
        public Value makeEventFrom(EventValue prototype, Value spec) {
            asked.add("event like");
            return WHAT_WAS_MADE;
        }
    }

    private static List<String> whatWasAskedWhenMaking(Value prototype, Value spec) {
        RecordingMaker maker = new RecordingMaker();
        assertThat(prototype.make(spec, maker)).isSameAs(WHAT_WAS_MADE);
        return maker.asked;
    }

    private static BlockValue aBlock() {
        return BlockValue.block(List.of(IntegerValue.of(1)));
    }

    private static FunctionValue aFunction() {
        return new FunctionValue(BlockValue.block(), BlockValue.block(),
                List.of(), List.of(), Context.root());
    }

    private static StructValue aStruct() {
        BlockValue layout = BlockValue.block(List.of(WordValue.of("a"),
                BlockValue.block(List.of(WordValue.of("uint8!")))));
        return StructValue.of(StructSpec.of(layout, name -> Optional.empty()));
    }

    @Nested
    @DisplayName("a datatype asks for one of the kind it names")
    class ADatatype {

        @ParameterizedTest(name = "{0}")
        @MethodSource("org.jebol.domain.value.AValueMakesItsOwnKindTest#someDatatypes")
        void asksForItsKind(Datatype kind) {
            assertThat(whatWasAskedWhenMaking(DatatypeValue.of(kind), aBlock()))
                    .containsExactly("made " + kind.literalSpelling());
        }
    }

    static Stream<Arguments> someDatatypes() {
        return Stream.of(Datatype.OBJECT, Datatype.MAP, Datatype.BLOCK, Datatype.STRING,
                Datatype.FUNCTION, Datatype.ERROR, Datatype.INTEGER, Datatype.STRUCT)
                .map(Arguments::of);
    }

    @Nested
    @DisplayName("a value used as a prototype")
    class APrototype {

        @Test
        @DisplayName("an object is made like itself")
        void anObject() {
            assertThat(whatWasAskedWhenMaking(new ObjectValue(Context.root()), aBlock()))
                    .containsExactly("object like");
        }

        @Test
        @DisplayName("a function given a block derives from itself")
        void aFunctionGivenABlock() {
            assertThat(whatWasAskedWhenMaking(aFunction(), aBlock()))
                    .containsExactly("derived from function!");
        }

        @Test
        @DisplayName("a function given anything else is made as any other function would be")
        void aFunctionGivenSomethingElse() {
            assertThat(whatWasAskedWhenMaking(aFunction(), IntegerValue.of(1)))
                    .containsExactly("made from a function!");
        }

        @Test
        @DisplayName("a native given a block derives from itself")
        void aNativeGivenABlock() {
            assertThat(whatWasAskedWhenMaking(new PrintNative(), aBlock()))
                    .containsExactly("derived from native!");
        }

        @Test
        @DisplayName("a native given anything else is made as any other native would be")
        void aNativeGivenSomethingElse() {
            assertThat(whatWasAskedWhenMaking(new PrintNative(),
                    IntegerValue.of(1)))
                    .containsExactly("made from a native!");
        }

        @Test
        @DisplayName("an error given a message makes an error from it")
        void anErrorGivenAMessage() {
            assertThat(whatWasAskedWhenMaking(
                    ErrorValue.of(ErrorCategory.USER, "message", "a"), StringValue.of("b")))
                    .containsExactly("error from");
        }

        @Test
        @DisplayName("an error given anything else is made as any other error would be")
        void anErrorGivenSomethingElse() {
            assertThat(whatWasAskedWhenMaking(
                    ErrorValue.of(ErrorCategory.USER, "message", "a"), aBlock()))
                    .containsExactly("made from a error!");
        }

        @Test
        @DisplayName("a struct is made like itself")
        void aStruct() {
            assertThat(whatWasAskedWhenMaking(AValueMakesItsOwnKindTest.aStruct(), aBlock()))
                    .containsExactly("struct like");
        }

        @Test
        @DisplayName("an event is made like itself")
        void anEvent() {
            assertThat(whatWasAskedWhenMaking(EventValue.fresh(), aBlock()))
                    .containsExactly("event like");
        }

        @Test
        @DisplayName("anything else is made as any other value of its datatype would be")
        void anythingElse() {
            assertThat(whatWasAskedWhenMaking(aBlock(), IntegerValue.of(3)))
                    .containsExactly("made from a block!");
            assertThat(whatWasAskedWhenMaking(StringValue.of("a"), IntegerValue.of(3)))
                    .containsExactly("made from a string!");
            assertThat(whatWasAskedWhenMaking(IntegerValue.of(1), StringValue.of("7")))
                    .containsExactly("made from a integer!");
            assertThat(whatWasAskedWhenMaking(MapValue.empty(), aBlock()))
                    .containsExactly("made from a map!");
        }
    }
}
