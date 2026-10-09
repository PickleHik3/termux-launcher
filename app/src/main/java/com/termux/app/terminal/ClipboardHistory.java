package com.termux.app.terminal;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * What the in-app keyboard's clipboard panel shows: the text copied <em>inside the launcher</em>,
 * newest first, with the items the user pinned kept above the rest.
 *
 * <p>The launcher is the only source. Every way of copying in here — an {@code OSC 52} write or
 * {@code launcherctl clipboard copy} through {@link ShellSignals#clipboardWrite}, the terminal's
 * selection toolbar, the keyboard's copy and cut keys, the copy-selected action, a link or a
 * hint copied from a sheet, a find-mode yank — calls {@link #record} beside its write to the
 * Android clipboard. Nothing here ever reads the Android clipboard or listens to it: what other
 * apps copy is theirs, and the keyboard's paste key keeps pasting whatever the Android clipboard
 * holds, exactly as before.
 *
 * <p>Bounded, per the memory rules: at most {@link #MAX_RECENT} recent items and
 * {@link #MAX_PINNED} pins, none longer than {@link #MAX_TEXT_BYTES} of UTF-8 — a larger copy
 * still reaches the Android clipboard and is simply not kept. Recent items live in memory and
 * are gone when the process dies; only the pins are written to disk, as a small JSON file in the
 * app's private files directory, and read back on the first use after a start. Copying a text
 * that is already here moves it to the top rather than adding it twice.
 *
 * <p>Everything is meant for the main thread, which is where every copy and every tap on the
 * panel already runs; only the pin file's write is handed to {@code saveExecutor}.
 */
public final class ClipboardHistory {

    /** How many unpinned items are kept; the oldest goes when a newer one arrives. */
    public static final int MAX_RECENT = 30;

    /** How many items can be pinned; {@link #pin} refuses beyond it rather than evicting. */
    public static final int MAX_PINNED = 20;

    /** The largest text kept, in UTF-8 bytes. Larger copies are refused, never truncated. */
    public static final int MAX_TEXT_BYTES = 16 * 1024;

    static final String PIN_FILE_NAME = "keyboard-clipboard-pins.json";
    static final int VERSION = 1;

    private static final String LOG_TAG = "ClipboardHistory";

    /** Where the pins live between runs. */
    public interface PinStore {
        @NonNull List<String> load();

        void save(@NonNull List<String> pins);
    }

    /** Told after every change, on the thread that made it. */
    public interface Listener {
        void onClipboardHistoryChanged();
    }

    /** One row of the panel. */
    public static final class Item {
        @NonNull public final String text;
        public final boolean pinned;

        Item(@NonNull String text, boolean pinned) {
            this.text = text;
            this.pinned = pinned;
        }
    }

    @Nullable private static ClipboardHistory sInstance;

    /** The process-wide history, created on first use with the pins read back from disk. */
    @NonNull
    public static synchronized ClipboardHistory get(@NonNull Context context) {
        if (sInstance == null) {
            File file = new File(context.getApplicationContext().getFilesDir(), PIN_FILE_NAME);
            sInstance = new ClipboardHistory(new FilePinStore(file),
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "termux-clipboard-pins");
                    thread.setDaemon(true);
                    return thread;
                }));
        }
        return sInstance;
    }

    /** Replaces the process-wide history for a test; null puts the lazy default back. */
    @VisibleForTesting
    public static synchronized void installForTest(@Nullable ClipboardHistory history) {
        sInstance = history;
    }

    /**
     * A copy made inside the launcher: on the Android clipboard, where every paste reads it, and
     * in the history. The one call for a copy path that has no {@link ShellSignals} of its own.
     */
    public static void copy(@NonNull Context context, @Nullable String text) {
        if (text == null) return;
        ShareUtils.copyTextToClipboard(context, text);
        get(context).record(text);
    }

    @NonNull private final PinStore mStore;
    @NonNull private final Executor mSaveExecutor;
    /** Newest first. */
    @NonNull private final ArrayList<String> mPinned = new ArrayList<>();
    /** Newest first. */
    @NonNull private final ArrayList<String> mRecent = new ArrayList<>();
    @Nullable private Listener mListener;

    public ClipboardHistory(@NonNull PinStore store, @NonNull Executor saveExecutor) {
        mStore = store;
        mSaveExecutor = saveExecutor;
        for (String pin : store.load()) {
            if (pin == null || !fits(pin) || mPinned.contains(pin)) continue;
            if (mPinned.size() >= MAX_PINNED) break;
            mPinned.add(pin);
        }
    }

    /** Whether {@code text} is something the history keeps: not blank, and within the byte cap. */
    public static boolean fits(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) return false;
        // A cheap upper bound first: a string this short cannot exceed the cap in UTF-8.
        if (text.length() * 3L <= MAX_TEXT_BYTES) return true;
        return text.getBytes(StandardCharsets.UTF_8).length <= MAX_TEXT_BYTES;
    }

    /**
     * A copy happened. The text goes to the top of the recent items, or stays where it is when
     * it is pinned; a recent item copied again moves up rather than doubling. False when the
     * text is refused: blank, or over {@link #MAX_TEXT_BYTES}.
     */
    public boolean record(@Nullable String text) {
        if (!fits(text)) return false;
        if (mPinned.contains(text)) return true;
        mRecent.remove(text);
        mRecent.add(0, text);
        while (mRecent.size() > MAX_RECENT) mRecent.remove(mRecent.size() - 1);
        notifyChanged();
        return true;
    }

    /** Pins a recent item, newest pin first. False when it is not here or the pins are full. */
    public boolean pin(@NonNull String text) {
        if (mPinned.contains(text)) return true;
        if (mPinned.size() >= MAX_PINNED || !mRecent.remove(text)) return false;
        mPinned.add(0, text);
        savePins();
        notifyChanged();
        return true;
    }

    /** Unpins an item: it goes back to the top of the recent items. False when it was not pinned. */
    public boolean unpin(@NonNull String text) {
        if (!mPinned.remove(text)) return false;
        mRecent.remove(text);
        mRecent.add(0, text);
        while (mRecent.size() > MAX_RECENT) mRecent.remove(mRecent.size() - 1);
        savePins();
        notifyChanged();
        return true;
    }

    /** Drops an item, pinned or not. False when it was not here. */
    public boolean remove(@NonNull String text) {
        boolean pinned = mPinned.remove(text);
        boolean recent = mRecent.remove(text);
        if (!pinned && !recent) return false;
        if (pinned) savePins();
        notifyChanged();
        return true;
    }

    /** Clears the recent items. The pins stay: they are removed one by one, deliberately. */
    public void clearRecent() {
        if (mRecent.isEmpty()) return;
        mRecent.clear();
        notifyChanged();
    }

    public boolean isEmpty() {
        return mPinned.isEmpty() && mRecent.isEmpty();
    }

    public int recentCount() {
        return mRecent.size();
    }

    public int pinnedCount() {
        return mPinned.size();
    }

    /** The pinned texts, newest pin first. A snapshot. */
    @NonNull
    public List<String> pinned() {
        return Collections.unmodifiableList(new ArrayList<>(mPinned));
    }

    /** The unpinned texts, newest first. A snapshot. */
    @NonNull
    public List<String> recent() {
        return Collections.unmodifiableList(new ArrayList<>(mRecent));
    }

    /** Every row the panel shows, pinned first, each section newest first. A snapshot. */
    @NonNull
    public List<Item> items() {
        ArrayList<Item> items = new ArrayList<>(mPinned.size() + mRecent.size());
        for (String text : mPinned) items.add(new Item(text, true));
        for (String text : mRecent) items.add(new Item(text, false));
        return Collections.unmodifiableList(items);
    }

    /** One listener at a time: the panel while it is open. Null to stop. */
    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    private void notifyChanged() {
        Listener listener = mListener;
        if (listener != null) listener.onClipboardHistoryChanged();
    }

    private void savePins() {
        final List<String> snapshot = new ArrayList<>(mPinned);
        mSaveExecutor.execute(() -> mStore.save(snapshot));
    }

    /** The pins as {@code {"v":1,"pins":[…]}}, written whole and renamed into place. */
    public static final class FilePinStore implements PinStore {

        @NonNull private final File mFile;

        public FilePinStore(@NonNull File file) {
            mFile = file;
        }

        @Override
        @NonNull
        public List<String> load() {
            if (!mFile.isFile()) return Collections.emptyList();
            try (InputStream in = new FileInputStream(mFile)) {
                JSONObject root = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
                JSONArray pins = root.optJSONArray("pins");
                if (pins == null) return Collections.emptyList();
                ArrayList<String> result = new ArrayList<>(pins.length());
                for (int i = 0; i < pins.length(); i++) {
                    String pin = pins.optString(i, null);
                    if (pin != null) result.add(pin);
                }
                return result;
            } catch (IOException | JSONException | RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Could not read " + mFile + ": " + e);
                return Collections.emptyList();
            }
        }

        @Override
        public void save(@NonNull List<String> pins) {
            File parent = mFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                Logger.logWarn(LOG_TAG, "Could not create " + parent);
                return;
            }
            if (pins.isEmpty()) {
                if (mFile.exists() && !mFile.delete())
                    Logger.logWarn(LOG_TAG, "Could not delete " + mFile);
                return;
            }
            File temp = new File(parent, mFile.getName() + ".tmp");
            try {
                JSONObject root = new JSONObject();
                root.put("v", VERSION);
                root.put("pins", new JSONArray(pins));
                byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = new FileOutputStream(temp)) {
                    out.write(bytes);
                }
                if (!temp.renameTo(mFile))
                    throw new IOException("rename to " + mFile + " failed");
            } catch (IOException | JSONException | RuntimeException e) {
                Logger.logWarn(LOG_TAG, "Could not write " + mFile + ": " + e);
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }

        private static byte[] readAll(@NonNull InputStream in) throws IOException {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return buffer.toByteArray();
        }
    }
}
