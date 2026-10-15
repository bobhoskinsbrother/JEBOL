package org.jebol.domain.value;

import org.jebol.domain.eval.actions.AddAction;
import org.jebol.domain.eval.natives.DoNative;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AContextWalksItsOwnFieldsTest {

    private static String errorIdOf(Throwable thrown) {
        return ((Raised) thrown).error().errorId();
    }

    private static Context holding(String name, Value value) {
        Context context = Context.root();
        context.register(name, value);
        return context;
    }

    private static ObjectValue anObjectHolding(String name, Value value) {
        return new ObjectValue(holding(name, value));
    }

    @Nested
    @DisplayName("valueAt follows field names from one object into the next")
    class ValueAt {

        @Test
        @DisplayName("one name is the value the context gives it")
        void oneName() {
            assertThat(holding("a", IntegerValue.of(1)).valueAt("a"))
                    .isEqualTo(IntegerValue.of(1));
        }

        @Test
        @DisplayName("the first name may be held by an enclosing context")
        void theFirstNameMayBeHeldAbove() {
            Context beneath = Context.childOf(holding("a", IntegerValue.of(1)));

            assertThat(beneath.valueAt("a")).isEqualTo(IntegerValue.of(1));
        }

        @Test
        @DisplayName("each later name is a field of the object the one before reached")
        void laterNamesAreFields() {
            Context context = holding("system",
                    anObjectHolding("version", TupleValue.of(3, 22, 5)));

            assertThat(context.valueAt("system", "version"))
                    .isEqualTo(TupleValue.of(3, 22, 5));
        }

        @Test
        @DisplayName("a first name nobody holds is none")
        void anUnknownFirstName() {
            assertThat(holding("a", IntegerValue.of(1)).valueAt("b"))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a later name the object lacks is none")
        void aMissingField() {
            Context context = holding("system",
                    anObjectHolding("version", TupleValue.of(3, 22, 5)));

            assertThat(context.valueAt("system", "script")).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("stepping through something that is not an object is none")
        void steppingThroughANonObject() {
            Context context = holding("system", IntegerValue.of(1));

            assertThat(context.valueAt("system", "version")).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a hidden field is not found, as field selection would not find it")
        void aHiddenFieldIsNone() {
            Context inside = holding("version", TupleValue.of(3, 22, 5));
            inside.slotFor("version").hide(true);
            Context context = holding("system", new ObjectValue(inside));

            assertThat(context.valueAt("system", "version")).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a later name does not leak out to what encloses the object")
        void aFieldDoesNotLeakOutward() {
            Context enclosing = holding("leak", IntegerValue.of(1));
            Context inside = Context.childOf(enclosing);
            inside.register("version", TupleValue.of(3, 22, 5));
            Context context = holding("system", new ObjectValue(inside));

            assertThat(context.valueAt("system", "leak")).isEqualTo(NoneValue.none());
        }
    }

    @Nested
    @DisplayName("systemFunctionNamed finds a function in system/contexts/sys")
    class SystemFunctionNamed {

        private static Context aSystemWhoseSysIs(Value sys) {
            return holding("system", anObjectHolding("contexts",
                    anObjectHolding("sys", sys)));
        }

        private static Context aSystemWhoseSysHolds(String name, Value value) {
            return aSystemWhoseSysIs(anObjectHolding(name, value));
        }

        @Test
        @DisplayName("a native held there is the answer")
        void aNative() {
            NativeValue held = new DoNative();

            assertThat(aSystemWhoseSysHolds("do*", held).systemFunctionNamed("do*"))
                    .isEqualTo(held);
        }

        @Test
        @DisplayName("an operator held there is the answer too")
        void anOperator() {
            OperatorValue held = new OperatorValue("+", new AddAction());

            assertThat(aSystemWhoseSysHolds("do*", held).systemFunctionNamed("do*"))
                    .isEqualTo(held);
        }

        @Test
        @DisplayName("with no system at all the name is not-defined")
        void noSystem() {
            assertThatThrownBy(() -> Context.root().systemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::errorIdOf)
                    .isEqualTo("not-defined");
        }

        @Test
        @DisplayName("a sys context without the name is bad-sys-func")
        void sysLacksTheName() {
            Context context = aSystemWhoseSysHolds("other", IntegerValue.of(1));

            assertThatThrownBy(() -> context.systemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::errorIdOf)
                    .isEqualTo("bad-sys-func");
        }

        @Test
        @DisplayName("a name in sys that holds something other than a function is bad-sys-func")
        void sysHoldsSomethingElse() {
            Context context = aSystemWhoseSysHolds("do*", IntegerValue.of(1));

            assertThatThrownBy(() -> context.systemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::errorIdOf)
                    .isEqualTo("bad-sys-func");
        }

        @Test
        @DisplayName("a sys that is not an object is not-defined")
        void sysIsNotAnObject() {
            Context context = aSystemWhoseSysIs(IntegerValue.of(1));

            assertThatThrownBy(() -> context.systemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::errorIdOf)
                    .isEqualTo("not-defined");
        }
    }
}
