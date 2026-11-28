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
import com.termux.shared.logger.Logger;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Saved Workspaces under Terl (#431; spec "Windows and places", glossary "Overview"): Save from a
 * Workspace's menu asks for a name and keeps the Workspace in front in {@link SavedWorkspaceStore};
 * Overview's Saved row lists them, loads one with a tap, and renames, deletes or marks one Open at
 * start from a hold. A saved Workspace loads as a new Workspace of the same name
 * ({@link TilingDesktop#loadSaved}); on a cold start each one marked Open at start loads once,
 * unless a Workspace of its name is already there (the desktop came back as it was).
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
    }

    /** Every saved Workspace by name, delivered on the main thread. */
    public void list(@NonNull Consumer<List<SavedWorkspaceStore.Entry>> onRead) {
        mFiles.execute(() -> {
            List<SavedWorkspaceStore.Entry> entries = mStore.list();
            mMain.execute(() -> onRead.accept(entries));
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
     * A cold start: each saved Workspace marked Open at start loads once, the place in front
     * staying, unless the desktop already holds a Workspace of its name.
     */
    public void openAtStart() {
        list(entries -> {
            for (SavedWorkspaceStore.Entry entry : entries) {
                if (entry.workspace == null || !entry.workspace.openAtStart()) continue;
                if (mDesktop.holdsWorkspaceNamed(entry.name)) continue;
                if (!mDesktop.loadSaved(entry.workspace, entry.name, false)) {
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
                        mDesktop.nameWorkspaceOf(windowId, name);
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

    /** Asks for a new name for saved Workspace {@code name}; {@code onChanged} runs once it is renamed. */
    public void askToRename(@NonNull String name, @NonNull Runnable onChanged) {
        askName(mActivity.getString(R.string.saved_workspace_rename_title, name), name,
            R.string.saved_workspace_rename, to -> change(name, () -> mStore.rename(name, to), to, onChanged));
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
