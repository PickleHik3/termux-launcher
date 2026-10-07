package com.termux.app.api.file;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Patterns;
import android.util.TypedValue;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.net.uri.UriUtils;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.net.uri.UriScheme;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;
import com.termux.app.TermuxService;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FileReceiverActivity extends AppCompatActivity {

    static final String TERMUX_RECEIVEDIR = TermuxConstants.TERMUX_FILES_DIR_PATH + "/home/downloads";

    static final String EDITOR_PROGRAM = TermuxConstants.TERMUX_HOME_DIR_PATH + "/bin/termux-file-editor";

    static final String URL_OPENER_PROGRAM = TermuxConstants.TERMUX_HOME_DIR_PATH + "/bin/termux-url-opener";

    /**
     * If the activity should be finished when the name input dialog is dismissed. This is disabled
     * before showing an error dialog, since the act of showing the error dialog will cause the
     * name input dialog to be implicitly dismissed, and we do not want to finish the activity directly
     * when showing the error dialog.
     */
    boolean mFinishOnDismissNameDialog = true;

    private static final String API_TAG = TermuxConstants.TERMUX_APP_NAME + "FileReceiver";

    private static final String LOG_TAG = "FileReceiverActivity";

    /**
     * Provider reads and copies run here, one at a time, never on main: a shared file can be any
     * size and a content provider can be another process taking its time.
     */
    private static final ExecutorService IO = newIoExecutor();

    @NonNull
    private static ExecutorService newIoExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 15L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, "FileReceiverIO"));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /** Work for {@link #IO}. */
    private interface BackgroundStep<T> {
        T run();
    }

    /** What happens on main with a {@link BackgroundStep}'s result. */
    private interface MainStep<T> {
        void accept(T result);
    }

    /** Set while a background step runs, so a resume meanwhile does not handle the intent again. */
    private boolean mWorkInFlight;
    @Nullable private AlertDialog mProgressDialog;

    static boolean isSharedTextAnUrl(String sharedText) {
        if (sharedText == null || sharedText.isEmpty()) return false;

        return Patterns.WEB_URL.matcher(sharedText).matches()
            || Pattern.matches("magnet:\\?xt=urn:btih:.*?", sharedText)
            || Pattern.matches("nzblnk:\\?((t|h|g|p)=(.*)&?)+", sharedText)
            || Pattern.matches("(jabber|mailto|xmpp):.+", sharedText)
            || Pattern.matches("(gopher|irc(6|s)?|nfs|rtmp|sftp|smb)://.+", sharedText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWorkInFlight) return;
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();
        final String scheme = intent.getScheme();
        Logger.logVerbose(LOG_TAG, "Intent Received:\n" + IntentUtils.getIntentString(intent));
        final String sharedTitle = IntentUtils.getStringExtraIfSet(intent, Intent.EXTRA_TITLE, null);
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            final String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            final Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (sharedUri != null) {
                handleContentUri(sharedUri, sharedTitle);
            } else if (sharedText != null) {
                if (isSharedTextAnUrl(sharedText)) {
                    handleUrlAndFinish(sharedText);
                } else {
                    String subject = IntentUtils.getStringExtraIfSet(intent, Intent.EXTRA_SUBJECT, null);
                    if (subject == null)
                        subject = sharedTitle;
                    if (subject != null)
                        subject += ".txt";
                    promptNameAndSave(new ByteArrayInputStream(sharedText.getBytes(StandardCharsets.UTF_8)), subject);
                }
            } else {
                showErrorDialogAndQuit("Send action without content - nothing to save.");
            }
        } else {
            Uri dataUri = intent.getData();
            if (dataUri == null) {
                showErrorDialogAndQuit("Data uri not passed.");
                return;
            }
            if (UriScheme.SCHEME_CONTENT.equals(scheme)) {
                handleContentUri(dataUri, sharedTitle);
            } else if (UriScheme.SCHEME_FILE.equals(scheme)) {
                Logger.logVerbose(LOG_TAG, "uri: \"" + dataUri + "\", path: \"" + dataUri.getPath() + "\", fragment: \"" + dataUri.getFragment() + "\"");
                // Get full path including fragment (anything after last "#")
                String path = UriUtils.getUriFilePathWithFragment(dataUri);
                if (DataUtils.isNullOrEmpty(path)) {
                    showErrorDialogAndQuit("File path from data uri is null, empty or invalid.");
                    return;
                }
                File file = new File(path);
                try {
                    FileInputStream in = new FileInputStream(file);
                    promptNameAndSave(in, file.getName(), file.getAbsolutePath());
                } catch (FileNotFoundException e) {
                    showErrorDialogAndQuit("Cannot open file: " + e.getMessage() + ".");
                }
            } else {
                showErrorDialogAndQuit("Unable to receive any file or URL.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        dismissProgress();
        super.onDestroy();
    }

    /**
     * Runs {@code work} on {@link #IO} and hands its result to {@code then} on main, unless the
     * activity has gone meanwhile; then {@code ifGone} gets it instead, to release what it holds.
     *
     * <p>With {@code afterNameDialog} the step follows a tap on the name dialog: an indeterminate
     * progress dialog stands in while it runs, and the name dialog's own dismissal (which follows
     * the tap) must not finish the activity before the work is done.
     */
    private <T> void runOffMain(boolean afterNameDialog, @NonNull BackgroundStep<T> work,
                                @NonNull MainStep<T> then, @Nullable MainStep<T> ifGone) {
        mWorkInFlight = true;
        if (afterNameDialog) {
            mFinishOnDismissNameDialog = false;
            showProgress();
        }
        IO.execute(() -> {
            T result;
            try {
                result = work.run();
            } catch (RuntimeException e) {
                // The steps report their own failures; this is what used to crash on main.
                Logger.logStackTraceWithMessage(LOG_TAG, "Receiving shared content failed", e);
                runOnUiThread(() -> {
                    mWorkInFlight = false;
                    dismissProgress();
                    if (isFinishing() || isDestroyed()) return;
                    showErrorDialogAndQuit("Unable to handle shared content:\n\n" + e.getMessage());
                });
                return;
            }
            runOnUiThread(() -> {
                mWorkInFlight = false;
                dismissProgress();
                if (isFinishing() || isDestroyed()) {
                    if (ifGone != null) ifGone.accept(result);
                    return;
                }
                then.accept(result);
            });
        });
    }

    private void showProgress() {
        if (mProgressDialog != null) return;
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
            getResources().getDisplayMetrics()));
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(padding, padding, padding, padding);
        frame.addView(progress, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER));
        mProgressDialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_file_received)
            .setView(frame)
            .setCancelable(false)
            .create();
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.show();
    }

    private void dismissProgress() {
        AlertDialog dialog = mProgressDialog;
        mProgressDialog = null;
        if (dialog == null) return;
        try {
            dialog.dismiss();
        } catch (RuntimeException ignored) {
            // The window went with the activity.
        }
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    void showErrorDialogAndQuit(String message) {
        mFinishOnDismissNameDialog = false;
        MessageDialogUtils.showMessage(this, API_TAG, message, null, (dialog, which) -> finish(), null, null, dialog -> finish());
    }

    /** What {@link #openSharedContent} found: a directory, a stream, or why neither. */
    private static final class SharedContent {
        @Nullable String attachmentFileName;
        @Nullable Path directoryLink;
        @Nullable ParcelFileDescriptor directoryDescriptor;
        @Nullable InputStream stream;
        @Nullable String error;

        void close() {
            closeQuietly(directoryDescriptor);
            closeQuietly(stream);
        }
    }

    /**
     * Reads the shared content's name and opens it, off main (the provider query and open are
     * calls into another process), then asks for the name to save it under.
     */
    void handleContentUri(@NonNull final Uri uri, String subjectFromIntent) {
        runOffMain(false, () -> openSharedContent(uri, subjectFromIntent), content -> {
            if (content.error != null) {
                showErrorDialogAndQuit(content.error);
            } else if (content.directoryLink != null && content.directoryDescriptor != null
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                promptNameAndCopy(content.directoryLink, content.directoryDescriptor,
                    content.attachmentFileName);
            } else {
                promptNameAndSave(content.stream, content.attachmentFileName);
            }
        }, SharedContent::close);
    }

    /** {@link #handleContentUri}'s provider work; on {@link #IO}. */
    @NonNull
    private SharedContent openSharedContent(@NonNull final Uri uri, String subjectFromIntent) {
        SharedContent content = new SharedContent();
        try {
            Logger.logVerbose(LOG_TAG, "uri: \"" + uri + "\", path: \"" + uri.getPath() + "\", fragment: \"" + uri.getFragment() + "\"");
            String attachmentFileName = null;
            String[] projection = new String[] { OpenableColumns.DISPLAY_NAME };
            try (Cursor c = getContentResolver().query(uri, projection, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    final int fileNameColumnId = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (fileNameColumnId >= 0)
                        attachmentFileName = c.getString(fileNameColumnId);
                }
            }

            if (attachmentFileName == null) attachmentFileName = subjectFromIntent;
            if (attachmentFileName == null) attachmentFileName = UriUtils.getUriFileBasename(uri, true);
            content.attachmentFileName = attachmentFileName;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ParcelFileDescriptor fileDescriptor = null;
                try {
                    fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
                    int fd = fileDescriptor.getFd();
                    Path link = Paths.get("/proc/self/fd/" + fd);
                    if (Files.isDirectory(link)) {
                        content.directoryLink = link;
                        content.directoryDescriptor = fileDescriptor;
                        return content;
                    }
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "handleContentUri(uri=" + uri + ") as directory failed (continuing as file)", e);
                    if (fileDescriptor != null) {
                        fileDescriptor.close();
                    }
                }
            }

            content.stream = getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            content.error = "Unable to handle shared content:\n\n" + e.getMessage();
            Logger.logStackTraceWithMessage(LOG_TAG, "handleContentUri(uri=" + uri + ") failed", e);
        }
        return content;
    }

    @androidx.annotation.RequiresApi(api = android.os.Build.VERSION_CODES.O)
    void promptNameAndCopy(final Path SOURCE, @NonNull final ParcelFileDescriptor fd, final String attachmentFileName) {
        final TextInputDialogUtils.TextSetListener listener = (
            text -> runOffMain(true, () -> copyDirectory(SOURCE, fd, text), outcome -> {
                if (outcome.error != null) {
                    showErrorDialogAndQuit(outcome.error);
                    return;
                }
                Intent executeIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE);
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, outcome.receiveDir.toString());
                executeIntent.setClass(FileReceiverActivity.this, TermuxService.class);
                startService(executeIntent);
                finish();
            }, null));
        TextInputDialogUtils.textInput(this, R.string.title_file_received, attachmentFileName,
            R.string.action_file_received_edit, listener,
            R.string.action_file_received_open_directory, listener,
            android.R.string.cancel, text -> finish(), dialog -> {
                if (mFinishOnDismissNameDialog) {
                    finish();
                }
            });
    }

    /** Where a directory copy ended: the directory written, or why it failed. */
    private static final class CopyOutcome {
        @Nullable Path receiveDir;
        @Nullable String error;
    }

    /** Copies the shared directory under the receive directory; on {@link #IO}. */
    @NonNull
    @androidx.annotation.RequiresApi(api = android.os.Build.VERSION_CODES.O)
    private static CopyOutcome copyDirectory(final Path SOURCE, @NonNull final ParcelFileDescriptor fd,
                                             final String text) {
        CopyOutcome outcome = new CopyOutcome();
        try (final ParcelFileDescriptor fileDescriptor = fd) {
            if (DataUtils.isNullOrEmpty(text)) {
                outcome.error = "File name cannot be null or empty";
                return outcome;
            }
            final Path receiveDir = resolveReceiveDirectory(text);
            Files.createDirectories(receiveDir);
            if (!Files.isDirectory(receiveDir)) {
                outcome.error = "Cannot create directory: " + receiveDir.toAbsolutePath().toString();
                return outcome;
            }
            try (Stream<Path> children = Files.list(SOURCE)) {
                for (Iterator<Path> itr = children.iterator(); itr.hasNext();) {
                    Path child = itr.next();
                    try (Stream<Path> stream = Files.walk(child)) {
                        for (Iterator<Path> i = stream.iterator(); i.hasNext();) {
                            Path source = i.next();
                            if (Files.isSymbolicLink(source)) {
                                throw new IOException("Symbolic links are not supported in received directories: " + source);
                            }
                            Path target = receiveDir.resolve(SOURCE.relativize(source)).normalize();
                            if (!target.startsWith(receiveDir)) {
                                throw new IOException("Refusing to copy outside receive directory: " + source);
                            }
                            Files.copy(source, target, LinkOption.NOFOLLOW_LINKS);
                        }
                    }
                }
            }
            fileDescriptor.getFd();
            outcome.receiveDir = receiveDir;
        } catch (Exception e) {
            outcome.error = "Error copying directory:\n\n" + e;
            Logger.logStackTraceWithMessage(LOG_TAG, "Error copying directory", e);
        }
        return outcome;
    }
    
    void promptNameAndSave(final InputStream in, final String attachmentFileName) {
        promptNameAndSave(in, attachmentFileName, null);
    }

    /**
     * @param sourcePath If non-null, shown to the user before saving. The incoming intent's data
     *                    URI is not scoped by any content-provider grant when it uses the "file"
     *                    scheme, so the path being read may lie outside anything the calling app
     *                    itself has access to; surfacing it lets the user notice a suspicious source
     *                    before approving the save.
     */
    void promptNameAndSave(final InputStream in, final String attachmentFileName, final String sourcePath) {
        String message = sourcePath != null ? getString(R.string.msg_file_received_source, sourcePath) : null;
        TextInputDialogUtils.textInput(this, R.string.title_file_received, message, attachmentFileName, R.string.action_file_received_edit,
            text -> runOffMain(true, () -> saveStreamWithName(in, text, true), saved -> {
                if (saved.error != null) {
                    showErrorDialogAndQuit(saved.error);
                    return;
                }
                if (!saved.editorExists) {
                    showErrorDialogAndQuit("The following file does not exist:\n$HOME/bin/termux-file-editor\n\n" + "Create this file as a script or a symlink - it will be called with the received file as only argument.");
                    return;
                }
                final Uri scriptUri = UriUtils.getFileUri(EDITOR_PROGRAM);
                Intent executeIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, scriptUri);
                executeIntent.setClass(FileReceiverActivity.this, TermuxService.class);
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[] { saved.outFile.getAbsolutePath() });
                startService(executeIntent);
                finish();
            }, null),
            R.string.action_file_received_open_directory,
            text -> runOffMain(true, () -> saveStreamWithName(in, text, false), saved -> {
                if (saved.error != null) {
                    showErrorDialogAndQuit(saved.error);
                    return;
                }
                Intent executeIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE);
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TERMUX_RECEIVEDIR);
                executeIntent.setClass(FileReceiverActivity.this, TermuxService.class);
                startService(executeIntent);
                finish();
            }, null),
            android.R.string.cancel, text -> finish(), dialog -> {
            if (mFinishOnDismissNameDialog)
                finish();
        });
    }

    /** Where a save ended: the file written, or why it failed. */
    static final class SaveOutcome {
        @Nullable File outFile;
        @Nullable String error;
        /** Whether $HOME/bin/termux-file-editor is there (made executable when it is). */
        boolean editorExists;
    }

    /**
     * Copies {@code in} to {@code attachmentFileName} in the receive directory; on {@link #IO}.
     * With {@code checkEditor} it also looks for the editor script, so the main thread does no
     * file system work after it.
     */
    @NonNull
    static SaveOutcome saveStreamWithName(InputStream in, String attachmentFileName,
                                          boolean checkEditor) {
        SaveOutcome outcome = new SaveOutcome();
        File receiveDir = new File(TERMUX_RECEIVEDIR);
        if (!receiveDir.isDirectory() && !receiveDir.mkdirs()) {
            outcome.error = "Cannot create directory: " + receiveDir.getAbsolutePath();
            return outcome;
        }
        try {
            final File outFile = resolveReceiveFile(receiveDir, attachmentFileName);
            try (FileOutputStream f = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[4096];
                int readBytes;
                while ((readBytes = in.read(buffer)) > 0) {
                    f.write(buffer, 0, readBytes);
                }
            }
            outcome.outFile = outFile;
        } catch (IOException e) {
            outcome.error = "Error saving file:\n\n" + e;
            Logger.logStackTraceWithMessage(LOG_TAG, "Error saving file", e);
            return outcome;
        }
        if (!checkEditor) return outcome;
        final File editorProgramFile = new File(EDITOR_PROGRAM);
        outcome.editorExists = editorProgramFile.isFile();
        if (outcome.editorExists) {
            // Do this for the user if necessary:
            //noinspection ResultOfMethodCallIgnored
            editorProgramFile.setExecutable(true);
        }
        return outcome;
    }

    static String sanitizeReceiveName(String attachmentFileName) throws IOException {
        if (DataUtils.isNullOrEmpty(attachmentFileName)) {
            throw new IOException("File name cannot be null or empty");
        }
        String name = attachmentFileName.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name) || name.contains("/") || name.contains("\\")) {
            throw new IOException("File name cannot contain path separators");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                throw new IOException("File name cannot contain control characters");
            }
        }
        return name;
    }

    private static File resolveReceiveFile(File receiveDir, String attachmentFileName) throws IOException {
        String name = sanitizeReceiveName(attachmentFileName);
        File canonicalReceiveDir = receiveDir.getCanonicalFile();
        File outFile = new File(canonicalReceiveDir, name).getCanonicalFile();
        if (!isPathInDirectory(canonicalReceiveDir, outFile)) {
            throw new IOException("Refusing to save outside receive directory");
        }
        return outFile;
    }

    private static Path resolveReceiveDirectory(String attachmentFileName) throws IOException {
        String name = sanitizeReceiveName(attachmentFileName);
        Path receiveRootPath = Paths.get(TERMUX_RECEIVEDIR);
        Files.createDirectories(receiveRootPath);
        Path receiveRoot = receiveRootPath.toRealPath();
        Path receiveDir = receiveRoot.resolve(name).normalize();
        if (!receiveDir.startsWith(receiveRoot)) {
            throw new IOException("Refusing to create directory outside receive directory");
        }
        return receiveDir;
    }

    private static boolean isPathInDirectory(File directory, File file) throws IOException {
        String directoryPath = directory.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.startsWith(directoryPath + File.separator);
    }

    void handleUrlAndFinish(final String url) {
        final File urlOpenerProgramFile = new File(URL_OPENER_PROGRAM);
        if (!urlOpenerProgramFile.isFile()) {
            showErrorDialogAndQuit("The following file does not exist:\n$HOME/bin/termux-url-opener\n\n" + "Create this file as a script or a symlink - it will be called with the shared URL as the first argument.");
            return;
        }
        // Do this for the user if necessary:
        //noinspection ResultOfMethodCallIgnored
        urlOpenerProgramFile.setExecutable(true);
        final Uri urlOpenerProgramUri = UriUtils.getFileUri(URL_OPENER_PROGRAM);
        Intent executeIntent = new Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, urlOpenerProgramUri);
        executeIntent.setClass(FileReceiverActivity.this, TermuxService.class);
        executeIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[] { url });
        startService(executeIntent);
        finish();
    }

    /**
     * Update {@link TERMUX_APP#FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME} component state depending on
     * {@link TermuxPropertyConstants#KEY_DISABLE_FILE_SHARE_RECEIVER} value and
     * {@link TERMUX_APP#FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME} component state depending on
     * {@link TermuxPropertyConstants#KEY_DISABLE_FILE_VIEW_RECEIVER} value.
     */
    public static void updateFileReceiverActivityComponentsState(@NonNull Context context) {
        new Thread() {

            @Override
            public void run() {
                TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
                String errmsg;
                boolean state;
                state = !properties.isFileShareReceiverDisabled();
                Logger.logVerbose(LOG_TAG, "Setting " + TERMUX_APP.FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME + " component state to " + state);
                errmsg = PackageUtils.setComponentState(context, TermuxConstants.TERMUX_PACKAGE_NAME, TERMUX_APP.FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME, state, null, false, false);
                if (errmsg != null)
                    Logger.logError(LOG_TAG, errmsg);
                state = !properties.isFileViewReceiverDisabled();
                Logger.logVerbose(LOG_TAG, "Setting " + TERMUX_APP.FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME + " component state to " + state);
                errmsg = PackageUtils.setComponentState(context, TermuxConstants.TERMUX_PACKAGE_NAME, TERMUX_APP.FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME, state, null, false, false);
                if (errmsg != null)
                    Logger.logError(LOG_TAG, errmsg);
            }
        }.start();
    }
}
