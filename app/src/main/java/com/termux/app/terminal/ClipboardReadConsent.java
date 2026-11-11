package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides clipboard reads the Fast engine hands to the app. It holds no views: the sheet, the
 * clipboard and the state of the launcher come through {@link Ui}, and the request through
 * {@link Request}, so the policy runs on the JVM. Main thread only.
 *
 * <p>A request that needs consent waits in a queue per session until that pane is in front and the
 * terminal is showing; then one sheet asks, and every request of that session waiting on it
 * gets the same answer. Only one sheet is open at a time.
 */
public final class ClipboardReadConsent {

    /** The parts of a read request the policy uses. */
    public interface Request {
        void allow(@Nullable String text);
        void deny();
        boolean isPasted();
        boolean isListingOnly();
    }

    public enum Choice { DENY, ONCE, SESSION, ALWAYS }

    /** The sheet's way back into the policy. */
    public interface Callback {
        void onChoice(@NonNull Choice choice);
        /** The sheet closed, however it closed; after a choice this does nothing. */
        void onDismissed();
    }

    public interface Ui {
        /** Whether "Let programs read the clipboard" is on. */
        boolean isReadSettingOn();
        /** Whether the terminal is the page on screen, at rest. */
        boolean isTerminalShowing();
        /** The session of the pane in front, or null. */
        @Nullable Object frontSession();
        /** The foreground program of the session's pty, or null when it cannot be read. */
        @Nullable String programName(@NonNull Object session);
        /** The clipboard's text, or null when it holds none. */
        @Nullable String readClipboard();
        /** Opens the consent sheet; false when it cannot be shown now. */
        boolean showSheet(@Nullable String programName, @NonNull Callback callback);
        /** Closes the consent sheet if it is open. */
        void closeSheet();
    }

    private static final class Entry {
        final Request request;
        @Nullable final String name;
        final boolean needsConsent;

        Entry(Request request, @Nullable String name, boolean needsConsent) {
            this.request = request;
            this.name = name;
            this.needsConsent = needsConsent;
        }
    }

    private final Ui mUi;
    private final ClipboardReadGrants mGrants;
    private final Map<Object, List<Entry>> mQueues = new IdentityHashMap<>();

    @Nullable private Object mSheetSession;
    @Nullable private String mSheetName;
    @Nullable private List<Entry> mSheetWaiting;

    public ClipboardReadConsent(@NonNull Ui ui, @NonNull ClipboardReadGrants grants) {
        mUi = ui;
        mGrants = grants;
    }

    public void onRequest(@NonNull Object session, @NonNull Request request) {
        boolean pasted = request.isPasted();
        if (!mUi.isReadSettingOn() && !pasted) {
            request.deny();
            return;
        }
        boolean needsConsent = !pasted && !request.isListingOnly();
        String name = needsConsent ? mUi.programName(session) : null;
        List<Entry> queue = mQueues.get(session);
        if (queue == null) {
            queue = new ArrayList<>();
            mQueues.put(session, queue);
        }
        queue.add(new Entry(request, name, needsConsent));
        pump();
    }

    /** The front pane or the launcher's visibility may have changed. */
    public void onFrontMaybeChanged() {
        pump();
    }

    /** The session ended: its waiting requests are denied, its sheet closed, its grants forgotten. */
    public void onSessionFinished(@NonNull Object session) {
        List<Entry> queue = mQueues.remove(session);
        if (queue != null) for (Entry entry : queue) entry.request.deny();
        if (mSheetSession == session) {
            List<Entry> waiting = mSheetWaiting;
            clearSheet();
            if (waiting != null) for (Entry entry : waiting) entry.request.deny();
            mUi.closeSheet();
        }
        mGrants.forgetSession(session);
        pump();
    }

    private boolean isGranted(@NonNull Object session, @NonNull Entry entry) {
        return !entry.needsConsent
            || mGrants.isSessionGranted(session, entry.name)
            || mGrants.isAlwaysGranted(entry.name);
    }

    private void clearSheet() {
        mSheetSession = null;
        mSheetName = null;
        mSheetWaiting = null;
    }

    private void pump() {
        if (!mUi.isTerminalShowing()) return;
        Object front = mUi.frontSession();
        if (front == null) return;
        List<Entry> queue = mQueues.get(front);
        if (queue == null || queue.isEmpty()) return;
        List<Entry> taken = new ArrayList<>(queue);
        queue.clear();
        List<Entry> asking = new ArrayList<>();
        String clipboard = null;
        boolean read = false;
        for (Entry entry : taken) {
            if (isGranted(front, entry)) {
                if (!read) {
                    clipboard = mUi.readClipboard();
                    read = true;
                }
                entry.request.allow(clipboard);
            } else {
                asking.add(entry);
            }
        }
        if (asking.isEmpty()) return;
        if (mSheetSession == front && mSheetWaiting != null) {
            // Same session, sheet already up: these get its answer.
            mSheetWaiting.addAll(asking);
            return;
        }
        if (mSheetSession != null) {
            // Another session's sheet is up: wait for it to close.
            queue.addAll(asking);
            return;
        }
        mSheetSession = front;
        mSheetName = asking.get(0).name;
        mSheetWaiting = asking;
        final Object sheetSession = front;
        final List<Entry> sheetWaiting = asking;
        boolean shown = mUi.showSheet(mSheetName, new Callback() {
            @Override public void onChoice(@NonNull Choice choice) {
                if (mSheetWaiting != sheetWaiting) return;
                decide(sheetSession, sheetWaiting, choice);
            }

            @Override public void onDismissed() {
                if (mSheetWaiting != sheetWaiting) return;
                decide(sheetSession, sheetWaiting, Choice.DENY);
            }
        });
        if (!shown && mSheetWaiting == sheetWaiting) {
            clearSheet();
            queue.addAll(asking);
        }
    }

    private void decide(Object session, List<Entry> waiting, Choice choice) {
        String name = mSheetName;
        clearSheet();
        if (choice == Choice.DENY) {
            for (Entry entry : waiting) entry.request.deny();
        } else {
            if (name != null) {
                if (choice == Choice.SESSION) mGrants.allowForSession(session, name);
                else if (choice == Choice.ALWAYS) mGrants.allowAlways(name);
            }
            String clipboard = mUi.readClipboard();
            for (Entry entry : waiting) entry.request.allow(clipboard);
        }
        mUi.closeSheet();
        pump();
    }
}
