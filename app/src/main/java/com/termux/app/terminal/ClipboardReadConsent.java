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
 * <p>A request that needs consent waits in a queue per session until its program's name is known,
 * that pane is in front and the terminal is showing; then one sheet asks for that program, and
 * every request of the same session and the same program waiting on it gets the same answer.
 * Another program's requests wait for their own sheet, and a request whose program could not be
 * named is asked alone. Only one sheet is open at a time.
 *
 * <p>Waiting requests are bounded: at most {@link #MAX_WAITING_PER_SESSION} per session and
 * {@link #MAX_WAITING} in all; a request past either limit is denied at once.
 */
public final class ClipboardReadConsent {

    /** The most requests one session may have waiting, counting those on its open sheet. */
    static final int MAX_WAITING_PER_SESSION = 8;
    /** The most requests waiting across every session. */
    static final int MAX_WAITING = 32;

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

    /** Where a program's name arrives, on the main thread. */
    public interface NameCallback {
        void onName(@Nullable String programName);
    }

    public interface Ui {
        /** Whether "Let programs read the clipboard" is on. */
        boolean isReadSettingOn();
        /** Whether the terminal is the page on screen, at rest. */
        boolean isTerminalShowing();
        /** The session of the pane in front, or null. */
        @Nullable Object frontSession();
        /**
         * Finds the foreground program of the session's pty as it is now and hands its name (null
         * when it cannot be read) to {@code done} on the main thread, now or later. The name is
         * read without file I/O on the calling thread.
         */
        void resolveProgramName(@NonNull Object session, @NonNull NameCallback done);
        /** The clipboard's text, or null when it holds none. */
        @Nullable String readClipboard();
        /** Opens the consent sheet; false when it cannot be shown now. */
        boolean showSheet(@Nullable String programName, @NonNull Callback callback);
        /** Closes the consent sheet if it is open. */
        void closeSheet();
    }

    private static final class Entry {
        final Request request;
        final boolean needsConsent;
        @Nullable String name;
        boolean resolved;
        boolean answered;

        Entry(Request request, boolean needsConsent) {
            this.request = request;
            this.needsConsent = needsConsent;
            this.resolved = !needsConsent;
        }

        void allow(@Nullable String text) {
            if (answered) return;
            answered = true;
            request.allow(text);
        }

        void deny() {
            if (answered) return;
            answered = true;
            request.deny();
        }

        /** Whether this request may share a sheet with {@code other}: both from one named program. */
        boolean sameProgram(@NonNull Entry other) {
            return name != null && name.equals(other.name);
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
        if (waitingFor(session) >= MAX_WAITING_PER_SESSION || waitingTotal() >= MAX_WAITING) {
            request.deny();
            return;
        }
        boolean needsConsent = !pasted && !request.isListingOnly();
        final Entry entry = new Entry(request, needsConsent);
        List<Entry> queue = mQueues.get(session);
        if (queue == null) {
            queue = new ArrayList<>();
            mQueues.put(session, queue);
        }
        queue.add(entry);
        if (needsConsent) {
            mUi.resolveProgramName(session, name -> {
                if (entry.answered || entry.resolved) return;
                entry.name = name;
                entry.resolved = true;
                pump();
            });
        }
        pump();
    }

    /** The front pane or the launcher's visibility may have changed. */
    public void onFrontMaybeChanged() {
        pump();
    }

    /** The session ended: its waiting requests are denied, its sheet closed, its grants forgotten. */
    public void onSessionFinished(@NonNull Object session) {
        List<Entry> queue = mQueues.remove(session);
        if (queue != null) for (Entry entry : queue) entry.deny();
        if (mSheetSession == session) {
            List<Entry> waiting = mSheetWaiting;
            clearSheet();
            if (waiting != null) for (Entry entry : waiting) entry.deny();
            mUi.closeSheet();
        }
        mGrants.forgetSession(session);
        pump();
    }

    private int waitingFor(@NonNull Object session) {
        List<Entry> queue = mQueues.get(session);
        int n = queue == null ? 0 : queue.size();
        if (mSheetSession == session && mSheetWaiting != null) n += mSheetWaiting.size();
        return n;
    }

    private int waitingTotal() {
        int n = mSheetWaiting == null ? 0 : mSheetWaiting.size();
        for (List<Entry> queue : mQueues.values()) n += queue.size();
        return n;
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
        boolean sheetUpHere = mSheetSession == front && mSheetWaiting != null;
        boolean sheetUpElsewhere = mSheetSession != null && !sheetUpHere;
        Entry sheetFirst = null;
        List<Entry> asking = null;
        String clipboard = null;
        boolean read = false;
        for (Entry entry : taken) {
            if (entry.answered) continue;
            if (!entry.resolved) {
                // Its program is not known yet: it waits.
                queue.add(entry);
            } else if (isGranted(front, entry)) {
                if (!read) {
                    clipboard = mUi.readClipboard();
                    read = true;
                }
                entry.allow(clipboard);
            } else if (sheetUpHere) {
                // This session's sheet is up: only requests from its program share its answer.
                if (mSheetName != null && mSheetName.equals(entry.name)) mSheetWaiting.add(entry);
                else queue.add(entry);
            } else if (sheetUpElsewhere) {
                // Another session's sheet is up: wait for it to close.
                queue.add(entry);
            } else if (sheetFirst == null) {
                sheetFirst = entry;
                asking = new ArrayList<>();
                asking.add(entry);
            } else if (entry.sameProgram(sheetFirst)) {
                asking.add(entry);
            } else {
                // Another program: it gets a sheet of its own after this one.
                queue.add(entry);
            }
        }
        if (asking == null) return;
        mSheetSession = front;
        mSheetName = sheetFirst.name;
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
            queue.addAll(0, asking);
        }
    }

    private void decide(Object session, List<Entry> waiting, Choice choice) {
        String name = mSheetName;
        clearSheet();
        if (choice == Choice.DENY) {
            for (Entry entry : waiting) entry.deny();
        } else {
            if (name != null) {
                if (choice == Choice.SESSION) mGrants.allowForSession(session, name);
                else if (choice == Choice.ALWAYS) mGrants.allowAlways(name);
            }
            String clipboard = mUi.readClipboard();
            for (Entry entry : waiting) entry.allow(clipboard);
        }
        mUi.closeSheet();
        pump();
    }
}
