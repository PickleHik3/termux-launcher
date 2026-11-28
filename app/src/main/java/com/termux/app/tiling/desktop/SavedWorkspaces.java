package com.termux.app.tiling.desktop;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.notice.AppNotice;
import com.termux.app.tiling.SavedWorkspace;
import com.termux.app.tiling.SavedWorkspaceStore;
import com.termux.app.tiling.TilingWindow;
import com.termux.app.tiling.TilingWorkspace;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Saved Workspaces under Terl (#431; spec "Windows and places", glossary "Overview"): Save from a
 * Workspace's menu asks for a name and keeps the Workspace in front in {@link SavedWorkspaceStore};
 * Overview's Saved row lists them, loads one with a tap, and renames, deletes or marks one Open at
 * start from a hold. A saved Workspace loads as a new Workspace of the same name
 * ({@link TilingDesktop#loadSaved}) marked as that save's ({@link TilingWorkspace#savedId()}). Open
 * at start is decided once per process, on its cold start: each saved Workspace marked so loads
 * once, onto whichever desktop is alive when the files have been read (an activity recreated in
 * between still gets them), unless the desktop already holds a Workspace of that save (it came
 * back as it was).
 *
 * <p>The files are read and written on a thread of their own; every answer comes back on the main
 * thread.
 */
public final class SavedWorkspaces {

    private static final String LOG_TAG = "SavedWorkspaces";
    private static final Executor FILES = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "saved-workspaces");
        thread.setDaemon(true);
        return thread;
    });

    /** This process started cold and its Open at start Workspaces have not loaded yet. */
    private static boolean sOpenAtStartPending;
    /** The instance of the activity alive now, which Open at start loads onto. */
    @Nullable private static SavedWorkspaces sLive;

    @NonNull private final Activity mActivity;
    @NonNull private final TilingDesktop mDesktop;
    @NonNull private final SavedWorkspaceStore mStore;
    @NonNull private final Executor mFiles;
    @NonNull private final Executor mMain;
    @NonNull private final Runnable mBeforeTyping;

    /**
     * {@code beforeTyping} runs before a name field asks for the system keyboard (the activity's
     * {@code onSystemImeRequested()}).
     */
    public SavedWorkspaces(@NonNull Activity activity, @NonNull TilingDesktop desktop,
                           @NonNull SavedWorkspaceStore store, @NonNull Runnable beforeTyping) {
        this(activity, desktop, store, beforeTyping, FILES, new Handler(Looper.getMainLooper())::post);
    }

    SavedWorkspaces(@NonNull Activity activity, @NonNull TilingDesktop desktop, @NonNull SavedWorkspaceStore store,
                    @NonNull Runnable beforeTyping, @NonNull Executor files, @NonNull Executor main) {
        mActivity = activity;
        mDesktop = desktop;
        mStore = store;
        mBeforeTyping = beforeTyping;
        mFiles = files;
        mMain = main;
        sLive = this;
    }

    /** The activity goes: Open at start no longer loads onto this one. */
    public void release() {
        if (sLive == this) sLive = null;
    }

    /** Forgets this process's Open at start, as a new process starts without it. */
    @androidx.annotation.VisibleForTesting
    static void forgetProcessState() {
        sOpenAtStartPending = false;
        sLive = null;
    }

    /** Every saved Workspace by name, delivered on the main thread. */
    public void list(@NonNull Consumer<List<SavedWorkspaceStore.Entry>> onRead) {
        list(entry -> entry, onRead);
    }

    /**
     * Every saved Workspace by name, each turned by {@code map} on the file thread (a card's
     * picture is drawn there, not on the main thread), delivered on the main thread.
     */
    public <T> void list(@NonNull Function<SavedWorkspaceStore.Entry, T> map, @NonNull Consumer<List<T>> onRead) {
        mFiles.execute(() -> {
            List<SavedWorkspaceStore.Entry> entries = mStore.list();
            List<T> mapped = new ArrayList<>(entries.size());
            for (SavedWorkspaceStore.Entry entry : entries) mapped.add(map.apply(entry));
            mMain.execute(() -> onRead.accept(mapped));
        });
    }

    /** Loads saved Workspace {@code name} as a new Workspace and goes there. */
    public void open(@NonNull String name) {
        mFiles.execute(() -> {
            SavedWorkspace saved = mStore.load(name);
            mMain.execute(() -> {
                if (saved == null || !mDesktop.loadSaved(saved, name, true)) {
                    notice(mActivity.getString(R.string.saved_workspace_not_opened, name));
                }
            });
        });
    }

    /**
     * The desktop is installed; {@code coldStart}: in a new process ({@link TilingDesktop#startedCold}).
     * While this process's Open at start has not loaded, each saved Workspace marked so loads once,
     * the place in front staying, onto the desktop alive when the files are read, unless that
     * desktop already holds a Workspace of it.
     */
    public void openAtStart(boolean coldStart) {
        if (coldStart) sOpenAtStartPending = true;
        if (!sOpenAtStartPending) return;
        list(entries -> {
            SavedWorkspaces live = sLive;
            // An activity recreated meanwhile asked again; its own read loads onto it.
            if (!sOpenAtStartPending || live == null || !live.mDesktop.isLive()) return;
            sOpenAtStartPending = false;
            for (SavedWorkspaceStore.Entry entry : entries) {
                if (entry.workspace == null || !entry.workspace.openAtStart()) continue;
                if (live.mDesktop.holdsSaved(entry.workspace, entry.name)) continue;
                if (!live.mDesktop.loadSaved(entry.workspace, entry.name, false)) {
                    Logger.logWarn(LOG_TAG, "Could not open " + entry.name + " at start");
                }
            }
        });
    }

    // ---- Save, from the Workspace's menu ---------------------------------------------------------

    /** Asks for a name and saves the Workspace in front under it; nothing on Home or in an empty Workspace. */
    public void askToSave() {
        SavedWorkspace saved = mDesktop.frontAsSaved();
        TilingWindow main = mDesktop.frontMainWindow();
        if (saved == null || main == null) return;
        long windowId = main.id();
        askName(mActivity.getString(R.string.saved_workspace_save_title), mDesktop.frontName(),
            R.string.saved_workspace_save, name -> save(name, saved, windowId, false));
    }

    private void save(@NonNull String name, @NonNull SavedWorkspace saved, long windowId, boolean replace) {
        mFiles.execute(() -> {
            SavedWorkspaceStore.Result result = mStore.save(name, saved, replace);
            mMain.execute(() -> {
                switch (result) {
                    case OK:
                        // The Workspace takes the name, so Open at start knows it is already there.
                        mDesktop.nameWorkspaceOf(windowId, name, saved.id());
                        notice(mActivity.getString(R.string.saved_workspace_saved, name));
                        break;
                    case NAME_TAKEN:
                        confirm(mActivity.getString(R.string.saved_workspace_replace_title, name),
                            R.string.saved_workspace_replace_message, R.string.saved_workspace_replace,
                            () -> save(name, saved, windowId, true));
                        break;
                    case INVALID_NAME:
                        notice(mActivity.getString(R.string.saved_workspace_name_invalid));
                        break;
                    default:
                        notice(mActivity.getString(R.string.saved_workspace_not_saved));
                        break;
                }
            });
        });
    }

    // ---- Overview's Saved row --------------------------------------------------------------------

    /**
     * Asks for a new name for saved Workspace {@code name}; {@code onChanged} runs once it is
     * renamed. A Workspace of it still called {@code name} takes the new name too.
     */
    public void askToRename(@NonNull String name, @NonNull Runnable onChanged) {
        askName(mActivity.getString(R.string.saved_workspace_rename_title, name), name,
            R.string.saved_workspace_rename, to -> {
                String[] savedId = new String[1];
                change(name, () -> {
                    SavedWorkspace saved = mStore.load(name);
                    savedId[0] = saved == null ? null : saved.id();
                    return mStore.rename(name, to);
                }, to, () -> {
                    String clean = SavedWorkspaceStore.cleanName(to);
                    if (clean != null) mDesktop.savedRenamed(savedId[0], name, clean);
                    onChanged.run();
                });
            });
    }

    /** Asks before deleting saved Workspace {@code name}; {@code onChanged} runs once it is gone. */
    public void askToDelete(@NonNull String name, @NonNull Runnable onChanged) {
        confirm(mActivity.getString(R.string.saved_workspace_delete_title, name),
            R.string.saved_workspace_delete_message, R.string.saved_workspace_delete,
            () -> change(name, () -> mStore.delete(name), name, onChanged));
    }

    /** Marks saved Workspace {@code name} to open, or not, at start; {@code onChanged} runs once it is set. */
    public void setOpenAtStart(@NonNull String name, boolean on, @NonNull Runnable onChanged) {
        change(name, () -> mStore.setOpenAtStart(name, on), name, onChanged);
    }

    private interface Change {
        @NonNull SavedWorkspaceStore.Result run();
    }

    private void change(@NonNull String name, @NonNull Change change, @NonNull String newName,
                        @NonNull Runnable onChanged) {
        mFiles.execute(() -> {
            SavedWorkspaceStore.Result result = change.run();
            mMain.execute(() -> {
                switch (result) {
                    case OK:
                        onChanged.run();
                        break;
                    case NAME_TAKEN:
                        notice(mActivity.getString(R.string.saved_workspace_name_taken, newName.trim()));
                        break;
                    case INVALID_NAME:
                        notice(mActivity.getString(R.string.saved_workspace_name_invalid));
                        break;
                    default:
                        notice(mActivity.getString(R.string.saved_workspace_not_changed, name));
                        onChanged.run();
                        break;
                }
            });
        });
    }

    // ---- the dialogs -----------------------------------------------------------------------------

    /** A name field titled {@code title}, holding {@code initial}; {@code onName} gets what is typed, trimmed. */
    private void askName(@NonNull String title, @Nullable String initial, @StringRes int confirm,
                         @NonNull Consumer<String> onName) {
        if (mActivity.isFinishing()) return;
        float density = mActivity.getResources().getDisplayMetrics().density;
        EditText input = new EditText(mActivity);
        input.setSingleLine();
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint(R.string.saved_workspace_name_hint);
        String start = initial == null ? null : SavedWorkspaceStore.cleanName(initial);
        if (start != null) {
            input.setText(start);
            input.setSelection(start.length());
        }
        FrameLayout box = new FrameLayout(mActivity);
        int side = Math.round(24 * density);
        box.setPadding(side, Math.round(8 * density), side, 0);
        box.addView(input);
        AlertDialog dialog = new MaterialAlertDialogBuilder(mActivity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(confirm, (d, which) -> onName.accept(input.getText().toString().trim()))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        input.setOnEditorActionListener((v, action, event) -> {
            dialog.dismiss();
            onName.accept(input.getText().toString().trim());
            return true;
        });
        mBeforeTyping.run();
        dialog.show();
        input.requestFocus();
    }

    private void confirm(@NonNull String title, @StringRes int message, @StringRes int action,
                         @NonNull Runnable onYes) {
        if (mActivity.isFinishing()) return;
        new MaterialAlertDialogBuilder(mActivity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(action, (d, which) -> onYes.run())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void notice(@NonNull String text) {
        AppNotice.show(mActivity, text);
    }
}
