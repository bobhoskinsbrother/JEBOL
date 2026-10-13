package org.jebol.domain.read;

import org.jebol.application.WritingTheLibraryImage;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TheLibraryImageFromTheSourceTest {

    private static Map<String, AnyBlockValue> theLibrary() throws IOException {
        return WritingTheLibraryImage.everyLibraryFileUnder(
                Path.of("src", "main", "resources"));
    }

    @Test
    @Timeout(120)
    @DisplayName("every borrowed file comes back from the image exactly as the scanner reads it")
    void everyFileRoundTrips() throws IOException {
        Map<String, AnyBlockValue> read = theLibrary();

        assertThat(read.keySet())
                .as("the image is only worth having if it carries the real library")
                .contains(
                        "/org/jebol/mezz/base-defs.reb",
                        "/org/jebol/sysobj.reb",
                        "/org/jebol/prelude.reb",
                        "/org/jebol/boot/schemes.reb");

        Map<String, AnyBlockValue> image = LibraryImage.treesIn(LibraryImage.asBytes(read));

        assertThat(image.keySet()).isEqualTo(read.keySet());
        for (String name : read.keySet()) {
            assertSameTree(name, read.get(name), image.get(name));
        }
    }

    private void assertSameTree(String name, Value read, Value fromImage) {
        assertThat(fromImage.datatype()).as(name).isEqualTo(read.datatype());
        if (read instanceof AnyBlockValue readBlock) {
            AnyBlockValue imageBlock = (AnyBlockValue) fromImage;
            assertThat(imageBlock.remaining().size())
                    .as(name).isEqualTo(readBlock.remaining().size());
            for (int at = 1; at <= readBlock.remaining().size() + 1; at++) {
                assertThat(imageBlock.storage().breaksLineAt(at))
                        .as("%s, line break at %d", name, at)
                        .isEqualTo(readBlock.storage().breaksLineAt(at));
            }
            for (int item = 0; item < readBlock.remaining().size(); item++) {
                assertSameTree(name, readBlock.remaining().get(item),
                        imageBlock.remaining().get(item));
            }
            return;
        }
        assertThat(Molder.mold(fromImage))
                .as(name)
                .isEqualTo(Molder.mold(read));
    }
}
