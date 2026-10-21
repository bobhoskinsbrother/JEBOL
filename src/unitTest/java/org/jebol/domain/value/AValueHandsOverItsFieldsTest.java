package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AValueHandsOverItsFieldsTest {

    private static Context makeAContextHolding(String name, Value value) {
        Context fields = Context.root();
        fields.register(name, value);
        return fields;
    }

    @Test
    @DisplayName("an object hands over its own context, not a copy")
    void coversAnObject() {
        Context fields = makeAContextHolding("a", IntegerValue.of(1));

        assertThat(new ObjectValue(fields).fieldsAsContext()).containsSame(fields);
    }

    @Test
    @DisplayName("a module hands over its own context")
    void coversAModule() {
        Context fields = makeAContextHolding("a", IntegerValue.of(1));
        ObjectValue header = new ObjectValue(Context.root());

        assertThat(new ModuleValue(fields, header).fieldsAsContext()).containsSame(fields);
    }

    @Test
    @DisplayName("a port hands over its own context")
    void coversAPort() {
        Context fields = makeAContextHolding("spec", NoneValue.none());

        assertThat(new PortValue(fields).fieldsAsContext()).containsSame(fields);
    }

    @Test
    @DisplayName("an error hands over every field an error has, none where it has nothing")
    void coversAnError() {
        ErrorValue error = ErrorValue.of(
                ErrorCategory.MATH, "zero-divide", "attempt to divide by zero",
                IntegerValue.of(7));

        Context fields = error.fieldsAsContext().orElseThrow();

        assertThat(ErrorValue.FIELDS).allSatisfy(name ->
                assertThat(fields.holds(name)).as(name).isTrue());
        assertThat(fields.findSlotFor("arg1").value()).isEqualTo(IntegerValue.of(7));
        assertThat(fields.findSlotFor("arg2").value()).isEqualTo(NoneValue.none());
    }

    static Stream<Arguments> valuesWithNoFields() {
        return Stream.of(
                Arguments.of(IntegerValue.of(1)),
                Arguments.of(NoneValue.none()),
                Arguments.of(BlockValue.of(List.of(IntegerValue.of(1)))),
                Arguments.of(StringValue.of("a")),
                Arguments.of(MapValue.empty()),
                Arguments.of(WordValue.of("a")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valuesWithNoFields")
    @DisplayName("anything else has no fields to hand over")
    void answersAnythingElse(Value value) {
        assertThat(value.fieldsAsContext()).isEmpty();
    }
}
