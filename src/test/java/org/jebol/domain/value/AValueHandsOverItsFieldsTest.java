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

    private static Context holding(String name, Value value) {
        Context fields = Context.root();
        fields.register(name, value);
        return fields;
    }

    @Test
    @DisplayName("an object hands over its own context, not a copy")
    void anObject() {
        Context fields = holding("a", IntegerValue.of(1));

        assertThat(new ObjectValue(fields).fieldsAsAContext()).containsSame(fields);
    }

    @Test
    @DisplayName("a module hands over its own context")
    void aModule() {
        Context fields = holding("a", IntegerValue.of(1));
        ObjectValue header = new ObjectValue(Context.root());

        assertThat(new ModuleValue(fields, header).fieldsAsAContext()).containsSame(fields);
    }

    @Test
    @DisplayName("a port hands over its own context")
    void aPort() {
        Context fields = holding("spec", NoneValue.none());

        assertThat(new PortValue(fields).fieldsAsAContext()).containsSame(fields);
    }

    @Test
    @DisplayName("an error hands over every field an error has, none where it has nothing")
    void anError() {
        ErrorValue error = ErrorValue.about(
                ErrorCategory.MATH, "zero-divide", "attempt to divide by zero",
                IntegerValue.of(7));

        Context fields = error.fieldsAsAContext().orElseThrow();

        assertThat(ErrorValue.FIELDS).allSatisfy(name ->
                assertThat(fields.holds(name)).as(name).isTrue());
        assertThat(fields.slotFor("arg1").value()).isEqualTo(IntegerValue.of(7));
        assertThat(fields.slotFor("arg2").value()).isEqualTo(NoneValue.none());
    }

    static Stream<Arguments> valuesWithNoFields() {
        return Stream.of(
                Arguments.of(IntegerValue.of(1)),
                Arguments.of(NoneValue.none()),
                Arguments.of(BlockValue.block(List.of(IntegerValue.of(1)))),
                Arguments.of(StringValue.of("a")),
                Arguments.of(MapValue.empty()),
                Arguments.of(WordValue.of("a")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("valuesWithNoFields")
    @DisplayName("anything else has no fields to hand over")
    void anythingElse(Value value) {
        assertThat(value.fieldsAsAContext()).isEmpty();
    }
}
