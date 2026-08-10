package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.jebol.domain.eval.WindowPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The five things a script may ask the operator for through a window, and the
 * three ways it can be told no.
 *
 * <p>Specs read from {@code src/boot/natives.reb} for BROWSE, REQUEST-FILE and
 * REQUEST-DIR, and from {@code src/mezz/mezz-osx-dialogs.reb} for
 * REQUEST-COLOR and REQUEST-PASSWORD, which is where the platform ones are
 * written.
 *
 * <p>The distinction this file exists to pin: **a declined dialog answers
 * none, and a refused service raises.** A person who closes a file chooser has
 * answered the question. A script that cannot tell that from "you were not
 * granted a screen" retries the wrong one, and the operator gets the dialog
 * again for as long as they keep closing it.
 */
class WindowServiceTest {

    /** A screen that answers whatever the test tells it to. */
    private static final class Screen implements WindowPort {

        private final List<String> files;
        private final Optional<String> directory;
        private final Optional<int[]> colour;
        private final Optional<String> password;
        private String browsed = "";

        Screen(List<String> files, Optional<String> directory,
                Optional<int[]> colour, Optional<String> password) {
            this.files = files;
            this.directory = directory;
            this.colour = colour;
            this.password = password;
        }

        /** An operator who declines everything. */
        static Screen decliningEverything() {
            return new Screen(List.of(), Optional.empty(),
                    Optional.empty(), Optional.empty());
        }

        static Screen answering(String file) {
            return new Screen(List.of(file), Optional.of("/chosen/"),
                    Optional.of(new int[] {10, 20, 30}), Optional.of("hunter2"));
        }

        @Override
        public void browse(String target) {
            browsed = target;
        }

        @Override
        public List<String> chooseFiles(
                boolean forSaving, boolean allowingMany,
                Optional<String> suggestedName, Optional<String> title) {
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
        public Optional<String> askForPassword(Optional<String> title) {
            return password;
        }

        String browsed() {
            return browsed;
        }
    }

    private static Interpreter withAScreen(WindowPort screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useWindows(screen);
        return interpreter;
    }

