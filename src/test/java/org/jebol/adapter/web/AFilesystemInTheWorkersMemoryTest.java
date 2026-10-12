package org.jebol.adapter.web;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AFilesystemInTheWorkersMemoryTest {

    private static byte[] bytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String textOf(WebFiles files, String path) {
        return new String(files.readBytes(path), StandardCharsets.UTF_8);
    }

    private static String refusalIdOf(Runnable operation) {
        try {
            operation.run();
            return "no-refusal";
        } catch (FilePort.Denied denied) {
            return denied.errorId();
        }
    }

    @Test
    @DisplayName("a file written is a file read, exactly")
    void writtenThenRead() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("hello"));

        assertThat(textOf(files, "/a.txt")).isEqualTo("hello");
        assertThat(files.exists("/a.txt")).isTrue();
        assertThat(files.exists("/b.txt")).isFalse();
    }

    @Test
    @DisplayName("bytes are bytes, not text")
    void bytesAreBytes() {
        WebFiles files = new WebFiles();
        files.write("/raw", new byte[] {(byte) 0xFF, 0, (byte) 0x80});

        assertThat(files.readBytes("/raw")).containsExactly((byte) 0xFF, 0, (byte) 0x80);
    }

    @Test
    @DisplayName("reading what is not there, or a directory, is cannot-open")
    void readingWhatIsNotAFileRaises() {
        WebFiles files = new WebFiles();

        assertThatThrownBy(() -> files.readBytes("/nothing"))
                .isInstanceOf(FilePort.Denied.class)
                .hasMessageContaining("cannot read");
        assertThatThrownBy(() -> files.readBytes("/"))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("a relative path counts from the directory the script changed to")
    void relativePathsCountFromTheScriptsDirectory() {
        WebFiles files = new WebFiles();
        files.makeDirectory("/sub/deeper", true);
        files.write("/sub/deeper/a.txt", bytesOf("deep"));
        files.changeDirectory("/sub/deeper");

        assertThat(textOf(files, "a.txt")).isEqualTo("deep");
        assertThat(files.workingDirectory()).isEqualTo("/sub/deeper/");
        assertThat(textOf(files, "../deeper/a.txt")).isEqualTo("deep");
    }

    @Test
    @DisplayName("the dots are worked out, and one at the root stays at the root")
    void theDotsClampAtTheRoot() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("top"));

        assertThat(textOf(files, "/../../a.txt")).isEqualTo("top");
        assertThat(textOf(files, "./a.txt")).isEqualTo("top");
    }

    @Test
    @DisplayName("changing to what is not a directory raises and moves nothing")
    void changingToWhatIsNotADirectoryRaises() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("x"));

        assertThatThrownBy(() -> files.changeDirectory("/a.txt"))
                .isInstanceOf(FilePort.Denied.class);
        assertThat(files.workingDirectory()).isEqualTo("/");
    }

    @Test
    @DisplayName("a directory is made with its parents, and making one again says nothing")
    void makingDirectories() {
        WebFiles files = new WebFiles();
        files.makeDirectory("/one/two", true);

        assertThat(files.isDirectory("/one/two")).isTrue();
        assertThat(files.namesIn("/one")).containsExactly("two/");
        files.makeDirectory("/one/two", false);
        assertThat(files.isDirectory("/one/two")).isTrue();

        files.write("/plain.txt", bytesOf("x"));
        assertThat(refusalIdOf(() -> files.makeDirectory("/plain.txt", false)))
                .isEqualTo("no-create");
        assertThat(refusalIdOf(() -> files.makeDirectory("/deep/one/two", false)))
                .isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("a file cannot be written into a directory that is not there")
    void writingIntoNowhereRaises() {
        WebFiles files = new WebFiles();

        assertThatThrownBy(() -> files.write("/nowhere/a.txt", bytesOf("x")))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("appending joins, and writing at a position overlays without shortening")
    void appendingAndWritingAt() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("one"));
        files.appendTo("/a.txt", bytesOf("two"));
        assertThat(textOf(files, "/a.txt")).isEqualTo("onetwo");

        files.writeAt("/a.txt", 3, bytesOf("TWO"));
        assertThat(textOf(files, "/a.txt")).isEqualTo("oneTWO");
    }

    @Test
    @DisplayName("deleting answers whether there was anything, and a directory must be empty")
    void deleting() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("x"));

        assertThat(files.delete("/a.txt")).isTrue();
        assertThat(files.delete("/a.txt")).isFalse();
        assertThat(files.exists("/a.txt")).isFalse();

        files.makeDirectory("/empty", false);
        assertThat(files.delete("/empty/")).isTrue();

        files.makeDirectory("/full", false);
        files.write("/full/a.txt", bytesOf("x"));
        assertThat(refusalIdOf(() -> files.delete("/full/")))
                .isEqualTo("no-delete");
    }

    @Test
    @DisplayName("renaming moves the bytes, and a missing source raises")
    void renaming() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("x"));
        files.rename("/a.txt", "/b.txt");

        assertThat(files.exists("/a.txt")).isFalse();
        assertThat(textOf(files, "/b.txt")).isEqualTo("x");
        assertThatThrownBy(() -> files.rename("/nothing", "/c.txt"))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("the names in a directory come sorted, with a slash on each directory")
    void listing() {
        WebFiles files = new WebFiles();
        files.makeDirectory("/d", false);
        files.write("/d/b.txt", bytesOf("x"));
        files.write("/d/a.txt", bytesOf("x"));

        assertThat(files.namesIn("/d")).containsExactly("a.txt", "b.txt");
        files.makeDirectory("/d/nest", false);
        assertThat(files.namesIn("/d")).containsExactly("a.txt", "b.txt", "nest/");
        assertThat(files.namesIn("/")).containsExactly("d/");
    }

    @Test
    @DisplayName("a file answers its size and a directory answers none")
    void information() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("four"));
        files.makeDirectory("/d", false);

        assertThat(files.informationAbout("/a.txt"))
                .map(FileInformation::size)
                .contains(Optional.of(4L));
        assertThat(files.informationAbout("/d"))
                .map(FileInformation::isDirectory)
                .contains(true);
        assertThat(files.informationAbout("/d"))
                .map(FileInformation::size)
                .contains(Optional.empty());
        assertThat(files.informationAbout("/nothing")).isEmpty();
    }

    @Test
    @DisplayName("canonicalising answers a path only for something that is there")
    void canonicalising() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", bytesOf("x"));
        files.makeDirectory("/d", false);

        assertThat(files.canonicalPathOf("/a.txt")).isEqualTo("/a.txt");
        assertThat(files.canonicalPathOf("/d")).isEqualTo("/d/");
        assertThat(files.canonicalPathOf("/nothing")).isNull();
    }

    private static final class AStoreThatRemembers implements WebFiles.Store {

        private final Map<String, byte[]> files = new LinkedHashMap<>();
        private final Set<String> directories = new TreeSet<>();
        private final List<String> events = new ArrayList<>();
        private String refusal;

        AStoreThatRemembers holding(String path, String contents) {
            files.put(path, bytesOf(contents));
            return this;
        }

        AStoreThatRemembers holdingADirectory(String path) {
            directories.add(path);
            return this;
        }

        AStoreThatRemembers refusing(String because) {
            this.refusal = because;
            return this;
        }

        @Override
        public Tree everything() {
            return new Tree(files, directories);
        }

        @Override
        public void wrote(String path, byte[] contents) {
            refuseIfAsked();
            files.put(path, contents.clone());
            events.add("wrote " + path);
        }

        @Override
        public void removed(String path) {
            refuseIfAsked();
            files.remove(path);
            directories.remove(path);
            events.add("removed " + path);
        }

        @Override
        public void madeADirectory(String path) {
            refuseIfAsked();
            directories.add(path);
            events.add("made " + path);
        }

        private void refuseIfAsked() {
            if (refusal != null) {
                throw new FilePort.Denied("cannot-open", refusal);
            }
        }
    }

    @Test
    @DisplayName("what the store already holds is a file the script can read")
    void theStoreIsLoadedFirst() {
        WebFiles files = new WebFiles(new AStoreThatRemembers()
                .holding("/kept.txt", "kept")
                .holdingADirectory("/d"));

        assertThat(textOf(files, "/kept.txt")).isEqualTo("kept");
        assertThat(files.isDirectory("/d")).isTrue();
        assertThat(files.namesIn("/")).containsExactly("d/", "kept.txt");
    }

    @Test
    @DisplayName("every change is written back to the store")
    void everyChangeGoesToTheStore() {
        AStoreThatRemembers store = new AStoreThatRemembers();
        WebFiles files = new WebFiles(store);

        files.makeDirectory("/d", false);
        files.write("/d/a.txt", bytesOf("one"));
        files.appendTo("/d/a.txt", bytesOf("two"));
        files.rename("/d/a.txt", "/d/b.txt");
        files.delete("/d/b.txt");

        assertThat(store.events).containsExactly(
                "made /d",
                "wrote /d/a.txt",
                "wrote /d/a.txt",
                "removed /d/a.txt",
                "wrote /d/b.txt",
                "removed /d/b.txt");
    }

    @Test
    @DisplayName("a store that refuses stops the change before the memory has it")
    void aRefusingStoreStopsTheChange() {
        AStoreThatRemembers store = new AStoreThatRemembers().refusing("no room in the store");
        WebFiles files = new WebFiles(store);

        assertThat(refusalIdOf(() -> files.write("/a.txt", bytesOf("x"))))
                .isEqualTo("cannot-open");
        assertThat(files.exists("/a.txt")).isFalse();
    }

    @Test
    @DisplayName("an empty store is an empty filesystem")
    void anEmptyStoreIsEmpty() {
        WebFiles files = new WebFiles(new AStoreThatRemembers());

        assertThat(files.namesIn("/")).isEmpty();
        assertThat(files.exists("/anything")).isFalse();
    }
}
