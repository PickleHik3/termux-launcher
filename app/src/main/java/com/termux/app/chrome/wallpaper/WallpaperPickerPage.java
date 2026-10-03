package com.termux.app.chrome.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.ai.TaiVisionModels;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.chrome.wallpaper.living.LivingStillJob;
import com.termux.app.chrome.wallpaper.living.LivingStills;
import com.termux.app.chrome.wallpaper.living.Manifest;
import com.termux.app.fragments.settings.termux.TaiModelCentreFragment;
import com.termux.app.fragments.settings.termux.TermuxStylePreferencesFragment;
import com.termux.app.launcher.data.IconPackChoices;
import com.termux.app.layouteditor.EditorM3;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * The full-screen wallpaper picker page (lock-live-wallpaper.md, "The picker page"): two slot
 * previews (Lock, then Home) in a snap pager, the Lock slot's Motion toggle, the Look / Icon pack /
 * Layout shortcuts, the thumbnail strip and the Apply split button. It replaces the old bottom
 * sheet and its home / lock / both dialog.
 *
 * <p>The page reads and writes the slots only through {@link Slots}, which in the app wraps
 * {@link WallpaperSlots}; it never names WallpaperManager or a slot preference. Tapping a
 * thumbnail sets the centred slot's pending choice and previews it there, live. Apply puts the
 * centred card's background on Home and points Lock at it (Same as Home); its menu applies to one
 * slot only. Swiping keeps each slot's pending choice. Back closes without applying.</p>
 *
 * <p>Look, Layout and Photo… close the page and hand the host a {@link ReturnState}; the host
 * shows the page again with it when that editor or the photo crop ends, on the same slot with the
 * same pending choices. A cropped photo comes back as the centred slot's pending choice
 * ({@link ReturnState#withPhoto}), previewed in its card and set only by Apply. Back without Apply
 * discards a pending photo's file. The Icon pack button opens a menu on the page itself.</p>
 *
 * <p>Photos are first-class on every API level: a slot holding a photo shows it in its card, and
 * the last three applied photos lead the strip as the Recent group. When the animated backgrounds
 * are not offered (below API 34, or Fancier Glass off) the page has no animated tiles, no Motion
 * toggle and no live preview: Recent, Photo… and the current pictures only.</p>
 *
 * <p>Living stills (living-stills.md, Part D; API 34+, animated backgrounds offered): a slot whose
 * choice is a photo shows Bring to life in the Motion row. It asks for any missing vision model
 * (a dialog that opens the model centre), else starts the process-owned {@link LivingStillJob},
 * whose determinate bar and stage replace the button; the page only attaches a listener, so the run
 * outlives it. When it ends the pending choice becomes the living still, the card plays it, Apply
 * puts it on the slot, and the row holds that slot's Motion switch (on) and a small Read again. A
 * photo that already has a living still is adopted as one when chosen. More settings opens the old
 * Look page.</p>
 *
 * <p>Hosted in a full-screen {@link AppCompatDialog} by {@link #show}; tests build the page on
 * its own with {@link #WallpaperPickerPage(Context, Slots, Listener, int, Runnable, ReturnState)}.</p>
 */
public final class WallpaperPickerPage {

    /** The page's one door to the slots. The app's is {@link #systemSlots}; tests pass a fake. */
    public interface Slots {
        @NonNull WallpaperSlots.State read();

        void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                   @Nullable WallpaperSlots.Callback cb);

        void setLockMotion(boolean on, @Nullable WallpaperSlots.Callback cb);

        /** The Home slot's Motion toggle, for a living still. */
        default void setHomeMotion(boolean on, @Nullable WallpaperSlots.Callback cb) {
            if (cb != null) cb.onDone(true, null);
        }

        /**
         * The process's living-still job, or null when this page does not build living stills
         * (below API 34, tests that do not exercise them).
         */
        @Nullable
        default LivingStillJob livingJob() {
            return null;
        }

        /** The living still built for {@code photo}, or null. Reads the photo to hash it. */
        @Nullable
        default Manifest livingFor(@NonNull File photo) {
            return null;
        }

        /** The manifest of the living still {@code living:<hash>}, or null when its files are gone. */
        @Nullable
        default Manifest livingById(@NonNull String id) {
            return null;
        }

        /** The vision models the analysis still needs on this phone; empty when it can run. */
        @NonNull
        default List<TaiVisionModels.Missing> livingMissing() {
            return Collections.emptyList();
        }

        /** Opens the model centre on {@code modelId}'s row. */
        default void openModelCentre(@NonNull String modelId) {}

        /** Opens the old Look settings page. */
        default void openMoreSettings() {}

        /** The recent photos, newest first. */
        @NonNull
        default List<File> recents() {
            return Collections.emptyList();
        }

        /** A pending photo the page will not apply: its file goes (only a pending file ever does). */
        default void discardPhoto(@NonNull File photo) {}
    }

    /** What the host does. Every call is on the main thread. */
    public interface Listener {
        /** The page covers the launcher (true) or has gone (false): pause or resume its backdrop. */
        void onPageShown(boolean shown);

        /**
         * Photo… was tapped for {@code slot}; the page has dismissed. Run the photo picker and the
         * crop, then show the page again with {@link ReturnState#withPhoto}{@code (back, …)}, or
         * with {@code back} itself when the pick or the crop was cancelled.
         */
        void onPickPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull ReturnState back);

        /** Apply succeeded for {@code slot}; the page stays open. Home refreshes the glass. */
        void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice);

        /**
         * Look or Layout; the page has dismissed. Open that editor and, when it ends, show the page
         * again with {@code back}.
         */
        void onOpenLook(@NonNull ReturnState back);

        void onOpenLayout(@NonNull ReturnState back);

        /** The Icon pack menu's rows ({@link IconPackChoices#KEY_PINNED}), the one in force checked. */
        @NonNull IconPackChoices.Listing iconPacks();

        /** An Icon pack row was picked ("" for the default row); the page stays open. */
        void onIconPackChosen(@NonNull String packageName);
    }

    /** What the page comes back to after Look or Layout: the centred slot and both pending choices. */
    public static final class ReturnState {
        @NonNull public final WallpaperSlots.Slot centred;
        @NonNull public final WallpaperSlots.Choice pendingHome;
        @NonNull public final WallpaperSlots.Choice pendingLock;

        public ReturnState(@NonNull WallpaperSlots.Slot centred, @NonNull WallpaperSlots.Choice pendingHome,
                           @NonNull WallpaperSlots.Choice pendingLock) {
            this.centred = centred;
            this.pendingHome = pendingHome;
            this.pendingLock = pendingLock;
        }

        /**
         * {@code back} with the cropped {@code photo} as {@code slot}'s pending choice, centred on
         * that slot. With no {@code back} (the activity was recreated during the crop) the other
         * slot keeps what {@code stored} has.
         */
        @NonNull
        public static ReturnState withPhoto(@Nullable ReturnState back, @NonNull WallpaperSlots.Slot slot,
                                            @NonNull File photo, @NonNull WallpaperSlots.State stored) {
            WallpaperSlots.Choice home = back != null ? back.pendingHome : stored.home;
            WallpaperSlots.Choice lock = back != null ? back.pendingLock : stored.lock;
            WallpaperSlots.Choice picked = WallpaperSlots.Choice.photo(photo);
            if (slot == WallpaperSlots.Slot.HOME) home = picked;
            else lock = picked;
            return new ReturnState(slot, home, lock);
        }
    }

    /** {@link WallpaperSlots} for this activity. */
    @NonNull
    public static Slots systemSlots(@NonNull Activity activity) {
        return new Slots() {
            @NonNull @Override public WallpaperSlots.State read() {
                return WallpaperSlots.read(activity);
            }

            @Override public void apply(@NonNull WallpaperSlots.Slot slot,
                                        @NonNull WallpaperSlots.Choice choice,
                                        @Nullable WallpaperSlots.Callback cb) {
                WallpaperSlots.apply(activity, slot, choice, cb);
            }

            @Override public void setLockMotion(boolean on, @Nullable WallpaperSlots.Callback cb) {
                WallpaperSlots.setLockMotion(activity, on, cb);
            }

            @Override public void setHomeMotion(boolean on, @Nullable WallpaperSlots.Callback cb) {
                WallpaperSlots.setHomeMotion(activity, on, cb);
            }

            @Nullable @Override public LivingStillJob livingJob() {
                return Build.VERSION.SDK_INT >= 34 ? LivingStillJob.get(activity) : null;
            }

            @Nullable @Override public Manifest livingFor(@NonNull File photo) {
                return LivingStills.find(activity, photo);
            }

            @Nullable @Override public Manifest livingById(@NonNull String id) {
                return AnimatedWallpapers.isLivingId(id)
                    ? LivingStills.findByHash(activity, id.substring(LivingStill.ID_PREFIX.length())) : null;
            }

            @NonNull @Override public List<TaiVisionModels.Missing> livingMissing() {
                return TaiVisionModels.missing(activity);
            }

            @Override public void openModelCentre(@NonNull String modelId) {
                TaiModelCentreFragment.openForModel(activity, modelId);
            }

            @Override public void openMoreSettings() {
                activity.startActivity(SettingsActivity.createFragmentIntent(activity,
                    TermuxStylePreferencesFragment.class, R.string.termux_style_preferences_title));
            }

            @NonNull @Override public List<File> recents() {
                return WallpaperSlots.recentPhotos(activity);
            }

            @Override public void discardPhoto(@NonNull File photo) {
                WallpaperSlots.discardPendingPhoto(activity, photo);
            }
        };
    }

    /** Shows the page full screen over {@code activity}. */
    @NonNull
    public static WallpaperPickerPage show(@NonNull AppCompatActivity activity, @NonNull Slots slots,
                                           @NonNull Listener listener) {
        return show(activity, slots, listener, null);
    }

    /** Shows the page full screen over {@code activity}, back at {@code restore} when given. */
    @NonNull
    public static WallpaperPickerPage show(@NonNull AppCompatActivity activity, @NonNull Slots slots,
                                           @NonNull Listener listener, @Nullable ReturnState restore) {
        return show(activity, slots, listener, WallpaperSlots.lockLiveSupported(Build.VERSION.SDK_INT), restore);
    }

    /**
     * Shows the page full screen over {@code activity}, back at {@code restore} when given.
     * {@code animatedOffered} false (below API 34, or Fancier Glass off) leaves the photos only.
     */
    @NonNull
    public static WallpaperPickerPage show(@NonNull AppCompatActivity activity, @NonNull Slots slots,
                                           @NonNull Listener listener, boolean animatedOffered,
                                           @Nullable ReturnState restore) {
        AppCompatDialog dialog = new AppCompatDialog(activity, R.style.ThemeOverlay_Termux_WallpaperPickerPage);
        WallpaperPickerPage page = new WallpaperPickerPage(dialog.getContext(), slots, listener,
            Build.VERSION.SDK_INT, animatedOffered, dialog::dismiss, restore, new WallpaperThumbs());
        dialog.setContentView(page.root());
        dialog.setCancelable(true);
        dialog.setOnDismissListener(d -> page.onDismissed());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowCompat.setDecorFitsSystemWindows(window, false);
            boolean lightSurface = MaterialColors.isColorLight(MaterialColors.getColor(page.root(),
                com.google.android.material.R.attr.colorSurface));
            WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(window, window.getDecorView());
            bars.setAppearanceLightStatusBars(lightSurface);
            bars.setAppearanceLightNavigationBars(lightSurface);
        }
        ViewCompat.setOnApplyWindowInsetsListener(page.root(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        dialog.show();
        listener.onPageShown(true);
        return page;
    }

    private static final String LOG_TAG = "WallpaperPickerPage";
    private static final int THUMB_HEIGHT_DP = 112;
    private static final int CARD_VERTICAL_PAD_DP = 8;
    private static final int CARD_GAP_DP = 12;
    private static final float CARD_MAX_WIDTH_FRACTION = 0.62f;
    private static final int TILE_GAP_DP = 8;
    private static final int TILE_STROKE_DP = 3;

    /** Pager positions: Lock first. */
    static final int POS_LOCK = 0;
    static final int POS_HOME = 1;

    /** The Apply menu's item ids. */
    private static final int MENU_HOME_ONLY = 1;
    private static final int MENU_LOCK_ONLY = 2;

    private final Context mContext;
    private final Slots mSlots;
    private final Listener mListener;
    private final int mSdk;
    private final Runnable mDismiss;
    /** Whether the animated backgrounds are offered: their tiles, Motion and the live preview. */
    private final boolean mAnimatedOffered;
    private final float mDensity;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final WallpaperThumbs mThumbs;

    private final View mRoot;
    private final TextView mTitle;
    private final MaterialButton mApply;
    private final MaterialButton mApplyMore;
    private final View mIconPack;
    private final LinearProgressIndicator mProgress;
    private final RecyclerView mPager;
    private final LinearLayoutManager mPagerLayout;
    private final PagerSnapHelper mSnap = new PagerSnapHelper();
    private final View mMotionRow;
    private final MaterialSwitch mMotion;
    private final MaterialButton mLivingAgain;
    private final MaterialButton mLivingOffer;
    private final View mLivingWorking;
    private final TextView mLivingStage;
    private final LinearProgressIndicator mLivingBar;
    @Nullable private final LivingStillJob mLivingJob;
    private final LinearLayout mStrip;
    private final MaterialButton mPhoto;

    @NonNull private WallpaperSlots.State mStored;
    /** Pending choices by {@link WallpaperSlots.Slot#ordinal()}. */
    private final WallpaperSlots.Choice[] mPending = new WallpaperSlots.Choice[2];
    /** The page opens on Home: what the user does first goes to the home screen (and Apply's both). */
    @NonNull private WallpaperSlots.Slot mCentred = WallpaperSlots.Slot.HOME;
    private boolean mBusy;
    private boolean mDismissed;
    /** Closed for Look, Layout or Photo…: the host brings the page back, pending photos and all. */
    private boolean mHandedOff;
    private boolean mSettingMotion;
    @Nullable private PopupMenu mIconPackMenu;
    @Nullable private androidx.appcompat.app.AlertDialog mMissingDialog;
    /** The photos whose living still was looked up: path to the manifest, or absent when it has none. */
    private final java.util.Map<String, Manifest> mLivingByPhoto = new java.util.HashMap<>();
    private final java.util.Set<String> mLivingAbsent = new java.util.HashSet<>();

    private final WallpaperPreviewView[] mCards = new WallpaperPreviewView[2];
    private int mCardW;
    private int mCardH;
    private final int mThumbW;
    private final int mThumbH;

    /** The strip's tiles, in order: Same as Home first, then the recent photos, then the backgrounds. */
    private final List<Tile> mTiles = new ArrayList<>();
    @Nullable private Tile mSameAsHomeTile;
    private int mRecentTiles;
    private int mAnimatedTiles;

    private final Runnable mClockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            mMain.postDelayed(this, WallpaperPickerLogic.millisToNextMinute(System.currentTimeMillis()));
        }
    };

    private static final class Tile {
        @NonNull final WallpaperSlots.Choice choice;
        @NonNull final View view;
        @NonNull final ShapeableImageView image;

        Tile(@NonNull WallpaperSlots.Choice choice, @NonNull View view, @NonNull ShapeableImageView image) {
            this.choice = choice;
            this.view = view;
            this.image = image;
        }
    }

    @VisibleForTesting
    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               int sdkInt, @NonNull Runnable dismiss) {
        this(context, slots, listener, sdkInt, dismiss, null);
    }

    @VisibleForTesting
    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               int sdkInt, @NonNull Runnable dismiss, @Nullable ReturnState restore) {
        this(context, slots, listener, sdkInt, WallpaperSlots.lockLiveSupported(sdkInt), dismiss, restore,
            new WallpaperThumbs());
    }

    @VisibleForTesting
    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               int sdkInt, boolean animatedOffered, @NonNull Runnable dismiss,
                               @Nullable ReturnState restore, @NonNull WallpaperThumbs thumbs) {
        mContext = context;
        mSlots = slots;
        mListener = listener;
        mSdk = sdkInt;
        mAnimatedOffered = animatedOffered;
        mThumbs = thumbs;
        mDismiss = dismiss;
        mDensity = context.getResources().getDisplayMetrics().density;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mThumbH = dp(THUMB_HEIGHT_DP);
        mThumbW = WallpaperPickerLogic.thumbWidth(mThumbH, dm.widthPixels, dm.heightPixels);

        mStored = readSafe();
        mPending[WallpaperSlots.Slot.HOME.ordinal()] = mStored.home;
        mPending[WallpaperSlots.Slot.LOCK.ordinal()] = mStored.lock;
        if (restore != null) {
            mPending[WallpaperSlots.Slot.HOME.ordinal()] = restore.pendingHome;
            mPending[WallpaperSlots.Slot.LOCK.ordinal()] = restore.pendingLock;
            mCentred = restore.centred;
        }

        mRoot = LayoutInflater.from(context).inflate(R.layout.wallpaper_picker_page, null, false);
        mTitle = mRoot.findViewById(R.id.wallpaper_picker_title);
        mApply = mRoot.findViewById(R.id.wallpaper_picker_apply);
        mApplyMore = mRoot.findViewById(R.id.wallpaper_picker_apply_more);
        mIconPack = mRoot.findViewById(R.id.wallpaper_picker_icon_pack);
        mProgress = mRoot.findViewById(R.id.wallpaper_picker_progress);
        mPager = mRoot.findViewById(R.id.wallpaper_picker_pager);
        mMotionRow = mRoot.findViewById(R.id.wallpaper_picker_motion_row);
        mMotion = mRoot.findViewById(R.id.wallpaper_picker_motion);
        mLivingAgain = mRoot.findViewById(R.id.wallpaper_picker_living_again);
        mLivingOffer = mRoot.findViewById(R.id.wallpaper_picker_living_offer);
        mLivingWorking = mRoot.findViewById(R.id.wallpaper_picker_living_working);
        mLivingStage = mRoot.findViewById(R.id.wallpaper_picker_living_stage);
        mLivingBar = mRoot.findViewById(R.id.wallpaper_picker_living_progress);
        mLivingJob = livingOffered() ? mSlots.livingJob() : null;
        mStrip = mRoot.findViewById(R.id.wallpaper_picker_strip);
        mPhoto = mRoot.findViewById(R.id.wallpaper_picker_photo);

        mRoot.findViewById(R.id.wallpaper_picker_back).setOnClickListener(v -> dismiss());
        mApply.setOnClickListener(v -> applyBoth());
        mApplyMore.setOnClickListener(v -> showApplyMenu());
        mPhoto.setOnClickListener(v -> {
            if (mBusy || mDismissed) return;
            WallpaperSlots.Slot slot = mCentred;
            ReturnState back = returnState();
            mHandedOff = true;
            dismiss();
            mListener.onPickPhoto(slot, back);
        });
        mRoot.findViewById(R.id.wallpaper_picker_look).setOnClickListener(v -> openEditor(false));
        mRoot.findViewById(R.id.wallpaper_picker_more_settings).setOnClickListener(v -> {
            if (!mDismissed) mSlots.openMoreSettings();
        });
        mLivingOffer.setOnClickListener(v -> onBringToLife());
        mLivingAgain.setOnClickListener(v -> onReadAgain());
        mRoot.findViewById(R.id.wallpaper_picker_living_cancel).setOnClickListener(v -> {
            if (mLivingJob != null) mLivingJob.cancel();
        });
        mIconPack.setOnClickListener(v -> showIconPackMenu());
        mRoot.findViewById(R.id.wallpaper_picker_layout).setOnClickListener(v -> openEditor(true));

        mMotion.setChecked(motionOf(mCentred));
        mMotion.setOnCheckedChangeListener((b, on) -> onMotionToggled(on));
        adoptLiving(WallpaperSlots.Slot.HOME);
        adoptLiving(WallpaperSlots.Slot.LOCK);

        mPagerLayout = new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
        mPager.setLayoutManager(mPagerLayout);
        mPager.setAdapter(new CardAdapter());
        mSnap.attachToRecyclerView(mPager);
        mPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                // A layout pass reports a scroll too, before the side padding that centres the
                // cards is set, and Home can be nearest the middle then: follow real scrolls only.
                if (rv.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) return;
                centreFromScroll();
            }

            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) centreFromScroll();
            }
        });
        mPager.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) sizeCards(r - l, b - t);
        });

        buildStrip();
        mRoot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View v) {
                mMain.removeCallbacks(mClockTick);
                mClockTick.run();
            }

            @Override public void onViewDetachedFromWindow(@NonNull View v) {
                mMain.removeCallbacks(mClockTick);
            }
        });
        updateClock();
        applyCentred();
        if (mLivingJob != null) mLivingJob.attach(mLivingListener);
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    @NonNull
    WallpaperSlots.Slot centredSlot() {
        return mCentred;
    }

    /** Where the page is now, for {@link Listener#onOpenLook} and {@link Listener#onOpenLayout}. */
    @NonNull
    ReturnState returnState() {
        return new ReturnState(mCentred, mPending[WallpaperSlots.Slot.HOME.ordinal()],
            mPending[WallpaperSlots.Slot.LOCK.ordinal()]);
    }

    /** The pending choice for {@code slot}. */
    @NonNull
    WallpaperSlots.Choice pending(@NonNull WallpaperSlots.Slot slot) {
        return mPending[slot.ordinal()];
    }

    /** Centres {@code slot}'s card, as a swipe that settled there would. */
    @MainThread
    public void centre(@NonNull WallpaperSlots.Slot slot) {
        int pos = slot == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME;
        if (mCardW > 0) {
            // The neighbour is already in view, so a plain smoothScrollToPosition would not move:
            // scroll by the snap helper's own distance to centre it.
            View target = mPagerLayout.findViewByPosition(pos);
            int[] d = target == null ? null : mSnap.calculateDistanceToFinalSnap(mPagerLayout, target);
            if (d != null) mPager.smoothScrollBy(d[0], d[1]);
            else mPagerLayout.scrollToPositionWithOffset(pos, 0);
        }
        setCentred(slot);
    }

    /** A tap on the strip tile for {@code choice}, as the user would. */
    @MainThread
    void choose(@NonNull WallpaperSlots.Choice choice) {
        if (mBusy || mDismissed) return;
        if (choice.sameAsHome && mCentred != WallpaperSlots.Slot.LOCK) return;
        mPending[mCentred.ordinal()] = choice;
        adoptLiving(mCentred);
        refreshCards();
        refreshSameAsHomeThumb();
        refreshSelection();
        refreshApply();
        refreshMotionRow();
    }

    // --- state ---

    @NonNull
    private WallpaperSlots.State readSafe() {
        try {
            return mSlots.read();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the wallpaper slots failed", e);
            return new WallpaperSlots.State(WallpaperSlots.Choice.photo(),
                WallpaperSlots.Choice.sameAsHome(), true, false);
        }
    }

    @NonNull
    private WallpaperSlots.Choice stored(@NonNull WallpaperSlots.Slot slot) {
        return slot == WallpaperSlots.Slot.HOME ? mStored.home : mStored.lock;
    }

    /** What a slot's card shows: Lock's Same as Home shows Home's pending choice. */
    @NonNull
    private WallpaperSlots.Choice shown(@NonNull WallpaperSlots.Slot slot) {
        WallpaperSlots.Choice c = mPending[slot.ordinal()];
        if (slot == WallpaperSlots.Slot.LOCK && c.sameAsHome) return mPending[WallpaperSlots.Slot.HOME.ordinal()];
        return c;
    }

    private void setCentred(@NonNull WallpaperSlots.Slot slot) {
        if (slot == mCentred) return;
        mCentred = slot;
        applyCentred();
    }

    private void applyCentred() {
        mTitle.setText(mCentred == WallpaperSlots.Slot.LOCK
            ? R.string.wallpaper_picker_slot_lock : R.string.wallpaper_picker_slot_home);
        setMotionChecked(motionOf(mCentred));
        refreshMotionRow();
        if (mSameAsHomeTile != null) {
            mSameAsHomeTile.view.setVisibility(
                WallpaperPickerLogic.showsSameAsHome(mCentred) ? View.VISIBLE : View.GONE);
        }
        refreshLive();
        refreshSelection();
        refreshApply();
    }

    private void refreshApply() {
        mApply.setEnabled(WallpaperPickerLogic.applyBothEnabled(primaryChoice(), mStored.home,
            mStored.lock, mBusy));
        mApplyMore.setEnabled(slotOnlyEnabled(WallpaperSlots.Slot.HOME)
            || slotOnlyEnabled(WallpaperSlots.Slot.LOCK));
    }

    /** The centred card's background: what Apply puts on Home. */
    @NonNull
    private WallpaperSlots.Choice primaryChoice() {
        return WallpaperPickerLogic.primaryChoice(mCentred, mPending[WallpaperSlots.Slot.HOME.ordinal()],
            mPending[WallpaperSlots.Slot.LOCK.ordinal()]);
    }

    /** What "Home screen only" or "Lock screen only" puts on {@code target}. */
    @NonNull
    private WallpaperSlots.Choice slotOnlyChoice(@NonNull WallpaperSlots.Slot target) {
        return WallpaperPickerLogic.slotOnlyChoice(target, mCentred,
            mPending[WallpaperSlots.Slot.HOME.ordinal()], mPending[WallpaperSlots.Slot.LOCK.ordinal()]);
    }

    @VisibleForTesting
    boolean slotOnlyEnabled(@NonNull WallpaperSlots.Slot target) {
        return WallpaperPickerLogic.applyOneEnabled(target, slotOnlyChoice(target), stored(target), mBusy);
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mProgress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        mMotion.setEnabled(!busy);
        mLivingAgain.setEnabled(!busy);
        mStrip.setAlpha(busy ? 0.5f : 1f);
        refreshApply();
    }

    // --- apply, motion, photo, shortcuts ---

    /**
     * Apply: the centred card's background on Home, then Lock pointed at Home (Same as Home).
     * Android's one-time live-wallpaper preview may open for the Lock half when Motion is on.
     */
    @VisibleForTesting
    void applyBoth() {
        if (mBusy || mDismissed) return;
        final WallpaperSlots.Choice choice = primaryChoice();
        if (!WallpaperPickerLogic.applyBothEnabled(choice, mStored.home, mStored.lock, false)) return;
        setBusy(true);
        final Runnable lockFollows = () -> runApply(WallpaperSlots.Slot.LOCK,
            WallpaperSlots.Choice.sameAsHome(), () -> {
                // A photo is stored as the slot's own kept copy, not the pending file.
                mPending[WallpaperSlots.Slot.HOME.ordinal()] = choice.photo ? mStored.home : choice;
                mPending[WallpaperSlots.Slot.LOCK.ordinal()] = WallpaperSlots.Choice.sameAsHome();
                onApplyFinished();
            });
        if (WallpaperPickerLogic.homeChanges(choice, mStored.home)) {
            runApply(WallpaperSlots.Slot.HOME, choice, lockFollows);
        } else {
            lockFollows.run();
        }
    }

    /** A one-slot menu item: {@code target} only. */
    @VisibleForTesting
    void applySlotOnly(@NonNull WallpaperSlots.Slot target) {
        if (mBusy || mDismissed) return;
        final WallpaperSlots.Choice choice = slotOnlyChoice(target);
        if (!WallpaperPickerLogic.applyOneEnabled(target, choice, stored(target), false)) return;
        setBusy(true);
        runApply(target, choice, () -> {
            mPending[target.ordinal()] = choice.photo ? stored(target) : choice;
            onApplyFinished();
        });
    }

    /** Applies one slot; {@code next} runs on success, and a failure ends the run with an error. */
    private void runApply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                          @NonNull Runnable next) {
        WallpaperSlots.Callback done = (ok, error) -> {
            if (!ok) {
                Logger.logError(LOG_TAG, "Applying to " + slot + " failed: " + error);
                if (mDismissed) return;
                setBusy(false);
                mStored = readSafe();
                refreshApply();
                showError(R.string.wallpaper_picker_apply_failed);
                return;
            }
            mListener.onApplied(slot, choice);
            mStored = readSafe();
            next.run();
        };
        try {
            mSlots.apply(slot, choice, done);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Applying to " + slot + " failed", e);
            done.onDone(false, e.getMessage());
        }
    }

    /** Every slot in the run applied: the page shows what is stored now. */
    private void onApplyFinished() {
        if (mDismissed) return;
        setBusy(false);
        refreshCards();
        refreshSameAsHomeThumb();
        refreshSelection();
        refreshApply();
        refreshMotionRow();
    }

    /** The split button's trailing half: Home screen only / Lock screen only. */
    private void showApplyMenu() {
        if (mBusy || mDismissed) {
            mApplyMore.setChecked(false);
            return;
        }
        mApplyMore.setChecked(true);
        PopupMenu menu = new PopupMenu(mContext, mApplyMore);
        Menu items = menu.getMenu();
        items.add(Menu.NONE, MENU_HOME_ONLY, 0, R.string.wallpaper_picker_apply_home_only)
            .setEnabled(slotOnlyEnabled(WallpaperSlots.Slot.HOME));
        items.add(Menu.NONE, MENU_LOCK_ONLY, 1, R.string.wallpaper_picker_apply_lock_only)
            .setEnabled(slotOnlyEnabled(WallpaperSlots.Slot.LOCK));
        menu.setOnMenuItemClickListener(item -> {
            applySlotOnly(item.getItemId() == MENU_HOME_ONLY
                ? WallpaperSlots.Slot.HOME : WallpaperSlots.Slot.LOCK);
            return true;
        });
        menu.setOnDismissListener(m -> mApplyMore.setChecked(false));
        menu.show();
    }

    /** The Icon pack menu: the default row and each installed pack, the one in force checked. */
    private void showIconPackMenu() {
        if (mBusy || mDismissed) return;
        final IconPackChoices.Listing listing;
        try {
            listing = mListener.iconPacks();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Listing icon packs failed", e);
            return;
        }
        PopupMenu menu = new PopupMenu(mContext, mIconPack);
        Menu items = menu.getMenu();
        for (int i = 0; i < listing.entries.size(); i++) {
            MenuItem item = items.add(Menu.NONE, i, i, listing.entries.get(i).label);
            item.setCheckable(true);
            item.setChecked(i == listing.checked);
        }
        items.setGroupCheckable(Menu.NONE, true, true);
        menu.setOnMenuItemClickListener(item -> {
            int at = item.getItemId();
            if (at >= 0 && at < listing.entries.size() && at != listing.checked) {
                mListener.onIconPackChosen(listing.entries.get(at).value);
            }
            return true;
        });
        mIconPackMenu = menu;
        menu.setOnDismissListener(m -> {
            if (mIconPackMenu == m) mIconPackMenu = null;
        });
        menu.show();
    }

    private void onMotionToggled(boolean on) {
        if (mSettingMotion || mDismissed) return;
        if (mBusy) {
            setMotionChecked(!on);
            return;
        }
        setBusy(true);
        final WallpaperSlots.Slot slot = mCentred;
        WallpaperSlots.Callback done = (ok, error) -> {
            if (mDismissed) return;
            setBusy(false);
            if (!ok) {
                Logger.logError(LOG_TAG, "Motion " + on + " failed: " + error);
                setMotionChecked(!on);
                showError(R.string.wallpaper_picker_motion_failed);
                return;
            }
            mStored = readSafe();
            setMotionChecked(motionOf(mCentred));
            refreshCards();
        };
        try {
            // Home's switch is only ever shown for a living still; Lock's is the lock live wallpaper's.
            if (slot == WallpaperSlots.Slot.HOME) mSlots.setHomeMotion(on, done);
            else mSlots.setLockMotion(on, done);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Motion failed", e);
            done.onDone(false, e.getMessage());
        }
    }

    /**
     * A toast, not an AppNotice: the notice pill draws in the activity's own window, which this
     * full-screen page covers.
     */
    private void showError(int messageRes) {
        android.widget.Toast.makeText(mContext, messageRes, android.widget.Toast.LENGTH_LONG).show();
    }

    private void setMotionChecked(boolean on) {
        mSettingMotion = true;
        mMotion.setChecked(on);
        mSettingMotion = false;
    }

    /** Look or Layout: the page closes, and the host brings it back here when the editor ends. */
    private void openEditor(boolean layout) {
        if (mBusy || mDismissed) return;
        ReturnState back = returnState();
        mHandedOff = true;
        dismiss();
        if (layout) mListener.onOpenLayout(back);
        else mListener.onOpenLook(back);
    }

    private void dismiss() {
        if (mDismissed) return;
        mDismiss.run();
        // A host without a dialog (tests) never calls back: settle here too.
        onDismissed();
    }

    /** The dialog went away (back, the arrow, Look, Layout or Photo…). */
    void onDismissed() {
        if (mDismissed) return;
        mDismissed = true;
        mMain.removeCallbacks(mClockTick);
        if (mLivingJob != null) mLivingJob.detach(mLivingListener);
        androidx.appcompat.app.AlertDialog missing = mMissingDialog;
        mMissingDialog = null;
        if (missing != null) missing.dismiss();
        PopupMenu iconPackMenu = mIconPackMenu;
        mIconPackMenu = null;
        if (iconPackMenu != null) iconPackMenu.dismiss();
        for (WallpaperPreviewView card : mCards) {
            if (card == null) continue;
            card.setLive(false);
            card.show(null, null, false);
        }
        for (Tile tile : mTiles) tile.image.setImageDrawable(null);
        // Every picture is recycled here, so no view may still hold one.
        mThumbs.release();
        if (!mHandedOff) discardPendingPhotos();
        mListener.onPageShown(false);
    }

    /** Closed for good (back, or the arrow): a photo never applied loses its pending file. */
    private void discardPendingPhotos() {
        for (WallpaperSlots.Choice c : mPending) {
            if (c == null || !WallpaperPickerLogic.photoWithPicture(c) || c.photoFile == null) continue;
            if (WallpaperPickerLogic.sameChoice(c, mStored.home) || WallpaperPickerLogic.sameChoice(c, mStored.lock)) {
                continue;
            }
            try {
                mSlots.discardPhoto(c.photoFile);
            } catch (RuntimeException e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Discarding a pending photo failed", e);
            }
        }
    }

    // --- living stills ---

    /** Living stills can be built here: API 34+ with the animated backgrounds offered. */
    private boolean livingOffered() {
        return mAnimatedOffered && mSdk >= 34;
    }

    /** The Motion toggle's stored value for {@code slot}. */
    private boolean motionOf(@NonNull WallpaperSlots.Slot slot) {
        return slot == WallpaperSlots.Slot.HOME ? mStored.homeMotion : mStored.lockMotion;
    }

    /** The living still of this photo, cached; null when it has none. */
    @Nullable
    private Manifest manifestFor(@NonNull File photo) {
        String key = photo.getAbsolutePath();
        Manifest known = mLivingByPhoto.get(key);
        if (known != null) return known;
        if (mLivingAbsent.contains(key)) return null;
        Manifest found = null;
        try {
            found = mSlots.livingFor(photo);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Looking for a living still failed", e);
        }
        if (found == null) mLivingAbsent.add(key);
        else mLivingByPhoto.put(key, found);
        return found;
    }

    /** The manifest behind a living choice, or null when its files are gone. */
    @Nullable
    private Manifest livingManifest(@NonNull WallpaperSlots.Choice living) {
        String id = living.animatedId;
        if (id == null) return null;
        try {
            return mSlots.livingById(id);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading a living still failed", e);
            return null;
        }
    }

    /**
     * A pending photo whose living still already exists becomes that living still: the photo with
     * its motion, which Apply puts on the slot and the Motion switch then governs.
     */
    private void adoptLiving(@NonNull WallpaperSlots.Slot slot) {
        if (!livingOffered()) return;
        WallpaperSlots.Choice c = mPending[slot.ordinal()];
        if (c == null || !WallpaperPickerLogic.photoWithPicture(c) || c.photoFile == null) return;
        Manifest manifest = manifestFor(c.photoFile);
        if (manifest != null) mPending[slot.ordinal()] = WallpaperSlots.Choice.animated(manifest.wallpaperId());
    }

    /** The photo a living-still control acts on for {@code c}: the photo itself, or a living still's own copy. */
    @Nullable
    private File livingPhotoFor(@NonNull WallpaperSlots.Choice c) {
        if (WallpaperPickerLogic.photoWithPicture(c)) return c.photoFile;
        if (WallpaperPickerLogic.isLiving(c)) {
            Manifest manifest = livingManifest(c);
            return manifest == null ? null : manifest.image();
        }
        return null;
    }

    private static boolean sameFile(@Nullable File a, @Nullable File b) {
        return a != null && b != null && a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    /** Shows what the Motion row holds for the centred slot: the switch, Bring to life, or the working bar. */
    private void refreshMotionRow() {
        if (!WallpaperPickerLogic.motionRowExists(mSdk, mAnimatedOffered)) {
            mMotionRow.setVisibility(View.GONE);
            return;
        }
        mMotionRow.setVisibility(View.VISIBLE);
        WallpaperSlots.Choice shown = shown(mCentred);
        File photo = WallpaperPickerLogic.photoWithPicture(shown) ? shown.photoFile : null;
        LivingStillJob job = mLivingJob;
        boolean running = job != null && job.isRunning();
        boolean working = photo != null && running && sameFile(job.runningPhoto(), photo);
        boolean hasLiving = photo != null && manifestFor(photo) != null;
        WallpaperPickerLogic.MotionRow row = WallpaperPickerLogic.motionRow(mCentred, mSdk, job != null,
            shown, hasLiving, working);
        View motionGroup = mRoot.findViewById(R.id.wallpaper_picker_motion_group);
        motionGroup.setVisibility(row == WallpaperPickerLogic.MotionRow.SWITCH
            || row == WallpaperPickerLogic.MotionRow.SWITCH_HIDDEN ? View.VISIBLE : View.GONE);
        mMotion.setVisibility(row == WallpaperPickerLogic.MotionRow.SWITCH ? View.VISIBLE : View.INVISIBLE);
        mLivingAgain.setVisibility(row == WallpaperPickerLogic.MotionRow.SWITCH
            && WallpaperPickerLogic.isLiving(shown) ? View.VISIBLE : View.GONE);
        mLivingAgain.setEnabled(!mBusy && !running);
        mLivingOffer.setVisibility(row == WallpaperPickerLogic.MotionRow.OFFER ? View.VISIBLE : View.GONE);
        // One job at a time: another photo's run leaves this button waiting.
        mLivingOffer.setEnabled(!running);
        mLivingOffer.setTooltipText(running ? mContext.getString(R.string.living_busy) : null);
        mLivingWorking.setVisibility(row == WallpaperPickerLogic.MotionRow.WORKING ? View.VISIBLE : View.GONE);
        if (row == WallpaperPickerLogic.MotionRow.WORKING) showLivingProgress(job.lastProgress());
    }

    private void showLivingProgress(@Nullable LivingStillJob.Progress p) {
        int percent = p == null ? 0 : p.overallPercent;
        String stage = mContext.getString(stageLabel(p));
        mLivingBar.setProgressCompat(percent, true);
        mLivingStage.setText(stage);
        mLivingBar.setContentDescription(mContext.getString(R.string.living_progress_description, stage, percent));
    }

    private static int stageLabel(@Nullable LivingStillJob.Progress p) {
        if (p == null) return R.string.living_stage_depth;
        switch (WallpaperPickerLogic.stageKey(p.stage, p.asksGemma())) {
            case "scene": return R.string.living_stage_scene;
            case "subject": return R.string.living_stage_subject;
            case "gemma": return R.string.living_stage_gemma;
            case "recipe": return R.string.living_stage_recipe;
            default: return R.string.living_stage_depth;
        }
    }

    /** Bring to life: ask for missing models first, else start the job. */
    private void onBringToLife() {
        if (mDismissed || mLivingJob == null) return;
        File photo = livingPhotoFor(shown(mCentred));
        if (photo != null) startLiving(photo);
    }

    /** Read again: the same analysis over the living still's own copy of the photo. */
    private void onReadAgain() {
        if (mDismissed || mLivingJob == null || mBusy) return;
        File photo = livingPhotoFor(shown(mCentred));
        if (photo != null) startLiving(photo);
    }

    private void startLiving(@NonNull File photo) {
        LivingStillJob job = mLivingJob;
        if (job == null) return;
        List<TaiVisionModels.Missing> missing;
        try {
            missing = mSlots.livingMissing();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Checking the vision models failed", e);
            missing = Collections.emptyList();
        }
        if (!missing.isEmpty()) {
            showMissingModels(missing);
            return;
        }
        if (!job.start(photo)) showError(R.string.living_busy);
        refreshMotionRow();
    }

    /** The models the analysis lacks, with their sizes, and a way to the model centre on the first. */
    private void showMissingModels(@NonNull List<TaiVisionModels.Missing> missing) {
        StringBuilder lines = new StringBuilder();
        for (TaiVisionModels.Missing m : missing) {
            if (lines.length() > 0) lines.append('\n');
            lines.append(mContext.getString(R.string.living_missing_item, m.displayName,
                android.text.format.Formatter.formatShortFileSize(mContext, m.sizeBytes)));
        }
        final String first = missing.get(0).id;
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(mContext)
            .setTitle(R.string.living_missing_title)
            .setMessage(mContext.getString(R.string.living_missing_message, lines.toString()))
            .setPositiveButton(R.string.living_missing_open, (d, which) -> {
                if (!mDismissed) mSlots.openModelCentre(first);
            })
            .setNegativeButton(R.string.living_missing_not_now, null)
            .create();
        androidx.appcompat.app.AlertDialog previous = mMissingDialog;
        mMissingDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (mMissingDialog == dialog) mMissingDialog = null;
        });
        if (previous != null) previous.dismiss();
        dialog.show();
    }

    private final LivingStillJob.Listener mLivingListener = new LivingStillJob.Listener() {
        @Override public void onProgress(@NonNull LivingStillJob.Progress progress) {
            if (mDismissed) return;
            refreshMotionRow();
        }

        @Override public void onFinished(@NonNull LivingStillJob.Result result) {
            if (mDismissed) return;
            onLivingFinished(result);
        }
    };

    /**
     * The job ended. A finished still replaces the pending choice of every slot that held that
     * photo, and its card starts playing; a failure says so; a cancel only brings the button back.
     */
    private void onLivingFinished(@NonNull LivingStillJob.Result result) {
        Manifest manifest = result.manifest;
        if (manifest != null) {
            String key = result.photo.getAbsolutePath();
            mLivingAbsent.remove(key);
            mLivingByPhoto.put(key, manifest);
            mLivingByPhoto.put(manifest.image().getAbsolutePath(), manifest);
            mLivingAbsent.remove(manifest.image().getAbsolutePath());
            for (WallpaperSlots.Slot slot : WallpaperSlots.Slot.values()) {
                WallpaperSlots.Choice c = mPending[slot.ordinal()];
                if (c != null && WallpaperPickerLogic.photoWithPicture(c) && sameFile(c.photoFile, result.photo)) {
                    mPending[slot.ordinal()] = WallpaperSlots.Choice.animated(manifest.wallpaperId());
                }
            }
        } else if (!result.cancelled) {
            Logger.logError(LOG_TAG, "Bring to life failed: " + result.error + " " + result.message);
            showError(R.string.living_failed);
        }
        refreshCards();
        refreshSameAsHomeThumb();
        refreshSelection();
        refreshApply();
        refreshMotionRow();
    }

    // --- pager ---

    private void sizeCards(int pagerW, int pagerH) {
        if (pagerW <= 0 || pagerH <= 0) return;
        int cardH = Math.max(1, pagerH - 2 * dp(CARD_VERTICAL_PAD_DP));
        int cardW = Math.round(cardH * WallpaperPreviewView.OVERLAY_W / WallpaperPreviewView.OVERLAY_H);
        int maxW = Math.round(pagerW * CARD_MAX_WIDTH_FRACTION);
        if (cardW > maxW) {
            cardW = maxW;
            cardH = Math.round(cardW * WallpaperPreviewView.OVERLAY_H / WallpaperPreviewView.OVERLAY_W);
        }
        mCardW = cardW;
        mCardH = cardH;
        int itemW = cardW + 2 * dp(CARD_GAP_DP / 2);
        int side = Math.max(0, (pagerW - itemW) / 2);
        mPager.post(() -> {
            if (mDismissed) return;
            mPager.setPaddingRelative(side, 0, side, 0);
            RecyclerView.Adapter<?> adapter = mPager.getAdapter();
            if (adapter != null) adapter.notifyDataSetChanged();
            mPagerLayout.scrollToPositionWithOffset(mCentred == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME, 0);
        });
    }

    private void centreFromScroll() {
        View snap = mSnap.findSnapView(mPagerLayout);
        if (snap == null) return;
        int pos = mPagerLayout.getPosition(snap);
        if (pos == RecyclerView.NO_POSITION) return;
        setCentred(pos == POS_LOCK ? WallpaperSlots.Slot.LOCK : WallpaperSlots.Slot.HOME);
    }

    private final class CardAdapter extends RecyclerView.Adapter<CardHolder> {
        @NonNull @Override
        public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout cell = new FrameLayout(parent.getContext());
            WallpaperPreviewView card = new WallpaperPreviewView(parent.getContext());
            cell.addView(card, new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
            return new CardHolder(cell, card);
        }

        @Override
        public void onBindViewHolder(@NonNull CardHolder holder, int position) {
            int itemW = Math.max(1, mCardW + 2 * dp(CARD_GAP_DP / 2));
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(itemW,
                ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) holder.card.getLayoutParams();
            lp.width = Math.max(1, mCardW);
            lp.height = Math.max(1, mCardH);
            holder.card.setLayoutParams(lp);
            final WallpaperSlots.Slot slot = position == POS_LOCK ? WallpaperSlots.Slot.LOCK : WallpaperSlots.Slot.HOME;
            for (int i = 0; i < mCards.length; i++) if (mCards[i] == holder.card) mCards[i] = null;
            mCards[position] = holder.card;
            holder.card.setLock(slot == WallpaperSlots.Slot.LOCK);
            holder.card.setContentDescription(mContext.getString(slot == WallpaperSlots.Slot.LOCK
                ? R.string.wallpaper_picker_slot_lock : R.string.wallpaper_picker_slot_home));
            holder.card.setOnClickListener(v -> {
                if (mCentred != slot) centre(slot);
            });
            bindCard(position);
            holder.card.setClock(mClock);
            holder.card.setLive(liveFor(position));
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    private static final class CardHolder extends RecyclerView.ViewHolder {
        @NonNull final WallpaperPreviewView card;

        CardHolder(@NonNull View itemView, @NonNull WallpaperPreviewView card) {
            super(itemView);
            this.card = card;
        }
    }

    private boolean liveFor(int position) {
        if (mDismissed || !mAnimatedOffered) return false;
        int centred = mCentred == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME;
        if (position != centred) return false;
        // A Lock with Motion off shows its still, as the lock screen will.
        if (position == POS_LOCK) return mStored.lockMotion || !WallpaperSlots.lockLiveSupported(mSdk);
        // A living still on Home with Motion off is its photo.
        return !WallpaperPickerLogic.isLiving(shown(WallpaperSlots.Slot.HOME)) || mStored.homeMotion;
    }

    private void refreshLive() {
        for (int i = 0; i < mCards.length; i++) if (mCards[i] != null) mCards[i].setLive(liveFor(i));
    }

    private void refreshCards() {
        for (int i = 0; i < mCards.length; i++) if (mCards[i] != null) bindCard(i);
        refreshLive();
    }

    private void bindCard(int position) {
        final WallpaperPreviewView card = mCards[position];
        if (card == null) return;
        WallpaperSlots.Slot slot = position == POS_LOCK ? WallpaperSlots.Slot.LOCK : WallpaperSlots.Slot.HOME;
        WallpaperSlots.Choice c = shown(slot);
        if (c.photo) {
            bindPhotoCard(position, card, slot, c);
            return;
        }
        if (WallpaperPickerLogic.isLiving(c)) {
            bindLivingCard(position, card, slot, c);
            return;
        }
        AnimatedWallpaper w = AnimatedWallpapers.byId(c.animatedId);
        Bitmap still = w == null ? null : mThumbs.cached(w, mCardW, mCardH);
        card.show(w, still, c.photo);
        if (w != null && still == null && mCardW > 0 && mCardH > 0) {
            final String id = w.id();
            mThumbs.request(w, mCardW, mCardH, bmp -> {
                AnimatedWallpaper now = AnimatedWallpapers.byId(shown(slot).animatedId);
                if (now != null && now.id().equals(id) && mCards[position] == card) card.setStill(bmp);
            });
        }
    }

    /**
     * A living still's card: its photo as the still and the playing still over it while the card
     * is live. A still whose files are gone shows as an empty photo card.
     */
    private void bindLivingCard(int position, @NonNull WallpaperPreviewView card,
                                @NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice c) {
        final Manifest manifest = livingManifest(c);
        if (manifest == null) {
            card.show(null, null, true);
            return;
        }
        AnimatedWallpaper w = AnimatedWallpapers.byId(mContext, c.animatedId);
        final File file = manifest.image();
        Bitmap still = mCardW <= 0 ? null : mThumbs.cachedPhoto(file, mCardW, mCardH);
        card.show(w, still, false);
        if (still != null || mCardW <= 0 || mCardH <= 0) return;
        mThumbs.requestPhoto(file, mCardW, mCardH, bmp -> {
            if (mDismissed || mCards[position] != card) return;
            WallpaperSlots.Choice now = shown(slot);
            if (WallpaperPickerLogic.isLiving(now) && now.animatedId.equals(c.animatedId)) card.setStill(bmp);
        });
    }

    /** A photo card: its picture centre-cropped, as the system shows it; the photo glyph while it loads. */
    private void bindPhotoCard(int position, @NonNull WallpaperPreviewView card,
                               @NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice c) {
        final File file = c.photoFile;
        Bitmap still = file == null || mCardW <= 0 ? null : mThumbs.cachedPhoto(file, mCardW, mCardH);
        card.show(null, still, true);
        if (file == null || still != null || mCardW <= 0 || mCardH <= 0) return;
        mThumbs.requestPhoto(file, mCardW, mCardH, bmp -> {
            if (mDismissed || mCards[position] != card) return;
            WallpaperSlots.Choice now = shown(slot);
            if (now.photo && now.photoFile != null && now.photoFile.equals(file)) card.setStill(bmp);
        });
    }

    // --- clock ---

    @NonNull private String mClock = "";

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        mClock = WallpaperPickerLogic.composedTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
            DateFormat.is24HourFormat(mContext));
        WallpaperPreviewView lock = mCards[POS_LOCK];
        if (lock != null) lock.setClock(mClock);
    }

    // --- strip ---

    private void buildStrip() {
        mStrip.removeAllViews();
        mTiles.clear();
        mSameAsHomeTile = addTile(WallpaperSlots.Choice.sameAsHome(),
            mContext.getString(R.string.wallpaper_picker_same_as_home), true);
        refreshSameAsHomeThumb();
        List<File> recents;
        try {
            recents = mSlots.recents();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the recent photos failed", e);
            recents = Collections.emptyList();
        }
        mRecentTiles = 0;
        for (File photo : recents) {
            if (mRecentTiles >= RecentWallpapers.MAX) break;
            mRecentTiles++;
            Tile tile = addTile(WallpaperSlots.Choice.photo(photo),
                mContext.getString(R.string.wallpaper_picker_recent_photo, mRecentTiles), false);
            if (mRecentTiles == 1) addRecentBadge(tile);
            final ShapeableImageView image = tile.image;
            final String key = photo.getAbsolutePath();
            image.setTag(key);
            mThumbs.requestPhoto(photo, mThumbW, mThumbH, bmp -> {
                if (key.equals(image.getTag())) image.setImageBitmap(bmp);
            });
        }
        mAnimatedTiles = 0;
        if (!mAnimatedOffered) return;
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
            mAnimatedTiles++;
            Tile tile = addTile(WallpaperSlots.Choice.animated(w.id()), w.label(), false);
            final ShapeableImageView image = tile.image;
            final String id = w.id();
            image.setTag(id);
            mThumbs.request(w, mThumbW, mThumbH, bmp -> {
                if (id.equals(image.getTag())) image.setImageBitmap(bmp);
            });
        }
    }

    @NonNull
    private Tile addTile(@NonNull WallpaperSlots.Choice choice, @NonNull String label, boolean link) {
        FrameLayout cell = new FrameLayout(mContext);
        ShapeableImageView image = new ShapeableImageView(mContext);
        image.setShapeAppearanceModel(EditorM3.shape(mContext,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(MaterialColors.getColor(mContext,
            com.google.android.material.R.attr.colorSurfaceContainerHighest, 0));
        int inset = dp(TILE_STROKE_DP) / 2 + 1;
        image.setPadding(inset, inset, inset, inset);
        image.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(mContext,
            androidx.appcompat.R.attr.colorPrimary, 0)));
        image.setStrokeWidth(0f);
        cell.addView(image, new FrameLayout.LayoutParams(mThumbW, mThumbH));
        if (link) {
            ImageView badge = new ImageView(mContext);
            badge.setImageResource(R.drawable.ic_symbol_link);
            badge.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(mContext,
                com.google.android.material.R.attr.colorOnSecondaryContainer, 0)));
            badge.setScaleType(ImageView.ScaleType.CENTER);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(32), dp(32),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            blp.bottomMargin = dp(6);
            cell.addView(badge, blp);
            badge.setBackground(EditorM3.surface(badge,
                com.google.android.material.R.attr.shapeAppearanceCornerExtraLarge,
                com.google.android.material.R.attr.colorSecondaryContainer));
            badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        cell.setContentDescription(label);
        cell.setTooltipText(label);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setOnClickListener(v -> choose(choice));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (mStrip.getChildCount() > 0) lp.setMarginStart(dp(TILE_GAP_DP));
        mStrip.addView(cell, lp);
        Tile tile = new Tile(choice, cell, image);
        mTiles.add(tile);
        return tile;
    }

    /**
     * The Recent group's mark: a "Recent" label on its first tile, in the same tonal pill as
     * Same as Home's link badge. The tiles say "Recent photo N" to TalkBack.
     */
    private void addRecentBadge(@NonNull Tile tile) {
        if (!(tile.view instanceof FrameLayout)) return;
        TextView label = new TextView(mContext);
        label.setText(R.string.wallpaper_picker_recent);
        label.setTextAppearance(resolveStyle(com.google.android.material.R.attr.textAppearanceLabelSmall));
        label.setTextColor(MaterialColors.getColor(mContext,
            com.google.android.material.R.attr.colorOnSecondaryContainer, 0));
        label.setMaxLines(1);
        label.setGravity(Gravity.CENTER);
        label.setPadding(dp(8), dp(2), dp(8), dp(2));
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        label.setBackground(EditorM3.surface(label,
            com.google.android.material.R.attr.shapeAppearanceCornerExtraLarge,
            com.google.android.material.R.attr.colorSecondaryContainer));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.setMargins(dp(6), dp(6), dp(6), 0);
        ((FrameLayout) tile.view).addView(label, lp);
        mRecentBadge = label;
    }

    @Nullable private TextView mRecentBadge;

    /** A theme attribute's style resource, or 0. */
    private int resolveStyle(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        return mContext.getTheme().resolveAttribute(attr, tv, true) ? tv.resourceId : 0;
    }

    /** Same as Home is drawn as the Home slot's thumbnail: its background's still or its photo. */
    private void refreshSameAsHomeThumb() {
        if (mSameAsHomeTile == null) return;
        final ShapeableImageView image = mSameAsHomeTile.image;
        WallpaperSlots.Choice home = mPending[WallpaperSlots.Slot.HOME.ordinal()];
        if (home.photo && home.photoFile != null) {
            final File file = home.photoFile;
            final String key = file.getAbsolutePath();
            image.setTag(key);
            Bitmap cached = mThumbs.cachedPhoto(file, mThumbW, mThumbH);
            image.setImageBitmap(cached);
            if (cached == null) {
                mThumbs.requestPhoto(file, mThumbW, mThumbH, bmp -> {
                    if (key.equals(image.getTag())) image.setImageBitmap(bmp);
                });
            }
            return;
        }
        if (WallpaperPickerLogic.isLiving(home)) {
            Manifest manifest = livingManifest(home);
            if (manifest == null) {
                image.setTag(null);
                image.setImageDrawable(null);
                return;
            }
            final File file = manifest.image();
            final String key = file.getAbsolutePath();
            image.setTag(key);
            Bitmap cached = mThumbs.cachedPhoto(file, mThumbW, mThumbH);
            image.setImageBitmap(cached);
            if (cached == null) {
                mThumbs.requestPhoto(file, mThumbW, mThumbH, bmp -> {
                    if (key.equals(image.getTag())) image.setImageBitmap(bmp);
                });
            }
            return;
        }
        AnimatedWallpaper w = AnimatedWallpapers.byId(home.animatedId);
        if (w == null) {
            image.setTag(null);
            image.setImageDrawable(null);
            return;
        }
        final String id = w.id();
        image.setTag(id);
        Bitmap cached = mThumbs.cached(w, mThumbW, mThumbH);
        image.setImageBitmap(cached);
        if (cached == null) {
            mThumbs.request(w, mThumbW, mThumbH, bmp -> {
                if (id.equals(image.getTag())) image.setImageBitmap(bmp);
            });
        }
    }

    private void refreshSelection() {
        WallpaperSlots.Choice pending = mPending[mCentred.ordinal()];
        for (Tile tile : mTiles) {
            boolean selected = WallpaperPickerLogic.sameChoice(tile.choice, pending);
            tile.image.setStrokeWidth(selected ? dp(TILE_STROKE_DP) : 0f);
            tile.view.setSelected(selected);
        }
    }

    // --- test seams ---

    @VisibleForTesting
    @NonNull
    View sameAsHomeTile() {
        if (mSameAsHomeTile == null) throw new IllegalStateException("no strip");
        return mSameAsHomeTile.view;
    }

    @VisibleForTesting
    int tileCount() {
        return mTiles.size();
    }

    @VisibleForTesting
    int recentTileCount() {
        return mRecentTiles;
    }

    @VisibleForTesting
    int animatedTileCount() {
        return mAnimatedTiles;
    }

    /** The Recent group's label, or null with no recent photos. */
    @VisibleForTesting
    @Nullable
    TextView recentBadge() {
        return mRecentBadge;
    }

    /** {@code slot}'s preview card while it is bound, or null. */
    @VisibleForTesting
    @Nullable
    WallpaperPreviewView card(@NonNull WallpaperSlots.Slot slot) {
        return mCards[slot == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME];
    }

    /** The missing-models dialog while it is open. */
    @VisibleForTesting
    @Nullable
    androidx.appcompat.app.AlertDialog missingDialog() {
        return mMissingDialog;
    }

    /** Back, as the arrow or the system back would close the page. */
    @VisibleForTesting
    void close() {
        dismiss();
    }

    /** The Icon pack menu while it is open. */
    @VisibleForTesting
    @Nullable
    PopupMenu iconPackMenu() {
        return mIconPackMenu;
    }

    private int dp(int v) {
        return Math.round(v * mDensity);
    }
}
