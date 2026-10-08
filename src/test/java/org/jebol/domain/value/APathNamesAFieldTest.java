package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class APathNamesAFieldTest {

    private static Context holding(String name, Value value) {
        Context fields = Context.root();
        fields.register(name, value);
        return fields;
    }

    private static WordValue boundIn(String spelling, Context context) {
        return WordValue.of(spelling).boundTo(context);
    }

    private static BlockValue path(Value... segments) {
        return BlockValue.block(List.of(segments)).as(Datatype.PATH);
    }

    private static Context aScriptHoldingAnObject() {
        return holding("account", new ObjectValue(holding("balance", IntegerValue.of(10))));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Datatype.class, names = {"PATH", "SET_PATH", "GET_PATH", "LIT_PATH"})
    @DisplayName("every kind of path names the field it ends on")
    void everyKindOfPath(Datatype kind) {
        Context script = aScriptHoldingAnObject();
        BlockValue named = BlockValue.block(List.of(
                boundIn("account", script), WordValue.of("balance"))).as(kind);

        assertThat(named.fieldThePathNames()).hasValueSatisfying(slot ->
                assertThat(slot.value()).isEqualTo(IntegerValue.of(10)));
    }

    @Test
    @DisplayName("a path through nested objects names the innermost field")
    void throughNestedObjects() {
        Context script = holding("bank", new ObjectValue(holding("account",
                new ObjectValue(holding("balance", IntegerValue.of(10))))));

        assertThat(path(boundIn("bank", script), WordValue.of("account"),
                WordValue.of("balance")).fieldThePathNames())
                .hasValueSatisfying(slot ->
                        assertThat(slot.value()).isEqualTo(IntegerValue.of(10)));
    }

    @Test
    @DisplayName("the slot is the object's own, so writing through it changes the object")
    void theSlotIsTheObjectsOwn() {
        Context balanceHolder = holding("balance", IntegerValue.of(10));
        Context script = holding("account", new ObjectValue(balanceHolder));

        path(boundIn("account", script), WordValue.of("balance"))
                .fieldThePathNames().orElseThrow().setValue(IntegerValue.of(20));

        assertThat(balanceHolder.slotFor("balance").value()).isEqualTo(IntegerValue.of(20));
    }

    @Test
    @DisplayName("a path of one segment names no field")
    void oneSegment() {
        assertThat(path(boundIn("account", aScriptHoldingAnObject())).fieldThePathNames())
                .isEmpty();
    }

    @Test
    @DisplayName("an empty path names no field")
    void noSegments() {
        assertThat(path().fieldThePathNames()).isEmpty();
    }

    @Test
    @DisplayName("an unbound first word names no field")
    void anUnboundStart() {
        assertThat(path(WordValue.of("account"), WordValue.of("balance")).fieldThePathNames())
                .isEmpty();
    }

    @Test
    @DisplayName("a first segment that is not a word names no field")
    void aStartThatIsNotAWord() {
        assertThat(path(IntegerValue.of(1), WordValue.of("balance")).fieldThePathNames())
                .isEmpty();
    }

    @Test
    @DisplayName("stepping through something that is not an object names no field")
    void steppingThroughANonObject() {
        Context script = holding("account", IntegerValue.of(1));

        assertThat(path(boundIn("account", script), WordValue.of("balance"))
                .fieldThePathNames()).isEmpty();
    }

    @Test
    @DisplayName("a field the object does not hold is named by nothing")
    void aMissingField() {
        assertThat(path(boundIn("account", aScriptHoldingAnObject()), WordValue.of("owner"))
                .fieldThePathNames()).isEmpty();
    }

    @Test
    @DisplayName("a segment that is not a word names no field")
    void aSegmentThatIsNotAWord() {
        assertThat(path(boundIn("account", aScriptHoldingAnObject()), IntegerValue.of(1))
                .fieldThePathNames()).isEmpty();
    }

    @Test
    @DisplayName("a hidden field is named by nothing, as field selection would not find it")
    void aHiddenField() {
        Context balanceHolder = holding("balance", IntegerValue.of(10));
        balanceHolder.slotFor("balance").hide(true);
        Context script = holding("account", new ObjectValue(balanceHolder));

        assertThat(path(boundIn("account", script), WordValue.of("balance"))
                .fieldThePathNames()).isEmpty();
    }

    @Test
    @DisplayName("a block that is not a path names no field, whatever it holds")
    void aBlockThatIsNotAPath() {
        BlockValue block = BlockValue.block(List.of(
                boundIn("account", aScriptHoldingAnObject()), WordValue.of("balance")));

        assertThat(block.fieldThePathNames()).isEmpty();
    }
}