    private static String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(Interpreter interpreter, String source) {
        return answerFrom(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String NONE = "_";

    @Nested
    @DisplayName("the operator answers")
    class TheOperatorAnswers {

        @Test
        @DisplayName("BROWSE hands the target to the screen")
        void browseReachesTheScreen() {
            Screen screen = Screen.answering("/chosen/file.txt");
            Interpreter interpreter = withAScreen(screen);

            answerFrom(interpreter, "browse http://example.com");

            assertThat(screen.browsed()).isEqualTo("http://example.com");
        }

        @Test
        @DisplayName("BROWSE takes a url, a file or none")
        void browseTakesThreeDatatypes() {
            // `url [url! file! none!]` -- none is in the spec, so browsing
            // nothing is a call rather than a mistake.
            Screen screen = Screen.answering("x");
            assertThat(errorIdFrom(withAScreen(screen), "browse http://a"))
                    .isEqualTo("no-error");
            assertThat(errorIdFrom(withAScreen(screen), "browse %a.txt"))
                    .isEqualTo("no-error");
            assertThat(errorIdFrom(withAScreen(screen), "browse none"))
                    .isEqualTo("no-error");
            assertThat(errorIdFrom(withAScreen(screen), "browse 1"))
                    .isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("REQUEST-FILE answers one file, and a block only with /MULTI")
        void requestFileAnswersOneFileUnlessAskedForMany() {
            // The spec says "returns full file path (or block of paths)", and
            // /multi is what chooses between them. One file as a bare file is
            // what a caller reading `read request-file` depends on.
            Interpreter one = withAScreen(Screen.answering("/chosen/a.txt"));
            assertThat(answerFrom(one, "file? request-file")).isEqualTo(TRUE);

            Interpreter many = withAScreen(Screen.answering("/chosen/a.txt"));
            assertThat(answerFrom(many, "block? request-file/multi")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("REQUEST-DIR answers a file, because a directory is one")
        void requestDirAnswersAFile() {
            Interpreter interpreter = withAScreen(Screen.answering("x"));
            assertThat(answerFrom(interpreter, "file? request-dir")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("REQUEST-COLOR answers a tuple")
        void requestColourAnswersATuple() {
            // `/default color [tuple!]` in the spec, so a colour goes in as a
            // tuple and comes back as one.
            Interpreter interpreter = withAScreen(Screen.answering("x"));
            assertThat(answerFrom(interpreter, "tuple? request-color")).isEqualTo(TRUE);
            assertThat(answerFrom(withAScreen(Screen.answering("x")),
                    "10.20.30 = request-color")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("REQUEST-PASSWORD answers a string")
        void requestPasswordAnswersAString() {
            Interpreter interpreter = withAScreen(Screen.answering("x"));
            assertThat(answerFrom(interpreter, "string? request-password"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the operator declines, which is an answer")
    class TheOperatorDeclines {

        @Test
        @DisplayName("every dialog answers none rather than raising")
        void aDeclinedDialogAnswersNone() {
            // The rule this file exists for. Closing a chooser is an answer,
            // and a script has to be able to act on it: `if file: request-file
            // [...]` is the ordinary shape and it needs none to be falsey
            // rather than an error to catch.
            assertThat(answerFrom(withAScreen(Screen.decliningEverything()),
                    "request-file")).isEqualTo(NONE);
            assertThat(answerFrom(withAScreen(Screen.decliningEverything()),
                    "request-dir")).isEqualTo(NONE);
            assertThat(answerFrom(withAScreen(Screen.decliningEverything()),
                    "request-color")).isEqualTo(NONE);
            assertThat(answerFrom(withAScreen(Screen.decliningEverything()),
                    "request-password")).isEqualTo(NONE);
        }

        @Test
        @DisplayName("and raises nothing, so no TRY is needed around it")
        void decliningRaisesNothing() {
            assertThat(errorIdFrom(withAScreen(Screen.decliningEverything()),
                    "request-file")).isEqualTo("no-error");
            assertThat(errorIdFrom(withAScreen(Screen.decliningEverything()),
                    "request-password")).isEqualTo("no-error");
        }

        @Test
        @DisplayName("/MULTI declined answers an empty block, not none")
        void decliningTheManyFormAnswersAnEmptyBlock() {
            // The datatype of the answer follows the refinement rather than
            // the outcome, so code walking the block does not have to test
            // for none first.
            assertThat(answerFrom(withAScreen(Screen.decliningEverything()),
                    "mold request-file/multi")).isEqualTo("\"[]\"");
        }
    }

    @Nested
    @DisplayName("the three ways to be told no")
    class TheRefusals {

        @Test
        @DisplayName("without the grant, every one of the five raises")
        void withoutTheGrantEveryDialogRaises() {
            // A script that has not been granted a screen cannot put a window
            // on one, and it must not be told that by silence. A REQUEST-FILE
            // quietly answering none reads as a cancelled dialog.
            Interpreter walled = Interpreter.create();
            for (String call : new String[] {
                    "browse http://a", "request-file", "request-dir",
                    "request-color", "request-password"}) {
                assertThat(errorIdFrom(Interpreter.create(), call))
                        .as("%s must raise without the windows grant", call)
                        .isEqualTo("no-service");
            }
            assertThat(walled).isNotNull();
        }

        @Test
        @DisplayName("granted with no screen behind it is not-present, not not-granted")
        void grantedWithNoScreenIsNotPresent() {
            // Two different facts about the host, and a script may act on
            // either: the first can be fixed by granting and the second
            // cannot. The message says which.
            Interpreter granted = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));

            assertThat(errorIdFrom(granted, "request-file")).isEqualTo("no-service");

            Interpreter again = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));
            assertThat(answerFrom(again,
                    "e: try [request-file] find form disarm e \"no screen\""))
                    .as("the refusal says the host has no screen rather than that it withheld one")
                    .isNotEqualTo(NONE);
        }

        @Test
        @DisplayName("the windows grant does not open anything else")
        void oneGrantIsOneService() {
            // Granting a screen must not grant a filesystem. Each kind is a
            // separate decision.
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));
            interpreter.useWindows(Screen.answering("x"));

            assertThat(errorIdFrom(interpreter, "read %a.txt")).isEqualTo("no-service");
        }
    }
}
