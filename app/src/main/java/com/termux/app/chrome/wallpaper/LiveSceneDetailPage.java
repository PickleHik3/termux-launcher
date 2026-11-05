package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.termux.R;
import com.termux.app.chrome.scene.LiveWallpaperChoice;
import com.termux.app.chrome.scene.SceneThumbView;

import java.util.Locale;

/**
 * One scene's page: a preview that follows the knobs (Speed, Frame rate, and, where the scene has
 * them, Density and Colour), then Use as wallpaper, Back to photo while a scene is in use, and in
 * terminal-app mode Also set as phone wallpaper. The knobs change the preview only; the host
 * writes them with the choice when Use as wallpaper is tapped ({@link Actions#onUse}).
 */
public final class LiveSceneDetailPage {

    /** What the page asks of its host; on the main thread. */
    public interface Actions {
        /** Use as wallpaper, with the knobs as they are now. */
        void onUse(@NonNull LiveWallpaperChoice choice);

        void onBackToPhoto();

        /** Also set as phone wallpaper was switched (terminal-app mode). */
        void onAlsoPhone(boolean on);
    }

    private final Context mContext;
    private final Actions mActions;
    private final View mRoot;
    private final TextView mTitle;
    private final TextView mDesc;
    private final LiveAspectFrame mFrame;
    private final SceneThumbView mPreview;
    private final View mSpeedRow;
    private final Slider mSpeed;
    private final TextView mSpeedValue;
    private final MaterialButtonToggleGroup mRate;
    private final View mDensityRow;
    private final Slider mDensity;
    private final TextView mDensityValue;
    private final View mColourRow;
    private final ChipGroup mChips;
    private final View mAlsoRow;
    private final MaterialSwitch mAlso;
    private final View mProgress;
    private final MaterialButton mUse;
    private final MaterialButton mBack;
    private final View mScroll;

    /** The scene and the knobs on the page now. */
    @NonNull private LiveWallpaperChoice mCurrent = LiveWallpaperChoice.none();
    /** Knobs are being set from code: their listeners stay quiet. */
    private boolean mBinding;

