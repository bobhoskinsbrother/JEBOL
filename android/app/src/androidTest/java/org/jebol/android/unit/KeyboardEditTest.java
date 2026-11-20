package org.jebol.android.unit;

import org.jebol.adapter.android.KeyboardEdit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeyboardEditTest {

    @Test
    @DisplayName("text added at the end is typed, and nothing is deleted")
    void addedAtTheEnd() {
        assertThat(KeyboardEdit.between("good", "good morning")).isEqualTo(new KeyboardEdit(0, " morning"));
    }

    @Test
    @DisplayName("a word replaced at the end is that word's characters deleted and the new word typed")
    void wordReplacedAtTheEnd() {
        assertThat(KeyboardEdit.between("good helo", "good help")).isEqualTo(new KeyboardEdit(1, "p"));
        assertThat(KeyboardEdit.between("good morning", "good evening")).isEqualTo(new KeyboardEdit(7, "evening"));
    }

    @Test
    @DisplayName("text taken from the end is deleted, and nothing is typed")
    void takenFromTheEnd() {
        assertThat(KeyboardEdit.between("hello", "he")).isEqualTo(new KeyboardEdit(3, ""));
    }

    @Test
    @DisplayName("text that has not changed is no edit at all")
    void unchanged() {
        assertThat(KeyboardEdit.between("same", "same")).isEqualTo(new KeyboardEdit(0, ""));
        assertThat(KeyboardEdit.between("", "")).isEqualTo(new KeyboardEdit(0, ""));
    }

    @Test
    @DisplayName("a change before the end deletes back to it and types the rest again, as the script's cursor is at the end")
    void changedBeforeTheEnd() {
        assertThat(KeyboardEdit.between("cat sat", "bat sat")).isEqualTo(new KeyboardEdit(7, "bat sat"));
    }

    @Test
    @DisplayName("an emoji is one character deleted, even when its replacement shares its first half")
    void emojiIsOneCharacter() {
        assertThat(KeyboardEdit.between("a😀", "a😃")).isEqualTo(new KeyboardEdit(1, "😃"));
        assertThat(KeyboardEdit.between("a😀", "a")).isEqualTo(new KeyboardEdit(1, ""));
    }
}
