package org.jebol.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import org.jebol.adapter.android.AndroidKeys;
import org.jebol.adapter.android.AndroidPixelSurface;
import org.jebol.adapter.android.AndroidSurface;
import org.jebol.adapter.android.Density;
import org.jebol.adapter.android.LogcatOutput;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.DefaultFont;
import org.jebol.application.OneSurfaceScreen;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.render.DrawingCommand;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class JebolView extends View implements OneSurfaceScreen.Viewer {

    public static final String TIMING_TAG = LogcatOutput.TAG + "-timing";

    private static final Typeface CARRIED_FACE = AndroidSurface.faceFrom(
            DefaultFont.readFrom(new ClasspathFiles()).readFontBytes());

    private final Density density;
    private volatile List<DrawingCommand> newestPainting = List.of();
    private volatile OneSurfaceScreen screen;
    private volatile boolean painting = true;
    private boolean fingerDown;
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

    @Override
    public void takeTyping(String heading) {
        post(() -> {
            requestFocus();
            InputMethodManager keyboard = getContext().getSystemService(InputMethodManager.class);
            keyboard.restartInput(this);
            keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    public void releaseTyping() {
        post(() -> getContext().getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(getWindowToken(), 0));
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
        report(type, new ScreenEventDetail.At(density.toDp(event.getX(0)), density.toDp(event.getY(0))));
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
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

    private static final class KeysFromTheKeyboard extends BaseInputConnection {

        private final JebolView view;

        KeysFromTheKeyboard(JebolView view) {
            super(view, false);
            this.view = view;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            AndroidKeys.reportsForText(text).forEach(each -> view.report(each.type(), each.detail()));
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int before, int after) {
            for (int deleted = 0; deleted < before; deleted++) {
                AndroidKeys.reportFor(KeyEvent.KEYCODE_DEL).forEach(each -> view.report(each.type(), each.detail()));
            }
            return true;
        }

        @Override
        public boolean performEditorAction(int action) {
            AndroidKeys.reportFor(KeyEvent.KEYCODE_ENTER).forEach(each -> view.report(each.type(), each.detail()));
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                view.onKeyDown(event.getKeyCode(), event);
            }
            return true;
        }
    }
}
