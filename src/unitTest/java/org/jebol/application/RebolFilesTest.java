package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RebolFilesTest {

    private final RebolFiles rebolFiles = new RebolFiles(new ClasspathFiles());

    @Test
    @DisplayName("each file names its place in the build")
    void namesEachPath() {
        assertThat(Stream.of(
                rebolFiles.errors(), rebolFiles.typespec(), rebolFiles.ops(), rebolFiles.modes(),
                rebolFiles.actions(), rebolFiles.natives(), rebolFiles.genNatives(),
                rebolFiles.bootSymbolsTxt(), rebolFiles.prelude(), rebolFiles.sysobj(),
                rebolFiles.systemObject(), rebolFiles.schemes(), rebolFiles.qoiCodec(),
                rebolFiles.handOverToStart(), rebolFiles.modules(), rebolFiles.modulesDirectory(),
                rebolFiles.mezzBanner(), rebolFiles.protMysql(), rebolFiles.baseFuncs(),
                rebolFiles.orderTxt(), rebolFiles.mezzDir("x.reb"), rebolFiles.modulesDir("y.reb"))
                .map(RebolFile::path).toList()).containsExactly(
                "/org/jebol/errors.reb", "/org/jebol/typespec.reb", "/org/jebol/ops.reb",
                "/org/jebol/modes.reb", "/org/jebol/actions.reb", "/org/jebol/natives.reb",
                "/org/jebol/gen-natives.reb", "/org/jebol/boot-symbols.txt",
                "/org/jebol/prelude.reb", "/org/jebol/sysobj.reb",
                "/org/jebol/boot/system-object.reb", "/org/jebol/boot/schemes.reb",
                "/org/jebol/boot/qoi-codec.reb", "/org/jebol/boot/hand-over-to-start.reb",
                "/org/jebol/boot/modules.reb", "/org/jebol/boot/modules-directory.reb",
                "/org/jebol/mezz/mezz-banner.reb", "/org/jebol/mezz/prot-mysql.reb",
                "/org/jebol/mezz/base-funcs.reb", "/org/jebol/mezz/ORDER.txt",
                "/org/jebol/mezz/x.reb", "/org/jebol/modules/y.reb");
    }

    @Test
    @DisplayName("a file named by its directory is the same file as the one named outright")
    void equatesSameFile() {
        assertThat(rebolFiles.mezzDir("prot-mysql.reb")).isEqualTo(rebolFiles.protMysql());
        assertThat(rebolFiles.mezzDir("base-funcs.reb")).isNotEqualTo(rebolFiles.protMysql());
    }

    @Test
    @DisplayName("a file reads its source as text and its bytes as they are")
    void readsSourceAndBytes() {
        RebolFile errors = rebolFiles.errors();

        assertThat(errors.textSource()).contains("read-only:");
        assertThat(errors.readBytes()).isEqualTo(errors.textSource().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a library file reads as its header and body")
    void readsLibraryFile() {
        assertThat(rebolFiles.prelude().readLibraryFile().body().remaining()).isNotEmpty();
    }

    @Test
    @DisplayName("a file the build carries exists, and one it does not carry does not")
    void answersExistence() {
        assertThat(rebolFiles.prelude().exists()).isTrue();
        assertThat(rebolFiles.mezzDir("no-such-file.reb").exists()).isFalse();
    }

    @Test
    @DisplayName("a carried REBOL file reads as a library file, and a missing one does not")
    void answersWhetherItReadsAsLibraryFile() {
        assertThat(rebolFiles.prelude().readsAsLibraryFile()).isTrue();
        assertThat(rebolFiles.mezzDir("no-such-file.reb").readsAsLibraryFile()).isFalse();
    }

    @Test
    @DisplayName("reading a file the build does not carry fails, naming the path")
    void refusesMissingFile() {
        RebolFile missing = rebolFiles.mezzDir("no-such-file.reb");

        assertThatThrownBy(missing::textSource)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/org/jebol/mezz/no-such-file.reb");
        assertThatThrownBy(missing::readLibraryFile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/org/jebol/mezz/no-such-file.reb");
    }
}
