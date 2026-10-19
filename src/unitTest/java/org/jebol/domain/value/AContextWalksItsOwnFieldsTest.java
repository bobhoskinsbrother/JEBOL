package org.jebol.domain.value;

import org.jebol.domain.eval.actions.AddAction;
import org.jebol.domain.eval.natives.DoNative;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AContextWalksItsOwnFieldsTest {

    private static String readTheErrorIdOf(Throwable thrown) {
        return ((Raised) thrown).error().errorId();
    }

    private static Context makeAContextHolding(String name, Value value) {
        Context context = Context.root();
        context.register(name, value);
        return context;
    }

    private static ObjectValue makeAnObjectHolding(String name, Value value) {
        return new ObjectValue(makeAContextHolding(name, value));
    }

    @Nested
    @DisplayName("valueAt follows field names from one object into the next")
    class ValueAt {

        @Test
        @DisplayName("one name is the value the context gives it")
        void findsOneName() {
            assertThat(makeAContextHolding("a", IntegerValue.of(1)).readValueAt("a"))
                    .isEqualTo(IntegerValue.of(1));
        }

        @Test
        @DisplayName("the first name may be held by an enclosing context")
        void findsTheFirstNameHeldAbove() {
            Context beneath = Context.makeChildOf(makeAContextHolding("a", IntegerValue.of(1)));

            assertThat(beneath.readValueAt("a")).isEqualTo(IntegerValue.of(1));
        }

        @Test
        @DisplayName("each later name is a field of the object the one before reached")
        void laterNamesAreFields() {
            Context context = makeAContextHolding("system",
                    makeAnObjectHolding("version", TupleValue.of(3, 22, 5)));

            assertThat(context.readValueAt("system", "version"))
                    .isEqualTo(TupleValue.of(3, 22, 5));
        }

        @Test
        @DisplayName("a first name nobody holds is none")
        void answersAnUnknownFirstName() {
            assertThat(makeAContextHolding("a", IntegerValue.of(1)).readValueAt("b"))
                    .isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a later name the object lacks is none")
        void answersAMissingField() {
            Context context = makeAContextHolding("system",
                    makeAnObjectHolding("version", TupleValue.of(3, 22, 5)));

            assertThat(context.readValueAt("system", "script")).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("stepping through something that is not an object is none")
        void answersSteppingThroughANonObject() {
            Context context = makeAContextHolding("system", IntegerValue.of(1));

            assertThat(context.readValueAt("system", "version")).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a hidden field is not found, as field selection would not find it")
        void readsAHiddenFieldAsNone() {
            Context inside = makeAContextHolding("version", TupleValue.of(3, 22, 5));
            inside.findSlotFor("version").hide(true);
            Context context = makeAContextHolding("system", new ObjectValue(inside));

            assertThat(context.readValueAt("system", "version")).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("a later name does not leak out to what encloses the object")
        void keepsAFieldFromLeakingOutward() {
            Context enclosing = makeAContextHolding("leak", IntegerValue.of(1));
            Context inside = Context.makeChildOf(enclosing);
            inside.register("version", TupleValue.of(3, 22, 5));
            Context context = makeAContextHolding("system", new ObjectValue(inside));

            assertThat(context.readValueAt("system", "leak")).isEqualTo(NoneValue.none());
        }
    }

    @Nested
    @DisplayName("systemFunctionNamed finds a function in system/contexts/sys")
    class SystemFunctionNamed {

        private static Context makeASystemWhoseSysIs(Value sys) {
            return makeAContextHolding("system", makeAnObjectHolding("contexts",
                    makeAnObjectHolding("sys", sys)));
        }

        private static Context makeASystemWhoseSysHolds(String name, Value value) {
            return makeASystemWhoseSysIs(makeAnObjectHolding(name, value));
        }

        @Test
        @DisplayName("a native held there is the answer")
        void findsANative() {
            NativeValue held = new DoNative();

            assertThat(makeASystemWhoseSysHolds("do*", held).findTheSystemFunctionNamed("do*"))
                    .isEqualTo(held);
        }

        @Test
        @DisplayName("an operator held there is the answer too")
        void findsAnOperator() {
            OperatorValue held = new OperatorValue("+", new AddAction());

            assertThat(makeASystemWhoseSysHolds("do*", held).findTheSystemFunctionNamed("do*"))
                    .isEqualTo(held);
        }

        @Test
        @DisplayName("with no system at all the name is not-defined")
        void answersWithNoSystem() {
            assertThatThrownBy(() -> Context.root().findTheSystemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::readTheErrorIdOf)
                    .isEqualTo("not-defined");
        }

        @Test
        @DisplayName("a sys context without the name is bad-sys-func")
        void sysLacksTheName() {
            Context context = makeASystemWhoseSysHolds("other", IntegerValue.of(1));

            assertThatThrownBy(() -> context.findTheSystemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::readTheErrorIdOf)
                    .isEqualTo("bad-sys-func");
        }

        @Test
        @DisplayName("a name in sys that holds something other than a function is bad-sys-func")
        void sysHoldsSomethingElse() {
            Context context = makeASystemWhoseSysHolds("do*", IntegerValue.of(1));

            assertThatThrownBy(() -> context.findTheSystemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::readTheErrorIdOf)
                    .isEqualTo("bad-sys-func");
        }

        @Test
        @DisplayName("a sys that is not an object is not-defined")
        void sysIsNotAnObject() {
            Context context = makeASystemWhoseSysIs(IntegerValue.of(1));

            assertThatThrownBy(() -> context.findTheSystemFunctionNamed("do*"))
                    .isInstanceOf(Raised.class)
                    .extracting(AContextWalksItsOwnFieldsTest::readTheErrorIdOf)
                    .isEqualTo("not-defined");
        }
    }
}
