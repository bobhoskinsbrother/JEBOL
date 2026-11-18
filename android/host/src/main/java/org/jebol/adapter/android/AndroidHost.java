package org.jebol.adapter.android;

import android.content.Context;
import android.security.NetworkSecurityPolicy;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.adapter.host.FileSystemPort;
import org.jebol.adapter.host.JavaCertificateAuthority;
import org.jebol.adapter.host.JavaSecureTransport;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.adapter.host.ThreadTasks;
import org.jebol.adapter.host.WakeablePause;
import org.jebol.application.Bounds;
import org.jebol.application.LayeredFiles;
import org.jebol.application.ThreadPause;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.JvmDigests;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class AndroidHost {

    private static final String STORE_FOLDER = "jebol";

    private static final String TEMPORARY_FOLDER = "tmp";

    private static final long CEILING_PARTS_OF_HEAP = 3;

    private static final long HEAP_PARTS = 4;

    public HostPorts ports() {
        return HostPorts.none()
                .withCarriedFiles(new ClasspathFiles())
                .withDigests(new JvmDigests())
                .withMachine(new AndroidMachine())
                .withPause(new ThreadPause());
    }

    public static HostPorts appPorts(Context context, FilePort carriedApp, BooleanSupplier inFront, Bounds bounds) {
        FileSystemPort store = FileSystemPort.rootFileSystem(storeFolder(context));
        AndroidEnvironment environment = new AndroidEnvironment(context.getPackageName(), Locale.getDefault());
        return appPortsOn(store, carriedApp, environment, context, inFront, bounds);
    }

    private static HostPorts appPortsOn(FileSystemPort store, FilePort carriedApp, AndroidEnvironment environment,
            Context context, BooleanSupplier inFront, Bounds bounds) {

        return new AndroidHost().ports()
                .withFiles(new LayeredFiles(store, carriedApp))
                .withEnvironment(environment)
                .withImages(new AndroidImage())
                .withClipboard(new AndroidClipboard(context, inFront))
                .withNetwork(new JavaSocket(new AndroidHostNames(),
                        host -> NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)))
                .withSecureTransport(new JavaSecureTransport())
                .withCertificateAuthority(new JavaCertificateAuthority())
                .withPause(new WakeablePause())
                .withTasks(new ThreadTasks(bounds, () -> appPortsOn(store.copyInWorkingDirectory(), carriedApp,
                        environment.copy(), context, inFront, bounds)));
    }

    public static long memoryCeiling() {
        return Runtime.getRuntime().maxMemory() / HEAP_PARTS * CEILING_PARTS_OF_HEAP;
    }

    public static Path storeFolder(Context context) {
        Path folder = new File(context.getFilesDir(), STORE_FOLDER).toPath();
        try {
            return Files.createDirectories(folder);
        } catch (IOException unmade) {
            throw new UncheckedIOException(unmade);
        }
    }

    public static void emptyTemporaryFolder(Context context) {
        Path temporary = storeFolder(context).resolve(TEMPORARY_FOLDER);
        try {
            if (Files.exists(temporary)) {
                try (Stream<Path> held = Files.walk(temporary)) {
                    for (Path each : held.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(each);
                    }
                }
            }
            Files.createDirectories(temporary);
        } catch (IOException unemptied) {
            throw new UncheckedIOException(unemptied);
        }
    }
}
