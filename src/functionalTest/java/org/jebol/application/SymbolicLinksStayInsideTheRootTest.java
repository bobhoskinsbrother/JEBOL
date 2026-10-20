package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolicLinksStayInsideTheRootTest {

    private static final int THE_MOST_LINKS_FOLLOWED = 40;

    @TempDir
    Path machine;

    private Path root;

    private Path outside;

    private Interpreter interpreter;

    @BeforeEach
    void makeARootWithSomethingBesideIt() throws IOException {
        root = Files.createDirectories(machine.resolve("root"));
        outside = Files.createDirectories(machine.resolve("outside"));
        Files.writeString(root.resolve("inside.txt"), "inside");
        Files.writeString(outside.resolve("secret.txt"), "secret");
        interpreter = over(root);
    }

    private static Interpreter over(Path root) {
        return Interpreter.createOn(
                Interpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(root)),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
    }

    private String answerTo(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String readTheRefusalOf(String source) {
        return answerTo("""
                e: try [%s]
                either error? e [reduce [e/id e/arg1]] ['no-error]""".formatted(source));
    }

    private void link(String name, Path target) throws IOException {
        Files.createSymbolicLink(root.resolve(name), target);
    }

    @Nested
    @DisplayName("a link that lands inside the root is followed")
    class LandingInside {

        @Test
        @DisplayName("a relative link to a file beside it reads that file")
        void followsARelativeLink() throws IOException {
            link("again.txt", Path.of("inside.txt"));

            assertThat(answerTo("to string! read %again.txt")).isEqualTo("\"inside\"");
        }

        @Test
        @DisplayName("an absolute link to a file inside reads that file")
        void followsAnAbsoluteLink() throws IOException {
            link("again.txt", root.resolve("inside.txt").toAbsolutePath());

            assertThat(answerTo("to string! read %again.txt")).isEqualTo("\"inside\"");
        }

        @Test
        @DisplayName("a linked folder inside lists and writes like any other")
        void usesALinkedFolder() throws IOException {
            Files.createDirectories(root.resolve("real"));
            link("alias", root.resolve("real"));

            answerTo("write %alias/made.txt {made}");

            assertThat(root.resolve("real").resolve("made.txt")).hasContent("made");
            assertThat(answerTo("read %alias/")).isEqualTo("[%made.txt]");
        }

        @Test
        @DisplayName("a dangling link inside makes its target inside when written through")
        void makesTheTargetOfADanglingLinkInside() throws IOException {
            link("later.txt", Path.of("target.txt"));

            answerTo("write %later.txt {now}");

            assertThat(root.resolve("target.txt")).hasContent("now");
        }

        @Test
        @DisplayName("a chain of forty links is followed to its end")
        void fortyLinks() throws IOException {
            link("chain-" + THE_MOST_LINKS_FOLLOWED, Path.of("inside.txt"));
            for (int at = THE_MOST_LINKS_FOLLOWED - 1; at >= 1; at--) {
                link("chain-" + at, Path.of("chain-" + (at + 1)));
            }

            assertThat(answerTo("to string! read %chain-1")).isEqualTo("\"inside\"");
        }
    }

    @Nested
    @DisplayName("a link that leads out of the root is refused with security, naming the path as written")
    class LeavingTheRoot {

        @Test
        @DisplayName("reading through it")
        void refusesReading() throws IOException {
            link("door.txt", outside.resolve("secret.txt"));

            assertThat(readTheRefusalOf("read %door.txt")).isEqualTo("[security %door.txt]");
        }

        @Test
        @DisplayName("writing through it changes nothing outside")
        void refusesWriting() throws IOException {
            link("door.txt", outside.resolve("secret.txt"));

            assertThat(readTheRefusalOf("write %door.txt {changed}")).isEqualTo("[security %door.txt]");
            assertThat(outside.resolve("secret.txt")).hasContent("secret");
        }

        @Test
        @DisplayName("a relative link climbing out with dots")
        void refusesClimbingWithDots() throws IOException {
            link("door.txt", Path.of("..", "outside", "secret.txt"));

            assertThat(readTheRefusalOf("read %door.txt")).isEqualTo("[security %door.txt]");
        }

        @Test
        @DisplayName("a file inside a folder linked from outside")
        void refusesAFileThroughALinkedFolder() throws IOException {
            link("elsewhere", outside);

            assertThat(readTheRefusalOf("read %elsewhere/secret.txt")).isEqualTo("[security %elsewhere/secret.txt]");
        }

        @Test
        @DisplayName("listing a folder linked from outside")
        void refusesListingIt() throws IOException {
            link("elsewhere", outside);

            assertThat(readTheRefusalOf("read %elsewhere/")).isEqualTo("[security %elsewhere/]");
        }

        @Test
        @DisplayName("making a folder through a link leading out")
        void refusesMakingAFolder() throws IOException {
            link("elsewhere", outside);

            assertThat(readTheRefusalOf("make-dir %elsewhere/made/")).isEqualTo("[security %elsewhere/made/]");
            assertThat(outside.resolve("made")).doesNotExist();
        }

        @Test
        @DisplayName("deleting a file through a link leading out")
        void refusesDeletingThroughIt() throws IOException {
            link("elsewhere", outside);

            assertThat(readTheRefusalOf("delete %elsewhere/secret.txt")).isEqualTo("[security %elsewhere/secret.txt]");
            assertThat(outside.resolve("secret.txt")).exists();
        }

        @Test
        @DisplayName("changing directory into it")
        void refusesChangingDirectory() throws IOException {
            link("elsewhere", outside);

            assertThat(readTheRefusalOf("change-dir %elsewhere/")).isEqualTo("[security %elsewhere/]");
        }

        @Test
        @DisplayName("a dangling link pointing out is refused, and nothing is made outside")
        void refusesADanglingLinkOut() throws IOException {
            link("door.txt", outside.resolve("new.txt"));

            assertThat(readTheRefusalOf("write %door.txt {x}")).isEqualTo("[security %door.txt]");
            assertThat(outside.resolve("new.txt")).doesNotExist();
        }

        @Test
        @DisplayName("forty-one links are one more than are followed")
        void fortyOneLinks() throws IOException {
            link("chain-" + (THE_MOST_LINKS_FOLLOWED + 1), Path.of("inside.txt"));
            for (int at = THE_MOST_LINKS_FOLLOWED; at >= 1; at--) {
                link("chain-" + at, Path.of("chain-" + (at + 1)));
            }

            assertThat(readTheRefusalOf("read %chain-1")).isEqualTo("[security %chain-1]");
        }

        @Test
        @DisplayName("two links pointing at each other are refused rather than followed for ever")
        void refusesALoop() throws IOException {
            link("ping", Path.of("pong"));
            link("pong", Path.of("ping"));

            assertThat(readTheRefusalOf("read %ping")).isEqualTo("[security %ping]");
        }

        @Test
        @DisplayName("asking about it answers none, as for anything the script cannot see")
        void answersNoneOnAskingAboutIt() throws IOException {
            link("door.txt", outside.resolve("secret.txt"));

            assertThat(answerTo("to-real-file %door.txt")).isEqualTo("_");
            assertThat(answerTo("exists? %door.txt")).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("removing or renaming a link acts on the link itself")
    class TheLinkItself {

        @Test
        @DisplayName("deleting a link leading out removes the link and leaves the target")
        void deletesTheLink() throws IOException {
            link("door.txt", outside.resolve("secret.txt"));

            answerTo("delete %door.txt");

            assertThat(root.resolve("door.txt")).doesNotExist();
            assertThat(Files.exists(root.resolve("door.txt"), LinkOption.NOFOLLOW_LINKS)).isFalse();
            assertThat(outside.resolve("secret.txt")).hasContent("secret");
        }

        @Test
        @DisplayName("renaming a link leading out renames the link and leaves the target")
        void renamesTheLink() throws IOException {
            link("door.txt", outside.resolve("secret.txt"));

            answerTo("rename %door.txt %gate.txt");

            assertThat(Files.isSymbolicLink(root.resolve("gate.txt"))).isTrue();
            assertThat(outside.resolve("secret.txt")).hasContent("secret");
        }
    }

    @Test
    @DisplayName("a root that is itself reached through a link still holds its filePort")
    void keepsTheFilesOfARootReachedThroughALink() throws IOException {
        Path aliasOfTheRoot = machine.resolve("alias-of-root");
        Files.createSymbolicLink(aliasOfTheRoot, root);
        interpreter = over(aliasOfTheRoot);

        assertThat(answerTo("to string! read %inside.txt")).isEqualTo("\"inside\"");
    }

    @Test
    @DisplayName("a script on the whole machine follows any link, as there is no outside to reach")
    void followsAnyLinkOnTheWholeMachine() throws IOException {
        link("door.txt", outside.resolve("secret.txt"));
        interpreter = over(Path.of("/"));
        String door = root.resolve("door.txt").toAbsolutePath().toString();

        assertThat(answerTo("to string! read %" + door)).isEqualTo("\"secret\"");
    }
}
