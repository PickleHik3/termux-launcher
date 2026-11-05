package com.termux.app.chrome.wallpaper;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.termux.R;
import com.termux.app.chrome.scene.LiveWallpaperChoice;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Photo | Live at the top of the Wallpaper picker. Photo is the picker page as it always was
 * ({@link WallpaperPickerPage}, untouched); Live is the scene grid ({@link LiveScenePickerPage})
 * and, one level down, a scene's page ({@link LiveSceneDetailPage}). The host wraps the picker's
 * root: the sheet that frames the picker gets {@link #root()} instead. Below Android 13 there is
 * no Photo | Live row and nothing of Live shows.
 *
 * <p>It opens on Live while a scene is in use ({@link LiveSceneLogic#startKind}). A scene's page
 * is a sub-page: the sheet's back arrow and the system back go to the grid first
 * ({@link #consumeBack}). The scene is written (and so starts) only by Use as wallpaper; the knobs
 * on the page change its preview alone. What Use as wallpaper and Back to photo do to the phone's
 * wallpaper is {@link LiveWallpaperApplier}; terminal-app mode and Also set as phone wallpaper
 * are the picker's own ({@link WallpaperPickerPage#launcherOnly}, the same remembered setting).</p>
 */
public final class WallpaperLiveHost {

    private static final String LOG_TAG = "WallpaperLiveHost";

    /** What the host tells the activity. */
    public interface Listener {
        /** The phone's Home and Lock changed: the launcher takes up the new picture. */
        void onSystemWallpaperChanged();
    }

    private final Context mContext;
    private final WallpaperPickerPage mPicker;
    private final Listener mListener;
    private final boolean mSupported;
    private final boolean mHomeApp;
    private final LinearLayout mRoot;
    @Nullable private final LiveScenePickerPage mGrid;
    @Nullable private final LiveSceneDetailPage mDetail;
    private final FrameLayout mLive;
    private final View mPhotoRoot;
    @Nullable private MaterialButtonToggleGroup mKindGroup;
    private LiveSceneLogic.Kind mKind = LiveSceneLogic.Kind.PHOTO;
    private boolean mDetailShown;
    private boolean mReleased;
    /** Use as wallpaper or Back to photo changed what the Photo page read: it reads again when shown. */
    private boolean mPhotoStale;

    public WallpaperLiveHost(@NonNull Context context, @NonNull WallpaperPickerPage picker,
                             @NonNull Listener listener) {
        mContext = context;
        mPicker = picker;
        mListener = listener;
        mSupported = LiveWallpaperChoice.isSupported();
        mHomeApp = !picker.launcherOnly();
        mPhotoRoot = picker.root();

        mRoot = new LinearLayout(context);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        FrameLayout content = new FrameLayout(context);
        mLive = new FrameLayout(context);
        mLive.setVisibility(View.GONE);

        if (mSupported) {
            mRoot.addView(buildKindRow(context), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            mGrid = new LiveScenePickerPage(context, this::openScene);
            mDetail = new LiveSceneDetailPage(context, mHomeApp, new LiveSceneDetailPage.Actions() {
                @Override public void onUse(@NonNull LiveWallpaperChoice choice) {
                    use(choice);
                }

                @Override public void onBackToPhoto() {
                    backToPhoto();
                }

                @Override public void onAlsoPhone(boolean on) {
                    // The Photo page's own checkbox remembers it; one setting for both pages.
                    if (!mReleased) mPicker.alsoPhone().setChecked(on);
                }
            });
            mDetail.root().setVisibility(View.GONE);
            mLive.addView(mGrid.root(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mLive.addView(mDetail.root(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            mGrid = null;
            mDetail = null;
        }

        content.addView(mPhotoRoot, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(mLive, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRoot.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (mSupported) {
            LiveSceneLogic.Kind start = LiveSceneLogic.startKind(readStored(), true);
            showKind(start);
        }
    }

    /** What the sheet frames: the picker, with Photo | Live above it where Live is offered. */
    @NonNull
    public View root() {
        return mRoot;
    }

    /** The picker page under the host. */
    @NonNull
    public WallpaperPickerPage picker() {
        return mPicker;
    }

    // --- the surface's page ---

    /** Back from the surface: a scene's page goes to the grid first. True when it was spent here. */
    public boolean consumeBack() {
        if (mReleased || !mDetailShown) return false;
        showGrid();
        return true;
    }

    public void release() {
        mReleased = true;
        if (mGrid != null) mLive.setVisibility(View.GONE);
    }

    // --- Photo | Live ---

    @NonNull
    private View buildKindRow(@NonNull Context context) {
        View row = LayoutInflater.from(context).inflate(R.layout.preference_segmented_pill, null, false);
        // The settings row's title and summary header is not wanted: the two segments are the row.
        ((ViewGroup) row).getChildAt(0).setVisibility(View.GONE);
        MaterialButtonToggleGroup group = row.findViewById(R.id.segmented_pill_track);
        MaterialButton photo = row.findViewById(R.id.segmented_pill_default);
        MaterialButton live = row.findViewById(R.id.segmented_pill_capsule);
        photo.setText(R.string.wallpaper_kind_photo);
        live.setText(R.string.wallpaper_kind_live);
        ((ViewGroup.MarginLayoutParams) group.getLayoutParams()).topMargin = 0;
        group.setContentDescription(context.getString(R.string.wallpaper_kind_label));
        mKindGroup = group;
        final int photoId = photo.getId();
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked || mReleased) return;
            showKind(checkedId == photoId ? LiveSceneLogic.Kind.PHOTO : LiveSceneLogic.Kind.LIVE);
        });
        return row;
    }

    private void showKind(@NonNull LiveSceneLogic.Kind kind) {
        mKind = kind;
        boolean live = kind == LiveSceneLogic.Kind.LIVE;
        MaterialButtonToggleGroup group = mKindGroup;
        if (group != null) {
            int id = live ? R.id.segmented_pill_capsule : R.id.segmented_pill_default;
            if (group.getCheckedButtonId() != id) group.check(id);
        }
        if (!live && mPhotoStale) {
            mPhotoStale = false;
            mPicker.refreshStored();
        }
        mPhotoRoot.setVisibility(live ? View.GONE : View.VISIBLE);
        mLive.setVisibility(live ? View.VISIBLE : View.GONE);
        if (live) showGrid();
        else if (mDetail != null) mDetail.root().setVisibility(View.GONE);
        mDetailShown = false;
    }

    private void showGrid() {
        if (mGrid == null || mDetail == null) return;
        mGrid.bind(readStored());
        mDetail.root().setVisibility(View.GONE);
        mGrid.root().setVisibility(View.VISIBLE);
        mDetailShown = false;
    }

    private void openScene(@NonNull String kind) {
        if (mDetail == null || mGrid == null || mReleased) return;
        mDetail.show(kind, readStored(), mPicker.alsoPhone().isChecked());
        mGrid.root().setVisibility(View.GONE);
        mDetail.root().setVisibility(View.VISIBLE);
        mDetailShown = true;
    }

    /** The surface shows the page (again); the thumbnails follow their own visibility. */
    public void onShown() {
        if (mReleased || mKind != LiveSceneLogic.Kind.LIVE || mDetailShown) return;
        if (mGrid != null) mGrid.bind(readStored());
    }

    // --- Use as wallpaper, Back to photo ---

    private void use(@NonNull LiveWallpaperChoice choice) {
        if (mDetail == null || mReleased) return;
        mDetail.setBusy(true);
        boolean also = mPicker.alsoPhone().isChecked();
        LiveWallpaperApplier.useScene(mContext, choice, mHomeApp, also, result -> {
            if (mReleased || mDetail == null) return;
            mDetail.setBusy(false);
            switch (result) {
                case SET:
                    mListener.onSystemWallpaperChanged();
                    break;
                case STILL_FAILED:
                    toast(R.string.live_still_failed);
                    break;
                case FAILED:
                    toast(R.string.wallpaper_picker_apply_failed);
                    break;
                case IN_APP_ONLY:
                default:
                    break;
            }
            mPhotoStale = true;
            if (LiveSceneLogic.staysOnScenePage(result)) {
                // The scene plays in the app all the same: the page keeps the message and Back to photo.
                mDetail.setBackToPhotoVisible(true);
            } else {
                showGrid();
            }
        });
    }

    private void backToPhoto() {
        if (mDetail == null || mReleased) return;
        mDetail.setBusy(true);
        LiveWallpaperApplier.backToPhoto(mContext, result -> {
            if (mReleased || mDetail == null) return;
            mDetail.setBusy(false);
            mPhotoStale = true;
            if (result == LiveWallpaperApplier.Result.SET) mListener.onSystemWallpaperChanged();
            else if (result == LiveWallpaperApplier.Result.FAILED) toast(R.string.wallpaper_picker_apply_failed);
            // Back on the grid, with no scene in use.
            showGrid();
        });
    }

    private void toast(int res) {
        Toast.makeText(mContext, res, Toast.LENGTH_LONG).show();
    }

    @NonNull
    private LiveWallpaperChoice readStored() {
        try {
            TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(mContext.getApplicationContext(), false);
            if (prefs != null) return LiveWallpaperChoice.read(prefs);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the live wallpaper choice failed", e);
        }
        return LiveWallpaperChoice.none();
    }

    // --- tests ---

    @NonNull
    LiveSceneLogic.Kind kind() {
        return mKind;
    }

    @Nullable
    LiveScenePickerPage grid() {
        return mGrid;
    }

    @Nullable
    LiveSceneDetailPage detail() {
        return mDetail;
    }

    boolean detailShown() {
        return mDetailShown;
    }
}
