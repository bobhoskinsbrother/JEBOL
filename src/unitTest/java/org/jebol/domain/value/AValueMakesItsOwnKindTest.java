package org.jebol.domain.value;

import org.jebol.domain.eval.natives.PrintNative;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AValueMakesItsOwnKindTest {

    private final ObjectValue anEvaluatedObject = new ObjectValue(Context.root());

    private final FunctionValue aBoundFunction = new FunctionValue(BlockValue.of(), BlockValue.of(),
            List.of(), List.of(), Context.root());

    private final Value whatAPrototypeMade = IntegerValue.of(42);

    private final class RecordingMaker implements Maker {

        private final List<String> asked = new ArrayList<>();

        @Override
        public Value makeObjectFrom(ObjectValue prototype, BlockValue body) {
            asked.add("object like");
            return whatAPrototypeMade;
        }

        @Override
        public Value mergeAnObject(ObjectValue prototype, ObjectValue other) {
            asked.add("object merged");
            return whatAPrototypeMade;
        }

        @Override
        public Value makeFunctionFrom(AnyFunctionValue prototype, AnyBlockValue spec) {
            asked.add("derived from " + prototype.datatype().literalSpelling());
            return whatAPrototypeMade;
        }

        @Override
        public Value makeCommandFrom(AnyBlockValue definition) {
            asked.add("a command");
            return whatAPrototypeMade;
        }

        @Override
        public Value makeStructFrom(StructValue prototype, Value spec) {
            asked.add("struct like");
            return whatAPrototypeMade;
        }

        @Override
        public ObjectValue evaluateAnObject(AnyBlockValue body) {
            asked.add("object evaluated");
            return anEvaluatedObject;
        }

        @Override
        public FunctionValue bindFunction(AnyBlockValue spec, AnyBlockValue body) {
            asked.add("function bound");
            return aBoundFunction;
        }

        @Override
        public Value applySystemFunction(String name, Value argument) {
            asked.add("applied " + name);
            return whatAPrototypeMade;
        }

        @Override
        public Value readASimpleValue(Value piece) {
            return piece;
        }

        @Override
        public ErrorValue speakHere(ErrorValue error) {
            asked.add("error spoken");
            return error;
        }

        @Override
        public StructSpec.LayoutRegistry structLayouts() {
            return layoutName -> Optional.empty();
        }

        @Override
        public Optional<List<Value>> readTheValuesFrom(String source) {
            return Optional.empty();
        }

        @Override
        public AnyBlockValue readSource(String source) {
            asked.add("read " + source);
            return BlockValue.of();
        }
    }

    private final RecordingMaker maker = new RecordingMaker();

    private AnyBlockValue readABlock() {
        return BlockValue.of(List.of(IntegerValue.of(1)));
    }

    private FunctionValue makeAFunction() {
        return new FunctionValue(BlockValue.of(), BlockValue.of(),
                List.of(), List.of(), Context.root());
    }

    private StructValue makeAStruct() {
        AnyBlockValue layout = BlockValue.of(List.of(WordValue.of("a"),
                BlockValue.of(List.of(WordValue.of("uint8!")))));
        return StructValue.of(StructSpec.of(layout, name -> Optional.empty()));
    }

    private String readTheErrorIdOf(Runnable making) {
        try {
            making.run();
        } catch (Raised raised) {
            return raised.error().errorId();
        }
        return "nothing raised";
    }

    @Nested
    @DisplayName("a datatype makes one of the type it is, asking the maker only to evaluate")
    class ADatatype {

        @Test
        @DisplayName("an integer is made from a string without the maker")
        void makesAnInteger() {
            assertThat(IntegerValue.TYPE.make(StringValue.of("7"), maker)).isEqualTo(IntegerValue.of(7));
            assertThat(maker.asked).isEmpty();
        }

        @Test
        @DisplayName("a map is made from a block of pairs without the maker")
        void makesAMap() {
            assertThat(MapValue.TYPE.make(BlockValue.of(List.of(WordValue.of("a"), IntegerValue.of(1))), maker))
                    .isInstanceOf(MapValue.class);
            assertThat(maker.asked).isEmpty();
        }

        @Test
        @DisplayName("a block and a string are made from a number as room, without the maker")
        void makesRoomForABlockAndAString() {
            assertThat(BlockValue.TYPE.make(IntegerValue.of(3), maker)).isEqualTo(BlockValue.of());
            assertThat(StringValue.TYPE.make(IntegerValue.of(3), maker)).isEqualTo(StringValue.of(""));
            assertThat(maker.asked).isEmpty();
        }

        @Test
        @DisplayName("an object asks the maker to evaluate its body")
        void makesAnObject() {
            assertThat(ObjectValue.TYPE.make(readABlock(), maker)).isSameAs(anEvaluatedObject);
            assertThat(maker.asked).containsExactly("object evaluated");
        }

        @Test
        @DisplayName("a function asks the maker to bind its body")
        void makesAFunction() {
            assertThat(FunctionValue.TYPE.make(BlockValue.of(List.of(BlockValue.of(), BlockValue.of())), maker))
                    .isSameAs(aBoundFunction);
            assertThat(maker.asked).containsExactly("function bound");
        }

        @Test
        @DisplayName("a closure binds a function and turns it into a closure")
        void makesAClosure() {
            assertThat(ClosureValue.TYPE.make(BlockValue.of(List.of(BlockValue.of(), BlockValue.of())), maker))
                    .isInstanceOf(ClosureValue.class);
            assertThat(maker.asked).containsExactly("function bound");
        }

        @Test
        @DisplayName("an error from a message is spoken where it was made")
        void makesAnErrorFromAMessage() {
            assertThat(ErrorValue.TYPE.make(StringValue.of("b"), maker)).isInstanceOf(ErrorValue.class);
            assertThat(maker.asked).containsExactly("error spoken");
        }

        @Test
        @DisplayName("a module asks the maker to run make-module*, and refuses an answer that is not a module")
        void makesAModule() {
            assertThat(readTheErrorIdOf(() -> ModuleValue.TYPE.makeFrom(readABlock(), maker))).isEqualTo("invalid-spec");
            assertThat(maker.asked).containsExactly("applied make-module*");
        }
    }

    @Nested
    @DisplayName("a value used as a prototype")
    class APrototype {

        @Test
        @DisplayName("an object is made like itself")
        void makesAnObject() {
            assertThat(new ObjectValue(Context.root()).make(readABlock(), maker)).isSameAs(whatAPrototypeMade);
            assertThat(maker.asked).containsExactly("object like");
        }

        @Test
        @DisplayName("a function given a block derives from itself")
        void makesAFunctionGivenABlock() {
            makeAFunction().make(readABlock(), maker);
            assertThat(maker.asked).containsExactly("derived from function!");
        }

        @Test
        @DisplayName("a function given anything but a block refuses to be its prototype")
        void makesAFunctionGivenSomethingElse() {
            assertThat(readTheErrorIdOf(() -> makeAFunction().make(IntegerValue.of(1), maker))).isEqualTo("cannot-use");
            assertThat(maker.asked).isEmpty();
        }

        @Test
        @DisplayName("a native given a block derives from itself")
        void makesANativeGivenABlock() {
            new PrintNative().make(readABlock(), maker);
            assertThat(maker.asked).containsExactly("derived from native!");
        }

        @Test
        @DisplayName("a native given anything but a block refuses to be its prototype")
        void makesANativeGivenSomethingElse() {
            assertThat(readTheErrorIdOf(() -> new PrintNative().make(IntegerValue.of(1), maker))).isEqualTo("cannot-use");
        }

        @Test
        @DisplayName("an error given a message makes an error from it")
        void makesAnErrorGivenAMessage() {
            assertThat(ErrorValue.of(ErrorCategory.USER, "message", "a").make(StringValue.of("b"), maker))
                    .isInstanceOf(ErrorValue.class);
            assertThat(maker.asked).containsExactly("error spoken");
        }

        @Test
        @DisplayName("a struct is made like itself")
        void makesAStruct() {
            AValueMakesItsOwnKindTest.this.makeAStruct().make(readABlock(), maker);
            assertThat(maker.asked).containsExactly("struct like");
        }

        @Test
        @DisplayName("an event is made from a block of its fields")
        void makesAnEvent() {
            assertThat(EventValue.fresh().make(BlockValue.of(), maker)).isInstanceOf(EventValue.class);
        }

        @Test
        @DisplayName("anything else is made as any other value of its datatype would be")
        void makesAnythingElse() {
            assertThat(readABlock().make(IntegerValue.of(3), maker)).isEqualTo(BlockValue.of());
            assertThat(StringValue.of("a").make(IntegerValue.of(3), maker)).isEqualTo(StringValue.of(""));
            assertThat(IntegerValue.of(1).make(StringValue.of("7"), maker)).isEqualTo(IntegerValue.of(7));
            assertThatThrownBy(() -> TupleValue.of(1, 2, 3).make(BlockValue.of(List.of(WordValue.of("x"))), maker))
                    .isInstanceOf(Raised.class);
        }
    }
}
