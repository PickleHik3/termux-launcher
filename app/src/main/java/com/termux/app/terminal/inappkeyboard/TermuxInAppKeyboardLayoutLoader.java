package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.content.res.Resources;
import android.widget.Toast;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;

import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

/** Loads and caches {@code ~/.termux/keyboard/layout.xml} away from the main thread. */
public final class TermuxInAppKeyboardLayoutLoader {

    public static final long MAX_LAYOUT_BYTES = 512L * 1024L;

    private static final String LOG_TAG = "TermuxInAppKeyboardLayout";
    private static final String RELATIVE_LAYOUT_PATH = "keyboard/layout.xml";

    public interface Listener {
        void onLayoutLoaded(KeyboardData keyboardData);
    }

    public interface MainThread {
        void execute(Runnable runnable);
    }

    interface ErrorReporter {
        void report(String diagnostic, String userMessage);
    }

    private final Resources mResources;
    private final File mLayoutFile;
    private final Executor mBackgroundExecutor;
    private final MainThread mMainThread;
    private final ErrorReporter mErrorReporter;
    private volatile LayoutModifier.LayoutOptions mLayoutOptions;

    private Signature mCachedSignature;
    private Signature mLastReportedErrorSignature;
    private volatile KeyboardData mLastKnownGood;
    private int mGeneration;
    private boolean mClosed;

    /**
     * Creates the production loader. Custom layouts should be updated atomically by writing a
     * temporary file and renaming it to {@code layout.xml}.
     */
    public TermuxInAppKeyboardLayoutLoader(Context context, Executor backgroundExecutor,
                                           MainThread mainThread,
                                           LayoutModifier.LayoutOptions layoutOptions) {
        this(context,
            new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/" + RELATIVE_LAYOUT_PATH),
            backgroundExecutor, mainThread, layoutOptions, null);
    }

