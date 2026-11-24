package com.termux.launcherctl;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.x11.LinuxAppCatalog;
import com.termux.app.x11.X11Apps;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The routes {@code launcherctl linux-apps} and {@code launcherctl linux-app open} sit on: the
 * catalogue of Linux apps as the drawer lists it, and the drawer's own tap on one of them, so a
 * check that is not about the drawer need not search it. Plain text answers, one line each, so a
 * shell can read them without parsing JSON; {@code ?format=json} gives the list as JSON.
 *
 * <p>Opening goes through {@link LauncherAppLauncher#launchEntry}, the call a drawer tap makes,
 * on the main thread: display choice, a second tap raising the open window and the notices all
 * happen as they do for a tap. The server's token check and rate limit have already run.
 */
final class LinuxAppsRoutes {

    /** How long the request waits for the main thread to hand the launch to the runner. */
    private static final long HAND_OFF_TIMEOUT_MS = 5_000L;

    /** A status, a content type and a body; the server wraps it in its own response type. */
    static final class Result {
        final int status;
        @NonNull final String contentType;
        @NonNull final String body;

        Result(int status, @NonNull String contentType, @NonNull String body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private LinuxAppsRoutes() {
    }

    static boolean handles(@NonNull String method, @NonNull String path) {
        if ("/v1/linux-apps".equals(path)) return "GET".equals(method);
        if ("/v1/linux-apps/open".equals(path)) return "POST".equals(method);
        return false;
    }

    @NonNull
    static Result handle(@NonNull Context context, @NonNull String path, @Nullable String format,
                         @Nullable String body) {
        try {
            if ("/v1/linux-apps".equals(path)) return list("json".equals(format));
            return open(context, idOf(body));
        } catch (RuntimeException | JSONException e) {
            return text(500, "linux-app: failed: " + oneLine(e.getMessage()));
        }
    }

    /** One catalogue line: {@code id TAB name TAB source}, plus {@code TAB desktop} for a session. */
    @NonNull
    static String line(@NonNull String id, @NonNull String name, @NonNull String source,
                       boolean desktop) {
        return oneLine(id) + "\t" + oneLine(name) + "\t" + oneLine(source) + (desktop ? "\tdesktop" : "");
    }

    /** {@code termux} for the prefix's own apps, else the container's name. */
    @NonNull
    static String sourceOf(@NonNull LinuxAppCatalog.LinuxApp app) {
        return app.container.name.isEmpty() ? "termux" : app.container.name;
    }

    @NonNull
    static String oneLine(@Nullable String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    @Nullable
    private static String idOf(@Nullable String body) throws JSONException {
        if (body == null || body.trim().isEmpty()) return null;
        String id = new JSONObject(body).optString("id", "").trim();
        return id.isEmpty() ? null : id;
    }

    @NonNull
    private static Result list(boolean json) throws JSONException {
        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(LinuxAppCatalog.roots());
        if (json) {
            JSONArray array = new JSONArray();
            for (LinuxAppCatalog.LinuxApp app : apps) {
                array.put(new JSONObject().put("id", app.id).put("name", app.name)
                    .put("source", sourceOf(app)).put("desktop", app.session));
            }
            return new Result(200, "application/json; charset=utf-8",
                new JSONObject().put("apps", array).toString());
        }
        StringBuilder out = new StringBuilder();
        for (LinuxAppCatalog.LinuxApp app : apps) {
            out.append(line(app.id, app.name, sourceOf(app), app.session)).append('\n');
        }
        return text(200, out.toString());
    }

    @NonNull
    private static Result open(@NonNull Context context, @Nullable String id) {
        if (id == null) return text(400, "linux-app: open needs an id (launcherctl linux-apps lists them)");
        List<LinuxAppCatalog.LinuxApp> apps = LinuxAppCatalog.scan(LinuxAppCatalog.roots());
        LinuxAppCatalog.LinuxApp app = LinuxAppCatalog.find(apps, id);
        if (app == null) return text(404, "linux-app: no Linux app with id " + oneLine(id));
        LauncherAppEntry entry = new LauncherAppEntry(X11Apps.ref(app.id), app.name, null);
        AtomicBoolean launched = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);
        Runnable launch = () -> {
            try {
                launched.set(LauncherAppLauncher.launchEntry(context, entry));
            } catch (RuntimeException ignored) {
                // A bad launch is a failed launch, reported below; never an error into the launcher.
            } finally {
                done.countDown();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            launch.run();
        } else {
            new Handler(Looper.getMainLooper()).post(launch);
            try {
                if (!done.await(HAND_OFF_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    return text(503, "linux-app: the launcher did not answer; is the home screen shown?");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return text(503, "linux-app: interrupted");
            }
        }
        if (!launched.get()) {
            return text(503, "linux-app: Linux apps are unavailable; show the launcher's home screen first");
        }
        return text(200, "opened " + oneLine(app.id));
    }

    @NonNull
    private static Result text(int status, @NonNull String body) {
        // No final newline: the script's api() ends its output with one.
        return new Result(status, "text/plain; charset=utf-8",
            body.endsWith("\n") ? body.substring(0, body.length() - 1) : body);
    }
}
