package org.jebol.application;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathFilesTest {

    private static final String ERRORS = "/org/jebol/errors.reb";
    private static final String MISSING = "/org/jebol/no-such-file.reb";

    private final ClasspathFiles files = new ClasspathFiles();

    @Test
    @DisplayName("a bundled file reads as its bytes")
    void readsBundledFile() {
        assertThat(new String(files.readBytes(ERRORS), StandardCharsets.UTF_8))
                .contains("read-only:");
    }

    @Test
    @DisplayName("each read hands out its own copy, so changing one leaves the next untouched")
    void handsOutCopies() {
        byte[] first = files.readBytes(ERRORS);
        first[0] = 0;

        assertThat(files.readBytes(ERRORS)[0]).isNotZero();
    }

    @Test
    @DisplayName("a file the build does not carry is cannot-open naming its path")
    void refusesMissingFile() {
        assertThatThrownBy(() -> files.readBytes(MISSING))
                .isInstanceOfSatisfying(FilePort.Denied.class, refused -> {
                    assertThat(refused.errorId()).isEqualTo("cannot-open");
                    assertThat(refused.subject()).isEqualTo(MISSING);
                });
    }

    @Test
    @DisplayName("exists answers for a carried file and a missing one, and a file is never a directory")
    void answersExistence() {
        assertThat(files.exists(ERRORS)).isTrue();
        assertThat(files.exists(MISSING)).isFalse();
        assertThat(files.isDirectory(ERRORS)).isFalse();
    }

    @Test
    @DisplayName("a carried file resolves to itself and a missing one to none")
    void resolvesFiles() {
        List<Value> resolved = List.of(files.resolvedFile(ERRORS), files.resolvedFile(MISSING));

        assertThat(resolved).containsExactly(FileValue.of(ERRORS), NoneValue.none());
    }

    @Test
    @DisplayName("a carried file is described by its size, and a missing one not at all")
    void describesFiles() {
        assertThat(files.describe(ERRORS)).hasValueSatisfying(about ->
                assertThat(about.size()).contains((long) files.readBytes(ERRORS).length));
        assertThat(files.describe(MISSING)).isEmpty();
    }

    @Test
    @DisplayName("every change is refused as read-only, because the build's files are not the script's to change")
    void refusesEveryChange() {
        List<Runnable> changes = List.of(
                () -> files.write(ERRORS, new byte[0]),
                () -> files.writeAt(ERRORS, 0, new byte[0]),
                () -> files.appendTo(ERRORS, new byte[0]),
                () -> files.delete(ERRORS),
                () -> files.rename(ERRORS, MISSING),
                () -> files.makeDirectory("/org/jebol/new/", false));

        for (Runnable change : changes) {
            assertThatThrownBy(change::run)
                    .isInstanceOfSatisfying(FilePort.Denied.class, refused ->
                            assertThat(refused.errorId()).isEqualTo("read-only"));
        }
    }

    @Test
    @DisplayName("a path names itself, from a working directory at the root that never moves")
    void namesPathsFromRoot() {
        assertThat(files.workingDirectory()).isEqualTo("/");
        assertThat(files.hostPath(ERRORS)).isEqualTo(ERRORS);
        assertThat(files.locationFor(ERRORS)).isEqualTo(ERRORS);
        assertThatThrownBy(() -> files.changeDirectory("/org/"))
                .isInstanceOf(FilePort.Denied.class);
        assertThatThrownBy(() -> files.listFileNames("/org/jebol/"))
                .isInstanceOf(FilePort.Denied.class);
    }
}
