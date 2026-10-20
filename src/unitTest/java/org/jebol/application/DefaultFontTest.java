package org.jebol.application;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.render.Colour;
import org.jebol.domain.render.DefaultFontRenderer;
import org.jebol.domain.render.TestFont;
import org.jebol.domain.render.TextExtent;
import org.jebol.domain.render.TextRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class DefaultFontTest {

    private static final String WHERE_THE_CARRIED_FONT_SITS =
            "/org/jebol/fonts/NotoSans-Regular.ttf";

    @Test
    @DisplayName("the bytes come from the port, so a build carrying another font measures differently")
    void readsThroughThePort(@TempDir Path build) throws IOException {
        DefaultFont font = DefaultFont.readFrom(aBuildWhoseFontIs(build, TestFont.bytes()));

        assertThat(font.renderer().familyName()).isEqualTo("Test Sans");
        assertThat(extentOf(font.renderer(), "AB", 12).wide()).isCloseTo(9.6, offset(1e-9));
        assertThat(extentOf(font.renderer(), "AB", 12).wide())
                .isNotCloseTo(extentOf(carriedFont(), "AB", 12).wide(), offset(1e-3));
    }

    @Test
    @DisplayName("the font this build carries reads, and says which family it is")
    void readsTheCarriedFont() {
        assertThat(DefaultFont.readFrom(new ClasspathFiles()).renderer().familyName())
                .isEqualTo("Noto Sans");
    }

    @Test
    @DisplayName("a port with no font behind it fails the read, naming the font the build promises")
    void refusesAMissingFont() {
        assertThatThrownBy(() -> DefaultFont.readFrom(FilePort.none()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(WHERE_THE_CARRIED_FONT_SITS);
    }

    @Test
    @DisplayName("the bytes are handed out as a copy, so changing one cannot spoil the next")
    void handsOutCopies(@TempDir Path build) throws IOException {
        DefaultFont font = DefaultFont.readFrom(aBuildWhoseFontIs(build, TestFont.bytes()));

        font.readFontBytes()[1] = 0;

        assertThat(font.readFontBytes()[1]).isNotZero();
    }

    private static FilePort aBuildWhoseFontIs(Path build, byte[] fontBytes) throws IOException {
        Path where = build.resolve(WHERE_THE_CARRIED_FONT_SITS.substring(1));
        Files.createDirectories(where.getParent());
        Files.write(where, fontBytes);
        return FileSystemPort.rootFileSystem(build);
    }

    private static DefaultFontRenderer carriedFont() {
        return DefaultFont.readFrom(new ClasspathFiles()).renderer();
    }

    private static TextExtent extentOf(DefaultFontRenderer font, String text, double size) {
        return font.measure().measureTheExtentOf(
                new TextRun(text, Colour.BLACK, size, false, false));
    }
}
