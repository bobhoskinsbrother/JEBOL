package org.jebol.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.InputType;
import android.text.Selection;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.TextAttribute;

import org.jebol.adapter.android.AndroidKeys;
import org.jebol.adapter.android.AndroidPixelSurface;
import org.jebol.adapter.android.AndroidSurface;
import org.jebol.adapter.android.Density;
import org.jebol.adapter.android.KeyboardEdit;
import org.jebol.adapter.android.LogcatOutput;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.DefaultFont;
import org.jebol.application.OneSurfaceScreen;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.render.TextMeasurement;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class JebolView extends View implements OneSurfaceScreen.Viewer {

    public static final String TIMING_TAG = LogcatOutput.TAG + "-timing";

    private static final Typeface CARRIED_FACE = AndroidSurface.faceFrom(
            DefaultFont.readFrom(new ClasspathFiles()).readFontBytes());
    private static final TextMeasurement MEASURED_AS_DRAWN = AndroidSurface.measurement(CARRIED_FACE);

    private final Density density;
    private volatile List<DrawingCommand> newestPainting = List.of();
    private volatile OneSurfaceScreen screen;
    private volatile boolean painting = true;
    private boolean fingerDown;
    private volatile boolean keyboardWanted;
    private boolean hasDrawnFirst;

    public JebolView(Context context) {
        super(context);
        density = new Density(context.getResources().getDisplayMetrics().density);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void reportTo(OneSurfaceScreen given) {
        screen = given;
        if (getWidth() > 0 && getHeight() > 0) {
            measureViewport(getWidth(), getHeight());
        }
    }

    public Density density() {
        return density;
    }

    public List<DrawingCommand> newestPainting() {
        return newestPainting;
    }

    public void measureViewport(int widePixels, int highPixels) {
        OneSurfaceScreen given = screen;
        if (given != null) {
            given.measure(density.toDp(widePixels), density.toDp(highPixels));
        }
    }

    public void stopPainting() {
        painting = false;
        releaseTyping();
    }

    public void startPainting() {
        painting = true;
        postInvalidate();
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public int screenDpi() {
        return density.screenDpi();
    }

    @Override
    public void paint(List<DrawingCommand> painted) {
        newestPainting = List.copyOf(painted);
        if (painting) {
            postInvalidate();
        }
    }

    void measureKeyboard(int coveredPixels) {
        OneSurfaceScreen given = screen;
        if (given != null) {
            given.measureKeyboard(density.toDp(coveredPixels));
        }
    }

    @Override
    public void takeTyping(String heading) {
        keyboardWanted = true;
        post(() -> {
            requestFocus();
            InputMethodManager keyboard = getContext().getSystemService(InputMethodManager.class);
            keyboard.restartInput(this);
            keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    public void releaseTyping() {
        keyboardWanted = false;
        post(() -> {
            InputMethodManager keyboard = getContext().getSystemService(InputMethodManager.class);
            keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
            keyboard.restartInput(this);
        });
    }

    @Override
    public TextMeasurement textMeasurement(DefaultFont carried) {
        return MEASURED_AS_DRAWN;
    }

    @Override
    public Optional<ImagePort.Pixels> paintOffscreen(List<DrawingCommand> painted, int wide, int high) {
        return Optional.of(AndroidPixelSurface.paintOnWhite(painted, wide, high, CARRIED_FACE));
    }

    @Override
    protected void onSizeChanged(int wide, int high, int wasWide, int wasHigh) {
        super.onSizeChanged(wide, high, wasWide, wasHigh);
        measureViewport(wide, high);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        if (!painting) {
            return;
        }
        int saved = canvas.save();
        canvas.scale(density.pixelsPerDp(), density.pixelsPerDp());
        AndroidSurface surface = new AndroidSurface(canvas, CARRIED_FACE);
        List<DrawingCommand> painted = newestPainting;
        painted.forEach(surface::execute);
        canvas.restoreToCount(saved);
        if (!hasDrawnFirst && !painted.isEmpty()) {
            hasDrawnFirst = true;
            Log.i(TIMING_TAG, "first-picture " + SystemClock.uptimeMillis());
        }
        if (isTiming()) {
            Log.d(TIMING_TAG, "drawn " + SystemClock.uptimeMillis());
        }
    }

    private static boolean isTiming() {
        return Log.isLoggable(TIMING_TAG, Log.DEBUG);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                fingerDown = true;
                requestFocus();
                reportAt(ScreenEventType.DOWN, event);
            }
            case MotionEvent.ACTION_MOVE -> {
                if (fingerDown) {
                    reportAt(ScreenEventType.MOVE, event);
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (fingerDown) {
                    fingerDown = false;
                    reportAt(ScreenEventType.UP, event);
                }
            }
            default -> {
                return true;
            }
        }
        return true;
    }

    private void reportAt(ScreenEventType type, MotionEvent event) {
        if (isTiming()) {
            Log.d(TIMING_TAG, "touched " + type.name().toLowerCase(Locale.ROOT) + " " + event.getEventTime());
        }
        int across = density.toDp(event.getX(0));
        int down = density.toDp(event.getY(0));
        report(type, event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
                ? new ScreenEventDetail.At(across, down)
                : new ScreenEventDetail.Touched(across, down));
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return keyboardWanted;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo info) {
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        info.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new KeysFromTheKeyboard(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        List<AndroidKeys.Report> reports = AndroidKeys.reportFor(keyCode);
        if (!reports.isEmpty()) {
            reports.forEach(each -> report(each.type(), each.detail()));
            return true;
        }
        int typed = event.getUnicodeChar();
        if (typed != 0) {
            AndroidKeys.reportsForText(new String(Character.toChars(typed)))
                    .forEach(each -> report(each.type(), each.detail()));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    void report(ScreenEventType type, ScreenEventDetail detail) {
        OneSurfaceScreen given = screen;
        if (given != null) {
            given.report(type, detail);
        }
    }

    private void reportEdit(KeyboardEdit edit) {
        for (int deleted = 0; deleted < edit.deletions(); deleted++) {
            reportKey(KeyEvent.KEYCODE_DEL);
        }
        AndroidKeys.reportsForText(edit.typed()).forEach(each -> report(each.type(), each.detail()));
    }

    private void reportKey(int keyCode) {
        AndroidKeys.reportFor(keyCode).forEach(each -> report(each.type(), each.detail()));
    }

    private static final class KeysFromTheKeyboard extends BaseInputConnection {

        private final JebolView view;
        private String reported = "";

        KeysFromTheKeyboard(JebolView view) {
            super(view, true);
            this.view = view;
        }

        private boolean reportChanges(boolean done) {
            String now = getEditable().toString();
            view.reportEdit(KeyboardEdit.between(reported, now));
            reported = now;
            return done;
        }

        private int charactersBeforeCursor() {
            return Math.max(0, Selection.getSelectionStart(getEditable()));
        }

        private void forgetTyped() {
            getEditable().clear();
            reported = "";
            Selection.setSelection(getEditable(), 0);
            view.getContext().getSystemService(InputMethodManager.class).updateSelection(view, 0, 0, -1, -1);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            return reportChanges(super.commitText(text, newCursorPosition));
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            return reportChanges(super.setComposingText(text, newCursorPosition));
        }

        @Override
        public boolean replaceText(int start, int end, CharSequence text, int newCursorPosition,
                TextAttribute attribute) {
            return reportChanges(super.replaceText(start, end, text, newCursorPosition, attribute));
        }

        @Override
        public boolean deleteSurroundingText(int before, int after) {
            int pastWhatWasTyped = before - charactersBeforeCursor();
            for (int deleted = 0; deleted < pastWhatWasTyped; deleted++) {
                view.reportKey(KeyEvent.KEYCODE_DEL);
            }
            return reportChanges(super.deleteSurroundingText(before, after));
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int before, int after) {
            int pastWhatWasTyped = before - Character.codePointCount(getEditable(), 0, charactersBeforeCursor());
            for (int deleted = 0; deleted < pastWhatWasTyped; deleted++) {
                view.reportKey(KeyEvent.KEYCODE_DEL);
            }
            return reportChanges(super.deleteSurroundingTextInCodePoints(before, after));
        }

        @Override
        public boolean performEditorAction(int action) {
            forgetTyped();
            view.reportKey(KeyEvent.KEYCODE_ENTER);
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                return deleteSurroundingTextInCodePoints(1, 0);
            }
            int typed = event.getUnicodeChar();
            if (typed != 0 && AndroidKeys.reportFor(event.getKeyCode()).isEmpty()) {
                return commitText(new String(Character.toChars(typed)), 1);
            }
            forgetTyped();
            view.onKeyDown(event.getKeyCode(), event);
            return true;
        }
    }
}
