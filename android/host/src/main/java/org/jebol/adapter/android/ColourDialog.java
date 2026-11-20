package org.jebol.adapter.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ColourDialog {

    public static final String SWATCH_DESCRIPTION = "swatch";
    public static final String HUE_DESCRIPTION = "hue";
    public static final String SATURATION_DESCRIPTION = "saturation";
    public static final String VALUE_DESCRIPTION = "value";
    public static final String HEX_DESCRIPTION = "hex";

    private static final String TITLE = "Colour";
    private static final int MOST_HUE = 360;
    private static final int MOST_PERCENT = 100;
    private static final int SWATCH_HEIGHT_DP = 64;
    private static final int MARGIN_DP = 20;
    private static final int HEX_LENGTH_WITH_HASH = 7;

    private final View swatch;
    private final SeekBar hue;
    private final SeekBar saturation;
    private final SeekBar value;
    private final EditText hex;
    private int[] colour;
    private boolean isShowingAColour;

    private ColourDialog(Activity activity, int[] start) {
        swatch = new View(activity);
        swatch.setContentDescription(SWATCH_DESCRIPTION);
        hue = slider(activity, MOST_HUE, HUE_DESCRIPTION);
        saturation = slider(activity, MOST_PERCENT, SATURATION_DESCRIPTION);
        value = slider(activity, MOST_PERCENT, VALUE_DESCRIPTION);
        hex = new EditText(activity);
        hex.setContentDescription(HEX_DESCRIPTION);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hex.setFilters(new InputFilter[] {new InputFilter.LengthFilter(HEX_LENGTH_WITH_HASH)});
        colour = start.clone();
        showColour(true);
        listenToSliders();
        listenToHex();
    }

    public static void show(Activity activity, int[] start, CompletableFuture<Optional<int[]>> answer) {
        ColourDialog chooser = new ColourDialog(activity, start);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(TITLE)
                .setView(chooser.layout(activity))
                .setPositiveButton(android.R.string.ok, (shown, which) ->
                        answer.complete(Optional.of(chooser.colour.clone())))
                .setNegativeButton(android.R.string.cancel, (shown, which) -> answer.complete(Optional.empty()))
                .setOnCancelListener(shown -> answer.complete(Optional.empty()))
                .setOnDismissListener(shown -> answer.complete(Optional.empty()))
                .create();
        answer.whenComplete((given, failed) -> activity.runOnUiThread(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private View layout(Activity activity) {
        float density = activity.getResources().getDisplayMetrics().density;
        int margin = Math.round(MARGIN_DP * density);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(margin, margin / 2, margin, 0);
        column.addView(swatch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(SWATCH_HEIGHT_DP * density)));
        addLabelled(activity, column, "Hue", hue);
        addLabelled(activity, column, "Saturation", saturation);
        addLabelled(activity, column, "Value", value);
        addLabelled(activity, column, "Hex", hex);
        return column;
    }

    private static void addLabelled(Activity activity, LinearLayout column, String label, View control) {
        TextView named = new TextView(activity);
        named.setText(label);
        column.addView(named);
        column.addView(control, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static SeekBar slider(Activity activity, int most, String description) {
        SeekBar slider = new SeekBar(activity);
        slider.setMin(0);
        slider.setMax(most);
        slider.setContentDescription(description);
        return slider;
    }

    private void listenToSliders() {
        SeekBar.OnSeekBarChangeListener moved = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar slider, int progress, boolean byTheUser) {
                if (!isShowingAColour) {
                    colour = HexColour.fromHsv(new float[] {
                            hue.getProgress(),
                            saturation.getProgress() / (float) MOST_PERCENT,
                            value.getProgress() / (float) MOST_PERCENT});
                    showColour(false);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar slider) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar slider) {
            }
        };
        hue.setOnSeekBarChangeListener(moved);
        saturation.setOnSeekBarChangeListener(moved);
        value.setOnSeekBarChangeListener(moved);
    }

    private void listenToHex() {
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if (!isShowingAColour) {
                    HexColour.read(text.toString()).ifPresent(typed -> {
                        colour = typed;
                        showColour(true);
                    });
                }
            }
        });
    }

    private void showColour(boolean movingSliders) {
        isShowingAColour = true;
        try {
            swatch.setBackgroundColor(android.graphics.Color.rgb(colour[0], colour[1], colour[2]));
            if (movingSliders) {
                float[] hsv = HexColour.toHsv(colour);
                hue.setProgress(Math.round(hsv[0]));
                saturation.setProgress(Math.round(hsv[1] * MOST_PERCENT));
                value.setProgress(Math.round(hsv[2] * MOST_PERCENT));
            }
            String written = HexColour.write(colour);
            if (!HexColour.read(hex.getText().toString()).map(HexColour::write).orElse("").equals(written)) {
                hex.setText(written);
                hex.setSelection(written.length());
            }
        } finally {
            isShowingAColour = false;
        }
    }
}
