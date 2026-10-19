package org.jebol.adapter.web;

import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;
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

    private static byte[] readTheBytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String textOf(WebFiles files, String path) {
        return new String(files.readBytes(path), StandardCharsets.UTF_8);
    }

    private static String readTheRefusalIdOf(Runnable operation) {
        try {
            operation.run();
            return "no-refusal";
        } catch (FilePort.Denied denied) {
            return denied.errorId();
        }
    }

    @Test
    @DisplayName("a file written is a file read, exactly")
    void readsBackWhatItWrites() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("hello"));

        assertThat(textOf(files, "/a.txt")).isEqualTo("hello");
        assertThat(files.exists("/a.txt")).isTrue();
        assertThat(files.exists("/b.txt")).isFalse();
    }

    @Test
    @DisplayName("bytes are bytes, not text")
    void keepsBytesAsBytes() {
        WebFiles files = new WebFiles();
        files.write("/raw", new byte[] {(byte) 0xFF, 0, (byte) 0x80});

        assertThat(files.readBytes("/raw")).containsExactly((byte) 0xFF, 0, (byte) 0x80);
    }

    @Test
    @DisplayName("reading what is not there, or a directory, is cannot-open")
    void raisesReadingWhatIsNotAFile() {
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
        files.write("/sub/deeper/a.txt", readTheBytesOf("deep"));
        files.changeDirectory("/sub/deeper");

        assertThat(textOf(files, "a.txt")).isEqualTo("deep");
        assertThat(files.workingDirectory()).isEqualTo("/sub/deeper/");
        assertThat(textOf(files, "../deeper/a.txt")).isEqualTo("deep");
    }

    @Test
    @DisplayName("the dots are worked out, and one at the root stays at the root")
    void clampsTheDotsAtTheRoot() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("top"));

        assertThat(textOf(files, "/../../a.txt")).isEqualTo("top");
        assertThat(textOf(files, "./a.txt")).isEqualTo("top");
    }

    @Test
    @DisplayName("changing to what is not a directory raises and moves nothing")
    void raisesChangingToWhatIsNotADirectory() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("x"));

        assertThatThrownBy(() -> files.changeDirectory("/a.txt"))
                .isInstanceOf(FilePort.Denied.class);
        assertThat(files.workingDirectory()).isEqualTo("/");
    }

    @Test
    @DisplayName("a directory is made with its parents, and making one again says nothing")
    void makesDirectories() {
        WebFiles files = new WebFiles();
        files.makeDirectory("/one/two", true);

        assertThat(files.isDirectory("/one/two")).isTrue();
        assertThat(files.listFileNames("/one")).containsExactly("two/");
        files.makeDirectory("/one/two", false);
        assertThat(files.isDirectory("/one/two")).isTrue();

        files.write("/plain.txt", readTheBytesOf("x"));
        assertThat(readTheRefusalIdOf(() -> files.makeDirectory("/plain.txt", false)))
                .isEqualTo("no-create");
        assertThat(readTheRefusalIdOf(() -> files.makeDirectory("/deep/one/two", false)))
                .isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("a file cannot be written into a directory that is not there")
    void raisesWritingIntoNowhere() {
        WebFiles files = new WebFiles();

        assertThatThrownBy(() -> files.write("/nowhere/a.txt", readTheBytesOf("x")))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("appending joins, and writing at a position overlays without shortening")
    void appendsAndWritesAt() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("one"));
        files.appendTo("/a.txt", readTheBytesOf("two"));
        assertThat(textOf(files, "/a.txt")).isEqualTo("onetwo");

        files.writeAt("/a.txt", 3, readTheBytesOf("TWO"));
        assertThat(textOf(files, "/a.txt")).isEqualTo("oneTWO");
    }

    @Test
    @DisplayName("deleting answers whether there was anything, and a directory must be empty")
    void deletes() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("x"));

        assertThat(files.delete("/a.txt")).isTrue();
        assertThat(files.delete("/a.txt")).isFalse();
        assertThat(files.exists("/a.txt")).isFalse();

        files.makeDirectory("/empty", false);
        assertThat(files.delete("/empty/")).isTrue();

        files.makeDirectory("/full", false);
        files.write("/full/a.txt", readTheBytesOf("x"));
        assertThat(readTheRefusalIdOf(() -> files.delete("/full/")))
                .isEqualTo("no-delete");
    }

    @Test
    @DisplayName("renaming moves the bytes, and a missing source raises")
    void renames() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("x"));
        files.rename("/a.txt", "/b.txt");

        assertThat(files.exists("/a.txt")).isFalse();
        assertThat(textOf(files, "/b.txt")).isEqualTo("x");
        assertThatThrownBy(() -> files.rename("/nothing", "/c.txt"))
                .isInstanceOf(FilePort.Denied.class);
    }

    @Test
    @DisplayName("the names in a directory come sorted, with a slash on each directory")
    void lists() {
        WebFiles files = new WebFiles();
        files.makeDirectory("/d", false);
        files.write("/d/b.txt", readTheBytesOf("x"));
        files.write("/d/a.txt", readTheBytesOf("x"));

        assertThat(files.listFileNames("/d")).containsExactly("a.txt", "b.txt");
        files.makeDirectory("/d/nest", false);
        assertThat(files.listFileNames("/d")).containsExactly("a.txt", "b.txt", "nest/");
        assertThat(files.listFileNames("/")).containsExactly("d/");
    }

    @Test
    @DisplayName("a file answers its size and a directory answers none")
    void givesInformation() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("four"));
        files.makeDirectory("/d", false);

        assertThat(files.describe("/a.txt"))
                .map(FileInformation::size)
                .contains(Optional.of(4L));
        assertThat(files.describe("/d"))
                .map(FileInformation::isDirectory)
                .contains(true);
        assertThat(files.describe("/d"))
                .map(FileInformation::size)
                .contains(Optional.empty());
        assertThat(files.describe("/nothing")).isEmpty();
    }

    @Test
    @DisplayName("canonicalising answers a path only for something that is there")
    void canonicalises() {
        WebFiles files = new WebFiles();
        files.write("/a.txt", readTheBytesOf("x"));
        files.makeDirectory("/d", false);

        List<Value> answers = List.of(files.resolvedFile("/a.txt"), files.resolvedFile("/d"), files.resolvedFile("/nothing"));

        assertThat(answers).containsExactly(FileValue.of("/a.txt"), FileValue.of("/d/"), NoneValue.none());
    }

    private static final class AStoreThatRemembers implements WebFiles.Store {

        private final Map<String, byte[]> files = new LinkedHashMap<>();
        private final Set<String> directories = new TreeSet<>();
        private final List<String> events = new ArrayList<>();
        private String refusal;

        AStoreThatRemembers hold(String path, String contents) {
            files.put(path, readTheBytesOf(contents));
            return this;
        }

        AStoreThatRemembers holdADirectory(String path) {
            directories.add(path);
            return this;
        }

        AStoreThatRemembers refuse(String because) {
            this.refusal = because;
            return this;
        }

        @Override
        public Tree listAll() {
            return new Tree(files, directories);
        }

        @Override
        public void wrote(String path, byte[] contents) {
            refuseIfAsked();
            files.put(path, contents.clone());
            events.add("wrote " + path);
        }

        @Override
        public void remove(String path) {
            refuseIfAsked();
            files.remove(path);
            directories.remove(path);
            events.add("removed " + path);
        }

        @Override
        public void makeDirectory(String path) {
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
    void loadsTheStoreFirst() {
        WebFiles files = new WebFiles(new AStoreThatRemembers()
                .hold("/kept.txt", "kept")
                .holdADirectory("/d"));

        assertThat(textOf(files, "/kept.txt")).isEqualTo("kept");
        assertThat(files.isDirectory("/d")).isTrue();
        assertThat(files.listFileNames("/")).containsExactly("d/", "kept.txt");
    }

    @Test
    @DisplayName("every change is written back to the store")
    void sendsEveryChangeToTheStore() {
        AStoreThatRemembers store = new AStoreThatRemembers();
        WebFiles files = new WebFiles(store);

        files.makeDirectory("/d", false);
        files.write("/d/a.txt", readTheBytesOf("one"));
        files.appendTo("/d/a.txt", readTheBytesOf("two"));
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
    void stopsTheChangeWhenTheStoreRefuses() {
        AStoreThatRemembers store = new AStoreThatRemembers().refuse("no room in the store");
        WebFiles files = new WebFiles(store);

        assertThat(readTheRefusalIdOf(() -> files.write("/a.txt", readTheBytesOf("x"))))
                .isEqualTo("cannot-open");
        assertThat(files.exists("/a.txt")).isFalse();
    }

    @Test
    @DisplayName("an empty store is an empty filesystem")
    void startsEmptyFromAnEmptyStore() {
        WebFiles files = new WebFiles(new AStoreThatRemembers());

        assertThat(files.listFileNames("/")).isEmpty();
        assertThat(files.exists("/anything")).isFalse();
    }
}
