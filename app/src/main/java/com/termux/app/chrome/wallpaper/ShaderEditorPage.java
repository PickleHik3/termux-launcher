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
import com.termux.app.chrome.scene.ShaderErrors;
import com.termux.app.chrome.scene.ShaderSafety;
import com.termux.app.chrome.scene.ShaderStore;

import java.util.List;

/**
 * The shader editor: name, a monospace code box that scrolls sideways, Paste and Import file, the
 * error strip, the knobs (Quality, Frame rate, Texture), Apply, Back to photo while a motion is in
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
        public final boolean isNew;

        Draft(@NonNull String id, @NonNull String name, @NonNull String source,
              @NonNull ShaderSafety.Quality quality, int rateHz, @Nullable String texture,
              @Nullable String savedTexture, boolean isNew) {
            this.id = id;
            this.name = name;
            this.source = source;
            this.quality = quality;
            this.rateHz = rateHz;
            this.texture = texture;
            this.savedTexture = savedTexture;
            this.isNew = isNew;
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

        /** Choose image (the texture). */
        void onPickTexture(@NonNull String id);

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
    private final MaterialButtonToggleGroup mQuality;
    private final MaterialButtonToggleGroup mRate;
    private final TextView mTextureLabel;
    private final MaterialButton mTextureRemove;
    private final View mAlsoRow;
    private final MaterialSwitch mAlso;
    private final View mProgress;
    private final MaterialButton mApply;
    private final MaterialButton mBack;
    private final MaterialButton mDelete;
    private final MaterialButton mPaste;
    private final MaterialButton mImport;
    private final MaterialButton mTextureChoose;

    @NonNull private String mId = "";
    private boolean mIsNew = true;
    @NonNull private ShaderSafety.Quality mQualityValue = ShaderSafety.Quality.HALF;
    private int mRateValue = 30;
    @Nullable private String mTexture;
    @Nullable private String mSavedTexture;
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
        mTextureLabel = mRoot.findViewById(R.id.shader_texture_label);
        mTextureRemove = mRoot.findViewById(R.id.shader_texture_remove);
        mTextureChoose = mRoot.findViewById(R.id.shader_texture_choose);
        mAlsoRow = mRoot.findViewById(R.id.live_also_row);
        mAlso = mRoot.findViewById(R.id.live_also_switch);
        mProgress = mRoot.findViewById(R.id.shader_progress);
        mApply = mRoot.findViewById(R.id.shader_apply);
        mBack = mRoot.findViewById(R.id.live_back_to_photo);
        mDelete = mRoot.findViewById(R.id.shader_delete);
        mPaste = mRoot.findViewById(R.id.shader_paste);
        mImport = mRoot.findViewById(R.id.shader_import);

        mCode.setHorizontallyScrolling(true);

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
            if (b != null && b.getTag() instanceof Integer) mRateValue = (Integer) b.getTag();
        });

        mPaste.setOnClickListener(v -> paste());
        mImport.setOnClickListener(v -> mActions.onImport());
        mTextureChoose.setOnClickListener(v -> mActions.onPickTexture(mId));
        mTextureRemove.setOnClickListener(v -> {
            // The file goes on Apply (or with the folder, for a shader never saved).
            mTexture = null;
            bindTexture();
        });
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
                     boolean motionInUse, boolean alsoPhone, @Nullable String heldReason) {
        mBinding = true;
        try {
            mIsNew = id == null;
            mId = id == null ? ShaderStore.newId() : id;
            ShaderStore.Meta m = meta == null || mIsNew
                ? ShaderStore.Meta.defaults(mContext.getString(R.string.shader_default_name)) : meta;
            mName.setText(mIsNew ? "" : m.name);
            mCode.setText(mIsNew || source == null ? "" : source);
            mQualityValue = m.quality;
            mRateValue = m.rateHz;
            mTexture = m.texture;
            mSavedTexture = mIsNew ? null : m.texture;
            checkByTag(mQuality, mQualityValue);
            checkByTag(mRate, Integer.valueOf(mRateValue));
            bindTexture();
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
        return new Draft(mId, name, textOf(mCode), mQualityValue, mRateValue, mTexture, mSavedTexture, mIsNew);
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

    /** The code box takes {@code text} whole (an imported file). */
    public void setSource(@NonNull String text) {
        mCode.setText(text);
        showErrors(null);
    }

    /** The texture the draft uses; null for none. */
    public void setTexture(@Nullable String name) {
        mTexture = name;
        bindTexture();
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
        mApply.setEnabled(!busy);
        mBack.setEnabled(!busy);
        mDelete.setEnabled(!busy);
        mAlso.setEnabled(!busy);
        mPaste.setEnabled(!busy);
        mImport.setEnabled(!busy);
    }

    private void apply() {
        Draft d = draft();
        if (d.source.trim().isEmpty()) {
            showErrorText(mContext.getString(R.string.shader_empty));
            return;
        }
        mActions.onApply(d);
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

    private void bindTexture() {
        mTextureLabel.setText(mTexture == null ? mContext.getString(R.string.shader_texture_none)
            : mContext.getString(R.string.shader_texture_named, mTexture));
        mTextureRemove.setVisibility(mTexture == null ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private static String textOf(@NonNull TextInputEditText field) {
        CharSequence t = field.getText();
        return t == null ? "" : t.toString();
    }

    // --- tests ---

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
