package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The wallpaper picker as a small bottom sheet: a Photo row, and, when generated backgrounds are
 * offered, a row of tiles (rest-pose still, name, Material / Own palette toggle). Tapping a tile
 * asks home / lock / both and applies it through {@link GeneratedWallpaperApplier}.
 *
 * <p>Thumbnails are stills rendered once per (background, palette mode) on a worker thread and
 * kept in memory only while the sheet is open; nothing animates here.</p>
 */
public final class WallpaperPickerSheet {

    /** What the host does with the sheet's outcomes. Both are called on the main thread. */
    public interface Listener {
        /** Photo row tapped; the sheet has already dismissed. Run the existing photo flow. */
        void onPickPhoto();

        /** A generated background was applied; the sheet has dismissed itself. */
        void onGeneratedApplied(@NonNull AnimatedWallpaper wallpaper, @NonNull String paletteMode);
    }

    private static final String LOG_TAG = "WallpaperPickerSheet";
    private static final int THUMB_HEIGHT_DP = 150;

    private final AppCompatActivity mActivity;
    private final Listener mListener;
    private final BottomSheetDialog mSheet;
    private final float mDensity;
    private final ExecutorService mRenderer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wallpaper-picker-thumbs");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Bitmap> mThumbs = new HashMap<>();
    private final String mStoredId;
    private final String mStoredMode;
    private final int mThumbW;
    private final int mThumbH;
    private boolean mDismissed;
    private boolean mApplying;
    @Nullable private LinearProgressIndicator mProgress;
    @Nullable private TextView mProgressText;
    @Nullable private View mTiles;

    /**
     * Shows the sheet. {@code animatedOffered} is
     * {@code GeneratedWallpaperApplier.offered(Build.VERSION.SDK_INT, fancierActive)}; when false
     * only the Photo row is shown.
     */
    public static void show(@NonNull AppCompatActivity activity, boolean animatedOffered, @NonNull Listener listener) {
        new WallpaperPickerSheet(activity, animatedOffered && Build.VERSION.SDK_INT >= 34, listener);
    }

