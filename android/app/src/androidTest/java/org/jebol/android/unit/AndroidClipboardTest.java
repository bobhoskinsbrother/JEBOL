package org.jebol.android.unit;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.adapter.android.AndroidClipboard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidClipboardTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    @DisplayName("read while the app is not in front, the clipboard answers nothing rather than empty")
    void theBackgroundReadsNothing() {
        assertThat(new AndroidClipboard(context, () -> false).read()).isEmpty();
    }

    @Test
    @DisplayName("a write is Android's to allow from anywhere, and is reported as done")
    void aWriteIsDone() {
        assertThat(new AndroidClipboard(context, () -> false).write("from the background")).isTrue();
    }
}
