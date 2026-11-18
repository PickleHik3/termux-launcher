package com.termux.app.chrome.wallpaper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.R;
import com.termux.ai.TaiDepth;
import com.termux.app.chrome.scene.ShaderErrors;
import com.termux.app.chrome.scene.ShaderSafety;
import com.termux.app.chrome.scene.ShaderStore;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The shader editor: name, a monospace code box that scrolls sideways, Paste and Import file, the
 * error strip, the knobs (Quality, Frame rate), the Photo and Depth rows, Apply, Back to photo while a motion is in
 * use, and Delete. The page edits a draft; the host checks, saves and applies it
 * ({@link Actions#onApply}). Nothing is written until Apply.
 */
public final class ShaderEditorPage {

    /** The shader as the page has it now. */
    public static final class Draft {
        /** The folder name: the shader's own, or a fresh one for a new shader. */
        @NonNull public final String id;
        @NonNull public final String name;
        @NonNull public final String source;
        @NonNull public final ShaderSafety.Quality quality;
        public final int rateHz;
        /** The iChannel0 file name inside the shader's folder, or null. */
        @Nullable public final String texture;
        /** The texture the shader was saved with, to remove when it changed. */
        @Nullable public final String savedTexture;
        /** The iChannel1 (depth) file name inside the shader's folder, or null. */
        @Nullable public final String depth;
        /** The depth the shader was saved with, to remove when it changed. */
        @Nullable public final String savedDepth;
        public final boolean isNew;
        /** Opened from a bundled example: Apply saves a copy under {@link #id}, a fresh one. */
        public final boolean isExample;

        Draft(@NonNull String id, @NonNull String name, @NonNull String source,
              @NonNull ShaderSafety.Quality quality, int rateHz, @Nullable String texture,
              @Nullable String savedTexture, @Nullable String depth, @Nullable String savedDepth,
              boolean isNew, boolean isExample) {
            this.isExample = isExample;
            this.id = id;
            this.name = name;
            this.source = source;
            this.quality = quality;
            this.rateHz = rateHz;
            this.texture = texture;
            this.savedTexture = savedTexture;
            this.depth = depth;
            this.savedDepth = savedDepth;
            this.isNew = isNew;
        }

        /** The same draft under another name. */
        @NonNull
        Draft withName(@NonNull String other) {
            return new Draft(id, other, source, quality, rateHz, texture, savedTexture, depth, savedDepth, isNew,
                isExample);
        }
    }

    /** What the page asks of its host; on the main thread. */
    public interface Actions {
        /** Apply, with the draft as it is now. */
        void onApply(@NonNull Draft draft);

        void onBackToPhoto();

        /** Also set as phone wallpaper was switched (terminal-app mode). */
        void onAlsoPhone(boolean on);

        /** Import file. */
        void onImport();

        /** Choose image for the Photo (the texture). */
        void onPickTexture(@NonNull String id);

        /** Choose image for the Depth. */
        void onPickDepth(@NonNull String id);

        /** An image the draft no longer uses and never saved: delete the file. */
        void onDropFile(@NonNull String id, @NonNull String name);

        /** Delete, confirmed. */
        void onDelete(@NonNull String id);
    }

    private final Context mContext;
    private final Actions mActions;
    private final View mRoot;
    private final ScrollView mScroll;
    private final TextInputEditText mName;
    private final TextInputEditText mCode;
    private final TextView mErrors;
    private final TextView mTooHeavy;
    private final TextView mDescription;
    private final MaterialButtonToggleGroup mQuality;
    private final MaterialButtonToggleGroup mRate;
    private final ImageRowView mPhotoRow;
    private final ImageRowView mDepthRow;
    private final View mAlsoRow;
    private final MaterialSwitch mAlso;
    private final View mProgress;
    private final MaterialButton mApply;
    private final MaterialButton mBack;
    private final MaterialButton mDelete;
    private final MaterialButton mPaste;
    private final MaterialButton mImport;

    @NonNull private String mId = "";
    private boolean mIsNew = true;
    /** The open text is a bundled example: nothing of it is saved until Apply, as a copy. */
    private boolean mIsExample;
    @NonNull private ShaderSafety.Quality mQualityValue = ShaderSafety.Quality.HALF;
    private int mRateValue = 30;
    @Nullable private String mTexture;
    @Nullable private String mSavedTexture;
    @Nullable private String mDepth;
    @Nullable private String mSavedDepth;
    /** A depth picked by hand is kept across a photo change and then may not match it (not stored). */
    private boolean mDepthMayNotMatch;
    private ImageRowLogic.Run mRun = ImageRowLogic.Run.IDLE;
    private int mRunPercent;
    private boolean mDepthFailed;
    @Nullable private TaiDepth.Handle mHandle;
    /** Moves when a run or a model check is cancelled, so their late callbacks are dropped. */
    private int mRunToken;
    @Nullable private android.app.Dialog mDialog;
    private boolean mReleased;
    /** What is saved for the open shader, and whether it is the one in use, for {@link #updateApply}. */
    @Nullable private String mSavedSource;
    @Nullable private ShaderStore.Meta mSavedMeta;
    private boolean mInUse;
    /** The page is working: Apply waits whatever the draft says. */
    private boolean mBusy;
    /** Knobs are being set from code: their listeners stay quiet. */
    private boolean mBinding;

    public ShaderEditorPage(@NonNull Context context, boolean launcherIsHomeApp, @NonNull Actions actions) {
        mContext = context;
        mActions = actions;
        mRoot = LayoutInflater.from(context).inflate(R.layout.shader_editor_page, null, false);
        mScroll = mRoot.findViewById(R.id.shader_editor_scroll);
        mName = mRoot.findViewById(R.id.shader_name);
        mCode = mRoot.findViewById(R.id.shader_code);
        mErrors = mRoot.findViewById(R.id.shader_errors);
        mTooHeavy = mRoot.findViewById(R.id.shader_too_heavy);
        mPhotoRow = new ImageRowView(context, mRoot.findViewById(R.id.shader_photo_row), this::onPhotoAction);
        mDepthRow = new ImageRowView(context, mRoot.findViewById(R.id.shader_depth_row), this::onDepthAction);
        mAlsoRow = mRoot.findViewById(R.id.live_also_row);
        mAlso = mRoot.findViewById(R.id.live_also_switch);
        mDescription = mRoot.findViewById(R.id.shader_description);
        mProgress = mRoot.findViewById(R.id.shader_progress);
        mApply = mRoot.findViewById(R.id.shader_apply);
        mBack = mRoot.findViewById(R.id.live_back_to_photo);
        mDelete = mRoot.findViewById(R.id.shader_delete);
        mPaste = mRoot.findViewById(R.id.shader_paste);
        mImport = mRoot.findViewById(R.id.shader_import);

        mCode.setHorizontallyScrolling(true);
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override public void afterTextChanged(android.text.Editable s) {
                if (!mBinding) updateApply();
            }
        };
        mCode.addTextChangedListener(watcher);
        mName.addTextChangedListener(watcher);

        View qualityRow = mRoot.findViewById(R.id.shader_knob_quality);
        ((TextView) qualityRow.findViewById(android.R.id.title)).setText(R.string.shader_knob_quality);
        qualityRow.findViewById(android.R.id.summary).setVisibility(View.GONE);
        mQuality = qualityRow.findViewById(R.id.segmented_pill_track);
        MaterialButton qFull = qualityRow.findViewById(R.id.segmented_pill_default);
        MaterialButton qHalf = qualityRow.findViewById(R.id.segmented_pill_capsule);
        MaterialButton qQuarter = qualityRow.findViewById(R.id.segmented_pill_third);
        qFull.setText(R.string.shader_quality_full);
        qHalf.setText(R.string.shader_quality_half);
        qQuarter.setText(R.string.shader_quality_quarter);
        qQuarter.setVisibility(View.VISIBLE);
        qFull.setTag(ShaderSafety.Quality.FULL);
        qHalf.setTag(ShaderSafety.Quality.HALF);
        qQuarter.setTag(ShaderSafety.Quality.QUARTER);
        mQuality.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (mBinding || !isChecked) return;
            View b = group.findViewById(checkedId);
            if (b != null && b.getTag() instanceof ShaderSafety.Quality) {
                mQualityValue = (ShaderSafety.Quality) b.getTag();
                updateApply();
            }
        });

        View rateRow = mRoot.findViewById(R.id.shader_knob_rate);
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
            if (b != null && b.getTag() instanceof Integer) {
                mRateValue = (Integer) b.getTag();
                updateApply();
            }
        });

        mPaste.setOnClickListener(v -> paste());
        mImport.setOnClickListener(v -> mActions.onImport());
        mApply.setOnClickListener(v -> apply());
        mBack.setOnClickListener(v -> mActions.onBackToPhoto());
        mDelete.setOnClickListener(v -> confirmDelete());

        mAlso.setOnCheckedChangeListener((CompoundButton button, boolean on) -> {
            if (mBinding) return;
            mActions.onAlsoPhone(on);
        });
        ((TextView) mRoot.findViewById(R.id.live_also_title)).setLabelFor(R.id.live_also_switch);
        mAlsoRow.setVisibility(LiveSceneLogic.showsAlsoPhone(launcherIsHomeApp) ? View.VISIBLE : View.GONE);
        mAlsoRow.setOnClickListener(v -> mAlso.toggle());
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    /**
     * Opens a shader. {@code id} null is a new one (a fresh id, the defaults, an empty box);
     * otherwise its saved text and knobs.
     *
     * @param meta   the saved settings (ignored for a new shader)
     * @param source the saved text (ignored for a new shader)
     */
    public void show(@Nullable String id, @Nullable ShaderStore.Meta meta, @Nullable String source,
                     boolean motionInUse, boolean alsoPhone, @Nullable String heldReason, boolean inUse) {
        show(id, meta, source, false, motionInUse, alsoPhone, heldReason, inUse);
    }

    /**
     * Opens a bundled example: its name, text and knobs, as a new shader (a fresh id) that is
     * read-only until Apply saves it as a copy. No Delete, and the note says so.
     */
    public void showExample(@NonNull ShaderStore.Meta meta, @NonNull String source,
                            boolean motionInUse, boolean alsoPhone, @Nullable String heldReason) {
        show(null, meta, source, true, motionInUse, alsoPhone, heldReason, false);
    }

    private void show(@Nullable String id, @Nullable ShaderStore.Meta meta, @Nullable String source,
                      boolean example, boolean motionInUse, boolean alsoPhone, @Nullable String heldReason,
                      boolean inUse) {
        mBinding = true;
        try {
            mIsNew = id == null;
            mIsExample = example;
            mId = id == null ? ShaderStore.newId() : id;
            ShaderStore.Meta m = meta == null || (mIsNew && !example)
                ? ShaderStore.Meta.defaults(mContext.getString(R.string.shader_default_name)) : meta;
            mName.setText(mIsNew && !example ? "" : m.name);
            mCode.setText((mIsNew && !example) || source == null ? "" : source);
            mDescription.setText(example ? R.string.shader_example_note : R.string.shader_description);
            mQualityValue = m.quality;
            mRateValue = m.rateHz;
            cancelRun();
            mTexture = example ? null : m.texture;
            mSavedTexture = mIsNew ? null : m.texture;
            mDepth = example ? null : m.depth;
            mSavedDepth = mIsNew ? null : m.depth;
            mDepthMayNotMatch = false;
            mDepthFailed = false;
            mSavedMeta = mIsNew ? null : meta;
            mSavedSource = mIsNew ? null : source;
            mInUse = inUse;
            checkByTag(mQuality, mQualityValue);
            checkByTag(mRate, Integer.valueOf(mRateValue));
            bindImages();
            showErrors(null);
            mAlso.setChecked(alsoPhone);
            mBack.setVisibility(motionInUse ? View.VISIBLE : View.GONE);
            mDelete.setVisibility(mIsNew ? View.GONE : View.VISIBLE);
            mScroll.scrollTo(0, 0);
        } finally {
            mBinding = false;
        }
        setHeldReason(heldReason);
        setBusy(false);
    }

    private static void checkByTag(@NonNull MaterialButtonToggleGroup group, @NonNull Object tag) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View b = group.getChildAt(i);
            if (tag.equals(b.getTag())) {
                group.check(b.getId());
                return;
            }
        }
    }

    /** The draft as the page has it now. */
    @NonNull
    public Draft draft() {
        String name = ShaderEditorLogic.nameOrDefault(textOf(mName), mContext.getString(R.string.shader_default_name));
        return new Draft(mId, name, textOf(mCode), mQualityValue, mRateValue, mTexture, mSavedTexture, mDepth,
            mSavedDepth, mIsNew, mIsExample);
    }

    @NonNull
    public String id() {
        return mId;
    }

    public boolean isNew() {
        return mIsNew;
    }

    /** Whether the shader's folder holds a texture picked on this page and not saved yet. */
    @Nullable
    public String unsavedTexture() {
        return mTexture != null && !mTexture.equals(mSavedTexture) ? mTexture : null;
    }

    /** The depth picked on this page and not saved yet. */
    @Nullable
    public String unsavedDepth() {
        return mDepth != null && !mDepth.equals(mSavedDepth) ? mDepth : null;
    }

    /** The code box takes {@code text} whole (an imported file). */
    public void setSource(@NonNull String text) {
        mCode.setText(text);
        showErrors(null);
        updateApply();
    }

    /** The photo the draft uses; null for none. A depth made from the old photo goes, a picked one stays. */
    public void setTexture(@Nullable String name) {
        cancelRun();
        mTexture = name;
        mPhotoRow.clearThumb();
        String kept = ShaderEditorLogic.depthAfterPhotoChange(mDepth);
        if (!Objects.equals(kept, mDepth)) {
            dropIfUnsaved(mDepth, mSavedDepth);
            mDepth = kept;
            mDepthRow.clearThumb();
        }
        mDepthMayNotMatch = mDepth != null;
        mDepthFailed = false;
        bindImages();
        updateApply();
    }

    /** The depth image the draft uses; null for none. */
    public void setDepth(@Nullable String name) {
        cancelRun();
        if (!Objects.equals(name, mDepth)) dropIfUnsaved(mDepth, mSavedDepth);
        mDepth = name;
        mDepthMayNotMatch = false;
        mDepthFailed = false;
        mDepthRow.clearThumb();
        bindImages();
        updateApply();
    }

    private void dropIfUnsaved(@Nullable String name, @Nullable String savedName) {
        String drop = ShaderEditorLogic.unsavedFileToDrop(name, savedName);
        if (drop != null) mActions.onDropFile(mId, drop);
    }

    private void onPhotoAction(@NonNull ImageRowLogic.Action action) {
        if (action == ImageRowLogic.Action.REMOVE) setTexture(null);
        else if (action == ImageRowLogic.Action.CHOOSE || action == ImageRowLogic.Action.CHANGE) {
            mActions.onPickTexture(mId);
        }
    }

    private void onDepthAction(@NonNull ImageRowLogic.Action action) {
        switch (action) {
            case REMOVE: setDepth(null); break;
            case CHOOSE:
            case CHANGE: mActions.onPickDepth(mId); break;
            case MAKE:
            case TRY_AGAIN: startMake(); break;
            case CANCEL:
                cancelRun();
                bindImages();
                updateApply();
                break;
            default: break;
        }
    }

    // --- Make depth ---

    private static final ExecutorService CHECK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ShaderDepthCheck");
        t.setDaemon(true);
        return t;
    });

    /** Make: starts at once when the model is on the phone, else asks about the download first. */
    private void startMake() {
        if (mTexture == null || mRun != ImageRowLogic.Run.IDLE || mReleased) return;
        mDepthFailed = false;
        final int token = ++mRunToken;
        final Context app = mContext.getApplicationContext();
        CHECK.execute(() -> {
            final boolean ready = TaiDepth.modelReady(app);
            final long bytes = ready ? 0L : TaiDepth.downloadBytes(app);
            mRoot.post(() -> {
                if (token != mRunToken || mReleased) return;
                if (ready) beginRun();
                else confirmDownload(bytes);
            });
        });
    }

    private void confirmDownload(long bytes) {
        final int token = mRunToken;
        mDialog = new MaterialAlertDialogBuilder(mContext)
            .setTitle(R.string.shader_depth_confirm_title)
            .setMessage(mContext.getString(R.string.shader_depth_confirm_body, ImageRowLogic.formatSize(bytes)))
            .setNegativeButton(R.string.shader_depth_cancel, null)
            .setPositiveButton(R.string.shader_depth_confirm_download, (d, w) -> {
                if (token == mRunToken && !mReleased) beginRun();
            })
            .setOnDismissListener(d -> mDialog = null)
            .show();
    }

    private void beginRun() {
        if (mTexture == null) return;
        final Context app = mContext.getApplicationContext();
        final File dir = ShaderStore.shaderDir(app, mId);
        final int token = ++mRunToken;
        mRun = ImageRowLogic.Run.MAKING;
        mRunPercent = 0;
        mHandle = TaiDepth.start(app, new File(dir, mTexture), new File(dir, ShaderStore.MADE_DEPTH_FILE),
            new TaiDepth.Listener() {
                @Override public void onDownloading(int percent) {
                    if (token != mRunToken) return;
                    mRun = ImageRowLogic.Run.DOWNLOADING;
                    mRunPercent = percent;
                    bindImages();
                }

                @Override public void onMaking(int percent) {
                    if (token != mRunToken) return;
                    mRun = ImageRowLogic.Run.MAKING;
                    mRunPercent = percent;
                    bindImages();
                }

                @Override public void onDone(@NonNull File out) {
                    if (token != mRunToken) return;
                    endRun();
                    dropIfUnsaved(mDepth, mSavedDepth);
                    mDepth = ShaderStore.MADE_DEPTH_FILE;
                    mDepthMayNotMatch = false;
                    mDepthRow.clearThumb();
                    bindImages();
                    updateApply();
                }

                @Override public void onFailed(@NonNull String code) {
                    if (token != mRunToken) return;
                    endRun();
                    mDepthFailed = true;
                    bindImages();
                    updateApply();
                }
            });
        bindImages();
        updateApply();
    }

    private void endRun() {
        mRun = ImageRowLogic.Run.IDLE;
        mHandle = null;
    }

    /** Stops a running or pending depth run, and a download question, without a failure. */
    private void cancelRun() {
        mRunToken++;
        TaiDepth.Handle h = mHandle;
        if (h != null) h.cancel();
        endRun();
        android.app.Dialog dialog = mDialog;
        mDialog = null;
        if (dialog != null) dialog.dismiss();
    }

    /** The editor went away (Back, Apply, discard): the run stops and the thumbnails are let go. */
    void onClosed() {
        cancelRun();
        mPhotoRow.clearThumb();
        mDepthRow.clearThumb();
    }

    /** The host is released: as {@link #onClosed}, for good. */
    void release() {
        mReleased = true;
        onClosed();
    }

    /** Shows up to three errors on the strip; none hides it. */
    public void showErrors(@Nullable List<ShaderErrors.Error> errors) {
        showErrorText(ShaderEditorLogic.errorText(errors));
    }

    public void showErrorText(@NonNull String text) {
        mErrors.setText(text);
        boolean any = !text.isEmpty();
        mErrors.setVisibility(any ? View.VISIBLE : View.GONE);
        if (any) mErrors.post(() -> mScroll.smoothScrollTo(0, Math.max(0, mErrors.getTop() - mScroll.getHeight() / 3)));
    }

    /** "Too heavy at this quality" above the knobs while the running shader holds at its still. */
    public void setHeldReason(@Nullable String reason) {
        mTooHeavy.setVisibility(ShaderEditorLogic.showsTooHeavy(reason) ? View.VISIBLE : View.GONE);
    }

    /** Shows or hides Back to photo and keeps the page otherwise as it is, for after an apply. */
    public void setBackToPhotoVisible(boolean visible) {
        mBack.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Working: the buttons wait and the bar shows. */
    public void setBusy(boolean busy) {
        mProgress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        mBusy = busy;
        updateApply();
        mBack.setEnabled(!busy);
        mDelete.setEnabled(!busy);
        mAlso.setEnabled(!busy);
        mPaste.setEnabled(!busy);
        mImport.setEnabled(!busy);
    }

    /** Apply is on when there is something to apply and the page is not working. */
    private void updateApply() {
        mApply.setEnabled(!mBusy && mRun == ImageRowLogic.Run.IDLE && ShaderEditorLogic.canApply(draft(), mSavedSource, mSavedMeta, mInUse));
    }

    private void apply() {
        Draft d = draft();
        if (d.source.trim().isEmpty()) {
            showErrorText(mContext.getString(R.string.shader_empty));
            return;
        }
        mActions.onApply(d);
    }

    /** Paste: the clipboard's text into the code box, or the error strip's message when it has none. */
    void pasteClipboard() {
        paste();
    }

    private void paste() {
        ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        CharSequence text = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(mContext);
        if (text == null || text.toString().trim().isEmpty()) {
            showErrorText(mContext.getString(R.string.shader_clipboard_empty));
            return;
        }
        setSource(text.toString());
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(mContext)
            .setTitle(R.string.shader_delete_confirm)
            .setPositiveButton(R.string.shader_delete_yes, (d, w) -> mActions.onDelete(mId))
            .setNegativeButton(R.string.shader_delete_no, null)
            .show();
    }

    private void bindImages() {
        final File dir = ShaderStore.shaderDir(mContext.getApplicationContext(), mId);
        mPhotoRow.bind(ImageRowLogic.photo(mTexture), mTexture, mTexture == null ? null : new File(dir, mTexture));
        mDepthRow.bind(ImageRowLogic.depth(mTexture != null, mDepth, mDepthMayNotMatch, mRun, mRunPercent,
            mDepthFailed), mDepth, mDepth == null ? null : new File(dir, mDepth));
    }

    @NonNull
    private static String textOf(@NonNull TextInputEditText field) {
        CharSequence t = field.getText();
        return t == null ? "" : t.toString();
    }

    // --- tests ---

    @NonNull
    ImageRowView photoRow() {
        return mPhotoRow;
    }

    @NonNull
    ImageRowView depthRow() {
        return mDepthRow;
    }

    @NonNull
    View applyButton() {
        return mApply;
    }

    @NonNull
    View backToPhotoButton() {
        return mBack;
    }

    @NonNull
    View deleteButton() {
        return mDelete;
    }

    @NonNull
    View errorStrip() {
        return mErrors;
    }

    @NonNull
    View tooHeavyLine() {
        return mTooHeavy;
    }

    @NonNull
    View alsoRow() {
        return mAlsoRow;
    }

    @NonNull
    TextInputEditText codeBox() {
        return mCode;
    }
}
