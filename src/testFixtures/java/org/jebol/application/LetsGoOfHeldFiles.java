package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class LetsGoOfHeldFiles implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback,
        AfterAllCallback {

    private static final ThreadLocal<Deque<List<FileSystemPort>>> MADE_IN_EACH_SCOPE =
            ThreadLocal.withInitial(ArrayDeque::new);

    public static FileSystemPort rootFileSystem(Path root) {
        FileSystemPort made = FileSystemPort.rootFileSystem(root);
        Deque<List<FileSystemPort>> scopes = MADE_IN_EACH_SCOPE.get();
        if (!scopes.isEmpty()) {
            scopes.peek().add(made);
        }
        return made;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        MADE_IN_EACH_SCOPE.get().push(new ArrayList<>());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        MADE_IN_EACH_SCOPE.get().push(new ArrayList<>());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        letGoOfTheLastScope();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        letGoOfTheLastScope();
    }

    private static void letGoOfTheLastScope() {
        Deque<List<FileSystemPort>> scopes = MADE_IN_EACH_SCOPE.get();
        if (!scopes.isEmpty()) {
            scopes.pop().forEach(FileSystemPort::letGoOfEveryHeldFile);
        }
    }
}