    public LiveSceneDetailPage(@NonNull Context context, boolean launcherIsHomeApp, @NonNull Actions actions) {
        mContext = context;
        mActions = actions;
        mRoot = LayoutInflater.from(context).inflate(R.layout.live_scene_detail_page, null, false);
        mScroll = mRoot.findViewById(R.id.live_detail_scroll);
        mTitle = mRoot.findViewById(R.id.live_detail_title);
        mDesc = mRoot.findViewById(R.id.live_detail_desc);
        mFrame = mRoot.findViewById(R.id.live_detail_frame);
        mPreview = mRoot.findViewById(R.id.live_detail_preview);
        mProgress = mRoot.findViewById(R.id.live_detail_progress);
        mUse = mRoot.findViewById(R.id.live_use);
        mBack = mRoot.findViewById(R.id.live_back_to_photo);
        mAlsoRow = mRoot.findViewById(R.id.live_also_row);
        mAlso = mRoot.findViewById(R.id.live_also_switch);
        mColourRow = mRoot.findViewById(R.id.live_knob_colour);
        mChips = mRoot.findViewById(R.id.live_palette_chips);

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mFrame.setRatio(LiveSceneLogic.frameHeight(1000, dm.widthPixels, dm.heightPixels) / 1000f);
        mFrame.setMaxHeightPx(LiveSceneLogic.previewMaxHeightPx(dm.heightPixels, dm.density));

        mSpeedRow = mRoot.findViewById(R.id.live_knob_speed);
        mSpeed = mSpeedRow.findViewById(R.id.seekbar);
        mSpeedValue = mSpeedRow.findViewById(R.id.seekbar_value);
        prepareRow(mSpeedRow, R.string.live_knob_speed);
        mSpeed.setValueFrom(LiveWallpaperChoice.MIN_SPEED);
        mSpeed.setValueTo(LiveWallpaperChoice.MAX_SPEED);
        mSpeed.setValue(LiveWallpaperChoice.DEFAULT_SPEED);
        mSpeed.addOnChangeListener((slider, value, fromUser) -> {
            if (mBinding) return;
            setCurrent(mCurrent.withSpeed(value));
        });

        mDensityRow = mRoot.findViewById(R.id.live_knob_density);
        mDensity = mDensityRow.findViewById(R.id.seekbar);
        mDensityValue = mDensityRow.findViewById(R.id.seekbar_value);
        prepareRow(mDensityRow, R.string.live_knob_density);
        mDensity.setValueFrom(LiveWallpaperChoice.MIN_DENSITY);
        mDensity.setValueTo(LiveWallpaperChoice.MAX_DENSITY);
        mDensity.setValue(0f);
        mDensity.addOnChangeListener((slider, value, fromUser) -> {
            if (mBinding) return;
            setCurrent(mCurrent.withDensity(value));
        });

        View rateRow = mRoot.findViewById(R.id.live_knob_rate);
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
            if (b != null && b.getTag() instanceof Integer) setCurrent(mCurrent.withRateHz((Integer) b.getTag()));
        });

        mAlsoRow.setVisibility(View.GONE);
        mAlso.setOnCheckedChangeListener((CompoundButton button, boolean on) -> {
            if (mBinding) return;
            mActions.onAlsoPhone(on);
        });
        ((TextView) mRoot.findViewById(R.id.live_also_title)).setLabelFor(R.id.live_also_switch);
        mAlsoRow.setVisibility(LiveSceneLogic.showsAlsoPhone(launcherIsHomeApp) ? View.VISIBLE : View.GONE);
        mAlsoRow.setOnClickListener(v -> mAlso.toggle());

        mUse.setOnClickListener(v -> mActions.onUse(mCurrent));
        mBack.setOnClickListener(v -> mActions.onBackToPhoto());
    }

    /** A settings seekbar row used bare: its title, no summary, no icon. */
    private void prepareRow(@NonNull View row, int titleRes) {
        ((TextView) row.findViewById(android.R.id.title)).setText(titleRes);
        row.findViewById(android.R.id.summary).setVisibility(View.GONE);
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    /** The scene and the knobs on the page now. */
    @NonNull
    public LiveWallpaperChoice current() {
        return mCurrent;
    }

    /**
     * Opens {@code kind}: its name and fact, the knobs it has, set to {@link LiveSceneLogic#detailStart}
     * of {@code stored}; Back to photo only while {@code stored} has a scene.
     */
    public void show(@NonNull String kind, @NonNull LiveWallpaperChoice stored, boolean alsoPhone) {
        mBinding = true;
        try {
            mTitle.setText(LiveSceneLogic.nameRes(kind));
            mDesc.setText(LiveSceneLogic.descRes(kind));
            LiveWallpaperChoice start = LiveSceneLogic.detailStart(stored, kind);
            mCurrent = start;
            mSpeed.setValue(start.speed);
            mSpeedValue.setText(speedText(start.speed));
            View button = null;
            for (int i = 0; i < mRate.getChildCount(); i++) {
                View b = mRate.getChildAt(i);
                if (b.getTag() instanceof Integer && (Integer) b.getTag() == start.rateHz) button = b;
            }
            if (button != null) mRate.check(button.getId());
            boolean density = LiveSceneLogic.showsDensity(kind);
            mDensityRow.setVisibility(density ? View.VISIBLE : View.GONE);
            mDensity.setValue(start.density);
            mDensityValue.setText(densityText(start.density));
            buildChips(kind, start.palette);
            mBack.setVisibility(LiveSceneLogic.showsBackToPhoto(stored) ? View.VISIBLE : View.GONE);
            mAlso.setChecked(alsoPhone);
            mPreview.setChoice(start);
            mScroll.scrollTo(0, 0);
        } finally {
            mBinding = false;
        }
        setBusy(false);
    }

    /** Shows or hides Back to photo and keeps the page otherwise as it is, for after a set. */
    public void setBackToPhotoVisible(boolean visible) {
        mBack.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** Also set as phone wallpaper, kept in step with the Photo page's. */
    public void setAlsoPhone(boolean on) {
        mBinding = true;
        try {
            mAlso.setChecked(on);
        } finally {
            mBinding = false;
        }
    }

    /** Working: the buttons wait and the bar shows. */
    public void setBusy(boolean busy) {
        mProgress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        mUse.setEnabled(!busy);
        mBack.setEnabled(!busy);
        mAlso.setEnabled(!busy);
    }

    private void setCurrent(@NonNull LiveWallpaperChoice next) {
        mCurrent = next;
        mSpeedValue.setText(speedText(next.speed));
        mDensityValue.setText(densityText(next.density));
        mPreview.setChoice(next);
    }

    private void buildChips(@NonNull String kind, int selected) {
        mChips.setOnCheckedStateChangeListener(null);
        mChips.removeAllViews();
        int[] names = LiveSceneLogic.palettes(kind);
        mColourRow.setVisibility(names.length > 0 ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        for (int i = 0; i < names.length; i++) {
            Chip chip = (Chip) inflater.inflate(R.layout.live_palette_chip, mChips, false);
            chip.setId(View.generateViewId());
            chip.setText(names[i]);
            chip.setTag(i);
            mChips.addView(chip);
            if (i == LiveWallpaperChoice.clampPalette(selected)) mChips.check(chip.getId());
        }
        mChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (mBinding || ids.isEmpty()) return;
            View chip = group.findViewById(ids.get(0));
            if (chip != null && chip.getTag() instanceof Integer) {
                setCurrent(mCurrent.withPalette((Integer) chip.getTag()));
            }
        });
    }

    @NonNull
    private String speedText(float speed) {
        return String.format(Locale.getDefault(), mContext.getString(R.string.live_speed_value), speed);
    }

    @NonNull
    private String densityText(float density) {
        if (Math.abs(density) < 0.05f) return mContext.getString(R.string.live_density_default);
        return String.format(Locale.getDefault(), mContext.getString(R.string.live_density_value), density);
    }

    // --- tests ---

    @NonNull
    View useButton() {
        return mUse;
    }

    @NonNull
    View backToPhotoButton() {
        return mBack;
    }

    @NonNull
    View alsoRow() {
        return mAlsoRow;
    }

    @NonNull
    View densityRow() {
        return mDensityRow;
    }

    @NonNull
    View colourRow() {
        return mColourRow;
    }

    @NonNull
    ChipGroup chips() {
        return mChips;
    }

    @Nullable
    ViewGroup rateGroup() {
        return mRate;
    }
}
