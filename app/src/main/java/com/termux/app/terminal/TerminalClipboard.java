package com.termux.app.terminal;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.FileProvider;

import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.ClipboardOffer;
import com.termux.terminal.KittyClipboard;
import com.termux.terminal.TerminalEmulator;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The terminal's side of the Android clipboard beyond plain text: the one place that decides what
 * a paste into a pane is, and how a program reads and writes pictures.
 *
 * <ul>
 *   <li>{@link #paste}: every way the user pastes into a pane lands here. A program in kitty's
 *       paste-events mode is sent a paste event instead of anything typed; a picture is saved
 *       under the Termux tmp folder and its path is pasted, which is how shells and coding
 *       agents take pictures; text is pasted exactly as it always was.</li>
 *   <li>{@link #pasteContent}: a picture a keyboard inserts goes the same way.</li>
 *   <li>{@link #read} and {@link #write}: a program's {@code OSC 5522} read and picture write, under
 *       the rules {@link ShellSignals} keeps for the clipboard.</li>
 * </ul>
 *
 * <p>Called on the main thread. Reading another app's picture, re-encoding it and writing files
 * happen on one background thread that exists only while there is work; what reaches a pane or
 * the clipboard afterwards is posted back to the main thread, except a read's answer, which the
 * worker writes itself so a program that is slow to read it never holds up the home screen.
 */
public final class TerminalClipboard {

    private static final String LOG_TAG = "TerminalClipboard";

    /** The FileProvider authority behind copied pictures; the manifest declares the same. */
    static final String AUTHORITY = TermuxConstants.TERMUX_PACKAGE_NAME + ".clipboard";

    /** Largest picture pasted as a file: it is streamed to disk, never held. */
    static final long MAX_PASTE_BYTES = 64L * 1024 * 1024;

    private static final String TEXT_PLAIN = "text/plain";
    private static final String TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8";

    @NonNull private final Context mContext;
    @NonNull private final ShellSignals mSignals;
    @NonNull private final Executor mWorker;
    @NonNull private final Executor mMain;
    @NonNull private final File mPastedDir;
    @NonNull private final File mCopiedDir;

    /**
     * A picture a keyboard inserted while its pane was in paste-events mode: the program reads
     * that, not the clipboard, with the paste event's password. One per pane, replaced by its
     * next paste.
     */
    private final Map<TerminalEmulator, ClipboardOffer> mInsertedOffers =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** The production bridge: pasted pictures under {@code $PREFIX/tmp/clipboard}, copied ones in the cache. */
    @NonNull
    public static TerminalClipboard create(@NonNull Context context, @NonNull ShellSignals signals) {
        Handler main = new Handler(Looper.getMainLooper());
        // No thread is kept while there is nothing to do: the launcher never exits.
        ThreadPoolExecutor worker = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            runnable -> new Thread(runnable, "TerminalClipboard"));
        return new TerminalClipboard(context, signals, worker, main::post,
            new File(TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, "clipboard"),
            new File(context.getCacheDir(), "clipboard"));
    }

    @VisibleForTesting
    TerminalClipboard(@NonNull Context context, @NonNull ShellSignals signals, @NonNull Executor worker,
                      @NonNull Executor main, @NonNull File pastedDir, @NonNull File copiedDir) {
        mContext = context;
        mSignals = signals;
        mWorker = worker;
        mMain = main;
        mPastedDir = pastedDir;
        mCopiedDir = copiedDir;
    }

    // --- The user pastes ---

    /**
     * Paste the clipboard into {@code emulator}'s pane, the one decision every paste path shares.
     * The caller has already decided the paste may happen (the launcher on screen, a pane to
     * paste into).
     */
    public void paste(@NonNull TerminalEmulator emulator) {
        ClipData clip = primaryClip();
        ClipData.Item item = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0);
        String image = item == null ? null : pictureType(clip.getDescription(), item);
        if (emulator.isPasteEventsMode()) {
            mInsertedOffers.remove(emulator);
            List<String> types = pasteEventTypes(item, image);
            if (!types.isEmpty()) emulator.sendPasteEvent(types);
            return;
        }
        if (image != null) {
            Uri uri = item.getUri();
            pastePicture(emulator, () -> openUri(uri), image, false, null);
            return;
        }
        String text = ShareUtils.getTextStringFromClipboardIfSet(mContext, true);
        if (text != null) emulator.paste(text);
    }

    /**
     * A picture a keyboard inserted into {@code emulator}'s pane. It is saved like a pasted one;
     * a program in paste-events mode is told of it and reads it from that file. {@code release}
     * gives up the keyboard's grant once the picture has been read, whatever happened.
     */
    public void pasteContent(@NonNull TerminalEmulator emulator, @NonNull Uri uri, @NonNull String mime,
                             @NonNull Runnable release) {
        String type = mime.toLowerCase(Locale.ROOT);
        pastePicture(emulator, () -> openUri(uri), type, true, release);
    }

    /**
     * Save a picture to the pasted folder off the main thread, then paste its path — or, for one
     * a keyboard {@code inserted} into a pane in paste-events mode, offer the saved file to the
     * program with a paste event.
     */
    private void pastePicture(@NonNull TerminalEmulator emulator, @NonNull ClipboardImageOffer.Source source,
                              @NonNull String type, boolean inserted, @Nullable Runnable release) {
        mWorker.execute(() -> {
            File file;
            try (InputStream in = source.open()) {
                file = ClipboardFiles.save(mPastedDir, System.currentTimeMillis(), type, in,
                    MAX_PASTE_BYTES, ClipboardFiles.KEEP_PASTED);
            } catch (IOException | RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save the pasted picture", e);
                return;
            } finally {
                if (release != null) release.run();
            }
            mMain.execute(() -> {
                if (inserted && emulator.isPasteEventsMode()) {
                    // Inserted, not on the clipboard: the paste event's read is answered from the file.
                    mInsertedOffers.put(emulator, new ClipboardImageOffer(() -> new FileInputStream(file), type, null));
                    emulator.sendPasteEvent(ClipboardImageOffer.typesFor(type));
                } else {
                    emulator.paste(file.getAbsolutePath());
                }
            });
        });
    }

    // --- A program reads ---

    /**
     * Answer a program's {@code OSC 5522} read in {@code emulator}'s pane: refused (null) unless
     * {@link ShellSignals#clipboardReadRefusal(boolean)} allows it, the clipboard's text at once,
     * or a picture on the worker, where its bytes are read when asked for.
     */
    public void read(@Nullable TerminalEmulator emulator, boolean pasted, @NonNull KittyClipboard.ReadCallback callback) {
        if (mSignals.clipboardReadRefusal(pasted) != null) {
            callback.onOffer(null);
            return;
        }
        ClipboardOffer inserted = pasted && emulator != null ? mInsertedOffers.remove(emulator) : null;
        if (inserted != null) {
            mWorker.execute(() -> callback.onOffer(inserted));
            return;
        }
        ClipData clip = primaryClip();
        ClipData.Item item = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0);
        String image = item == null ? null : pictureType(clip.getDescription(), item);
        if (image == null) {
            String text = ShareUtils.getTextStringFromClipboardIfSet(mContext, true);
            callback.onOffer(text == null ? null : ClipboardOffer.ofText(text));
            return;
        }
        Uri uri = item.getUri();
        ClipboardOffer offer = new ClipboardImageOffer(() -> openUri(uri), image, itemText(item));
        mWorker.execute(() -> callback.onOffer(offer));
    }

    // --- A program writes ---

    /**
     * Put the picture of a program's {@code OSC 5522} write on the clipboard, with its text as a
     * second item when it carried any. The picture is saved under the cache and handed over as a
     * content link only this app's provider serves; the clipboard grants each reader access. Only
     * while the launcher is on screen, as for text ({@link ShellSignals#clipboardWritable()}).
     * Pictures are not recorded in the clipboard history, which is text.
     */
    public void write(@NonNull Map<String, byte[]> images, @Nullable String text) {
        if (images.isEmpty() || !mSignals.clipboardWritable()) return;
        // The first type the program sent is the one it put first; one picture is one clip.
        Map.Entry<String, byte[]> first = images.entrySet().iterator().next();
        String type = first.getKey();
        byte[] bytes = first.getValue();
        mWorker.execute(() -> {
            Uri uri;
            try {
                File file = ClipboardFiles.save(mCopiedDir, System.currentTimeMillis(), type,
                    new ByteArrayInputStream(bytes), Long.MAX_VALUE, ClipboardFiles.KEEP_COPIED);
                uri = FileProvider.getUriForFile(mContext, AUTHORITY, file);
            } catch (IOException | RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to put the copied picture on the clipboard", e);
                return;
            }
            mMain.execute(() -> {
                if (!mSignals.clipboardWritable()) return;
                ClipboardManager manager = clipboardManager();
                if (manager == null) return;
                try {
                    manager.setPrimaryClip(pictureClip(uri, type, text));
                } catch (RuntimeException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to put the copied picture on the clipboard", e);
                }
            });
        });
    }

    /** A clip of the picture at {@code uri}, with {@code text} as a second item when there is any. */
    @NonNull
    static ClipData pictureClip(@NonNull Uri uri, @NonNull String type, @Nullable String text) {
        String[] types = text == null ? new String[] {type} : new String[] {type, ClipDescription.MIMETYPE_TEXT_PLAIN};
        ClipData clip = new ClipData(new ClipDescription("Picture", types), new ClipData.Item(uri));
        if (text != null) clip.addItem(new ClipData.Item(text));
        return clip;
    }

    // --- What the clipboard holds ---

    /**
     * The picture type of a clip whose first item is a picture link and nothing else, from its
     * description; null for text, for a link to something else, and for a clip with no type.
     */
    @Nullable
    static String pictureType(@Nullable ClipDescription description, @NonNull ClipData.Item item) {
        if (item.getUri() == null || itemText(item) != null || description == null) return null;
        List<String> types = new ArrayList<>(description.getMimeTypeCount());
        for (int i = 0; i < description.getMimeTypeCount(); i++) types.add(description.getMimeType(i));
        return pictureType(types);
    }

    /** The first concrete {@code image/*} type in {@code types}, lower-cased, or null. */
    @Nullable
    public static String pictureType(@NonNull List<String> types) {
        for (String type : types) {
            if (type == null) continue;
            String normal = type.trim().toLowerCase(Locale.ROOT);
            if (normal.startsWith("image/") && !normal.equals("image/*") && normal.length() > "image/".length())
                return normal;
        }
        return null;
    }

    /**
     * The types a paste event offers for {@code item}: a picture in its own type and PNG, the
     * text types when the item has text — or, for anything else that is not empty, the text it
     * pastes as, as an ordinary paste would.
     */
    @NonNull
    static List<String> pasteEventTypes(@Nullable ClipData.Item item, @Nullable String image) {
        if (item == null) return Collections.emptyList();
        boolean text = image == null
            ? itemText(item) != null || item.getUri() != null || item.getIntent() != null
            : itemText(item) != null;
        return pasteEventTypes(text, image);
    }

    /** {@link #pasteEventTypes(ClipData.Item, String)} from whether there is text and the picture type. */
    @NonNull
    static List<String> pasteEventTypes(boolean text, @Nullable String image) {
        List<String> types = new ArrayList<>(4);
        if (text) {
            types.add(TEXT_PLAIN);
            types.add(TEXT_PLAIN_UTF8);
        }
        if (image != null) types.addAll(ClipboardImageOffer.typesFor(image));
        return types;
    }

    /** The item's own text, or null: a bare link is not text. */
    @Nullable
    private static String itemText(@NonNull ClipData.Item item) {
        CharSequence text = item.getText();
        return text == null ? null : text.toString();
    }

    @NonNull
    private InputStream openUri(@NonNull Uri uri) throws IOException {
        InputStream in = mContext.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Cannot open " + uri);
        return in;
    }

    @Nullable
    private ClipboardManager clipboardManager() {
        return (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Nullable
    private ClipData primaryClip() {
        ClipboardManager manager = clipboardManager();
        try {
            return manager == null ? null : manager.getPrimaryClip();
        } catch (RuntimeException e) {
            // A clipboard service that throws is no reason for a paste to take the home screen down.
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the clipboard", e);
            return null;
        }
    }
}
