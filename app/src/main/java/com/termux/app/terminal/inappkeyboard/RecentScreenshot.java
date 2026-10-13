package com.termux.app.terminal.inappkeyboard;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.terminal.TerminalClipboard;
import com.termux.shared.android.PermissionUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * The newest screenshot in the media store, followed while the in-app keyboard is up so its paste
 * key can offer it. It reads only with the storage permission the launcher already holds (it never
 * asks), queries off the main thread, and keeps four values of the newest row, never a picture.
 */
public final class RecentScreenshot {

    /** The newest screenshot: enough to paste it, nothing decoded. */
    public static final class Shot {
        public final long id;
        @NonNull public final String mime;
        /** When it was added to the media store, wall clock. */
        public final long takenMillis;

        public Shot(long id, @NonNull String mime, long takenMillis) {
            this.id = id;
            this.mime = mime;
            this.takenMillis = takenMillis;
        }

        /** The media store row, which opens to the picture's bytes. */
        @NonNull
        public Uri uri() {
            return ContentUris.withAppendedId(COLLECTION, id);
        }
    }

    /** Told on the main thread whenever the newest screenshot is read; null for none. */
    public interface Listener {
        void onRecentScreenshot(@Nullable Shot shot);
    }

    private static final Uri COLLECTION = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    /**
     * Taking a screenshot inserts a pending row and then updates it, so a change waits this long
     * for the next before reading.
     */
    @VisibleForTesting static final long REQUERY_DEBOUNCE_MS = 300;

    private static final String[] PROJECTION = {
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.MIME_TYPE,
    };

    @NonNull private final Context mContext;
    @NonNull private final Executor mWorker;
    @NonNull private final Handler mMain;
    @NonNull private final Listener mListener;
    private final ContentObserver mObserver;
    private final Runnable mQuery = this::query;
    private boolean mStarted;
    private boolean mObserving;
    /** Bumped by every query and by stop, so only the latest query's answer is told. */
    private int mGeneration;

    public RecentScreenshot(@NonNull Context context, @NonNull Executor worker, @NonNull Handler main,
                            @NonNull Listener listener) {
        mContext = Objects.requireNonNull(context, "context");
        mWorker = Objects.requireNonNull(worker, "worker");
        mMain = Objects.requireNonNull(main, "main");
        mListener = Objects.requireNonNull(listener, "listener");
        mObserver = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) {
                mMain.removeCallbacks(mQuery);
                mMain.postDelayed(mQuery, REQUERY_DEBOUNCE_MS);
            }
        };
    }

    /**
     * Follow the newest screenshot and read it now. Without the storage permission nothing is
     * followed and the listener is never told. A second start is a no-op.
     */
    public void start() {
        if (mStarted)
            return;
        mStarted = true;
        query();
    }

    /** Stop following; an answer still on its way is dropped. */
    public void stop() {
        if (!mStarted)
            return;
        mStarted = false;
        mGeneration++;
        mMain.removeCallbacks(mQuery);
        if (mObserving) {
            mObserving = false;
            try {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /**
     * Read the newest screenshot on the worker, permission check included, so a keyboard coming
     * up pays for neither; changes are followed once a read has been allowed.
     */
    private void query() {
        if (!mStarted)
            return;
        int generation = ++mGeneration;
        Context context = mContext;
        try {
            mWorker.execute(() -> {
                if (!PermissionUtils.checkPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE))
                    return;
                Shot shot = newest(context.getContentResolver(), Build.VERSION.SDK_INT);
                mMain.post(() -> {
                    if (!mStarted || generation != mGeneration)
                        return;
                    observe();
                    mListener.onRecentScreenshot(shot);
                });
            });
        } catch (RejectedExecutionException e) {
            // The worker is gone with the keyboard; nothing is left to tell.
        }
    }

    private void observe() {
        if (mObserving)
            return;
        try {
            mContext.getContentResolver().registerContentObserver(COLLECTION, true, mObserver);
            mObserving = true;
        } catch (RuntimeException e) {
            // A store that cannot be followed is read again on the next show.
        }
    }

    /** The newest screenshot row, or null for none, no access or a store that fails. Off the main thread. */
    @Nullable
    @VisibleForTesting
    static Shot newest(@NonNull ContentResolver resolver, int sdk) {
        try (Cursor cursor = resolver.query(COLLECTION, PROJECTION, selection(sdk),
                new String[] {selectionPath(sdk)}, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor == null || !cursor.moveToFirst())
                return null;
            return fromRow(cursor.getLong(0), cursor.isNull(1) ? 0 : cursor.getLong(1), cursor.getString(2));
        } catch (RuntimeException e) {
            // SecurityException included: a store we may not read has no screenshot for us.
            return null;
        }
    }

    /** Screenshots by folder: the relative path from Android 10, the file path before it. */
    @SuppressWarnings("deprecation")
    @VisibleForTesting
    static String selection(int sdk) {
        if (sdk >= Build.VERSION_CODES.Q)
            return MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND "
                + MediaStore.MediaColumns.IS_PENDING + " = 0";
        return MediaStore.MediaColumns.DATA + " LIKE ?";
    }

    @VisibleForTesting
    static String selectionPath(int sdk) {
        return sdk >= Build.VERSION_CODES.Q ? "%Screenshots/%" : "%/Screenshots/%";
    }

    /** A row as a shot: a concrete picture type and a time it was added, or null. */
    @Nullable
    @VisibleForTesting
    static Shot fromRow(long id, long dateAddedSeconds, @Nullable String mime) {
        String type = mime == null ? null : TerminalClipboard.pictureType(Collections.singletonList(mime));
        if (type == null || dateAddedSeconds <= 0)
            return null;
        return new Shot(id, type, dateAddedSeconds * 1000);
    }
}
