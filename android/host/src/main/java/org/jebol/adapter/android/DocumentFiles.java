package org.jebol.adapter.android;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import org.jebol.adapter.android.PickedDocuments.Pick;
import org.jebol.domain.host.FileInformation;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Value;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

public final class DocumentFiles implements FilePort {

    private static final String[] COLUMNS = {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};

    private static final String READ = "r";
    private static final String WRITE_TRUNCATING = "wt";
    private static final String READ_AND_WRITE = "rw";
    private static final String APPEND = "wa";

    private final ContentResolver resolver;
    private final PickedDocuments picks;

    public DocumentFiles(ContentResolver resolver, PickedDocuments picks) {
        this.resolver = resolver;
        this.picks = picks;
    }

    private enum Kind { MOUNT, PICK, DOCUMENT, MISSING }

    private record Found(String document, String name, boolean isFolder, Optional<Long> size,
            Optional<Long> modified) {
    }

    private record Place(Kind kind, String path, Optional<Pick> pick, Optional<Uri> uri, Optional<Uri> tree,
            Optional<Found> found, int depth) {

        static Place missing(String path) {
            return new Place(Kind.MISSING, path, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), 0);
        }

        boolean isFolder() {
            return kind == Kind.MOUNT || kind == Kind.PICK || found.map(Found::isFolder).orElse(false);
        }

        boolean isFile() {
            return kind == Kind.DOCUMENT && !isFolder();
        }

        boolean isPickItself() {
            return kind == Kind.DOCUMENT && depth == 0;
        }

