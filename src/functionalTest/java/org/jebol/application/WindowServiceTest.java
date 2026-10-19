package org.jebol.application;

import org.jebol.domain.host.WindowPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WindowServiceTest {

    private static final class Screen implements WindowPort {

        private final List<String> files;
        private final Optional<String> directory;
        private final Optional<int[]> colour;
        private final Optional<String> password;
        private String browsed = "";
        private List<String> filtersAsked = List.of();

        Screen(List<String> files, Optional<String> directory,
                Optional<int[]> colour, Optional<String> password) {
            this.files = files;
            this.directory = directory;
            this.colour = colour;
            this.password = password;
        }

        static Screen decliningEverything() {
            return new Screen(List.of(), Optional.empty(),
                    Optional.empty(), Optional.empty());
        }

        static Screen makeOneAnswering(String file) {
            return new Screen(List.of(file), Optional.of("/chosen/"),
                    Optional.of(new int[] {10, 20, 30}), Optional.of("hunter2"));
        }

        @Override
        public void browse(String target) {
            browsed = target;
        }

        private Optional<String> suggestedNameAsked = Optional.empty();
        private Optional<String> titleAsked = Optional.empty();

        @Override
        public List<String> chooseFiles(
                boolean forSaving, boolean allowingMany,
                Optional<String> suggestedName, Optional<String> title,
                List<String> filterPairs) {
            this.filtersAsked = filterPairs;
            this.suggestedNameAsked = suggestedName;
            this.titleAsked = title;
            return allowingMany || files.isEmpty() ? files : List.of(files.getFirst());
        }

        @Override
        public Optional<String> chooseDirectory(
                Optional<String> startingAt, Optional<String> title) {
            return directory;
        }

        @Override
        public Optional<int[]> chooseColour(Optional<int[]> suggested) {
            return colour;
        }

        @Override
        public Optional<String> askForPassword() {
            return password;
        }

        String browsed() {
            return browsed;
        }
    }

    private static Interpreter makeAnInterpreterWithAScreen(WindowPort screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.WINDOWS));
        interpreter.useWindows(screen);
        return interpreter;
    }

    private static String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFrom(Interpreter interpreter, String source) {
        return answerFrom(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String NONE = "_";

    @Nested
    @DisplayName("the operator answers")
    class TheOperatorAnswers {

        @Test
        @DisplayName("REQUEST-FILE/FILTER hands the name and pattern pairs to the screen")
        void filterPairsReachTheScreen() {
            Screen screen = Screen.makeOneAnswering("/chosen/file.r3");
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);

            answerFrom(interpreter, """
                    request-file/filter [{Rebol scripts} {*.r3}]""");

            assertThat(screen.filtersAsked).containsExactly("Rebol scripts", "*.r3");
        }

        @Test
        @DisplayName("filter items are formed to text, whatever they are")
        void filterItemsAreFormed() {
            Screen screen = Screen.makeOneAnswering("/chosen/file.r3");
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);

            answerFrom(interpreter, """
                    request-file/filter [%scripts 12]""");

            assertThat(screen.filtersAsked).containsExactly("scripts", "12");
        }

        @Test
        @DisplayName("an odd number of filter items is refused before the dialog opens")
        void refusesAnOddFilterList() {
            Screen screen = Screen.makeOneAnswering("/chosen/file.r3");
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(screen), """
                    request-file/filter [{orphaned name}]""")).isEqualTo("invalid-arg");
            assertThat(screen.filtersAsked).isEmpty();
        }

        @Test
        @DisplayName("a filter that is not a block is refused")
        void refusesAFilterOfTheWrongType() {
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(Screen.makeOneAnswering("x")), """
                    request-file/filter 5""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("the suggested name and the title each reach their own place")
        void keepsNameAndTitleApart() {
            Screen screen = Screen.makeOneAnswering("/chosen/file.txt");
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);

            answerFrom(interpreter, """
                    request-file/file/title %start.txt {Pick a script}""");

            assertThat(screen.suggestedNameAsked).contains("start.txt");
            assertThat(screen.titleAsked).contains("Pick a script");
        }

        @Test
        @DisplayName("BROWSE hands the target to the screen")
        void browseReachesTheScreen() {
            Screen screen = Screen.makeOneAnswering("/chosen/file.txt");
            Interpreter interpreter = makeAnInterpreterWithAScreen(screen);

            answerFrom(interpreter, "browse http://example.com");

            assertThat(screen.browsed()).isEqualTo("http://example.com");
        }

        @Test
        @DisplayName("BROWSE takes a url, a file or none")
        void browseTakesThreeDatatypes() {
            Screen screen = Screen.makeOneAnswering("x");
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(screen), "browse http://a"))
                    .isEqualTo("no-error");
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(screen), "browse %a.txt"))
                    .isEqualTo("no-error");
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(screen), "browse none"))
                    .isEqualTo("no-error");
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(screen), "browse 1"))
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("REQUEST-FILE answers one file, and a block only with /MULTI")
        void requestFileAnswersOneFileUnlessAskedForMany() {
            Interpreter one = makeAnInterpreterWithAScreen(Screen.makeOneAnswering("/chosen/a.txt"));
            assertThat(answerFrom(one, "file? request-file")).isEqualTo(TRUE);

            Interpreter many = makeAnInterpreterWithAScreen(Screen.makeOneAnswering("/chosen/a.txt"));
            assertThat(answerFrom(many, "block? request-file/multi")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("REQUEST-DIR answers a file, because a directory is one")
        void requestDirAnswersAFile() {
            Interpreter interpreter = makeAnInterpreterWithAScreen(Screen.makeOneAnswering("x"));
            assertThat(answerFrom(interpreter, "file? request-dir")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("REQUEST-COLOR answers a tuple")
        void requestColourAnswersATuple() {
            Interpreter interpreter = makeAnInterpreterWithAScreen(Screen.makeOneAnswering("x"));
            assertThat(answerFrom(interpreter, "tuple? request-color")).isEqualTo(TRUE);
            assertThat(answerFrom(makeAnInterpreterWithAScreen(Screen.makeOneAnswering("x")),
                    "10.20.30 = request-color")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("REQUEST-PASSWORD answers a string, and takes nothing at all")
        void requestPasswordAnswersAString() {
            Interpreter interpreter = makeAnInterpreterWithAScreen(Screen.makeOneAnswering("x"));
            assertThat(answerFrom(interpreter, "string? request-password"))
                    .isEqualTo(TRUE);
            assertThat(readTheErrorIdFrom(interpreter, "request-password/title \"q\""))
                    .isEqualTo("no-refine");
        }
    }

    @Nested
    @DisplayName("the operator declines, which is an answer")
    class TheOperatorDeclines {

        @Test
        @DisplayName("every dialog answers none rather than raising")
        void answersNoneForADeclinedDialog() {
            assertThat(answerFrom(makeAnInterpreterWithAScreen(Screen.decliningEverything()),
                    "request-file")).isEqualTo(NONE);
            assertThat(answerFrom(makeAnInterpreterWithAScreen(Screen.decliningEverything()),
                    "request-dir")).isEqualTo(NONE);
            assertThat(answerFrom(makeAnInterpreterWithAScreen(Screen.decliningEverything()),
                    "request-color")).isEqualTo(NONE);
            assertThat(answerFrom(makeAnInterpreterWithAScreen(Screen.decliningEverything()),
                    "request-password")).isEqualTo(NONE);
        }

        @Test
        @DisplayName("and raises nothing, so no TRY is needed around it")
        void raisesNothing() {
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(Screen.decliningEverything()),
                    "request-file")).isEqualTo("no-error");
            assertThat(readTheErrorIdFrom(makeAnInterpreterWithAScreen(Screen.decliningEverything()),
                    "request-password")).isEqualTo("no-error");
        }

        @Test
        @DisplayName("/MULTI declined answers an empty block, not none")
        void answersAnEmptyBlockForTheManyForm() {
            assertThat(answerFrom(makeAnInterpreterWithAScreen(Screen.decliningEverything()),
                    "mold request-file/multi")).isEqualTo("\"[]\"");
        }
    }

    @Nested
    @DisplayName("the three ways to be told no")
    class TheRefusals {

        @Test
        @DisplayName("without the grant, every one of the five raises")
        void raiseForEveryDialogWithoutTheGrant() {
            Interpreter walled = Interpreter.create();
            for (String call : new String[] {
                    "browse http://a", "request-file", "request-dir",
                    "request-color", "request-password"}) {
                assertThat(readTheErrorIdFrom(Interpreter.create(), call))
                        .as("%s must raise without the windows grant", call)
                        .isEqualTo("no-service");
            }
            assertThat(walled).isNotNull();
        }

        @Test
        @DisplayName("granted with no screen behind it is not-present, not not-granted")
        void sayNotPresentWhenGrantedWithNoScreen() {
            Interpreter granted = Interpreter.withBounds(
                    Bounds.standard().grant(HostService.WINDOWS));

            assertThat(readTheErrorIdFrom(granted, "request-file")).isEqualTo("no-service");

            Interpreter again = Interpreter.withBounds(
                    Bounds.standard().grant(HostService.WINDOWS));
            assertThat(answerFrom(again,
                    "e: try [request-file] find form disarm e \"no screen\""))
                    .as("the refusal says the host has no screen rather than that it withheld one")
                    .isNotEqualTo(NONE);
        }

        @Test
        @DisplayName("the windows grant does not open anything else")
        void grantOneServicePerGrant() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().grant(HostService.WINDOWS));
            interpreter.useWindows(Screen.makeOneAnswering("x"));

            assertThat(readTheErrorIdFrom(interpreter, "read %a.txt")).isEqualTo("no-service");
        }
    }
}
