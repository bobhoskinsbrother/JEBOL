package org.jebol.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.FilterResult;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jebol.testing.TestTarget.ANDROID;
import static org.jebol.testing.TestTarget.DESKTOP_LINUX;
import static org.jebol.testing.TestTarget.DESKTOP_MAC;
import static org.jebol.testing.TestTarget.DESKTOP_WINDOWS;
import static org.jebol.testing.TestTarget.WEB_LINUX;

class RunsOnFilterTest {

    static class SaysNothing {
        void anything() {
        }
    }

    @RunsOn(value = {DESKTOP_LINUX, DESKTOP_MAC, DESKTOP_WINDOWS}, because = "it starts a program, and Android runs none")
    static class OnTheDesktops {
        void anything() {
        }

        @RunsOn(value = ANDROID, because = "the method says otherwise, and the innermost wins")
        void onAndroidAlone() {
        }

        class Inner {
            void anything() {
            }
        }

        @RunsOn(value = WEB_LINUX, because = "the nested class says otherwise, and the innermost wins")
        class OnTheWebInstead {
            void anything() {
            }
        }
    }

    @RunsOn(value = ANDROID, requires = Needs.CHROME_WITH_EXNREF, because = "it opens the site in the device's Chrome")
    static class NeedsChrome {
        void anything() {
        }
    }

    private record AFakeCheck(Optional<String> shortfall, boolean requirementsMustBeMet) implements RequirementCheck {

        @Override
        public Set<Needs> answers() {
            return EnumSet.of(Needs.CHROME_WITH_EXNREF);
        }

        @Override
        public Optional<String> shortfall(Needs need) {
            return shortfall;
        }
    }

    private static FilterResult onADevice(RequirementCheck check, TestDescriptor descriptor) {
        return new RunsOnFilter(ANDROID, List.of(check)).apply(descriptor);
    }

    @Test
    @DisplayName("a test whose requirement this device meets runs")
    void aMetRequirementRuns() throws NoSuchMethodException {
        assertThat(onADevice(new AFakeCheck(Optional.empty(), true), testOf(NeedsChrome.class, "anything")).included())
                .isTrue();
    }

    @Test
    @DisplayName("a test whose requirement this device falls short of is left out, saying what it needs and what it has")
    void anUnmetRequirementIsLeftOutWithBoth() throws NoSuchMethodException {
        FilterResult result = onADevice(new AFakeCheck(Optional.of("this device's Chrome is 124"), false),
                testOf(NeedsChrome.class, "anything"));

        assertThat(result.excluded()).isTrue();
        assertThat(result.getReason()).hasValueSatisfying(reason -> assertThat(reason)
                .contains("exnref")
                .contains("this device's Chrome is 124"));
    }

    @Test
    @DisplayName("where requirements must be met, as in CI, a shortfall stops the run, saying what it needs and what it has")
    void anUnmetRequirementStopsARunThatMustMeetThem() {
        assertThatThrownBy(() -> onADevice(new AFakeCheck(Optional.of("this device's Chrome is 124"), true),
                testOf(NeedsChrome.class, "anything")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exnref")
                .hasMessageContaining("this device's Chrome is 124");
    }

    @Test
    @DisplayName("a requirement nothing on this platform can answer stops the run rather than being taken as met")
    void aRequirementNothingAnswersStopsTheRun() {
        assertThatThrownBy(() -> new RunsOnFilter(ANDROID, List.of()).apply(testOf(NeedsChrome.class, "anything")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CHROME_WITH_EXNREF");
    }

    @Test
    @DisplayName("a test for another target is left out for that, and its requirement is never asked")
    void anotherTargetsTestIsNeverAskedItsRequirement() throws NoSuchMethodException {
        assertThat(new RunsOnFilter(DESKTOP_LINUX, List.of()).apply(testOf(NeedsChrome.class, "anything")).excluded())
                .isTrue();
    }

    private static TestDescriptor testOf(Class<?> type, String method) throws NoSuchMethodException {
        return descriptor(MethodSource.from(type.getDeclaredMethod(method)), TestDescriptor.Type.TEST);
    }

    private static TestDescriptor descriptor(TestSource source, TestDescriptor.Type type) {
        return new AbstractTestDescriptor(UniqueId.root("sample", source.toString()), source.toString(), source) {
            @Override
            public Type getType() {
                return type;
            }
        };
    }

    private static FilterResult on(TestTarget here, TestDescriptor descriptor) {
        return new RunsOnFilter(here, List.of()).apply(descriptor);
    }

    @Test
    @DisplayName("a test that says nothing runs on every target")
    void aTestSayingNothingRunsEverywhere() throws NoSuchMethodException {
        for (TestTarget each : TestTarget.values()) {
            assertThat(on(each, testOf(SaysNothing.class, "anything")).included()).as("%s", each).isTrue();
        }
    }

    @Test
    @DisplayName("a test runs on each target its class names, and is left out of every other, with the reason given")
    void aClassNamesItsTargets() throws NoSuchMethodException {
        TestDescriptor test = testOf(OnTheDesktops.class, "anything");

        assertThat(on(DESKTOP_MAC, test).included()).isTrue();
        FilterResult onAndroid = on(ANDROID, test);
        assertThat(onAndroid.excluded()).isTrue();
        assertThat(onAndroid.getReason()).hasValueSatisfying(reason -> assertThat(reason)
                .contains("ANDROID")
                .contains("it starts a program, and Android runs none"));
    }

    @Test
    @DisplayName("a method's own word beats its class's")
    void theMethodWins() throws NoSuchMethodException {
        TestDescriptor test = testOf(OnTheDesktops.class, "onAndroidAlone");

        assertThat(on(ANDROID, test).included()).isTrue();
        assertThat(on(DESKTOP_LINUX, test).excluded()).isTrue();
    }

    @Test
    @DisplayName("a nested class takes its outer class's word unless it says its own")
    void aNestedClassTakesTheOuterWordUnlessItSaysItsOwn() throws NoSuchMethodException {
        TestDescriptor inner = testOf(OnTheDesktops.Inner.class, "anything");
        TestDescriptor onTheWeb = testOf(OnTheDesktops.OnTheWebInstead.class, "anything");

        assertThat(on(DESKTOP_WINDOWS, inner).included()).isTrue();
        assertThat(on(ANDROID, inner).excluded()).isTrue();
        assertThat(on(WEB_LINUX, onTheWeb).included()).isTrue();
        assertThat(on(DESKTOP_LINUX, onTheWeb).excluded()).isTrue();
    }

    @Test
    @DisplayName("a class is never left out itself, so a method of its that names this target still runs")
    void aClassIsJudgedByItsTests() {
        assertThat(on(ANDROID, descriptor(ClassSource.from(OnTheDesktops.class), TestDescriptor.Type.CONTAINER))
                .included()).isTrue();
    }

    @Test
    @DisplayName("a parameterised test's container is judged as its method is, before its cases exist")
    void aTemplateIsJudgedByItsMethod() throws NoSuchMethodException {
        TestDescriptor template = descriptor(
                MethodSource.from(OnTheDesktops.class.getDeclaredMethod("anything")), TestDescriptor.Type.CONTAINER);

        assertThat(on(ANDROID, template).excluded()).isTrue();
    }
}