        Uri requireUri() {
            return uri.orElseThrow();
        }
    }

    public Optional<Uri> referenceAt(String absolute) {
        return locate(absolute).uri;
    }

    public Optional<Uri> folderReferenceAt(String absolute) {
        Place place = locate(absolute);
        return place.isFolder() ? place.uri : Optional.empty();
    }

    @Override
    public byte[] readBytes(String path) {
        Place place = locate(path);
        if (!place.isFile()) {
            throw new Denied("cannot-open", "cannot read " + path, path);
        }
        try (InputStream in = resolver.openInputStream(place.requireUri())) {
            if (in == null) {
                throw new Denied("cannot-open", "cannot read " + path, path);
            }
            return in.readAllBytes();
        } catch (IOException | RuntimeException refused) {
            throw asDenied("cannot-open", "cannot read " + path, path, refused);
        }
    }

    @Override
    public void write(String path, byte[] contents) {
        Uri target = findOrCreateFile(path);
        try (ParcelFileDescriptor opened = openDescriptor(target, WRITE_TRUNCATING, path);
                FileOutputStream out = new FileOutputStream(opened.getFileDescriptor())) {
            out.write(contents);
            out.flush();
            truncateWherePossible(out.getChannel(), contents.length);
        } catch (IOException | RuntimeException refused) {
            throw asDenied("cannot-open", "cannot write " + path, path, refused);
        }
    }

    @Override
    public void writeAt(String path, long position, byte[] contents) {
        Uri target = findOrCreateFile(path);
        try (ParcelFileDescriptor opened = openDescriptor(target, READ_AND_WRITE, path);
                FileOutputStream out = new FileOutputStream(opened.getFileDescriptor())) {
            FileChannel channel = out.getChannel();
            ByteBuffer remaining = ByteBuffer.wrap(contents);
            long at = position;
            while (remaining.hasRemaining()) {
                at += channel.write(remaining, at);
            }
        } catch (IOException | RuntimeException refused) {
            throw asDenied("cannot-open", "cannot write " + path
                    + " at a position: its provider gives no file that can be moved about in", path, refused);
        }
    }

    @Override
    public void appendTo(String path, byte[] contents) {
        Uri target = findOrCreateFile(path);
        try (ParcelFileDescriptor opened = openDescriptor(target, APPEND, path);
                OutputStream out = new FileOutputStream(opened.getFileDescriptor())) {
            out.write(contents);
        } catch (IOException | RuntimeException refused) {
            throw asDenied("cannot-open", "cannot append to " + path, path, refused);
        }
    }

    @Override
    public boolean exists(String path) {
        Place place = locate(path);
        if (place.kind == Kind.MISSING) {
            return false;
        }
        return !path.endsWith("/") || place.isFolder();
    }

    @Override
    public String workingDirectory() {
        return PickedDocuments.MOUNT_FOLDER;
    }

    @Override
    public String hostPath(String path) {
        return locate(path).uri.map(Uri::toString).orElse(path);
    }

    @Override
    public Optional<String> findScriptPath(String hostPath) {
        return Optional.empty();
    }

    @Override
    public void changeDirectory(String path) {
        if (!locate(path).isFolder()) {
            throw new Denied("cannot-open", path + " is not a directory", path);
        }
    }

    @Override
    public void makeDirectory(String path, boolean andItsParents) {
        Place place = locate(path);
        if (place.isFolder()) {
            return;
        }
        if (place.kind != Kind.MISSING) {
            throw new Denied("no-create", "cannot make a directory at " + path, path, Optional.empty());
        }
        String parent = parentPath(path);
        if (andItsParents && locate(parent).kind == Kind.MISSING) {
            makeDirectory(parent, true);
        }
        create(path, Document.MIME_TYPE_DIR, "cannot make a directory at " + path);
    }

    @Override
    public boolean delete(String path) {
        Place place = locate(path);
        if (place.kind == Kind.MISSING) {
            return false;
        }
        if (place.kind != Kind.DOCUMENT || (path.endsWith("/") && !place.isFolder())
                || (place.isFolder() && !children(place).isEmpty())) {
            throw new Denied("no-delete", "cannot delete " + path, path);
        }
        try {
            if (!DocumentsContract.deleteDocument(resolver, place.requireUri())) {
                throw new Denied("no-delete", "cannot delete " + path, path);
            }
        } catch (IOException | RuntimeException refused) {
            throw asDenied("no-delete", "cannot delete " + path, path, refused);
        }
        if (place.isPickItself()) {
            picks.forget(place.pick.orElseThrow().number());
        }
        return true;
    }

    @Override
    public void rename(String from, String to) {
        Place source = locate(from);
        if (source.kind != Kind.DOCUMENT || locate(to).kind != Kind.MISSING) {
            throw new Denied("cannot-open", "cannot rename " + from);
        }
        String newName = lastName(to);
        try {
            Uri moved = parentPath(from).equals(parentPath(to))
                    ? source.requireUri()
                    : moveDocument(source, from, to);
            Uri renamed = lastName(from).equals(newName) ? moved
                    : Optional.ofNullable(DocumentsContract.renameDocument(resolver, moved, newName)).orElse(moved);
            if (source.isPickItself()) {
                Pick pick = source.pick.orElseThrow();
                picks.rename(pick.number(), pick.isFolder() ? pick.reference() : renamed.toString(), newName);
            }
        } catch (IOException | RuntimeException refused) {
            throw asDenied("cannot-open", "cannot rename " + from, "", refused);
        }
    }

    @Override
    public List<String> listFileNames(String path) {
        Place place = locate(path);
        return switch (place.kind) {
            case MOUNT -> picks.all().stream().map(pick -> pick.number() + "/").sorted().toList();
            case PICK -> {
                Pick pick = place.pick.orElseThrow();
                yield List.of(pick.name() + (pick.isFolder() ? "/" : ""));
            }
            case DOCUMENT -> {
                if (!place.isFolder()) {
                    throw new Denied("cannot-open", "cannot read the directory at " + path, path);
                }
                TreeSet<String> names = new TreeSet<>();
                children(place).forEach(child -> names.add(child.name() + (child.isFolder() ? "/" : "")));
                yield List.copyOf(names);
            }
            default -> throw new Denied("cannot-open", "cannot read the directory at " + path, path);
        };
    }

    @Override
    public boolean isDirectory(String path) {
        Place place = locate(path);
        return place.kind != Kind.MISSING && place.isFolder();
    }

    @Override
    public Value resolvedFile(String path) {
        return exists(path) ? FileValue.of(locationFor(path)) : NoneValue.none();
    }

    @Override
    public String locationFor(String path) {
        return path.endsWith("/") || isDirectory(path) ? asDirectory(path) : path;
    }

    @Override
    public Optional<FileInformation> describe(String path) {
        Place place = locate(path);
        if (place.kind == Kind.MISSING || (path.endsWith("/") && !place.isFolder())) {
            return Optional.empty();
        }
        Optional<Instant> modified = place.found.flatMap(Found::modified).map(Instant::ofEpochMilli);
        if (place.isFolder()) {
            return Optional.of(FileInformation.describeDirectory(asDirectory(path), modified,
                    Optional.empty(), Optional.empty()));
        }
        Optional<Long> size = place.found.flatMap(Found::size);
        return Optional.of(new FileInformation(path, false, size, modified, Optional.empty(), Optional.empty()));
    }

    private Place locate(String path) {
        List<String> names = namesUnderMount(path);
        if (names.isEmpty()) {
            if (picks.all().isEmpty()) {
                return Place.missing(path);
            }
            return new Place(Kind.MOUNT, path, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), 0);
        }
        Optional<Pick> pick = findPick(names.getFirst());
        if (pick.isEmpty()) {
            return Place.missing(path);
        }
        if (names.size() == 1) {
            return new Place(Kind.PICK, path, pick, Optional.empty(), Optional.empty(), Optional.empty(), 0);
        }
        Pick picked = pick.get();
        if (!names.get(1).equals(picked.name())) {
            return Place.missing(path);
        }
        Uri reference = Uri.parse(picked.reference());
        if (!picked.isFolder()) {
            return names.size() == 2 ? queryPlace(path, pick, reference, Optional.empty(), 0) : Place.missing(path);
        }
        Optional<Uri> tree = Optional.of(reference);
        Uri rootDocument;
        try {
            rootDocument = DocumentsContract.buildDocumentUriUsingTree(reference,
                    DocumentsContract.getTreeDocumentId(reference));
        } catch (RuntimeException notATree) {
            return Place.missing(path);
        }
        Place walked = queryPlace(path, pick, rootDocument, tree, 0);
        for (int at = 2; at < names.size() && walked.kind == Kind.DOCUMENT; at++) {
            walked = childNamed(walked, names.get(at), path, at - 1);
        }
        return walked;
    }

    private Place queryPlace(String path, Optional<Pick> pick, Uri uri, Optional<Uri> tree, int depth) {
        try (Cursor row = resolver.query(uri, COLUMNS, null, null, null)) {
            if (row == null || !row.moveToFirst()) {
                return Place.missing(path);
            }
            return new Place(Kind.DOCUMENT, path, pick, Optional.of(uri), tree, Optional.of(readRow(row)), depth);
        } catch (RuntimeException unreachable) {
            return Place.missing(path);
        }
    }

    private Place childNamed(Place parent, String name, String path, int depth) {
        if (!parent.isFolder()) {
            return Place.missing(path);
        }
        Uri tree = parent.tree.orElseThrow();
        for (Found child : children(parent)) {
            if (child.name().equals(name)) {
                return new Place(Kind.DOCUMENT, path, parent.pick,
                        Optional.of(DocumentsContract.buildDocumentUriUsingTree(tree, child.document())),
                        parent.tree, Optional.of(child), depth);
            }
        }
        return Place.missing(path);
    }

    private List<Found> children(Place folder) {
        Uri tree = folder.tree.orElseThrow();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                folder.found.orElseThrow().document());
        List<Found> found = new ArrayList<>();
        try (Cursor rows = resolver.query(children, COLUMNS, null, null, null)) {
            while (rows != null && rows.moveToNext()) {
                found.add(readRow(rows));
            }
        } catch (RuntimeException unreadable) {
            throw new Denied("cannot-open", "cannot read the directory at " + folder.path, folder.path);
        }
        return found;
    }

    private static Found readRow(Cursor row) {
        return new Found(
                row.getString(0),
                row.getString(1),
                Document.MIME_TYPE_DIR.equals(row.getString(2)),
                row.isNull(3) ? Optional.empty() : Optional.of(row.getLong(3)),
                row.isNull(4) ? Optional.empty() : Optional.of(row.getLong(4)));
    }

    private Uri findOrCreateFile(String path) {
        Place place = locate(path);
        if (place.isFile()) {
            return place.requireUri();
        }
        if (place.kind != Kind.MISSING) {
            throw new Denied("cannot-open", "cannot write " + path, path);
        }
        return create(path, PickerFilter.mimeTypeForName(lastName(path)), "cannot write " + path);
    }

    private Uri create(String path, String mimeType, String because) {
        Place parent = locate(parentPath(path));
        if (parent.kind != Kind.DOCUMENT || !parent.isFolder()) {
            throw new Denied("cannot-open", because, path);
        }
        try {
            Uri made = DocumentsContract.createDocument(resolver, parent.requireUri(), mimeType, lastName(path));
            if (made == null) {
                throw new Denied("cannot-open", because, path);
            }
            return made;
        } catch (IOException | RuntimeException refused) {
            throw asDenied("cannot-open", because, path, refused);
        }
    }

    private Uri moveDocument(Place source, String from, String to) throws IOException {
        Place targetFolder = locate(parentPath(to));
        Place sourceFolder = locate(parentPath(from));
        if (targetFolder.kind != Kind.DOCUMENT || !targetFolder.isFolder()
                || sourceFolder.kind != Kind.DOCUMENT || source.isPickItself()) {
            throw new Denied("cannot-open", "cannot rename " + from);
        }
        Uri moved = DocumentsContract.moveDocument(resolver, source.requireUri(), sourceFolder.requireUri(),
                targetFolder.requireUri());
        if (moved == null) {
            throw new Denied("cannot-open", "cannot rename " + from);
        }
        return moved;
    }

    private ParcelFileDescriptor openDescriptor(Uri target, String mode, String path) throws IOException {
        ParcelFileDescriptor opened = resolver.openFileDescriptor(target, mode);
        if (opened == null) {
            throw new Denied("cannot-open", "cannot open " + path, path);
        }
        return opened;
    }

    private static void truncateWherePossible(FileChannel channel, long size) {
        try {
            channel.truncate(size);
        } catch (IOException notAFile) {
            return;
        }
    }

    private Optional<Pick> findPick(String written) {
        try {
            return picks.find(Long.parseLong(written));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static List<String> namesUnderMount(String path) {
        String under = path.startsWith(PickedDocuments.MOUNT_FOLDER)
                ? path.substring(PickedDocuments.MOUNT_FOLDER.length())
                : "";
        List<String> names = new ArrayList<>();
        for (String name : under.split("/")) {
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private static String parentPath(String path) {
        String bare = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return bare.substring(0, bare.lastIndexOf('/') + 1);
    }

    private static String lastName(String path) {
        String bare = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return bare.substring(bare.lastIndexOf('/') + 1);
    }

    private static String asDirectory(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    private static Denied asDenied(String errorId, String because, String path, Exception refused) {
        if (refused instanceof Denied denied) {
            return denied;
        }
        String reason = Optional.ofNullable(refused.getMessage()).map(message -> because + ": " + message)
                .orElse(because);
        return path.isEmpty() ? new Denied(errorId, reason) : new Denied(errorId, reason, path);
    }
}
