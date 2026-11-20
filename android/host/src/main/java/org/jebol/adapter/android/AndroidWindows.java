package org.jebol.adapter.android;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.WindowPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AndroidWindows implements WindowPort {

    private static final Pattern URL_WITH_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    private static final String UNSUGGESTED_SAVE_NAME = "untitled";
    private static final String UNKNOWN_TYPE = "application/octet-stream";
    private static final int GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
    private static final int KEPT_GRANTS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private final ActivityRequests requests;
    private final FilePort files;
    private final DocumentFiles documents;
    private final PickedDocuments picks;

    public AndroidWindows(ActivityRequests requests, FilePort files, DocumentFiles documents, PickedDocuments picks) {
        this.requests = requests;
        this.files = files;
        this.documents = documents;
        this.picks = picks;
    }

    @Override
    public boolean browse(String target) {
        Intent view = URL_WITH_SCHEME.matcher(target).matches()
                ? new Intent(Intent.ACTION_VIEW, Uri.parse(target))
                : intentToViewFile(target);
        requests.start(view);
        return true;
    }

    @Override
    public List<String> chooseFiles(boolean forSaving, boolean allowingMany, Optional<String> suggestedName,
            Optional<String> title, List<String> filterPairs) {
        Intent request = forSaving
                ? saveRequest(suggestedName, filterPairs)
                : openRequest(allowingMany, filterPairs);
        initialUri(suggestedName).ifPresent(start -> request.putExtra(DocumentsContract.EXTRA_INITIAL_URI, start));
        Optional<Intent> answer = requests.startForResult(request);
        if (answer.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        for (Uri chosen : chosenUris(answer.get())) {
            keepGrant(chosen);
            kept.add(picks.keep(chosen.toString(), queryDisplayName(chosen), false).path());
        }
        return allowingMany || kept.isEmpty() ? kept : kept.subList(0, 1);
    }

    @Override
    public Optional<String> chooseDirectory(Optional<String> startingAt, Optional<String> title) {
        Intent request = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(GRANTS);
        initialUri(startingAt).ifPresent(start -> request.putExtra(DocumentsContract.EXTRA_INITIAL_URI, start));
        return requests.startForResult(request)
                .map(Intent::getData)
                .map(tree -> {
                    keepGrant(tree);
                    Uri root = DocumentsContract.buildDocumentUriUsingTree(tree,
                            DocumentsContract.getTreeDocumentId(tree));
                    return picks.keep(tree.toString(), queryDisplayName(root), true).path();
                });
    }

    @Override
    public Optional<int[]> chooseColour(Optional<int[]> suggested) {
        return requests.showDialog((activity, answer) ->
                ColourDialog.show(activity, suggested.orElse(new int[] {255, 255, 255}), answer));
    }

    @Override
    public Optional<String> askForPassword() {
        return requests.showDialog(PasswordDialog::show);
    }

    @Override
    public boolean hasOwnPasswordDialog() {
        return true;
    }

    private Intent intentToViewFile(String target) {
        String location = locate(target);
        Uri offered = location.startsWith(PickedDocuments.MOUNT_FOLDER)
                ? documents.referenceAt(location).orElseThrow(() -> refuseMissingFile(target))
                : offerCopy(target, location);
        String type = Optional.ofNullable(resolver().getType(offered)).orElse(PickerFilter.mimeTypeForName(location));
        return new Intent(Intent.ACTION_VIEW).setDataAndType(offered, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private Uri offerCopy(String target, String location) {
        byte[] contents;
        try {
            contents = files.readBytes(target);
        } catch (FilePort.Denied missing) {
            throw refuseMissingFile(target);
        }
        return BrowsedFiles.offer(context(), lastName(location), contents);
    }

    private Denied refuseMissingFile(String target) {
        return new Denied("cannot-open", "there is no " + target + " to open");
    }

    private Intent saveRequest(Optional<String> suggestedName, List<String> filterPairs) {
        String name = suggestedName.map(AndroidWindows::lastName).filter(given -> !given.isEmpty())
                .orElse(UNSUGGESTED_SAVE_NAME);
        String type = suggestedName.isPresent() && name.contains(".")
                ? PickerFilter.mimeTypeForName(name)
                : singleType(filterPairs);
        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(type)
                .putExtra(Intent.EXTRA_TITLE, name)
                .addFlags(GRANTS);
    }

    private static String singleType(List<String> filterPairs) {
        List<String> types = PickerFilter.mimeTypes(filterPairs);
        return types.size() == 1 && !types.getFirst().equals(PickerFilter.EVERYTHING) ? types.getFirst() : UNKNOWN_TYPE;
    }

    private static Intent openRequest(boolean allowingMany, List<String> filterPairs) {
        List<String> types = PickerFilter.mimeTypes(filterPairs);
        Intent request = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(types.size() == 1 ? types.getFirst() : PickerFilter.EVERYTHING)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowingMany)
                .addFlags(GRANTS);
        if (types.size() > 1) {
            request.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
        }
        return request;
    }

    private Optional<Uri> initialUri(Optional<String> path) {
        return path.map(this::locate)
                .filter(location -> location.startsWith(PickedDocuments.MOUNT_FOLDER))
                .flatMap(location -> documents.folderReferenceAt(location)
                        .or(() -> documents.folderReferenceAt(parentPath(location))));
    }

    private String locate(String target) {
        try {
            return files.locationFor(target);
        } catch (FilePort.Denied unresolved) {
            return target;
        }
    }

    private static List<Uri> chosenUris(Intent answer) {
        List<Uri> chosen = new ArrayList<>();
        ClipData many = answer.getClipData();
        if (many != null) {
            for (int at = 0; at < many.getItemCount(); at++) {
                Optional.ofNullable(many.getItemAt(at).getUri()).ifPresent(chosen::add);
            }
        }
        if (chosen.isEmpty()) {
            Optional.ofNullable(answer.getData()).ifPresent(chosen::add);
        }
        return chosen;
    }

    private void keepGrant(Uri chosen) {
        try {
            resolver().takePersistableUriPermission(chosen, KEPT_GRANTS);
        } catch (SecurityException noWriteGrant) {
            try {
                resolver().takePersistableUriPermission(chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException notPersistable) {
                return;
            }
        }
    }

    private String queryDisplayName(Uri document) {
        try (Cursor row = resolver().query(document, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (row != null && row.moveToFirst() && !row.isNull(0)) {
                return safeName(row.getString(0));
            }
        } catch (RuntimeException unreadable) {
            return safeName(document.getLastPathSegment());
        }
        return safeName(document.getLastPathSegment());
    }

    private static String safeName(String given) {
        String name = Optional.ofNullable(given).orElse("").replace('/', '_').replace('\\', '_').trim();
        return name.isEmpty() || name.equals(".") || name.equals("..") ? UNSUGGESTED_SAVE_NAME : name;
    }

    private Context context() {
        return requests.activity().getApplicationContext();
    }

    private ContentResolver resolver() {
        return context().getContentResolver();
    }

    private static String parentPath(String path) {
        String bare = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return bare.substring(0, bare.lastIndexOf('/') + 1);
    }

    private static String lastName(String path) {
        String bare = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return bare.substring(bare.lastIndexOf('/') + 1);
    }
}
