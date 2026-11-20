package org.jebol.adapter.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class PasswordDialog {

    public static final String FIELD_DESCRIPTION = "password";

    private static final String TITLE = "Password";
    private static final int MARGIN_DP = 20;

    private PasswordDialog() {
    }

    public static void show(Activity activity, CompletableFuture<Optional<String>> answer) {
        EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setContentDescription(FIELD_DESCRIPTION);
        FrameLayout padded = new FrameLayout(activity);
        int margin = Math.round(MARGIN_DP * activity.getResources().getDisplayMetrics().density);
        padded.setPadding(margin, margin / 2, margin, 0);
        padded.addView(field);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(TITLE)
                .setView(padded)
                .setPositiveButton(android.R.string.ok, (shown, which) ->
                        answer.complete(Optional.of(field.getText().toString())))
                .setNegativeButton(android.R.string.cancel, (shown, which) -> answer.complete(Optional.empty()))
                .setOnCancelListener(shown -> answer.complete(Optional.empty()))
                .setOnDismissListener(shown -> answer.complete(Optional.empty()))
                .create();
        answer.whenComplete((given, failed) -> activity.runOnUiThread(() -> {
            field.getText().clear();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }));
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        field.requestFocus();
    }
}
