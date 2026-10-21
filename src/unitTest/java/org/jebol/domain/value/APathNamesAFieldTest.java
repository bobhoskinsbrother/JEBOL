package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class APathNamesAFieldTest {

    private static Context makeAContextHolding(String name, Value value) {
        Context fields = Context.root();
        fields.register(name, value);
        return fields;
    }

    private static AnyWordValue bindIn(String spelling, Context context) {
        return WordValue.of(spelling).bindTo(context);
    }

    private static AnyBlockValue makeAPath(Value... segments) {
        return PathValue.of(List.of(segments));
    }

    private static Context makeAScriptHoldingAnObject() {
        return makeAContextHolding("account", new ObjectValue(makeAContextHolding("balance", IntegerValue.of(10))));
    }

    @Test
    @DisplayName("every type of path names the field it ends on")
    void namesAFieldWithEveryKindOfPath() {
        Context script = makeAScriptHoldingAnObject();
        assertThat(List.of(PathValue.TYPE, SetPathValue.TYPE, GetPathValue.TYPE, LitPathValue.TYPE))
                .allSatisfy(kind -> assertThat(kind.wrap(List.of(
                                bindIn("account", script), WordValue.of("balance"))).fieldPathNames())
                        .hasValueSatisfying(slot ->
                                assertThat(slot.value()).isEqualTo(IntegerValue.of(10))));
    }

    @Test
    @DisplayName("a path through nested objects names the innermost field")
    void namesAFieldThroughNestedObjects() {
        Context script = makeAContextHolding("bank", new ObjectValue(makeAContextHolding("account",
                new ObjectValue(makeAContextHolding("balance", IntegerValue.of(10))))));

        assertThat(makeAPath(bindIn("bank", script), WordValue.of("account"),
                WordValue.of("balance")).fieldPathNames())
                .hasValueSatisfying(slot ->
                        assertThat(slot.value()).isEqualTo(IntegerValue.of(10)));
    }

    @Test
    @DisplayName("the slot is the object's own, so writing through it changes the object")
    void namesTheObjectsOwnSlot() {
        Context balanceHolder = makeAContextHolding("balance", IntegerValue.of(10));
        Context script = makeAContextHolding("account", new ObjectValue(balanceHolder));

        makeAPath(bindIn("account", script), WordValue.of("balance"))
                .fieldPathNames().orElseThrow().setValue(IntegerValue.of(20));

        assertThat(balanceHolder.findSlotFor("balance").value()).isEqualTo(IntegerValue.of(20));
    }

    @Test
    @DisplayName("a path of one segment names no field")
    void namesAFieldWithOneSegment() {
        assertThat(makeAPath(bindIn("account", makeAScriptHoldingAnObject())).fieldPathNames())
                .isEmpty();
    }

    @Test
    @DisplayName("an empty path names no field")
    void namesNothingWithNoSegments() {
        assertThat(makeAPath().fieldPathNames()).isEmpty();
    }

    @Test
    @DisplayName("an unbound first word names no field")
    void answersAnUnboundStart() {
        assertThat(makeAPath(WordValue.of("account"), WordValue.of("balance")).fieldPathNames())
                .isEmpty();
    }

    @Test
    @DisplayName("a first segment that is not a word names no field")
    void answersAStartThatIsNotAWord() {
        assertThat(makeAPath(IntegerValue.of(1), WordValue.of("balance")).fieldPathNames())
                .isEmpty();
    }

    @Test
    @DisplayName("stepping through something that is not an object names no field")
    void answersSteppingThroughANonObject() {
        Context script = makeAContextHolding("account", IntegerValue.of(1));

        assertThat(makeAPath(bindIn("account", script), WordValue.of("balance"))
                .fieldPathNames()).isEmpty();
    }

    @Test
    @DisplayName("a field the object does not hold is named by nothing")
    void answersAMissingField() {
        assertThat(makeAPath(bindIn("account", makeAScriptHoldingAnObject()), WordValue.of("owner"))
                .fieldPathNames()).isEmpty();
    }

    @Test
    @DisplayName("a segment that is not a word names no field")
    void answersASegmentThatIsNotAWord() {
        assertThat(makeAPath(bindIn("account", makeAScriptHoldingAnObject()), IntegerValue.of(1))
                .fieldPathNames()).isEmpty();
    }

    @Test
    @DisplayName("a hidden field is named by nothing, as field selection would not find it")
    void answersAHiddenField() {
        Context balanceHolder = makeAContextHolding("balance", IntegerValue.of(10));
        balanceHolder.findSlotFor("balance").hide(true);
        Context script = makeAContextHolding("account", new ObjectValue(balanceHolder));

        assertThat(makeAPath(bindIn("account", script), WordValue.of("balance"))
                .fieldPathNames()).isEmpty();
    }

    @Test
    @DisplayName("a block that is not a path names no field, whatever it holds")
    void answersABlockThatIsNotAPath() {
        AnyBlockValue block = BlockValue.of(List.of(
                bindIn("account", makeAScriptHoldingAnObject()), WordValue.of("balance")));

        assertThat(block.fieldPathNames()).isEmpty();
    }
}
