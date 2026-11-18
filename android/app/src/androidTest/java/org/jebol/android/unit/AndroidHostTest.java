package org.jebol.android.unit;

import android.content.Context;
import android.security.NetworkSecurityPolicy;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.adapter.android.AndroidHost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidHostTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    @DisplayName("the store is a folder in the app's own private files")
    void theStoreIsPrivate() {
        Path store = AndroidHost.storeFolder(context);

        assertThat(store).isDirectory();
        assertThat(store.startsWith(context.getFilesDir().toPath())).isTrue();
    }

    @Test
    @DisplayName("the temporary folder is in the store, and starts empty")
    void theTemporaryFolderStartsEmpty() throws IOException {
        Path temporary = AndroidHost.storeFolder(context).resolve("tmp");
        Files.createDirectories(temporary.resolve("left/behind"));
        Files.write(temporary.resolve("left/behind/file.txt"), new byte[] {1, 2, 3});

        AndroidHost.emptyTemporaryFolder(context);

        assertThat(temporary).isEmptyDirectory();
    }

    @Test
    @DisplayName("the memory ceiling is three quarters of the heap Android gives the app")
    void theCeilingIsBelowTheHeap() {
        assertThat(AndroidHost.memoryCeiling()).isEqualTo(Runtime.getRuntime().maxMemory() / 4 * 3);
    }

    @Test
    @DisplayName("the debug build's policy allows cleartext to the host machine and localhost, and nowhere else")
    void theDebugPolicyAllowsOnlyTheHost() {
        NetworkSecurityPolicy policy = NetworkSecurityPolicy.getInstance();

        assertThat(policy.isCleartextTrafficPermitted("10.0.2.2")).isTrue();
        assertThat(policy.isCleartextTrafficPermitted("localhost")).isTrue();
        assertThat(policy.isCleartextTrafficPermitted("example.com")).isFalse();
        assertThat(policy.isCleartextTrafficPermitted("127.0.0.1")).isFalse();
    }
}
