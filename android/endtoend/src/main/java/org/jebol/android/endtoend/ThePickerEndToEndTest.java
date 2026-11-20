package org.jebol.android.endtoend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ThePickerEndToEndTest {

    private static final Duration PATIENCE = Duration.ofSeconds(30);
    private static final String PICKED = "jebol-e2e-picked.txt";
    private static final String SAVED = "jebol-e2e-saved.txt";
    private static final String FOLDER = "jebol-e2e-folder";

    private final TheApp app = TheApp.notLaunched();

    @BeforeEach
    void startWithNothingPicked() {
        removeTheFixtures();
        app.shell("pm clear " + TheApp.PACKAGE);
        app.runInShell("""
                mkdir -p %1$s
                cd %1$s
                mkdir -p %2$s/inner
                printf from-downloads > %3$s
                printf a > %2$s/a.txt
                printf b > %2$s/inner/b.txt
                """.formatted(ThePicker.FIXTURE_FOLDER, FOLDER, PICKED));
    }

    @AfterEach
    void removeTheFixtures() {
        app.runInShell("rm -rf %s%n".formatted(ThePicker.FIXTURE_FOLDER));
    }

    @Test
    @DisplayName("a file picked in Downloads is read at the page's path, and a write reaches the user's file")
    void aPickedFileIsReadAndWritten() {
        TheApp running = TheApp.launchedAfresh("""
                f: request-file
                print ["picked" mold f]
                print ["read" read/string f]
                write/append f {+appended}
                print "written"
                """);
        ThePicker picker = new ThePicker(running);

        picker.goToFixtureFolder();
        picker.choose(PICKED);

        assertThat(running.waitForLineStarting("picked ", PATIENCE)).isEqualTo("picked %/picked/1/" + PICKED);
        assertThat(running.waitForLineStarting("read ", PATIENCE)).isEqualTo("read from-downloads");
        running.waitForLineStarting("written", PATIENCE);
        assertThat(running.shell("cat " + ThePicker.FIXTURE_FOLDER + PICKED)).isEqualTo("from-downloads+appended");
    }

    @Test
    @DisplayName("after the app is ended and launched again, the earlier pick still reads at its path")
    void aPickSurvivesARestart() {
        TheApp running = TheApp.launchedAfresh("print ['picked mold request-file]");
        ThePicker picker = new ThePicker(running);
        picker.goToFixtureFolder();
        picker.choose(PICKED);
        running.waitForLineStarting("picked ", PATIENCE);

        running.endProcess();
        TheApp again = TheApp.launchedAfresh("print ['again read/string %/picked/1/" + PICKED + "]");

        assertThat(again.waitForLineStarting("again ", PATIENCE)).isEqualTo("again from-downloads");
    }

    @Test
    @DisplayName("a save names a new file in Downloads, and what the script writes is there")
    void aSaveWritesANewFile() {
        TheApp running = TheApp.launchedAfresh("""
                f: request-file/save/file %%%s
                print ["saved" mold f]
                write f {written through the save picker}
                print "written"
                """.formatted(SAVED));
        ThePicker picker = new ThePicker(running);

        picker.goToFixtureFolder();
        picker.save();

        assertThat(running.waitForLineStarting("saved ", PATIENCE)).isEqualTo("saved %/picked/1/" + SAVED);
        running.waitForLineStarting("written", PATIENCE);
        assertThat(running.shell("cat " + ThePicker.FIXTURE_FOLDER + SAVED)).isEqualTo("written through the save picker");
    }

    @Test
    @DisplayName("a folder picked is listed and walked, and a file the script makes in it lands in the user's folder")
    void aPickedFolderIsWalked() {
        TheApp running = TheApp.launchedAfresh("""
                d: request-dir
                print ["folder" mold d]
                print ["listed" mold sort read d]
                print ["deeper" read/string d/inner/b.txt]
                write d/made.txt {made here}
                print "written"
                """);
        ThePicker picker = new ThePicker(running);

        picker.goToFixtureFolder();
        picker.openFolder(FOLDER);
        picker.useThisFolder();

        assertThat(running.waitForLineStarting("folder ", PATIENCE)).isEqualTo("folder %/picked/1/" + FOLDER + "/");
        assertThat(running.waitForLineStarting("listed ", PATIENCE)).isEqualTo("listed [%a.txt %inner/]");
        assertThat(running.waitForLineStarting("deeper ", PATIENCE)).isEqualTo("deeper b");
        running.waitForLineStarting("written", PATIENCE);
        assertThat(running.shell("cat " + ThePicker.FIXTURE_FOLDER + FOLDER + "/made.txt")).isEqualTo("made here");
    }

    @Test
    @DisplayName("a picker backed out of answers none")
    void aCancelledPickerIsNone() {
        TheApp running = TheApp.launchedAfresh("print ['answered none? request-file]");

        new ThePicker(running).cancel();

        assertThat(running.waitForLineStarting("answered ", PATIENCE)).isEqualTo("answered true");
    }
}
