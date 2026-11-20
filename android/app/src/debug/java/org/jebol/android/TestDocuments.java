package org.jebol.android;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class TestDocuments extends DocumentsProvider {

    public static final String AUTHORITY = "org.jebol.android.testdocuments";

    private static final String ROOT_ID = "root";
    private static final String FOLDER = "test-documents";
    private static final String READ_ONLY = "read-only";
    private static final String UNSIZED = "unsized";

    private static final String[] ROOT_COLUMNS = {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS};
    private static final String[] DOCUMENT_COLUMNS = {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS};

    public static Path folder(Context context) {
        return new File(context.getCacheDir(), FOLDER).toPath();
    }

    public static String idFor(String relative) {
        return relative.isEmpty() ? ROOT_ID : ROOT_ID + "/" + relative;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor roots = new MatrixCursor(projection == null ? ROOT_COLUMNS : projection);
        roots.newRow()
                .add(Root.COLUMN_ROOT_ID, ROOT_ID)
                .add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
                .add(Root.COLUMN_TITLE, "JEBOL test documents")
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        return roots;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor answer = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        addRow(answer, documentId);
        return answer;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor answer = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        Path parent = fileFor(parentDocumentId);
        try (Stream<Path> children = Files.list(parent)) {
            for (Path child : children.sorted().toList()) {
                addRow(answer, parentDocumentId + "/" + child.getFileName());
            }
        } catch (IOException unreadable) {
            throw new FileNotFoundException(parentDocumentId);
        }
        return answer;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        Path file = fileFor(documentId);
        if (!"r".equals(mode) && isReadOnly(documentId)) {
            throw new SecurityException(documentId + " may only be read");
        }
        return ParcelFileDescriptor.open(file.toFile(), ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        if (isReadOnly(parentDocumentId)) {
            throw new UnsupportedOperationException(parentDocumentId + " takes no new documents");
        }
        Path made = fileFor(parentDocumentId).resolve(displayName);
        try {
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                Files.createDirectory(made);
            } else {
                Files.createFile(made);
            }
        } catch (IOException unmade) {
            throw new IllegalStateException("cannot make " + displayName, unmade);
        }
        return parentDocumentId + "/" + displayName;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        if (isReadOnly(documentId)) {
            throw new UnsupportedOperationException(documentId + " cannot be deleted");
        }
        try (Stream<Path> held = Files.walk(fileFor(documentId))) {
            for (Path each : held.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        } catch (IOException undeleted) {
            throw new IllegalStateException("cannot delete " + documentId, undeleted);
        }
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        if (isReadOnly(documentId)) {
            throw new UnsupportedOperationException(documentId + " cannot be renamed");
        }
        Path from = fileFor(documentId);
        try {
            Files.move(from, from.resolveSibling(displayName));
        } catch (IOException unmoved) {
            throw new IllegalStateException("cannot rename " + documentId, unmoved);
        }
        return documentId.substring(0, documentId.lastIndexOf('/') + 1) + displayName;
    }

    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        Path from = fileFor(sourceDocumentId);
        try {
            Files.move(from, fileFor(targetParentDocumentId).resolve(from.getFileName()));
        } catch (IOException unmoved) {
            throw new IllegalStateException("cannot move " + sourceDocumentId, unmoved);
        }
        return targetParentDocumentId + "/" + from.getFileName();
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return documentId.startsWith(parentDocumentId + "/");
    }

    private void addRow(MatrixCursor answer, String documentId) throws FileNotFoundException {
        Path file = fileFor(documentId);
        boolean isFolder = Files.isDirectory(file);
        String name = documentId.equals(ROOT_ID) ? FOLDER : file.getFileName().toString();
        int flags = isReadOnly(documentId) ? 0
                : isFolder ? Document.FLAG_DIR_SUPPORTS_CREATE | Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_RENAME | Document.FLAG_SUPPORTS_MOVE
                : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME
                        | Document.FLAG_SUPPORTS_MOVE;
        boolean isUnsized = name.startsWith(UNSIZED);
        answer.newRow()
                .add(Document.COLUMN_DOCUMENT_ID, documentId)
                .add(Document.COLUMN_DISPLAY_NAME, name)
                .add(Document.COLUMN_MIME_TYPE, isFolder ? Document.MIME_TYPE_DIR : "application/octet-stream")
                .add(Document.COLUMN_SIZE, isFolder || isUnsized ? null : file.toFile().length())
                .add(Document.COLUMN_LAST_MODIFIED, isUnsized ? null : file.toFile().lastModified())
                .add(Document.COLUMN_FLAGS, flags);
    }

    private static boolean isReadOnly(String documentId) {
        return documentId.contains(READ_ONLY);
    }

    private Path fileFor(String documentId) throws FileNotFoundException {
        if (!documentId.equals(ROOT_ID) && !documentId.startsWith(ROOT_ID + "/")) {
            throw new FileNotFoundException(documentId);
        }
        Path folder = folder(getContext()).normalize();
        Path file = folder.resolve(documentId.substring(ROOT_ID.length()).replaceFirst("^/", "")).normalize();
        if (!file.startsWith(folder) || !Files.exists(file)) {
            throw new FileNotFoundException(documentId);
        }
        return file;
    }
}
