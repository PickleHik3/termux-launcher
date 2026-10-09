package com.termux.app.launcher.widget.builtin;

import android.widget.Toast;

import androidx.annotation.NonNull;

import com.termux.R;

import java.io.File;
import java.util.concurrent.RejectedExecutionException;

/**
 * Opens a widget's file in the user's editor, in a terminal window of its own. The folder is
 * made first, so an editor saving a file that did not exist yet has somewhere to put it.
 */
final class FilesWidgetEditor {
    private FilesWidgetEditor() { }

    static void open(@NonNull BuiltinWidgetServices services, @NonNull String path) {
        try {
            services.io().execute(() -> {
                File parent = new File(path).getParentFile();
                if (parent != null && !parent.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    parent.mkdirs();
                }
                services.main().post(() -> launch(services, path));
            });
        } catch (RejectedExecutionException e) {
            launch(services, path);
        }
    }

    private static void launch(@NonNull BuiltinWidgetServices services, @NonNull String path) {
        boolean opened;
        try {
            opened = services.host().openCommandWindow(FilesWidgetPaths.editorCommand(path),
                FilesWidgetPaths.fileName(path));
        } catch (RuntimeException e) {
            opened = false;
        }
        if (!opened) {
            Toast.makeText(services.context(), R.string.bw_files_editor_unavailable,
                Toast.LENGTH_SHORT).show();
        }
    }
}
