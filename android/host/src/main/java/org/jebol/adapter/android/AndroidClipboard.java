package org.jebol.adapter.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import org.jebol.domain.host.ClipboardPort;

import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class AndroidClipboard implements ClipboardPort {

    private static final String NOTHING_COPIED = "";

    private static final String LABEL = "jebol";

    private final Context context;

    private final ClipboardManager clipboard;

    private final BooleanSupplier inFront;

    public AndroidClipboard(Context context, BooleanSupplier inFront) {
        this.context = context;
        this.clipboard = context.getSystemService(ClipboardManager.class);
        this.inFront = inFront;
    }

    @Override
    public Optional<String> read() {
        if (!inFront.getAsBoolean()) {
            return Optional.empty();
        }
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return Optional.of(NOTHING_COPIED);
            }
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return Optional.of(text == null ? NOTHING_COPIED : text.toString());
        } catch (RuntimeException unreadable) {
            throw new Unreachable("the clipboard could not be read: " + unreadable.getMessage());
        }
    }

    @Override
    public boolean write(String text) {
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(LABEL, text));
            return true;
        } catch (RuntimeException unwritable) {
            throw new Unreachable("the clipboard could not be written: " + unwritable.getMessage());
        }
    }
}
