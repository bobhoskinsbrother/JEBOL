package org.jebol.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.os.Bundle;
import android.util.Log;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.jebol.adapter.android.LogcatOutput;
import org.jebol.application.Interpreter;
import org.jebol.application.MemoryFiles;
import org.jebol.application.ScriptOutcome;

import java.util.Optional;

public final class JebolActivity extends Activity {

    static final String SCRIPT_EXTRA = "script";

    private static final String SAVED_PLACE = "place";

    private final OnBackInvokedCallback back = this::goBack;
    private JebolView view;
    private volatile AppSession session;
    private boolean isTakingBack;
    private boolean hasStartedBefore;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        view = new JebolView(this);
        setContentView(keepClearOfSystemBars(view));
        Optional<String> resumeAt = Optional.ofNullable(saved).map(state -> state.getString(SAVED_PLACE));
        MemoryFiles files = new MemoryFiles();
        CarriedApp carried = CarriedApp.read();
        carried.copyInto(files);
        String source = givenScript().orElseGet(carried::readEntry);
        Thread interpreting = new Thread(() -> run(files, source, resumeAt), "jebol-interpreter");
        interpreting.setDaemon(true);
        interpreting.start();
    }

    private FrameLayout keepClearOfSystemBars(JebolView content) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.WHITE);
        frame.setOnApplyWindowInsetsListener((padded, given) -> {
            Insets bars = given.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            padded.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        frame.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return frame;
    }

    private Optional<String> givenScript() {
        boolean isDebugBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        return isDebugBuild ? Optional.ofNullable(getIntent().getStringExtra(SCRIPT_EXTRA)) : Optional.empty();
    }

    private void run(MemoryFiles files, String source, Optional<String> resumeAt) {
        LogcatOutput output = LogcatOutput.toLogcat();
        AppSession opened = AppSession.open(view, files, output, wanted -> runOnUiThread(() -> takeBack(wanted)));
        session = opened;
        resumeAt.ifPresent(opened::resumeAt);
        Interpreter interpreter = opened.interpreter();
        ScriptOutcome outcome = interpreter.run(source);
        output.flush();
        switch (outcome.conclusion()) {
            case QUIT_EARLY -> runOnUiThread(this::finish);
            case PRODUCED_VALUE, CANCELLED -> {
            }
            default -> Log.e(LogcatOutput.TAG, interpreter.display(outcome));
        }
    }

    private void takeBack(boolean wanted) {
        if (wanted == isTakingBack) {
            return;
        }
        isTakingBack = wanted;
        OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        if (wanted) {
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
        } else {
            dispatcher.unregisterOnBackInvokedCallback(back);
        }
    }

    private void goBack() {
        AppSession running = session;
        if (running == null) {
            finish();
            return;
        }
        if (running.history().hasAPlaceBehind()) {
            running.followOperatorBack();
            return;
        }
        if (running.history().isGuarding()) {
            askBeforeLeaving();
            return;
        }
        finish();
    }

    private void askBeforeLeaving() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.leave_with_unsaved_work)
                .setPositiveButton(R.string.leave, (dialog, which) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppSession running = session;
        if (hasStartedBefore && running != null) {
            running.screen().showSurface();
        }
        view.startPainting();
        hasStartedBefore = true;
    }

    @Override
    protected void onStop() {
        view.stopPainting();
        AppSession running = session;
        if (running != null) {
            running.screen().hideSurface();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        AppSession running = session;
        if (running != null) {
            state.putString(SAVED_PLACE, running.history().currentAddress());
        }
    }

    @Override
    protected void onDestroy() {
        AppSession running = session;
        if (isFinishing() && running != null) {
            running.close();
        }
        super.onDestroy();
    }
}
