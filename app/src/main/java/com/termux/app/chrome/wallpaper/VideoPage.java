package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.view.LayoutInflater;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.app.chrome.scene.WallpaperMotion;

/**
 * The Video half of the wallpaper picker: the recommended format, Choose video or GIF, one line
 * about the file in use, the frame rate (15, 30 or 60), and Back to photo while a motion is in
 * use. The host imports the file and sets it ({@link Actions#onChoose}).
 */
public final class VideoPage {

    /** What the page asks of its host; on the main thread. */
    public interface Actions {
        /** Choose video or GIF. */
        void onChoose();

        void onBackToPhoto();

        /** The frame rate knob moved. */
        void onRate(int hz);
    }

    private final View mRoot;
    private final ScrollView mScroll;
    private final TextView mHint;
    private final MaterialButtonToggleGroup mRate;
    private final MaterialButton mChoose;
    private final MaterialButton mBack;
    private final LinearProgressIndicator mProgress;
    private final TextView mProgressLabel;
    private final TextView mRateNote;
    /** Per rate pill: the clip can play at it; all true until the clip's state is read. */
    private final boolean[] mAvailable = {true, true, true};
    private final VideoProgressLogic mLogic = new VideoProgressLogic();
    private boolean mBinding;

    public VideoPage(@NonNull Context context, @NonNull Actions actions) {
        mRoot = LayoutInflater.from(context).inflate(R.layout.video_page, null, false);
        mScroll = mRoot.findViewById(R.id.video_scroll);
        mHint = mRoot.findViewById(R.id.video_hint);
        mChoose = mRoot.findViewById(R.id.video_choose);
        mBack = mRoot.findViewById(R.id.video_back_to_photo);
        mProgress = mRoot.findViewById(R.id.video_progress);
        mProgressLabel = mRoot.findViewById(R.id.video_progress_label);
        mRateNote = mRoot.findViewById(R.id.video_rate_note);

        View rateRow = mRoot.findViewById(R.id.video_knob_rate);
        ((TextView) rateRow.findViewById(android.R.id.title)).setText(R.string.live_knob_frame_rate);
        rateRow.findViewById(android.R.id.summary).setVisibility(View.GONE);
        mRate = rateRow.findViewById(R.id.segmented_pill_track);
        MaterialButton b15 = rateRow.findViewById(R.id.segmented_pill_default);
        MaterialButton b30 = rateRow.findViewById(R.id.segmented_pill_capsule);
        MaterialButton b60 = rateRow.findViewById(R.id.segmented_pill_third);
        b15.setText(R.string.live_rate_15);
        b30.setText(R.string.live_rate_30);
        b60.setText(R.string.live_rate_60);
        b60.setVisibility(View.VISIBLE);
        b15.setTag(15);
        b30.setTag(30);
        b60.setTag(60);
        mRate.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (mBinding || !isChecked) return;
            View b = group.findViewById(checkedId);
            if (b != null && b.getTag() instanceof Integer) actions.onRate((Integer) b.getTag());
        });

        mChoose.setOnClickListener(v -> actions.onChoose());
        mBack.setOnClickListener(v -> actions.onBackToPhoto());
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    /** Checks the pill for rateHz (the draw rate in use) without telling the host. */
    public void selectRate(int rateHz) {
        mBinding = true;
        try {
            for (int i = 0; i < mRate.getChildCount(); i++) {
                View b = mRate.getChildAt(i);
                if (b.getTag() instanceof Integer && (Integer) b.getTag() == rateHz) mRate.check(b.getId());
            }
        } finally {
            mBinding = false;
        }
    }

    /** Disables the rates above the clip's own and says why in one line; null state clears both. */
    public void setClip(@Nullable com.termux.app.chrome.scene.VideoVariants.State state) {
        float fps = state == null ? 0f : state.fps;
        boolean[] on = state == null ? new boolean[] {true, true, true} : VideoRateLogic.enabled(fps);
        System.arraycopy(on, 0, mAvailable, 0, mAvailable.length);
        boolean reason = state != null && VideoRateLogic.showsReason(fps);
        mRateNote.setText(reason ? mRoot.getContext().getString(R.string.video_rate_reason,
            VideoRateLogic.clipFps(fps)) : "");
        mRateNote.setVisibility(reason ? View.VISIBLE : View.GONE);
        render();
    }

    /** A version for hz is being made from the stored clip; the rate pills stay usable. */
    public void startPreparing(int hz) {
        mLogic.startPreparing(hz);
        render();
    }

    /** The version ended (made, failed or replaced); the host then sets the hint. */
    public void endPreparing() {
        mLogic.finish(true);
        render();
    }

    /** An import began for a video (rateHz) or a GIF (0); sets the bar's wording. */
    public void setImportRate(int rateHz) {
        mLogic.setImportRate(rateHz);
        render();
    }

    /** Opens the page with the rate set and Back to photo only while a motion is in use. */
    public void show(@NonNull WallpaperMotion stored, int rateHz) {
        mBinding = true;
        try {
            for (int i = 0; i < mRate.getChildCount(); i++) {
                View b = mRate.getChildAt(i);
                if (b.getTag() instanceof Integer && (Integer) b.getTag() == rateHz) mRate.check(b.getId());
            }
            mBack.setVisibility(LiveSceneLogic.showsBackToPhoto(stored) ? View.VISIBLE : View.GONE);
            mScroll.scrollTo(0, 0);
        } finally {
            mBinding = false;
        }
        setBusy(false);
    }

    /** One line about the file (or what went wrong) under the button; null or empty hides it. */
    public void setHint(@Nullable String text) {
        boolean any = text != null && !text.isEmpty();
        mHint.setText(any ? text : "");
        mHint.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    public void setBackToPhotoVisible(boolean visible) {
        mBack.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** The host is setting the wallpaper: the buttons wait and the bar shows "Setting wallpaper…". */
    public void setBusy(boolean busy) {
        if (busy) mLogic.startApplying();
        else mLogic.finish(true);
        render();
    }

    /** An import began: indeterminate "Copying…" until the first progress arrives. */
    public void startImport() {
        mLogic.start();
        render();
    }

    /** A conversion fraction (0 to 1) from the import; main thread. */
    public void onImportProgress(float fraction) {
        if (mLogic.onProgress(fraction, SystemClock.uptimeMillis())) render();
    }

    /** The import ended; the host then sets the hint. */
    public void endImport() {
        mLogic.finish(true);
        render();
    }

    private void render() {
        boolean busy = mLogic.busy();
        mProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        mProgressLabel.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            if (mLogic.indeterminate()) {
                mProgress.setIndeterminate(true);
            } else {
                if (mProgress.isIndeterminate()) {
                    mProgress.setVisibility(View.INVISIBLE);
                    mProgress.setIndeterminate(false);
                    mProgress.setVisibility(View.VISIBLE);
                }
                mProgress.setProgressCompat(mLogic.percent(), false);
            }
            mProgressLabel.setText(mLogic.label(mRoot.getContext().getString(R.string.video_copying),
                mRoot.getContext().getString(R.string.video_converting),
                mRoot.getContext().getString(R.string.video_applying),
                mRoot.getContext().getString(R.string.video_preparing)));
        }
        mChoose.setEnabled(!busy);
        mBack.setEnabled(!busy);
        boolean rates = !busy || mLogic.preparing();
        mRate.setEnabled(rates);
        for (int i = 0; i < mRate.getChildCount(); i++) {
            View b = mRate.getChildAt(i);
            int idx = b.getTag() instanceof Integer
                ? com.termux.app.chrome.scene.VideoVariantRules.rateIndex((Integer) b.getTag()) : -1;
            b.setEnabled(rates && (idx < 0 || mAvailable[idx]));
        }
    }

    // --- tests ---

    @NonNull
    View chooseButton() {
        return mChoose;
    }

    @NonNull
    View backToPhotoButton() {
        return mBack;
    }

    @NonNull
    TextView hintLine() {
        return mHint;
    }
}
