package com.termux.app.launcher.web;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Hands a URL or a search query to whatever browser the user has set as default.
 *
 * <p>The launcher deliberately owns no web view. A page opened from here is a page in the
 * user's own browser, with their sessions, their extensions and their history — the launcher's
 * only lasting trace is the frequency count {@link LauncherWebVisitStats} keeps so the palette
 * can offer the address back.
 */
public final class LauncherWebOpener {

    private static final String LOG_TAG = "LauncherWebOpener";

    private LauncherWebOpener() {
    }

    /** The engine the user picked, with their custom template folded in. */
    @NonNull
    public static LauncherWebSearchEngines.Engine engine(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null)
            return LauncherWebSearchEngines.resolve(null, null);
        return LauncherWebSearchEngines.resolve(preferences.getWebSearchEngine(),
            preferences.getWebSearchCustomUrl());
    }

    /** The URL a search for {@code query} would open, or null when the query is empty. */
    @Nullable
    public static String searchUrl(@NonNull Context context, @NonNull String query) {
        return LauncherWebSearchEngines.searchUrl(engine(context), query);
    }

    /**
     * Opens {@code query} with the user's engine.
     *
     * @return the URL that was opened, or null when nothing could be opened
     */
    @Nullable
    public static String search(@NonNull Context context, @NonNull String query) {
        String url = searchUrl(context, query);
        if (url == null) return null;
        // The search itself is recorded under the query, not the result page: what the user
        // wants offered back is "the thing I searched for", not one engine URL among many.
        return open(context, url, query.trim()) ? url : null;
    }

    /**
     * Opens {@code url} in the default browser and records the visit.
     *
     * @param title what the palette should call this address later, or null for its host
     * @return false when the address is not an http(s) URL, or nothing can display it
     */
    public static boolean open(@NonNull Context context, @NonNull String url,
                               @Nullable String title) {
        String normalized = LauncherWebLinks.normalizeUrl(url);
        if (normalized == null) return false;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalized));
        // The launcher is the home task; a page has to open in its own, or Back from the browser
        // would land inside the home screen's stack.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Logger.logWarn(LOG_TAG, "No browser to open " + LauncherWebLinks.labelFor(normalized));
            return false;
        } catch (SecurityException e) {
            Logger.logWarn(LOG_TAG, "Refused to open " + LauncherWebLinks.labelFor(normalized)
                + ": " + e.getMessage());
            return false;
        }
        LauncherWebVisitStats.getInstance(context).recordVisit(normalized, title);
        return true;
    }
}
