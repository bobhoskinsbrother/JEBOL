package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AReadUsesTheConstructionItIsGivenTest {

    private static final String A_STRUCT_LITERAL = """
            #(struct! [a [uint8!]] [a: 1])""";

    private static final String A_FUNCTION_LITERAL = """
            #(function! [[x] [x]])""";

    private static final Value WHAT_THE_CONSTRUCTION_MADE = IntegerValue.of(42);

    private static final Value WHAT_THE_FUNCTION_BUILDER_MADE = IntegerValue.of(7);

    private static final class RecordingConstruction implements Construction {

        private final List<Datatype> asked = new ArrayList<>();

        @Override
        public Value madeOf(Datatype datatype, Value specification) {
            asked.add(datatype);
            return WHAT_THE_CONSTRUCTION_MADE;
        }

        @Override
        public Value functionMadeFrom(AnyBlockValue spec, AnyBlockValue body) {
            return WHAT_THE_FUNCTION_BUILDER_MADE;
        }
    }

    private static final class RefusingConstruction implements Construction {

        @Override
        public Value madeOf(Datatype datatype, Value specification) {
            throw new IllegalArgumentException("refused");
        }

        @Override
        public Value functionMadeFrom(AnyBlockValue spec, AnyBlockValue body) {
            throw new IllegalArgumentException("refused");
        }
    }

    private static String failureOf(TranscodeResult read) {
        return read.error().orElseThrow().errorId();
    }

    private static List<Value> valuesOf(TranscodeResult read) {
        return read.values().orElseThrow().remaining();
    }

    @Nested
    @DisplayName("with no construction, every construction literal is malconstruct")
    class WithNone {

        @Test
        @DisplayName("a struct literal is refused")
        void aStructIsRefused() {
            assertThat(failureOf(Transcoder.transcode(A_STRUCT_LITERAL)))
                    .isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("a function literal is refused")
        void aFunctionIsRefused() {
            assertThat(failureOf(Transcoder.transcode(A_FUNCTION_LITERAL)))
                    .isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("and an interpreter existing somewhere in the process changes nothing")
        void anotherInterpreterDoesNotLendItsConstruction() {
            Interpreter.create();

            assertThat(failureOf(Transcoder.transcode(A_STRUCT_LITERAL)))
                    .isEqualTo("malconstruct");
            assertThat(failureOf(Transcoder.transcode(A_FUNCTION_LITERAL)))
                    .isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("the explicit refusal reads the same as giving none")
        void theRefusalIsTheSameAsNone() {
            assertThat(failureOf(Transcoder.transcode(A_STRUCT_LITERAL, Construction.refused())))
                    .isEqualTo("malconstruct");
        }
    }

    @Nested
    @DisplayName("with a construction, the read hands it every construction literal")
    class WithOne {

        @Test
        @DisplayName("a struct literal is whatever the construction makes")
        void aStructIsMadeByTheConstruction() {
            RecordingConstruction construction = new RecordingConstruction();

            assertThat(valuesOf(Transcoder.transcode(A_STRUCT_LITERAL, construction)))
                    .containsExactly(WHAT_THE_CONSTRUCTION_MADE);
            assertThat(construction.asked).containsExactly(Datatype.STRUCT);
        }

        @Test
        @DisplayName("a function literal is whatever the construction builds")
        void aFunctionIsBuiltByTheConstruction() {
            assertThat(valuesOf(Transcoder.transcode(A_FUNCTION_LITERAL,
                    new RecordingConstruction())))
                    .containsExactly(WHAT_THE_FUNCTION_BUILDER_MADE);
        }

        @Test
        @DisplayName("a literal nested in a block reaches the same construction")
        void insideABlock() {
            RecordingConstruction construction = new RecordingConstruction();

            List<Value> read = valuesOf(Transcoder.transcode(
                    "[" + A_STRUCT_LITERAL + "]", construction));

            assertThat(((AnyBlockValue) read.getFirst()).remaining())
                    .containsExactly(WHAT_THE_CONSTRUCTION_MADE);
            assertThat(construction.asked).containsExactly(Datatype.STRUCT);
        }

        @Test
        @DisplayName("a literal inside a path segment, which is read on its own, reaches it too")
        void insideAPathSegment() {
            RecordingConstruction construction = new RecordingConstruction();

            Transcoder.transcode("p/(" + A_STRUCT_LITERAL + ")", construction);

            assertThat(construction.asked).containsExactly(Datatype.STRUCT);
        }

        @Test
        @DisplayName("a construction that refuses makes the literal malconstruct")
        void aRefusalIsMalconstruct() {
            assertThat(failureOf(Transcoder.transcode(A_STRUCT_LITERAL,
                    new RefusingConstruction())))
                    .isEqualTo("malconstruct");
            assertThat(failureOf(Transcoder.transcode(A_FUNCTION_LITERAL,
                    new RefusingConstruction())))
                    .isEqualTo("malconstruct");
        }

        @Test
        @DisplayName("a literal the reader builds itself never reaches the construction")
        void aLiteralTheReaderBuildsItself() {
            RecordingConstruction construction = new RecordingConstruction();

            Transcoder.transcode("#(block! [1 2])", construction);

            assertThat(construction.asked).isEmpty();
        }
    }
}