    private WallpaperPickerSheet(@NonNull AppCompatActivity activity, boolean animated, @NonNull Listener listener) {
        mActivity = activity;
        mListener = listener;
        mDensity = activity.getResources().getDisplayMetrics().density;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(activity.getApplicationContext(), false);
        mStoredId = prefs == null ? null : prefs.getManagedWallpaperAnimatedId();
        mStoredMode = prefs == null ? null : prefs.getManagedWallpaperAnimatedPalette();
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        mThumbH = Math.round(THUMB_HEIGHT_DP * mDensity);
        mThumbW = WallpaperPickerLogic.thumbWidth(mThumbH, dm.widthPixels, dm.heightPixels);

        mSheet = new BottomSheetDialog(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(18), pad, dp(20));

        TextView title = new TextView(activity);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurface));
        title.setText(R.string.wallpaper_picker_title);
        content.addView(title);

        content.addView(photoRow());

        if (animated) {
            content.addView(sectionLabel(R.string.wallpaper_picker_animated));
            HorizontalScrollView scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp(12);
                row.addView(tile(w), lp);
            }
            scroll.addView(row);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = dp(10);
            content.addView(scroll, sp);
            mTiles = scroll;

            mProgressText = new TextView(activity);
            mProgressText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            mProgressText.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
            mProgressText.setText(R.string.wallpaper_picker_applying);
            mProgressText.setPadding(0, dp(10), 0, dp(6));
            mProgressText.setVisibility(View.GONE);
            content.addView(mProgressText);
            mProgress = new LinearProgressIndicator(activity);
            mProgress.setIndeterminate(true);
            mProgress.setVisibility(View.GONE);
            content.addView(mProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        mSheet.setContentView(content);
        mSheet.setOnDismissListener(d -> release());
        mSheet.show();
    }

    @NonNull
    private View photoRow() {
        Context ctx = mActivity;
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(color(com.termux.shared.R.attr.termuxColorSurfacePanelHighest));
        row.setBackground(bg);
        row.setClickable(true);
        row.setFocusable(true);
        TextView label = new TextView(ctx);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        label.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurface));
        label.setText(R.string.wallpaper_picker_photo);
        row.addView(label);
        TextView hint = new TextView(ctx);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(color(com.termux.shared.R.attr.termuxColorOnSurfaceVariant));
        hint.setText(R.string.wallpaper_picker_photo_hint);
        row.addView(hint);
        row.setOnClickListener(v -> {
            if (mApplying) return;
            mSheet.dismiss();
            mListener.onPickPhoto();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        row.setLayoutParams(lp);
        return row;
    }

    @NonNull
    private View tile(@NonNull AnimatedWallpaper w) {
        Context ctx = mActivity;
        final boolean stored = WallpaperPickerLogic.isStored(w.id(), mStoredId);
        final String[] mode = {WallpaperPickerLogic.initialMode(w.id(), mStoredId, mStoredMode)};

        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView image = new ImageView(ctx);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(14));
        frame.setColor(color(com.termux.shared.R.attr.termuxColorSurfacePanelHighest));
        if (stored) frame.setStroke(dp(2), color(com.termux.shared.R.attr.termuxColorPrimary));
        image.setBackground(frame);
        image.setClipToOutline(true);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });
        tile.addView(image, new LinearLayout.LayoutParams(mThumbW, mThumbH));

        TextView name = new TextView(ctx);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextColor(color(stored ? com.termux.shared.R.attr.termuxColorPrimary
            : com.termux.shared.R.attr.termuxColorOnSurface));
        name.setText(stored ? ctx.getString(R.string.wallpaper_picker_current) + " · " + w.label() : w.label());
        name.setSingleLine(true);
        name.setPadding(0, dp(6), 0, dp(4));
        name.setMaxWidth(Math.max(mThumbW, dp(96)));
        tile.addView(name);

        MaterialButtonToggleGroup toggle = new MaterialButtonToggleGroup(ctx);
        toggle.setSingleSelection(true);
        toggle.setSelectionRequired(true);
        MaterialButton material = toggleButton(R.string.wallpaper_picker_palette_material);
        MaterialButton own = toggleButton(R.string.wallpaper_picker_palette_own);
        toggle.addView(material);
        toggle.addView(own);
        toggle.check(WallpaperPaletteCapture.MODE_OWN.equals(mode[0]) ? own.getId() : material.getId());
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            mode[0] = checkedId == own.getId() ? WallpaperPaletteCapture.MODE_OWN : WallpaperPaletteCapture.MODE_MATERIAL;
            loadThumb(w, mode[0], image);
        });
        tile.addView(toggle, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        image.setOnClickListener(v -> onTileTapped(w, mode[0]));
        name.setOnClickListener(v -> onTileTapped(w, mode[0]));
        loadThumb(w, mode[0], image);
        return tile;
    }

    @NonNull
    private MaterialButton toggleButton(int textRes) {
        MaterialButton b = new MaterialButton(mActivity, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setId(View.generateViewId());
        b.setText(textRes);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private void loadThumb(@NonNull AnimatedWallpaper w, @NonNull String mode, @NonNull ImageView image) {
        if (Build.VERSION.SDK_INT < 34) return;
        final String key = w.id() + ":" + mode;
        image.setTag(key);
        Bitmap cached = mThumbs.get(key);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        image.setImageDrawable(null);
        renderThumb(w, mode, key, image);
    }

    @RequiresApi(34)
    private void renderThumb(@NonNull AnimatedWallpaper w, @NonNull String mode, @NonNull String key,
                             @NonNull ImageView image) {
        // Theme attributes are read here on the main thread; the worker gets plain colours.
        final int[] palette;
        try {
            palette = WallpaperPaletteCapture.resolve(mActivity, w, mode);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Thumbnail palette failed", e);
            return;
        }
        try {
            mRenderer.execute(() -> {
                Bitmap bmp = null;
                try {
                    bmp = AnimatedWallpaperStill.render(w, palette, mThumbW, mThumbH);
                } catch (RuntimeException | OutOfMemoryError e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Thumbnail render failed", e);
                }
                final Bitmap result = bmp;
                image.post(() -> {
                    if (result == null) return;
                    if (mDismissed) return;
                    mThumbs.put(key, result);
                    if (key.equals(image.getTag())) image.setImageBitmap(result);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Sheet already released.
        }
    }

    private void onTileTapped(@NonNull AnimatedWallpaper w, @NonNull String mode) {
        if (mApplying || mDismissed) return;
        String[] labels = {
            mActivity.getString(R.string.wallpaper_target_home_screen),
            mActivity.getString(R.string.wallpaper_target_lock_screen),
            mActivity.getString(R.string.wallpaper_target_home_and_lock_screen)
        };
        String[] targets = {"home", "lock", "both"};
        new MaterialAlertDialogBuilder(mActivity)
            .setAdapter(new ArrayAdapter<>(mActivity, android.R.layout.simple_list_item_1, labels),
                (dialog, which) -> applyGenerated(w, mode, targets[which]))
            .show();
    }

    private void applyGenerated(@NonNull AnimatedWallpaper w, @NonNull String mode, @NonNull String target) {
        if (Build.VERSION.SDK_INT < 34 || mApplying || mDismissed) return;
        mApplying = true;
        setBusy(true);
        applyOnApi34(w, mode, target);
    }

    @RequiresApi(34)
    private void applyOnApi34(@NonNull AnimatedWallpaper w, @NonNull String mode, @NonNull String target) {
        GeneratedWallpaperApplier.apply(mActivity, w, mode, target, (ok, error) -> {
            mApplying = false;
            if (mActivity.isFinishing() || mActivity.isDestroyed()) {
                if (!mDismissed) mSheet.dismiss();
                return;
            }
            if (ok) {
                if (!mDismissed) mSheet.dismiss();
                mListener.onGeneratedApplied(w, mode);
            } else {
                Logger.logError(LOG_TAG, "Applying " + w.id() + " failed: " + error);
                if (!mDismissed) setBusy(false);
                Toast.makeText(mActivity, R.string.wallpaper_picker_apply_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setBusy(boolean busy) {
        if (mProgress != null) mProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (mProgressText != null) mProgressText.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (mTiles != null) mTiles.setAlpha(busy ? 0.5f : 1f);
    }

    private void release() {
        mDismissed = true;
        mRenderer.shutdown();
        // Not recycled: the views may still draw them through the dismiss animation.
        mThumbs.clear();
    }

    @NonNull
    private TextView sectionLabel(int textRes) {
        TextView label = new TextView(mActivity);
        label.setText(textRes);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setAllCaps(true);
        label.setLetterSpacing(0.12f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(color(com.termux.shared.R.attr.termuxColorPrimary));
        label.setPadding(0, dp(18), 0, 0);
        return label;
    }

    private int dp(int v) {
        return Math.round(v * mDensity);
    }

    private int color(int attr) {
        TypedValue value = new TypedValue();
        return mActivity.getTheme().resolveAttribute(attr, value, true) ? value.data : 0xFF808080;
    }
}
