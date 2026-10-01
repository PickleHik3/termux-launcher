package com.termux.ai;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class TaiRuntimeServiceClient {
    private static final long CONNECT_TIMEOUT_MS = 5_000L;
    private static final long REQUEST_TIMEOUT_MS = 120_000L;
    private static final int INLINE_BODY_LIMIT_BYTES = 512 * 1024;
    /** Sentinel placed on a streaming request's queue to mark the end of the stream. */
    private static final Object STREAM_END = new Object();

    private final Context appContext;
    private final Object connectionLock = new Object();
    private final Messenger incoming = new Messenger(new IncomingHandler());
    private final Map<String, PendingRequest> pending = new ConcurrentHashMap<>();
    @Nullable private Messenger service;
    @Nullable private CountDownLatch connectingLatch;
    private boolean binding;
    /** A {@code bindService} of ours is registered; only then is there a binding to release. */
    private boolean bound;

    public TaiRuntimeServiceClient(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    public JSONObject request(@NonNull String operation, @Nullable String body) throws JSONException {
        return request(operation, body, REQUEST_TIMEOUT_MS);
    }

    @NonNull
    public JSONObject request(@NonNull String operation, @Nullable String body, long timeoutMs) throws JSONException {
        PendingRequest request = send(operation, body, false, null);
        try {
            if (!request.done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pending.remove(request.requestId);
                return runtimeUnavailable("tai_runtime_timeout", "On-device AI runtime service timed out.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(request.requestId);
            return runtimeUnavailable("tai_runtime_interrupted", "On-device AI runtime request interrupted.");
        }
        if (request.result == null) return runtimeUnavailable("tai_runtime_unavailable", "On-device AI runtime service did not return a result.");
        return request.result;
    }

    public void stream(@NonNull String operation, @Nullable String body, @NonNull TaiManager.OpenAiStreamSink sink)
        throws JSONException, IOException {
        PendingRequest request = send(operation, body, true, sink);
        BlockingQueue<Object> events = request.events;
        // Drain stream events on this (background) caller thread. The IPC reply Handler runs on the
        // main looper, so it only enqueues events here — performing the SSE socket writes from the
        // main thread would throw NetworkOnMainThreadException and abort the stream.
        try {
            Object item;
            while (events != null && (item = events.take()) != STREAM_END) {
                sink.onEvent((JSONObject) item);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(request.requestId);
            emitRuntimeError(sink, runtimeUnavailable("tai_runtime_interrupted", "On-device AI runtime stream interrupted."));
            return;
        } catch (IOException | RuntimeException e) {
            // Client disconnected or write failed: stop tracking so late events are dropped.
            pending.remove(request.requestId);
            throw e;
        }
        if (request.result != null && !request.result.optBoolean("ok", true)) {
            emitRuntimeError(sink, request.result);
        } else {
            sink.onDone();
        }
    }

    @NonNull
    private PendingRequest send(
        @NonNull String operation,
        @Nullable String body,
        boolean stream,
        @Nullable TaiManager.OpenAiStreamSink sink
    ) throws JSONException {
        if (ensureConnected() == null) {
            PendingRequest failed = new PendingRequest(UUID.randomUUID().toString(), operation, stream, sink);
            failed.result = runtimeUnavailable("tai_runtime_unavailable", "On-device AI runtime service is not connected.");
            failed.signalEnd();
            failed.done.countDown();
            return failed;
        }

        String requestId = UUID.randomUUID().toString();
        PendingRequest pendingRequest = new PendingRequest(requestId, operation, stream, sink);
        TransportBody transportBody;
        try {
            transportBody = transportBody(requestId, body == null ? "" : body);
        } catch (IOException e) {
            pendingRequest.result = runtimeUnavailable("tai_runtime_transport_failed", e.getMessage());
            pendingRequest.signalEnd();
            pendingRequest.done.countDown();
            return pendingRequest;
        }

        Message message = Message.obtain(null, TaiRuntimeIpc.MSG_REQUEST);
        message.replyTo = incoming;
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        data.putString(TaiRuntimeIpc.KEY_OPERATION, operation);
        if (transportBody.inlineBody != null) data.putString(TaiRuntimeIpc.KEY_BODY, transportBody.inlineBody);
        if (transportBody.bodyFile != null) data.putString(TaiRuntimeIpc.KEY_BODY_FILE, transportBody.bodyFile);
        message.setData(data);
        // Registered and sent under the connection lock, so the runtime's idle-exit notice
        // (onIdleExitRequested) either finds this request pending and leaves the binding alone, or
        // has already unbound — then the runtime is bound again, in a fresh process, and the
        // request sent there. Messenger.send is one-way; nothing waits under the lock.
        for (boolean rebound = false; ; rebound = true) {
            synchronized (connectionLock) {
                if (service != null) {
                    pending.put(requestId, pendingRequest);
                    try {
                        service.send(message);
                    } catch (RemoteException e) {
                        pending.remove(requestId);
                        pendingRequest.result = runtimeCrashed("tai_runtime_send_failed", "On-device AI runtime service disconnected while starting the request.");
                        pendingRequest.signalEnd();
                        pendingRequest.done.countDown();
                    }
                    return pendingRequest;
                }
            }
            if (rebound || ensureConnected() == null) {
                pendingRequest.result = runtimeUnavailable("tai_runtime_unavailable", "On-device AI runtime service is not connected.");
                pendingRequest.signalEnd();
                pendingRequest.done.countDown();
                return pendingRequest;
            }
        }
    }

    @Nullable
    private Messenger ensureConnected() {
        synchronized (connectionLock) {
            if (service != null) return service;
            if (!binding) {
                binding = true;
                connectingLatch = new CountDownLatch(1);
                Intent intent = new Intent(appContext, TaiRuntimeService.class);
                // BIND_IMPORTANT raises the isolated :tai_runtime process to the launcher's own
                // oom_score_adj while a model is loaded, so Android's low-memory killer reaps other
                // background apps before the runtime (a large GPU load otherwise gets SIGKILLed mid
                // OpenCL init). This keeps crash isolation while matching Edge Gallery's effective
                // top-app priority, which is single-process and gets that for free.
                boolean bound = appContext.bindService(intent, connection,
                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
                if (!bound) {
                    binding = false;
                    if (connectingLatch != null) connectingLatch.countDown();
                    return null;
                }
                this.bound = true;
            }
        }

        CountDownLatch latch;
        synchronized (connectionLock) {
            latch = connectingLatch;
        }
        if (latch != null) {
            try {
                latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (connectionLock) {
            return service;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (connectionLock) {
                service = new Messenger(binder);
                binding = false;
                bound = true;
                if (connectingLatch != null) connectingLatch.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onRuntimeBinderDied();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            onRuntimeBinderDied();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            onRuntimeBinderDied();
        }
    };

    /**
     * The runtime has held nothing but its process baseline for {@link TaiPressureWatch#IDLE_EXIT_MS}
     * and asks to be let go. Unbinding is the only way its process can end: {@code BIND_AUTO_CREATE}
     * keeps a bound service alive for as long as this binding exists, and the runtime exits once it
     * is destroyed. Refused while a request is pending — the runtime is not idle from this side, and
     * it asks again after its next idle period. No binder-death callback follows an unbind, so an
     * idle exit never reaches {@link #onRuntimeBinderDied} and is never reported as a crash; the
     * next request binds again and starts a fresh process.
     */
    private void onIdleExitRequested() {
        synchronized (connectionLock) {
            if (service == null || !pending.isEmpty()) return;
            releaseBindingLocked();
            service = null;
            binding = false;
        }
    }

    /**
     * The runtime process died under this binding (a crash, the low-memory killer, a SIGKILL).
     * Every request it was serving fails here with {@code tai_runtime_crashed}; none is sent again.
     * The binding is released too: with {@code BIND_AUTO_CREATE} still registered, ActivityManager
     * would bring the process straight back (about a second after the death, measured on pong
     * 2026-09-24) with nobody having asked for it — a 330 MB baseline coming up while the GPU
     * driver is still freeing the dead process's buffers. Released, the process stays down until
     * the next request binds again ({@link #ensureConnected}), so a model killed mid-load is only
     * ever loaded again by a new request that asks for it.
     */
    private void onRuntimeBinderDied() {
        synchronized (connectionLock) {
            service = null;
            binding = false;
            if (connectingLatch != null) connectingLatch.countDown();
            releaseBindingLocked();
        }
        for (PendingRequest request : pending.values()) {
            request.result = runtimeDied(request.operation);
            // Don't write to the sink here (this runs on the main thread). The stream consumer on
            // the background thread emits the error after it observes the end sentinel.
            request.signalEnd();
            request.done.countDown();
        }
        pending.clear();
    }

    /**
     * Unbinds once. Guarded by {@link #bound} so a disconnect callback that follows our own unbind
     * (Robolectric delivers one; a second unbind would recurse) releases nothing twice.
     */
    private void releaseBindingLocked() {
        if (!bound) return;
        bound = false;
        try {
            appContext.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // Not bound from this context's point of view; there is nothing to release.
        }
    }

    /** The death of the runtime process as the request it interrupted sees it; nothing is retried. */
    @NonNull
    private JSONObject runtimeDied(@NonNull String operation) {
        String message;
        if (TaiRuntimeIpc.OP_LOAD_MODEL.equals(operation) || TaiRuntimeIpc.OP_KEEP_WARM.equals(operation)) {
            message = "AI runtime process died while loading the model; the load was not retried. "
                + "Load again to try once more, on CPU or with a smaller model.";
        } else if (TaiRuntimeService.isStatusOperation(operation)) {
            message = "AI runtime process died while answering a status request.";
        } else {
            message = "AI runtime process died while running the model; the request was not retried. "
                + "Try again, on CPU or with a smaller model.";
        }
        JSONObject error = runtimeCrashed("tai_runtime_crashed", message);
        try {
            error.put("operation", operation);
            error.put("retried", false);
        } catch (JSONException ignored) {
        }
        return error;
    }

    private final class IncomingHandler extends Handler {
        IncomingHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message message) {
            if (message.what == TaiRuntimeService.MSG_IDLE_EXIT) {
                onIdleExitRequested();
                return;
            }
            Bundle data = message.getData();
            String requestId = data.getString(TaiRuntimeIpc.KEY_REQUEST_ID, "");
            PendingRequest request = pending.get(requestId);
            if (request == null) {
                super.handleMessage(message);
                return;
            }
            try {
                if (message.what == TaiRuntimeIpc.MSG_RESPONSE) {
                    String result = data.getString(TaiRuntimeIpc.KEY_RESULT, "{}");
                    request.result = new JSONObject(result);
                    pending.remove(requestId);
                    // A stream the service ended with a plain response (it threw before its own
                    // done event) must still wake its consumer, which then reports the error.
                    if (request.stream) request.signalEnd();
                    request.done.countDown();
                    return;
                }
                if (message.what == TaiRuntimeIpc.MSG_STREAM_EVENT) {
                    // Hand the parsed event to the background consumer; never touch the socket here.
                    String event = data.getString(TaiRuntimeIpc.KEY_EVENT, "{}");
                    if (request.events != null) request.events.offer(new JSONObject(event));
                    return;
                }
                if (message.what == TaiRuntimeIpc.MSG_STREAM_DONE) {
                    pending.remove(requestId);
                    request.signalEnd();
                    request.done.countDown();
                    return;
                }
            } catch (Exception e) {
                request.result = runtimeUnavailable("tai_runtime_stream_failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                pending.remove(requestId);
                request.signalEnd();
                request.done.countDown();
                return;
            }
            super.handleMessage(message);
        }
    }

    @NonNull
    private TransportBody transportBody(@NonNull String requestId, @NonNull String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= INLINE_BODY_LIMIT_BYTES) return new TransportBody(body, null);
        File dir = new File(appContext.getCacheDir(), "tai-ipc");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create On-device AI IPC cache directory.");
        }
        File file = new File(dir, requestId + ".json");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        return new TransportBody(null, file.getAbsolutePath());
    }

    private void emitRuntimeError(@NonNull TaiManager.OpenAiStreamSink sink, @NonNull JSONObject source)
        throws JSONException, IOException {
        JSONObject error = new JSONObject();
        error.put("message", source.optString("message", "On-device AI runtime failed"));
        error.put("type", "invalid_request_error");
        error.put("code", source.optString("error", "tai_runtime_error"));
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("tai", source);
        sink.onEvent(response);
        sink.onDone();
    }

    @NonNull
    private JSONObject runtimeUnavailable(@NonNull String code, @Nullable String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", code);
            error.put("message", message == null || message.trim().isEmpty()
                ? "On-device AI runtime service is unavailable." : message);
            error.put("runtimeProcess", TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
            error.put("_statusCode", 503);
        } catch (JSONException ignored) {
        }
        return error;
    }

    @NonNull
    private JSONObject runtimeCrashed(@NonNull String code, @NonNull String message) {
        JSONObject error = runtimeUnavailable(code, message);
        try {
            JSONObject marker = TaiRuntimeCrashMarker.read(appContext);
            if (marker != null) error.put("lastRuntimeCrash", marker);
            TaiEventLog.log(appContext, TaiEventLog.CRASH_RECOVERED,
                marker == null ? null : marker.optString("modelId", null),
                marker == null ? null : marker.optString("backend", null),
                marker == null ? null : marker.optString("accelerator", null),
                marker == null ? 0 : marker.optInt("contextWindow", 0), 0L, 0L, code + ": " + message);
        } catch (JSONException ignored) {
        }
        return error;
    }

    private static final class TransportBody {
        @Nullable final String inlineBody;
        @Nullable final String bodyFile;

        TransportBody(@Nullable String inlineBody, @Nullable String bodyFile) {
            this.inlineBody = inlineBody;
            this.bodyFile = bodyFile;
        }
    }

    private static final class PendingRequest {
        final String requestId;
        /** The {@link TaiRuntimeIpc} operation, named in the error when the runtime dies under it. */
        final String operation;
        final boolean stream;
        @Nullable final TaiManager.OpenAiStreamSink sink;
        /** Non-null for streaming requests: events are produced on the main-looper Handler and
         *  consumed (written to the socket) on the background caller thread. */
        @Nullable final BlockingQueue<Object> events;
        final CountDownLatch done = new CountDownLatch(1);
        @Nullable JSONObject result;

        PendingRequest(@NonNull String requestId, @NonNull String operation, boolean stream,
                       @Nullable TaiManager.OpenAiStreamSink sink) {
            this.requestId = requestId;
            this.operation = operation;
            this.stream = stream;
            this.sink = sink;
            this.events = stream ? new LinkedBlockingQueue<>() : null;
        }

        /** Releases a waiting stream consumer; safe to call more than once. */
        void signalEnd() {
            if (events != null) events.offer(STREAM_END);
        }
    }
}