    TermuxInAppKeyboardLayoutLoader(Context context, File layoutFile,
                                    Executor backgroundExecutor, MainThread mainThread,
                                    LayoutModifier.LayoutOptions layoutOptions,
                                    ErrorReporter errorReporter) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        mResources = context.getResources();
        mLayoutFile = Objects.requireNonNull(layoutFile, "layoutFile");
        mBackgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        mMainThread = Objects.requireNonNull(mainThread, "mainThread");
        mLayoutOptions = Objects.requireNonNull(layoutOptions, "layoutOptions");
        mErrorReporter = errorReporter == null
            ? (diagnostic, userMessage) -> {
                Logger.logError(LOG_TAG, diagnostic);
                Toast.makeText(appContext, userMessage, Toast.LENGTH_LONG).show();
            }
            : errorReporter;
    }

    /** Rechecks the file signature and parses only when path, size, or mtime changed. */
    public void recheck(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        final Signature signature = Signature.read(mLayoutFile);
        final int generation;
        synchronized (this) {
            if (mClosed || signature.equals(mCachedSignature))
                return;
            mCachedSignature = signature;
            generation = ++mGeneration;
        }

        mBackgroundExecutor.execute(() -> load(signature, generation, listener));
    }

    public KeyboardData getLastKnownGood() {
        return mLastKnownGood;
    }

    /**
     * Applies new layout options and invalidates the cache, so the next {@link #recheck}
     * re-parses the layout and in-flight parses with the old options are dropped.
     */
    public synchronized void setLayoutOptions(LayoutModifier.LayoutOptions layoutOptions) {
        mLayoutOptions = Objects.requireNonNull(layoutOptions, "layoutOptions");
        mCachedSignature = null;
        mLastKnownGood = null;
        mGeneration++;
    }

    /** Prevents an in-flight parse from being delivered to a destroyed controller. */
    public synchronized void close() {
        mClosed = true;
        mGeneration++;
    }

    private void load(Signature signature, int generation, Listener listener) {
        KeyboardData loaded = null;
        Exception error = null;
        try {
            loaded = signature.exists ? loadCustom(signature) : loadBundled();
        } catch (Exception exception) {
            error = exception;
            if (mLastKnownGood == null) {
                try {
                    loaded = loadBundled();
                } catch (Exception fallbackError) {
                    exception.addSuppressed(fallbackError);
                }
            }
        }

        final KeyboardData result = loaded;
        final Throwable failure = error;
        mMainThread.execute(() -> deliver(signature, generation, result, failure, listener));
    }

    private KeyboardData loadCustom(Signature signature) throws Exception {
        if (!signature.regularFile)
            throw new IOException("path is not a readable regular file");
        if (signature.size > MAX_LAYOUT_BYTES)
            throw new IOException("layout exceeds " + MAX_LAYOUT_BYTES + " bytes");

        byte[] bytes = readBounded(mLayoutFile);
        KeyboardData parsed = KeyboardData.load_string_exn(
            new String(bytes, StandardCharsets.UTF_8));
        return LayoutModifier.modify(parsed, mLayoutOptions, mResources);
    }

    private KeyboardData loadBundled() {
        KeyboardData bundled = KeyboardData.load(
            mResources, juloo.keyboard2.R.xml.latn_qwerty_us);
        if (bundled == null)
            throw new IllegalStateException("Bundled QWERTY layout could not be parsed");
        return LayoutModifier.modify(bundled, mLayoutOptions, mResources);
    }

    private static byte[] readBounded(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                 (int) Math.min(file.length(), 32L * 1024L))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_LAYOUT_BYTES)
                    throw new IOException("layout changed while reading and exceeds "
                        + MAX_LAYOUT_BYTES + " bytes");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void deliver(Signature signature, int generation, KeyboardData loaded,
                         Throwable error, Listener listener) {
        synchronized (this) {
            if (mClosed || generation != mGeneration)
                return;
            if (loaded != null)
                mLastKnownGood = loaded;
        }

        if (error != null)
            reportOnce(signature, error);
        if (loaded != null)
            listener.onLayoutLoaded(loaded);
    }

    private void reportOnce(Signature signature, Throwable error) {
        synchronized (this) {
            if (signature.equals(mLastReportedErrorSignature))
                return;
            mLastReportedErrorSignature = signature;
        }

        Position position = Position.from(error);
        String where = position.line > 0
            ? " at line " + position.line + (position.column > 0 ? ", column " + position.column : "")
            : "";
        String errorClass = error.getClass().getSimpleName();
        String diagnostic = "Failed to load " + signature.path + where + ": " + errorClass
            + ": " + safeSummary(error);
        mErrorReporter.report(diagnostic, "In-app keyboard layout error" + where
            + "; using the last working layout");
    }

    /** Deliberately never copies parser text or an offending XML tag into logs. */
    private static String safeSummary(Throwable error) {
        String message = error.getMessage();
        if (error instanceof IOException && message != null) {
            if (message.contains("exceeds"))
                return "layout exceeds " + MAX_LAYOUT_BYTES + " bytes";
            if (message.contains("regular file"))
                return "path is not a readable regular file";
            return "layout file could not be read";
        }
        return "invalid keyboard XML";
    }

    private static final class Position {
        final int line;
        final int column;

        Position(int line, int column) {
            this.line = line;
            this.column = column;
        }

        static Position from(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                if (current instanceof XmlPullParserException) {
                    XmlPullParserException parserError = (XmlPullParserException) current;
                    return new Position(parserError.getLineNumber(), parserError.getColumnNumber());
                }
                if (current instanceof KeyboardData.ParseException) {
                    KeyboardData.ParseException parserError =
                        (KeyboardData.ParseException) current;
                    return new Position(parserError.lineNumber, parserError.columnNumber);
                }
                current = current.getCause();
            }
            return new Position(-1, -1);
        }
    }

    private static final class Signature {
        final String path;
        final boolean exists;
        final boolean regularFile;
        final long size;
        final long lastModified;

        Signature(String path, boolean exists, boolean regularFile, long size, long lastModified) {
            this.path = path;
            this.exists = exists;
            this.regularFile = regularFile;
            this.size = size;
            this.lastModified = lastModified;
        }

        static Signature read(File file) {
            String path;
            try {
                path = file.getCanonicalPath();
            } catch (IOException e) {
                path = file.getAbsolutePath();
            }
            boolean exists = file.exists();
            boolean regular = exists && file.isFile() && file.canRead();
            return new Signature(path, exists, regular, exists ? file.length() : -1L,
                exists ? file.lastModified() : -1L);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof Signature))
                return false;
            Signature that = (Signature) other;
            return exists == that.exists && regularFile == that.regularFile
                && size == that.size && lastModified == that.lastModified
                && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            int result = path.hashCode();
            result = 31 * result + (exists ? 1 : 0);
            result = 31 * result + (regularFile ? 1 : 0);
            result = 31 * result + Long.valueOf(size).hashCode();
            result = 31 * result + Long.valueOf(lastModified).hashCode();
            return result;
        }
    }
}
