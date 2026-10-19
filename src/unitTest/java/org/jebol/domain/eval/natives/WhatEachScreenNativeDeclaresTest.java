package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.render.TextMeasure;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhatEachScreenNativeDeclaresTest {

    private static final int ACROSS = 1024;
    private static final int DOWN = 768;

    private final class AScreenThatRecords implements ScreenPort {

        private final boolean present;
        private final int displays;
        private final List<GobValue> shown = new ArrayList<>();
        private GobValue root;

        private AScreenThatRecords(boolean present, int displays) {
            this.present = present;
            this.displays = displays;
        }

        @Override
        public boolean hasDisplay() {
            return present;
        }

        @Override
        public PairValue measure(ScreenMetric metric, int display) {
            return PairValue.of(ACROSS + display, DOWN + metric.ordinal());
        }

        @Override
        public int displayCount() {
            return displays;
        }

        @Override
        public void acceptRootGob(GobValue given) {
            root = given;
        }

        @Override
        public void show(GobValue gob) {
            if (!present) {
                throw new Denied("no-service", "there is no screen");
            }
            shown.add(gob);
        }

        @Override
        public Optional<ScreenEvent> acceptNextEvent() {
            return Optional.empty();
        }

        @Override
        public TextMeasure textMeasure() {
            return ScreenPort.none().textMeasure();
        }
    }

    private AScreenThatRecords makeAScreenOf(int displays) {
        return new AScreenThatRecords(true, displays);
    }

    private AScreenThatRecords makeNoScreen() {
        return new AScreenThatRecords(false, 0);
    }

    private GrantedServices grantWindows() {
        GrantedServices granted = new GrantedServices();
        granted.grantOnly(Set.of(HostService.WINDOWS));
        return granted;
    }

    private Evaluator makeAnEvaluatorOn(ScreenPort screen) {
        Evaluator evaluator = new Evaluator(Context.root(), line -> { });
        evaluator.useScreen(screen);
        return evaluator;
    }

    private Value call(DefaultNative definition, ScreenPort screen, Set<String> refinements,
                       Value... arguments) {
        return definition.behaviour().call(List.of(arguments), makeAnEvaluatorOn(screen), null, refinements);
    }

    private Raised catchTheRefusalOf(DefaultNative definition, ScreenPort screen, Set<String> refinements,
                                     Value... arguments) {
        return catchThrowableOfType(Raised.class, () -> call(definition, screen, refinements, arguments));
    }

    private Parameter findTheDeclaredParameter(DefaultNative definition, String parameter) {
        return definition.parametersAsWritten().stream()
                .filter(each -> each.name().equals(parameter))
                .findFirst()
                .orElseThrow();
    }

    private AnyWordValue nameAMetric(String spelling) {
        return WordValue.of(spelling);
    }

    Stream<Arguments> listEachNameAndItsRefinements() {
        GrantedServices granted = new GrantedServices();
        return Stream.of(
                Arguments.of(new InitTopWindowNative(granted), "init-top-window", Set.of()),
                Arguments.of(new GuiMetricNative(granted), "gui-metric", Set.of("set", "display")),
                Arguments.of(new ShowNative(granted), "show", Set.of()));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("listEachNameAndItsRefinements")
    @DisplayName("each answers to the name boot/window.reb gives it, with the refinements it declares")
    void declaresItsNameAndRefinements(DefaultNative definition, String name, Set<String> refinements) {
        assertThat(definition.nativeName()).isEqualTo(name);
        assertThat(definition.refinementsDeclaredApart()).isEqualTo(refinements);
    }

    @Nested
    @DisplayName("what each takes, as boot/window.reb declares it")
    class TheDeclarations {

        @Test
        @DisplayName("init-top-window takes a gob and nothing else")
        void initTopWindowTakesAGob() {
            InitTopWindowNative definition = new InitTopWindowNative(new GrantedServices());
            assertThat(definition.parametersAsWritten()).extracting(Parameter::name).containsExactly("gob");
            assertThat(findTheDeclaredParameter(definition, "gob").acceptedTypes()).containsExactly(GobValue.TYPE);
        }

        @Test
        @DisplayName("show takes a gob, none or a block")
        void showTakesAGobNoneOrABlock() {
            ShowNative definition = new ShowNative(new GrantedServices());
            assertThat(definition.parametersAsWritten()).extracting(Parameter::name).containsExactly("gob");
            assertThat(findTheDeclaredParameter(definition, "gob").acceptedTypes())
                    .containsExactlyInAnyOrder(GobValue.TYPE, NoneValue.TYPE, BlockValue.TYPE);
        }

        @Test
        @DisplayName("gui-metric takes a word, an untyped value under /set and an integer under /display")
        void giveGuiMetricAWordAValueAndAnIndex() {
            GuiMetricNative definition = new GuiMetricNative(new GrantedServices());
            assertThat(definition.parametersAsWritten()).extracting(Parameter::name)
                    .containsExactly("keyword", "val", "idx");
            assertThat(findTheDeclaredParameter(definition, "keyword").acceptedTypes()).containsExactly(WordValue.TYPE);
            assertThat(findTheDeclaredParameter(definition, "val").acceptedTypes())
                    .isEqualTo(TypesetValue.ANY_TYPE.members());
            assertThat(findTheDeclaredParameter(definition, "val").owningRefinement()).contains("set");
            assertThat(findTheDeclaredParameter(definition, "idx").acceptedTypes()).containsExactly(IntegerValue.TYPE);
            assertThat(findTheDeclaredParameter(definition, "idx").owningRefinement()).contains("display");
        }
    }

    @Nested
    @DisplayName("init-top-window")
    class InitTopWindow {

        private final InitTopWindowNative definition = new InitTopWindowNative(grantWindows());

        @Test
        @DisplayName("answers unset with a screen behind it, as the Windows host's RXR_UNSET does")
        void answersUnsetWithAScreen() {
            assertThat(call(definition, makeAScreenOf(1), Set.of(), GobValue.empty()))
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("and answers unset with no screen behind it")
        void answersUnsetWithNoScreen() {
            assertThat(call(definition, makeNoScreen(), Set.of(), GobValue.empty()))
                    .isInstanceOf(UnsetValue.class);
        }

        @Test
        @DisplayName("hands the gob to the screen as its root")
        void handsTheGobToTheScreen() {
            AScreenThatRecords screen = makeAScreenOf(1);
            GobValue root = GobValue.empty();
            call(definition, screen, Set.of(), root);
            assertThat(screen.root).isSameAs(root);
        }

        @Test
        @DisplayName("sizes the root at the first display's screen-size")
        void sizesTheRootAtTheScreen() {
            GobValue root = GobValue.empty();
            call(definition, makeAScreenOf(2), Set.of(), root);
            assertThat(root.storage().size())
                    .isEqualTo(PairValue.of(ACROSS, DOWN + ScreenMetric.SCREEN_SIZE.ordinal()));
        }

        @Test
        @DisplayName("sizes the root at nothing when there is no screen, and still takes it")
        void sizesTheRootAtNothingWithNoScreen() {
            AScreenThatRecords screen = makeNoScreen();
            GobValue root = GobValue.empty();
            call(definition, screen, Set.of(), root);
            assertThat(root.storage().size()).isEqualTo(PairValue.of(0, 0));
            assertThat(screen.root).isSameAs(root);
        }

        @Test
        @DisplayName("cuts the root loose from whatever pane held it")
        void cutsTheRootLoose() {
            GobValue holder = GobValue.empty();
            GobValue root = GobValue.empty();
            holder.storage().insertChild(1, root);
            call(definition, makeAScreenOf(1), Set.of(), root);
            assertThat(root.storage().parent()).isNull();
            assertThat(holder.storage().findThePositionOf(root.storage())).isNotPositive();
        }

        @Test
        @DisplayName("refuses a value that is not a gob as expect-arg")
        void refusesANonGob() {
            assertThat(catchTheRefusalOf(definition, makeAScreenOf(1), Set.of(), IntegerValue.of(1)).error().errorId())
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("refuses without the grant, and the screen is never handed the gob")
        void refusesWithoutTheGrant() {
            AScreenThatRecords screen = makeAScreenOf(1);
            Raised refused = catchTheRefusalOf(new InitTopWindowNative(new GrantedServices()), screen, Set.of(),
                    GobValue.empty());
            assertThat(refused.error().errorId()).isEqualTo("no-service");
            assertThat(screen.root).isNull();
        }
    }

    @Nested
    @DisplayName("show")
    class Show {

        private final ShowNative definition = new ShowNative(grantWindows());

        @Test
        @DisplayName("shows a gob and answers that same gob")
        void showsAGobAndAnswersIt() {
            AScreenThatRecords screen = makeAScreenOf(1);
            GobValue gob = GobValue.empty();
            assertThat(call(definition, screen, Set.of(), gob)).isSameAs(gob);
            assertThat(screen.shown).containsExactly(gob);
        }

        @Test
        @DisplayName("answers none for none, and shows nothing")
        void answersNoneForNone() {
            AScreenThatRecords screen = makeAScreenOf(1);
            assertThat(call(definition, screen, Set.of(), NoneValue.none())).isEqualTo(NoneValue.none());
            assertThat(screen.shown).isEmpty();
        }

        @Test
        @DisplayName("answers a block for a block, and shows nothing, as the C's gob-only branch does")
        void answersABlockForABlock() {
            AScreenThatRecords screen = makeAScreenOf(1);
            AnyBlockValue block = BlockValue.of(GobValue.empty());
            assertThat(call(definition, screen, Set.of(), block)).isSameAs(block);
            assertThat(screen.shown).isEmpty();
        }

        @Test
        @DisplayName("refuses a gob with no screen behind it as access no-service, not present")
        void refusesWithNoScreen() {
            Raised refused = catchTheRefusalOf(definition, makeNoScreen(), Set.of(), GobValue.empty());
            assertThat(refused.error().category()).isEqualTo(ErrorCategory.ACCESS);
            assertThat(refused.error().errorId()).isEqualTo("no-service");
            assertThat(refused.error().message()).endsWith("which is not present");
        }

        @Test
        @DisplayName("but none with no screen behind it is still answered, which UNVIEW relies on")
        void needsNoScreenForNone() {
            assertThat(call(definition, makeNoScreen(), Set.of(), NoneValue.none())).isEqualTo(NoneValue.none());
        }

        @Test
        @DisplayName("refuses without the grant, and shows nothing")
        void refusesWithoutTheGrant() {
            AScreenThatRecords screen = makeAScreenOf(1);
            Raised refused = catchTheRefusalOf(new ShowNative(new GrantedServices()), screen, Set.of(), GobValue.empty());
            assertThat(refused.error().errorId()).isEqualTo("no-service");
            assertThat(refused.error().message()).endsWith("not granted");
            assertThat(screen.shown).isEmpty();
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @DisplayName("gui-metric")
    class GuiMetric {

        private final GuiMetricNative definition = new GuiMetricNative(grantWindows());

        private PairValue measureOnDisplay(int display, ScreenMetric metric) {
            return PairValue.of(ACROSS + display, DOWN + metric.ordinal());
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ScreenMetric.class, names = "SCREENS", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("each of the eleven measurements answers the first display's pair")
        void answersAPairForEachMeasurement(ScreenMetric metric) {
            assertThat(call(definition, makeAScreenOf(1), Set.of(), nameAMetric(metric.spelling())))
                    .isEqualTo(measureOnDisplay(0, metric));
        }

        @ParameterizedTest(name = "{0} displays")
        @ValueSource(ints = {0, 1, 2})
        @DisplayName("screens answers the count of displays as an integer")
        void screensAnswersTheCount(int displays) {
            assertThat(call(definition, makeAScreenOf(displays), Set.of(), nameAMetric("screens")))
                    .isEqualTo(IntegerValue.of(displays));
        }

        @ParameterizedTest(name = "display {0}")
        @ValueSource(ints = {0, 1})
        @DisplayName("/display picks the display, from the first to the last there is")
        void displayPicksTheDisplay(int display) {
            assertThat(call(definition, makeAScreenOf(2), Set.of("display"),
                    nameAMetric("screen-size"), IntegerValue.of(display)))
                    .isEqualTo(measureOnDisplay(display, ScreenMetric.SCREEN_SIZE));
        }

        @ParameterizedTest(name = "display {0}")
        @ValueSource(ints = {-1, 2})
        @DisplayName("/display one past either end is refused as invalid-arg")
        void displayOutsideTheRangeIsRefused(int display) {
            assertThat(catchTheRefusalOf(definition, makeAScreenOf(2), Set.of("display"),
                    nameAMetric("screen-size"), IntegerValue.of(display)).error().errorId())
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("/display beside /set reads the index, not the value /set was given")
        void displayBesideSetReadsTheIndex() {
            assertThat(call(definition, makeAScreenOf(2), Set.of("set", "display"),
                    nameAMetric("screen-size"), IntegerValue.of(0), IntegerValue.of(1)))
                    .isEqualTo(measureOnDisplay(1, ScreenMetric.SCREEN_SIZE));
        }

        @Test
        @DisplayName("/set alone writes nothing and measures the first display, as neither host reads it")
        void setAloneMeasuresTheFirstDisplay() {
            assertThat(call(definition, makeAScreenOf(2), Set.of("set"),
                    nameAMetric("screen-size"), PairValue.of(1, 1)))
                    .isEqualTo(measureOnDisplay(0, ScreenMetric.SCREEN_SIZE));
        }

        @ParameterizedTest(name = "display {0}")
        @ValueSource(ints = {-1, 0, 7})
        @DisplayName("with no screen every measurement is 0x0, whichever display is asked for")
        void measuresNothingWithoutAScreen(int display) {
            assertThat(call(definition, makeNoScreen(), Set.of("display"),
                    nameAMetric("work-size"), IntegerValue.of(display)))
                    .isEqualTo(PairValue.of(0, 0));
        }

        @Test
        @DisplayName("with no screen there are no displays to count")
        void countsNoneWithoutAScreen() {
            assertThat(call(definition, makeNoScreen(), Set.of(), nameAMetric("screens")))
                    .isEqualTo(IntegerValue.of(0));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"nosuch", "virtual-screen-size", "screen-size-x", "screen-siz", "restore"})
        @DisplayName("a word no host serves is refused as invalid-arg")
        void refusesAnUnservedWord(String spelling) {
            assertThat(catchTheRefusalOf(definition, makeAScreenOf(1), Set.of(), nameAMetric(spelling)).error().errorId())
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("the word is matched without regard to case, as Rebol's words are")
        void ignoresTheWordsCase() {
            assertThat(call(definition, makeAScreenOf(1), Set.of(), nameAMetric("SCREEN-SIZE")))
                    .isEqualTo(measureOnDisplay(0, ScreenMetric.SCREEN_SIZE));
        }

        Stream<Value> keywordsOfTheWrongType() {
            return Stream.of(
                    StringValue.of("screen-size"),
                    IntegerValue.of(1),
                    LogicValue.of(true),
                    NoneValue.none(),
                    BlockValue.of(WordValue.of("screen-size")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("keywordsOfTheWrongType")
        @DisplayName("a keyword that is not a word is refused as expect-arg")
        void refusesAKeywordOfTheWrongType(Value keyword) {
            assertThat(catchTheRefusalOf(definition, makeAScreenOf(1), Set.of(), keyword).error().errorId())
                    .isEqualTo("expect-arg");
        }

        Stream<Value> indexesOfTheWrongType() {
            return Stream.of(
                    StringValue.of("0"),
                    PairValue.of(0, 0),
                    LogicValue.of(false),
                    NoneValue.none());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("indexesOfTheWrongType")
        @DisplayName("a display index that is not an integer is refused as expect-arg, never coerced")
        void refusesAnIndexOfTheWrongType(Value index) {
            assertThat(catchTheRefusalOf(definition, makeAScreenOf(2), Set.of("display"),
                    nameAMetric("screen-size"), index).error().errorId())
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("refuses without the grant, before reading the word")
        void refusesWithoutTheGrant() {
            assertThat(catchTheRefusalOf(new GuiMetricNative(new GrantedServices()), makeAScreenOf(1), Set.of(),
                    nameAMetric("nosuch")).error().errorId())
                    .isEqualTo("no-service");
        }
    }
}
