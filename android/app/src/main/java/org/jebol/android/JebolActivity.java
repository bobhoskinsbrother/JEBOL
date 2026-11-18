package org.jebol.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class JebolActivity extends Activity {

    static final String SCRIPT_EXTRA = "script";
    static final String RESTART_EXTRA = "restart";
    static final String RESTART_MARKER = "restarted ";

    private static final String SAVED_PLACE = "place";
    private static final Duration TO_END_AN_INTERPRETER = Duration.ofSeconds(10);
    private static final Duration TO_CANCEL_AGAIN = Duration.ofMillis(100);

    private final OnBackInvokedCallback back = this::goBack;
    private JebolView view;
    private volatile AppSession session;
    private Thread interpreting;
    private AlertDialog askingToLeave;
    private int restarts;
    private boolean isTakingBack;
    private boolean hasStartedBefore;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        view = new JebolView(this);
        setContentView(keepClearOfSystemBars(view));
        Optional<String> resumeAt = Optional.ofNullable(saved).map(state -> state.getString(SAVED_PLACE));
        String source = scriptGivenBy(getIntent());
        interpreting = startInterpreting(() -> run(source, resumeAt));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (isDebugBuild() && intent.getBooleanExtra(RESTART_EXTRA, false)) {
            setIntent(intent);
            restartInPlace(scriptGivenBy(intent));
        }
    }

    private void restartInPlace(String source) {
        if (askingToLeave != null) {
            askingToLeave.dismiss();
            askingToLeave = null;
        }
        view.releaseTyping();
        Thread ending = interpreting;
        int restart = ++restarts;
        interpreting = startInterpreting(() -> {
            if (!cancelUntilEnded(ending)) {
                Log.e(LogcatOutput.TAG, "the last interpreter did not end in " + TO_END_AN_INTERPRETER);
                return;
            }
            session = null;
            runOnUiThread(() -> takeBack(false));
            view.paint(List.of());
            Log.i(LogcatOutput.TAG, RESTART_MARKER + restart);
            run(source, Optional.empty());
        });
    }

    private boolean cancelUntilEnded(Thread ending) {
        long givenUpAt = System.nanoTime() + TO_END_AN_INTERPRETER.toNanos();
        while (ending.isAlive() && System.nanoTime() < givenUpAt) {
            AppSession stale = session;
            if (stale != null) {
                stale.close();
            }
            try {
                ending.join(TO_CANCEL_AGAIN.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !ending.isAlive();
    }

    private static Thread startInterpreting(Runnable work) {
        Thread started = new Thread(work, "jebol-interpreter");
        started.setDaemon(true);
        started.start();
        return started;
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

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private String scriptGivenBy(Intent intent) {
        Optional<String> given = isDebugBuild() ? Optional.ofNullable(intent.getStringExtra(SCRIPT_EXTRA))
                : Optional.empty();
        return given.orElseGet(() -> CarriedApp.read().readEntry());
    }

    private void run(String source, Optional<String> resumeAt) {
        MemoryFiles files = new MemoryFiles();
        CarriedApp.read().copyInto(files);
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
        askingToLeave = new AlertDialog.Builder(this)
                .setMessage(R.string.leave_with_unsaved_work)
                .setPositiveButton(R.string.leave, (dialog, which) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialog -> askingToLeave = null)
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
