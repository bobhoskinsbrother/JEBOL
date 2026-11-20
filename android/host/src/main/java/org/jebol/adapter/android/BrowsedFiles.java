package org.jebol.adapter.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class BrowsedFiles extends ContentProvider {

    private static final String FOLDER = "browsed";
    private static final String AUTHORITY_SUFFIX = ".browsed";

    public static Uri offer(Context context, String name, byte[] contents) {
        Path folder = folder(context);
        try {
            long next;
            try (Stream<Path> offered = Files.list(folder)) {
                next = offered.map(each -> each.getFileName().toString())
                        .filter(each -> each.chars().allMatch(Character::isDigit))
                        .mapToLong(Long::parseLong).max().orElse(0) + 1;
            }
            Path offeredFolder = Files.createDirectories(folder.resolve(Long.toString(next)));
            Files.write(offeredFolder.resolve(name), contents);
            return new Uri.Builder().scheme("content").authority(authority(context))
                    .appendPath(Long.toString(next)).appendPath(name).build();
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }

    public static void forgetAll(Context context) {
        Path folder = folder(context);
        try (Stream<Path> held = Files.walk(folder)) {
            for (Path each : held.sorted(Comparator.reverseOrder()).toList()) {
                if (!each.equals(folder)) {
                    Files.delete(each);
                }
            }
        } catch (IOException unemptied) {
            throw new UncheckedIOException(unemptied);
        }
    }

    private static String authority(Context context) {
        return context.getPackageName() + AUTHORITY_SUFFIX;
    }

    private static Path folder(Context context) {
        try {
            return Files.createDirectories(new File(context.getCacheDir(), FOLDER).toPath());
        } catch (IOException unmade) {
            throw new UncheckedIOException(unmade);
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new SecurityException("a file JEBOL offers for viewing may only be read");
        }
        return ParcelFileDescriptor.open(fileAt(uri).orElseThrow(() -> new FileNotFoundException(uri.toString())),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = fileAt(uri).orElse(null);
        String[] columns = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor answer = new MatrixCursor(columns);
        if (file == null) {
            return answer;
        }
        Object[] row = new Object[columns.length];
        for (int at = 0; at < columns.length; at++) {
            row[at] = switch (columns[at]) {
                case OpenableColumns.DISPLAY_NAME -> file.getName();
                case OpenableColumns.SIZE -> file.length();
                default -> null;
            };
        }
        answer.addRow(row);
        return answer;
    }

    @Override
    public String getType(Uri uri) {
        return PickerFilter.mimeTypeForName(Optional.ofNullable(uri.getLastPathSegment()).orElse(""));
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("a file JEBOL offers for viewing cannot be added to");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("a file JEBOL offers for viewing cannot be deleted");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("a file JEBOL offers for viewing cannot be changed");
    }

    private Optional<File> fileAt(Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (segments.size() != 2 || !segments.getFirst().chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        Path folder = folder(getContext()).normalize();
        Path file = folder.resolve(segments.get(0)).resolve(segments.get(1)).normalize();
        if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file.toFile());
    }
}
