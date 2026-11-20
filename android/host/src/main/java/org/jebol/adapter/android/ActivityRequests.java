package org.jebol.adapter.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;

import org.jebol.domain.host.WindowPort;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class ActivityRequests {

    private static final int FIRST_REQUEST_CODE = 0x4A00;

    private final Activity activity;
    private final AtomicInteger nextRequestCode = new AtomicInteger(FIRST_REQUEST_CODE);
    private final Map<Integer, CompletableFuture<Optional<Intent>>> awaitingResults = new ConcurrentHashMap<>();
    private final Map<CompletableFuture<?>, Runnable> awaitingDialogs = new ConcurrentHashMap<>();

    public ActivityRequests(Activity activity) {
        this.activity = activity;
    }

    public Activity activity() {
        return activity;
    }

    public Optional<Intent> startForResult(Intent request) {
        int requestCode = nextRequestCode.getAndIncrement();
        CompletableFuture<Optional<Intent>> answer = new CompletableFuture<>();
        awaitingResults.put(requestCode, answer);
        activity.runOnUiThread(() -> {
            try {
                activity.startActivityForResult(request, requestCode);
            } catch (ActivityNotFoundException | SecurityException unopened) {
                answer.completeExceptionally(unopened);
            }
        });
        try {
            return await(answer, "the picker");
        } finally {
            awaitingResults.remove(requestCode);
        }
    }

    public void start(Intent request) {
        CompletableFuture<Optional<Boolean>> started = new CompletableFuture<>();
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(request);
                started.complete(Optional.of(true));
            } catch (ActivityNotFoundException | SecurityException unopened) {
                started.completeExceptionally(unopened);
            }
        });
        await(started, "the viewer");
    }

    public <T> Optional<T> showDialog(BiConsumer<Activity, CompletableFuture<Optional<T>>> showing) {
        CompletableFuture<Optional<T>> answer = new CompletableFuture<>();
        awaitingDialogs.put(answer, () -> answer.complete(Optional.empty()));
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                answer.complete(Optional.empty());
                return;
            }
            showing.accept(activity, answer);
        });
        try {
            return await(answer, "the dialog");
        } finally {
            awaitingDialogs.remove(answer);
        }
    }

    public void deliver(int requestCode, int resultCode, Intent data) {
        Optional.ofNullable(awaitingResults.get(requestCode)).ifPresent(answer ->
                answer.complete(resultCode == Activity.RESULT_OK ? Optional.ofNullable(data) : Optional.empty()));
    }

    public void cancelAll() {
        awaitingResults.values().forEach(answer -> answer.complete(Optional.empty()));
        awaitingDialogs.values().forEach(Runnable::run);
    }

    private static <T> Optional<T> await(CompletableFuture<Optional<T>> answer, String what) {
        try {
            return answer.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException failed) {
            throw new WindowPort.Denied("cannot-open", what + " could not be opened: " + failed.getCause().getMessage());
        }
    }
}
