package org.jebol.application;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsDrivesAsTheFirstSegmentTest {

    private final MemoryFiles driveC = new MemoryFiles();
    private final MemoryFiles driveD = new MemoryFiles();
    private final DriveLetterFiles files = new DriveLetterFiles(Map.of("C", driveC, "D", driveD), "/");

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(FilePort port, String path) {
        return new String(port.readBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the first segment names the drive, and the rest is a path on it")
    void routesByTheFirstSegment() {
        files.write("/C/notes.txt", bytes("on c"));
        files.write("/D/notes.txt", bytes("on d"));

        assertThat(text(driveC, "/notes.txt")).isEqualTo("on c");
        assertThat(text(driveD, "/notes.txt")).isEqualTo("on d");
        assertThat(text(files, "/C/notes.txt")).isEqualTo("on c");
    }

    @Test
    @DisplayName("a drive letter is the same drive in either case, as Windows has it")
    void readsTheLetterInEitherCase() {
        files.write("/c/notes.txt", bytes("lower"));

        assertThat(text(files, "/C/notes.txt")).isEqualTo("lower");
    }

    @Test
    @DisplayName("the root lists the drives, as R3's read %/ does on Windows")
    void listsTheDrivesAtTheRoot() {
        assertThat(files.listFileNames("/")).containsExactly("C/", "D/");
        assertThat(files.isDirectory("/")).isTrue();
        assertThat(files.exists("/")).isTrue();
    }

    @Test
    @DisplayName("the root is a directory with no size and no dates, as R3's Query_File answers %/")
    void describesTheRootWithoutDates() {
        FileInformation described = files.describe("/").orElseThrow();

        assertThat(described.name()).isEqualTo("/");
        assertThat(described.isDirectory()).isTrue();
        assertThat(described.size()).isEmpty();
        assertThat(described.modified()).isEmpty();
        assertThat(described.created()).isEmpty();
    }

    @Test
    @DisplayName("a drive that is not there does not exist, and nothing can be written on it")
    void refusesADriveThatIsNotThere() {
        assertThat(files.exists("/E/")).isFalse();
        assertThat(files.exists("/E/notes.txt")).isFalse();
        assertThatThrownBy(() -> files.write("/E/notes.txt", bytes("lost")))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("nothing is written at the root, where only drives are")
    void refusesAFileAtTheRoot() {
        assertThatThrownBy(() -> files.write("/notes.txt", bytes("nowhere")))
                .isInstanceOf(FilePort.Denied.class);
        assertThatThrownBy(() -> files.makeDirectory("/work/", false))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("a relative path counts from the working directory, drive included")
    void readsARelativePathFromTheWorkingDirectory() {
        driveC.makeDirectory("/work/", false);
        files.changeDirectory("/C/work/");

        files.write("a.txt", bytes("relative"));

        assertThat(files.workingDirectory()).isEqualTo("/C/work/");
        assertThat(text(driveC, "/work/a.txt")).isEqualTo("relative");
    }

    @Test
    @DisplayName("backing out past a drive reaches the root, and from there another drive")
    void backsOutPastTheDrive() {
        driveD.write("/x.txt", bytes("on d"));
        files.changeDirectory("/C/");

        assertThat(text(files, "../D/x.txt")).isEqualTo("on d");
        files.changeDirectory("..");
        assertThat(files.workingDirectory()).isEqualTo("/");
        assertThat(text(files, "D/x.txt")).isEqualTo("on d");
    }

    @Test
    @DisplayName("changing to a folder that is not there is refused, and the working directory stays")
    void refusesChangingToAMissingFolder() {
        assertThatThrownBy(() -> files.changeDirectory("/C/missing/"))
                .isInstanceOf(FilePort.Denied.class);
        assertThat(files.workingDirectory()).isEqualTo("/");
    }

    @Test
    @DisplayName("a Windows path the host gives is written with its drive first")
    void writesAHostPathWithItsDriveFirst() {
        assertThat(files.findScriptPath("C:\\Users\\someone")).contains("/C/Users/someone");
        assertThat(files.findScriptPath("d:\\")).contains("/d/");
        assertThat(files.findScriptPath("relative\\path")).isEmpty();
    }

    @Test
    @DisplayName("a location the drive answers carries the drive in front of it")
    void putsTheDriveInFrontOfALocation() {
        files.write("/D/x.txt", bytes("on d"));

        assertThat(files.locationFor("/D/x.txt")).isEqualTo("/D/x.txt");
        assertThat(files.locationFor("/D/")).isEqualTo("/D/");
    }

    @Test
    @DisplayName("a file is described by its name from the working directory")
    void describesFromTheWorkingDirectory() {
        driveC.makeDirectory("/work/", false);
        files.write("/C/work/a.txt", bytes("abc"));
        files.changeDirectory("/C/");

        FileInformation described = files.describe("work/a.txt").orElseThrow();

        assertThat(described.name()).isEqualTo("work/a.txt");
        assertThat(described.size()).contains(3L);
    }

    @Test
    @DisplayName("a file renamed on its own drive moves there, and one moved to another drive goes across")
    void renamesOnADriveAndAcross() {
        files.write("/C/a.txt", bytes("moving"));

        files.rename("/C/a.txt", "/C/b.txt");
        assertThat(text(driveC, "/b.txt")).isEqualTo("moving");

        files.rename("/C/b.txt", "/D/b.txt");
        assertThat(driveC.exists("/b.txt")).isFalse();
        assertThat(text(driveD, "/b.txt")).isEqualTo("moving");
    }

    @Test
    @DisplayName("a copy for a second interpreter starts where this one stands and moves on its own")
    void copiesTheWorkingDirectory() {
        driveC.makeDirectory("/work/", false);
        files.changeDirectory("/C/work/");

        DriveLetterFiles copy = files.copyInWorkingDirectory();
        copy.changeDirectory("/D/");

        assertThat(files.workingDirectory()).isEqualTo("/C/work/");
        assertThat(copy.workingDirectory()).isEqualTo("/D/");
    }
}
