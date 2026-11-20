package org.jebol.android.unit;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.adapter.android.PickedDocuments;
import org.jebol.adapter.android.PickedDocuments.Pick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PickedDocumentsTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Path table = context.getCacheDir().toPath().resolve("picked-documents-test.txt");

    @BeforeEach
    @AfterEach
    void removeTheTable() throws IOException {
        Files.deleteIfExists(table);
    }

    @Test
    @DisplayName("the first pick is 1, and each after it is one past the highest in use")
    void numbersCountUpFromTheHighest() {
        PickedDocuments picks = PickedDocuments.keptIn(table);

        assertThat(picks.keep("content://a/document/1", "a.txt", false).number()).isEqualTo(1);
        assertThat(picks.keep("content://a/document/2", "b.txt", false).number()).isEqualTo(2);
        picks.forget(1);
        assertThat(picks.keep("content://a/document/3", "c.txt", false).number()).isEqualTo(3);
        picks.forget(3);
        assertThat(picks.keep("content://a/document/4", "d.txt", false).number()).isEqualTo(3);
    }

    @Test
    @DisplayName("a pick's path is the page's: /picked/N/name for a file, with a slash after a folder")
    void aPickNamesThePagesPath() {
        PickedDocuments picks = PickedDocuments.keptIn(table);

        assertThat(picks.keep("content://a/document/1", "notes.txt", false).path())
                .isEqualTo("/picked/1/notes.txt");
        assertThat(picks.keep("content://a/tree/2", "Docs", true).path()).isEqualTo("/picked/2/Docs/");
    }

    @Test
    @DisplayName("the table read back after a restart names the same documents")
    void theTableSurvivesARestart() {
        PickedDocuments before = PickedDocuments.keptIn(table);
        before.keep("content://a/document/1", "notes.txt", false);
        before.keep("content://a/tree/two%2Fthree", "a name\twith a tab", true);

        PickedDocuments after = PickedDocuments.keptIn(table);

        assertThat(after.find(1)).contains(new Pick(1, "notes.txt", "content://a/document/1", false));
        assertThat(after.find(2)).contains(new Pick(2, "a name\twith a tab", "content://a/tree/two%2Fthree", true));
        assertThat(after.all()).hasSize(2);
    }

    @Test
    @DisplayName("a pick renamed in place keeps its number and is read back with its new name")
    void aRenameIsKept() {
        PickedDocuments picks = PickedDocuments.keptIn(table);
        picks.keep("content://a/document/1", "old.txt", false);

        picks.rename(1, "content://a/document/1b", "new.txt");

        assertThat(PickedDocuments.keptIn(table).find(1))
                .contains(new Pick(1, "new.txt", "content://a/document/1b", false));
    }

    @Test
    @DisplayName("a table that is missing or that cannot be read is an empty one, and picking starts at 1")
    void aMissingOrBrokenTableIsEmpty() throws IOException {
        assertThat(PickedDocuments.keptIn(table).all()).isEmpty();

        Files.write(table, "not a table at all\n7\tfile".getBytes(StandardCharsets.UTF_8));

        PickedDocuments picks = PickedDocuments.keptIn(table);
        assertThat(picks.all()).isEmpty();
        assertThat(picks.keep("content://a/document/1", "a.txt", false).number()).isEqualTo(1);
    }

    @Test
    @DisplayName("a number never picked is found to be nothing")
    void anUnknownNumberIsNothing() {
        assertThat(PickedDocuments.keptIn(table).find(5)).isEmpty();
    }
}
