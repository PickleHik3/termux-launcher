package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Applies an {@link ImageRowLogic.Row} to one {@code shader_image_row} (Photo or Depth). Every
 * setter compares first, so a bind that changes nothing requests no layout. The thumbnail is
 * decoded small and off the main thread, belongs to the row's view only (no cache anywhere) and is
 * dropped by {@link #clearThumb}.
 */
final class ImageRowView {

    /** Called with the action a button stands for. */
    interface OnAction {
        void onAction(@NonNull ImageRowLogic.Action action);
    }

    private static final ExecutorService DECODE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ShaderThumb");
        t.setDaemon(true);
        return t;
    });

    private final Context mContext;
    private final View mRoot;
    private final ImageView mThumb;
    private final TextView mLabel;
    private final TextView mNote;
    private final LinearProgressIndicator mProgress;
    private final MaterialButton mFirst;
    private final MaterialButton mSecond;
    @Nullable private ImageRowLogic.Action mFirstAction;
    @Nullable private ImageRowLogic.Action mSecondAction;
    /** The file the thumbnail shows or is being decoded from; a changed file under one name needs {@link #clearThumb}. */
    @Nullable private String mThumbKey;
    /** Moves with every request, so a late decode for an older one is dropped. */
    private int mGeneration;

    ImageRowView(@NonNull Context context, @NonNull View root, @NonNull OnAction onAction) {
        mContext = context;
        mRoot = root;
        mThumb = root.findViewById(R.id.image_row_thumb);
        mLabel = root.findViewById(R.id.image_row_label);
        mNote = root.findViewById(R.id.image_row_note);
        mProgress = root.findViewById(R.id.image_row_progress);
        mFirst = root.findViewById(R.id.image_row_first);
        mSecond = root.findViewById(R.id.image_row_second);
        mFirst.setOnClickListener(v -> {
            if (mFirstAction != null) onAction.onAction(mFirstAction);
        });
        mSecond.setOnClickListener(v -> {
            if (mSecondAction != null) onAction.onAction(mSecondAction);
        });
    }

    /**
     * Shows {@code row}. {@code name} fills the "named" labels (a file name); {@code thumb} is the
     * image to show when the row has a thumbnail.
     */
    void bind(@NonNull ImageRowLogic.Row row, @Nullable String name, @Nullable File thumb) {
        setText(mLabel, labelText(row, name));
        int noteRes = noteRes(row.note);
        setText(mNote, noteRes == 0 ? "" : mContext.getString(noteRes));
        setVisible(mNote, noteRes != 0);
        mNote.setTextColor(MaterialColors.getColor(mNote,
            row.noteIsError ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
        setVisible(mProgress, row.showProgress);
        if (row.showProgress && mProgress.getProgress() != row.percent) {
            mProgress.setProgressCompat(row.percent, false);
        }
        mFirstAction = row.first;
        mSecondAction = row.second;
        bindButton(mFirst, row.first, row.firstEnabled);
        bindButton(mSecond, row.second, true);
        if (row.showThumb && thumb != null) showThumb(thumb);
        else clearThumb();
    }

    private void bindButton(@NonNull MaterialButton b, @Nullable ImageRowLogic.Action action, boolean enabled) {
        setVisible(b, action != null);
        if (action == null) return;
        setText(b, mContext.getString(actionRes(action)));
        if (b.isEnabled() != enabled) b.setEnabled(enabled);
    }

    @NonNull
    private String labelText(@NonNull ImageRowLogic.Row row, @Nullable String name) {
        String n = name == null ? "" : name;
        switch (row.label) {
            case PHOTO_NONE: return mContext.getString(R.string.shader_photo_none);
            case PHOTO_NAMED: return mContext.getString(R.string.shader_photo_named, n);
            case DEPTH_DOWNLOADING: return mContext.getString(R.string.shader_depth_downloading, row.percent);
            case DEPTH_MAKING: return mContext.getString(R.string.shader_depth_making);
            case DEPTH_MADE: return mContext.getString(R.string.shader_depth_made);
            case DEPTH_NAMED: return mContext.getString(R.string.shader_depth_named, n);
            case DEPTH_NONE:
            default: return mContext.getString(R.string.shader_depth_none);
        }
    }

    @StringRes
    private static int noteRes(@NonNull ImageRowLogic.Note note) {
        switch (note) {
            case CHOOSE_PHOTO_FIRST: return R.string.shader_depth_choose_photo_first;
            case MAY_NOT_MATCH: return R.string.shader_depth_may_not_match;
            case FAILED: return R.string.shader_depth_failed;
            case NONE:
            default: return 0;
        }
    }

    @StringRes
    private static int actionRes(@NonNull ImageRowLogic.Action action) {
        switch (action) {
            case CHANGE: return R.string.shader_image_change;
            case REMOVE: return R.string.shader_image_remove;
            case MAKE: return R.string.shader_depth_make;
            case CANCEL: return R.string.shader_depth_cancel;
            case TRY_AGAIN: return R.string.shader_depth_try_again;
            case CHOOSE:
            default: return R.string.shader_image_choose;
        }
    }

    private void showThumb(@NonNull File file) {
        setVisible(mThumb, true);
        final String key = file.getPath();
        if (key.equals(mThumbKey)) return;
        mThumbKey = key;
        final int generation = ++mGeneration;
        mThumb.setImageDrawable(null);
        final int target = Math.max(1, Math.round(36f * mContext.getResources().getDisplayMetrics().density));
        DECODE.execute(() -> {
            final Bitmap bmp = decode(file, target);
            mRoot.post(() -> {
                if (generation != mGeneration || bmp == null) return;
                mThumb.setImageBitmap(bmp);
            });
        });
    }

    /** Takes the thumbnail down and lets go of its pixels. */
    void clearThumb() {
        mGeneration++;
        mThumbKey = null;
        if (mThumb.getDrawable() != null) mThumb.setImageDrawable(null);
        setVisible(mThumb, false);
    }

    /** The image decoded no larger than needed for {@code targetPx} (a power-of-two sample size). */
    @Nullable
    static Bitmap decode(@NonNull File file, int targetPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            int shortSide = Math.min(bounds.outWidth, bounds.outHeight);
            while (shortSide / (sample * 2) >= targetPx) sample *= 2;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getPath(), o);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static void setText(@NonNull TextView v, @NonNull CharSequence text) {
        if (!text.toString().contentEquals(v.getText())) v.setText(text);
    }

    private static void setVisible(@NonNull View v, boolean visible) {
        int want = visible ? View.VISIBLE : View.GONE;
        if (v.getVisibility() != want) v.setVisibility(want);
    }

    // --- tests ---

    @NonNull View thumbView() {
        return mThumb;
    }

    @NonNull View firstButton() {
        return mFirst;
    }

    @NonNull View secondButton() {
        return mSecond;
    }

    @NonNull TextView labelView() {
        return mLabel;
    }

    @NonNull TextView noteView() {
        return mNote;
    }
}
