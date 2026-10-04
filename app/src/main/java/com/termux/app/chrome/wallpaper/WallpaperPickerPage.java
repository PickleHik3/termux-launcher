package com.termux.app.chrome.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.PopupMenu;
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
import com.termux.app.layouteditor.EditorM3;
import com.termux.app.surfaces.AppearanceSurfaceController;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Appearance surface's Overview (appearance-round-2026-10-04.md; it began as the wallpaper
 * picker page of lock-live-wallpaper.md, "The picker page"): the fixed heading Appearance, two slot
 * previews (Lock, then Home, each under a small label) in a snap pager, the Lock slot's Motion
 * toggle, the Look / Icon pack / Layout shortcuts, the thumbnail strip and the Apply split button.
 *
 * <p>The page reads and writes the slots only through {@link Slots}, which in the app wraps
 * {@link WallpaperSlots}; it never names WallpaperManager or a slot preference. Tapping a
 * thumbnail sets the centred slot's pending choice and previews it there, live. Apply puts the
 * centred card's background on Home and points Lock at it (Same as Home); its menu applies to one
 * slot only. Swiping keeps each slot's pending choice. Back closes without applying.</p>
 *
 * <p>It is a page of the surface ({@link AppearanceSurfaceController.OverviewPage}), not a
 * window: built once per session, hidden (not destroyed) while Look, Layout or Icons shows, and
 * released when the surface closes. What it reads from disk (the slots, the recent photos, the
 * manifest of a photo's living still, which hashes the photo) is read off the main thread
 * ({@link #load}, then {@link Io}); a lookup that has not come back yet leaves its card or its
 * Motion row empty for a moment rather than stalling the page. Look, Layout and Icon pack go to
 * the surface through {@link Listener}; Photo… closes the surface and hands the host a
 * {@link ReturnState}, with which the host opens it again when the photo pick and the crop end. A
 * cropped photo comes back as the centred slot's pending choice ({@link ReturnState#withPhoto}),
 * previewed in its card and set only by Apply. Back without Apply discards a pending photo's
 * file.</p>
 *
 * <p>Photos are first-class on every API level: a slot holding a photo shows it in its card, and
 * the strip holds Same as Home (Lock only) then the photos last applied, evenly spaced across the
 * row. There are no pre-made backgrounds. When animated wallpapers are not offered (below API 34,
 * or Fancier Glass off) the page has no Motion toggle and no live preview.</p>
 *
 * <p>Living stills (living-stills.md, Part D; API 34+, animated backgrounds offered): a slot whose
 * choice is a photo shows Bring to life in the Motion row. It asks for any missing vision model
 * (a dialog that opens the model centre), else starts the process-owned {@link LivingStillJob},
 * whose determinate bar and stage replace the button; the page only attaches a listener, so the run
 * outlives it. When it ends the pending choice becomes the living still, the card plays it with its
 * own effects map (water, mist), Apply puts it on the slot, and the row holds that slot's Motion
 * switch (on) and an AI-star button, Read again. More settings opens the old Look page.</p>
 *
 * <p>Tests build the page on its own with
 * {@link #WallpaperPickerPage(Context, Slots, Listener, int, Runnable, ReturnState)}, which loads
 * synchronously.</p>
 */
public final class WallpaperPickerPage implements AppearanceSurfaceController.OverviewPage {

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

        /**
         * The playable living still {@code id} names (its player, which the card draws), or null
         * when its files are gone. Reads the manifest from disk, so never on the main thread.
         */
        @Nullable
        default AnimatedWallpaper livingPlayer(@NonNull String id) {
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
        /**
         * Photo… was tapped for {@code slot}; the page has asked to close. Run the photo picker and
         * the crop, then open the surface again with {@link ReturnState#withPhoto}{@code (back, …)},
         * or with {@code back} itself when the pick or the crop was cancelled.
         */
        void onPickPhoto(@NonNull WallpaperSlots.Slot slot, @NonNull ReturnState back);

        /** Apply succeeded for {@code slot}; the page stays open. Home refreshes the glass. */
        void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice);

        /** The Look shortcut: the surface shows the editor in its Look mode. */
        void onOpenLook();

        /** The Layout shortcut: the surface shows the editor in its Layout mode. */
        void onOpenLayout();

        /** The Icon pack shortcut: the surface shows the Icons page. */
        void onOpenIcons();
    }

    /** What the page comes back to after Photo…: the centred slot and both pending choices. */
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

            @Nullable @Override public AnimatedWallpaper livingPlayer(@NonNull String id) {
                return AnimatedWallpapers.byId(activity, id);
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

    /**
     * Runs slow reads off the main thread and hands the result back on it. The surface's is
     * {@link #background}; tests run both halves inline.
     */
    public interface Io {
        <T> void run(@NonNull Supplier<T> work, @NonNull Consumer<T> onMain);

        default void shutdown() {}

        /** Both halves inline, on the calling thread. */
        Io INLINE = new Io() {
            @Override public <T> void run(@NonNull Supplier<T> work, @NonNull Consumer<T> onMain) {
                onMain.accept(work.get());
            }
        };

        /** One daemon thread, results posted to the main looper. */
        @NonNull
        static Io background() {
            final Handler main = new Handler(Looper.getMainLooper());
            final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "wallpaper-picker-io");
                t.setDaemon(true);
                return t;
            });
            return new Io() {
                @Override public <T> void run(@NonNull Supplier<T> work, @NonNull Consumer<T> onMain) {
                    try {
                        executor.execute(() -> {
                            T result = null;
                            try {
                                result = work.get();
                            } catch (RuntimeException e) {
                                Logger.logStackTraceWithMessage(LOG_TAG, "A background read failed", e);
                            }
                            final T value = result;
                            main.post(() -> onMain.accept(value));
                        });
                    } catch (RejectedExecutionException ignored) {
                        // Released while this was being asked: nothing wants the answer.
                    }
                }

                @Override public void shutdown() {
                    executor.shutdown();
                }
            };
        }
    }

    /**
     * What the page needs from disk before it can draw: the slots, the recent photos, and the
     * living stills (manifest and player) of every photo and living choice it will show first.
     * Read by {@link #load} off the main thread.
     */
    public static final class Loaded {
        @NonNull final WallpaperSlots.State state;
        @NonNull final List<File> recents;
        /** Photo path to the manifest of its living still. */
        @NonNull final Map<String, Manifest> manifestByPhoto = new HashMap<>();
        /** Photo paths looked up and found to have none. */
        @NonNull final Set<String> noLiving = new HashSet<>();
        /** Living id to its manifest and its player. */
        @NonNull final Map<String, Manifest> manifestById = new HashMap<>();
        @NonNull final Map<String, AnimatedWallpaper> players = new HashMap<>();

        Loaded(@NonNull WallpaperSlots.State state, @NonNull List<File> recents) {
            this.state = state;
            this.recents = recents;
        }
    }

    /**
     * Reads everything the page opens with. Blocking: the surface calls it on its worker, tests
     * call it inline. {@code livingOffered} false skips the living stills (below API 34, or
     * animated backgrounds off).
     */
    @WorkerThread
    @NonNull
    public static Loaded load(@NonNull Slots slots, boolean livingOffered, @Nullable ReturnState restore) {
        WallpaperSlots.State state;
        try {
            state = slots.read();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the wallpaper slots failed", e);
            state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(),
                WallpaperSlots.Choice.sameAsHome(), true, false);
        }
        List<File> recents;
        try {
            recents = slots.recents();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the recent photos failed", e);
            recents = Collections.emptyList();
        }
        Loaded loaded = new Loaded(state, new ArrayList<>(recents));
        if (!livingOffered)
            return loaded;
        List<WallpaperSlots.Choice> choices = new ArrayList<>();
        choices.add(state.home);
        choices.add(state.lock);
        if (restore != null) {
            choices.add(restore.pendingHome);
            choices.add(restore.pendingLock);
        }
        for (WallpaperSlots.Choice c : choices) {
            if (c == null)
                continue;
            if (WallpaperPickerLogic.photoWithPicture(c) && c.photoFile != null)
                lookUpPhoto(slots, loaded, c.photoFile);
            else if (WallpaperPickerLogic.isLiving(c) && c.animatedId != null)
                lookUpLiving(slots, loaded, c.animatedId);
        }
        // A tap on a recent photo asks whether it has a living still: answered before the tap.
        for (File photo : loaded.recents)
            lookUpPhoto(slots, loaded, photo);
        return loaded;
    }

    private static void lookUpPhoto(@NonNull Slots slots, @NonNull Loaded loaded, @NonNull File photo) {
        String key = photo.getAbsolutePath();
        if (loaded.manifestByPhoto.containsKey(key) || loaded.noLiving.contains(key))
            return;
        Manifest found = null;
        try {
            found = slots.livingFor(photo);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Looking for a living still failed", e);
        }
        if (found == null) {
            loaded.noLiving.add(key);
            return;
        }
        loaded.manifestByPhoto.put(key, found);
        lookUpLiving(slots, loaded, found.wallpaperId());
    }

    private static void lookUpLiving(@NonNull Slots slots, @NonNull Loaded loaded, @NonNull String id) {
        if (loaded.manifestById.containsKey(id))
            return;
        try {
            Manifest manifest = slots.livingById(id);
            if (manifest == null)
                return;
            loaded.manifestById.put(id, manifest);
            AnimatedWallpaper player = slots.livingPlayer(id);
            if (player != null)
                loaded.players.put(id, player);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading a living still failed", e);
        }
    }

    private static final String LOG_TAG = "WallpaperPickerPage";
    private static final int THUMB_HEIGHT_DP = 112;
    private static final int CARD_VERTICAL_PAD_DP = 8;
    private static final int CARD_GAP_DP = 12;
    /** The small label over each preview card, and the gap under it. */
    private static final int CARD_LABEL_DP = 24;
    private static final int CARD_LABEL_GAP_DP = 2;
    private static final float CARD_MAX_WIDTH_FRACTION = 0.62f;
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
    /** The back arrow: the surface closes. */
    private final Runnable mClose;
    private final Io mIo;
    /** Whether the animated backgrounds are offered: Motion, living stills and the live preview. */
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
    private final View mTopBar;
    private final View mStripCard;
    private final View mShortcuts;
    @Nullable private Drawable mBackground;

    @NonNull private WallpaperSlots.State mStored;
    /** Pending choices by {@link WallpaperSlots.Slot#ordinal()}. */
    private final WallpaperSlots.Choice[] mPending = new WallpaperSlots.Choice[2];
    /** The page opens on Home: what the user does first goes to the home screen (and Apply's both). */
    @NonNull private WallpaperSlots.Slot mCentred = WallpaperSlots.Slot.HOME;
    private boolean mBusy;
    /** The surface closed: nothing here may touch a view or a callback any more. */
    private boolean mReleased;
    /** On screen: the centred card plays and the clock ticks. False while another page shows. */
    private boolean mShown = true;
    /** Closed for Photo…: the host brings the page back, pending photos and all. */
    private boolean mHandedOff;
    private boolean mSettingMotion;
    @Nullable private androidx.appcompat.app.AlertDialog mMissingDialog;
    /** The photos whose living still was looked up: path to the manifest, or absent when it has none. */
    private final Map<String, Manifest> mLivingByPhoto = new HashMap<>();
    private final Set<String> mLivingAbsent = new HashSet<>();
    /** Living ids read from disk: manifest and player; ids whose files are gone. */
    private final Map<String, Manifest> mLivingById = new HashMap<>();
    private final Map<String, AnimatedWallpaper> mPlayers = new HashMap<>();
    private final Set<String> mLivingIdAbsent = new HashSet<>();
    /** Lookups on the worker that have not come back: the same one is never asked twice. */
    private final Set<String> mLookingUp = new HashSet<>();

    private final WallpaperPreviewView[] mCards = new WallpaperPreviewView[2];
    private int mCardW;
    private int mCardH;
    private final int mThumbW;
    private final int mThumbH;

    /** The strip's tiles, in order: Same as Home first, then the recently applied photos. */
    private final List<Tile> mTiles = new ArrayList<>();
    @Nullable private Tile mSameAsHomeTile;
    private int mRecentTiles;

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
                               int sdkInt, @NonNull Runnable close) {
        this(context, slots, listener, sdkInt, close, null);
    }

    @VisibleForTesting
    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               int sdkInt, @NonNull Runnable close, @Nullable ReturnState restore) {
        this(context, slots, listener, sdkInt, WallpaperSlots.lockLiveSupported(sdkInt), close, restore,
            new WallpaperThumbs());
    }

    /** Loads on the calling thread: what a test wants. The app builds the page with {@link #load} and {@link Io#background}. */
    @VisibleForTesting
    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               int sdkInt, boolean animatedOffered, @NonNull Runnable close,
                               @Nullable ReturnState restore, @NonNull WallpaperThumbs thumbs) {
        this(context, slots, listener, sdkInt, animatedOffered, close, restore, thumbs,
            load(slots, animatedOffered && sdkInt >= 34, restore), Io.INLINE);
    }

    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               int sdkInt, boolean animatedOffered, @NonNull Runnable close,
                               @Nullable ReturnState restore, @NonNull WallpaperThumbs thumbs,
                               @NonNull Loaded loaded, @NonNull Io io) {
        mContext = context;
        mSlots = slots;
        mListener = listener;
        mSdk = sdkInt;
        mAnimatedOffered = animatedOffered;
        mThumbs = thumbs;
        mClose = close;
        mIo = io;
        mDensity = context.getResources().getDisplayMetrics().density;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mThumbH = dp(THUMB_HEIGHT_DP);
        mThumbW = WallpaperPickerLogic.thumbWidth(mThumbH, dm.widthPixels, dm.heightPixels);

        mStored = loaded.state;
        mLivingByPhoto.putAll(loaded.manifestByPhoto);
        mLivingAbsent.addAll(loaded.noLiving);
        mLivingById.putAll(loaded.manifestById);
        mPlayers.putAll(loaded.players);
        mPending[WallpaperSlots.Slot.HOME.ordinal()] = mStored.home;
        mPending[WallpaperSlots.Slot.LOCK.ordinal()] = mStored.lock;
        if (restore != null) {
            mPending[WallpaperSlots.Slot.HOME.ordinal()] = restore.pendingHome;
            mPending[WallpaperSlots.Slot.LOCK.ordinal()] = restore.pendingLock;
            mCentred = restore.centred;
        }

        mRoot = LayoutInflater.from(context).inflate(R.layout.wallpaper_picker_page, null, false);
        mTitle = mRoot.findViewById(R.id.wallpaper_picker_title);
        mTopBar = mRoot.findViewById(R.id.wallpaper_picker_top_bar);
        mStripCard = mRoot.findViewById(R.id.wallpaper_picker_strip_card);
        mShortcuts = mRoot.findViewById(R.id.wallpaper_picker_shortcuts);
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

        // The heading is the surface's, not the centred slot's: the cards carry their own labels.
        mTitle.setText(R.string.wallpaper_picker_title);
        mRoot.findViewById(R.id.wallpaper_picker_back).setOnClickListener(v -> {
            if (!mReleased) mClose.run();
        });
        mApply.setOnClickListener(v -> applyBoth());
        mApplyMore.setOnClickListener(v -> showApplyMenu());
        mPhoto.setOnClickListener(v -> {
            if (mBusy || mReleased) return;
            WallpaperSlots.Slot slot = mCentred;
            ReturnState back = returnState();
            // The host closes the surface and brings it back with the cropped photo.
            mHandedOff = true;
            mListener.onPickPhoto(slot, back);
        });
        mRoot.findViewById(R.id.wallpaper_picker_look).setOnClickListener(v -> openEditor(false));
        mRoot.findViewById(R.id.wallpaper_picker_more_settings).setOnClickListener(v -> {
            if (!mReleased) mSlots.openMoreSettings();
        });
        mLivingOffer.setOnClickListener(v -> onBringToLife());
        mLivingAgain.setOnClickListener(v -> onReadAgain());
        mRoot.findViewById(R.id.wallpaper_picker_living_cancel).setOnClickListener(v -> {
            if (mLivingJob != null) mLivingJob.cancel();
        });
        mIconPack.setOnClickListener(v -> {
            if (!mBusy && !mReleased) mListener.onOpenIcons();
        });
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

        buildStrip(loaded.recents);
        mRoot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View v) {
                mMain.removeCallbacks(mClockTick);
                if (mShown && !mReleased) mClockTick.run();
            }

            @Override public void onViewDetachedFromWindow(@NonNull View v) {
                mMain.removeCallbacks(mClockTick);
            }
        });
        updateClock();
        applyCentred();
        if (mLivingJob != null) mLivingJob.attach(mLivingListener);
    }

    // --- the surface's page ---

    @NonNull
    @Override
    public CharSequence title() {
        return mContext.getString(R.string.wallpaper_picker_title);
    }

    /** On screen again (or for the first time): the centred card plays and the clock ticks. */
    @Override
    public void onShown() {
        if (mReleased) return;
        mShown = true;
        mMain.removeCallbacks(mClockTick);
        if (mRoot.isAttachedToWindow()) mClockTick.run();
        refreshLive();
    }

    /** Another page is showing: nothing here animates, nothing is released. */
    @Override
    public void onHidden() {
        if (mReleased) return;
        mShown = false;
        mMain.removeCallbacks(mClockTick);
        refreshLive();
    }

    @Nullable
    @Override
    public View sharedCard() {
        WallpaperPreviewView card = mCards[mCentred == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME];
        if (card == null) card = mCards[POS_HOME];
        if (card == null) card = mCards[POS_LOCK];
        return card != null && card.getWidth() > 0 ? card : null;
    }

    @NonNull
    @Override
    public List<View> leavingViews() {
        return Arrays.asList(mStripCard, mMotionRow, mShortcuts);
    }

    @NonNull
    @Override
    public List<View> fadingViews() {
        return Arrays.asList(mTopBar, mProgress, mPager);
    }

    @Override
    public void setBackgroundAlpha(float alpha) {
        if (mBackground == null) {
            Drawable background = mRoot.getBackground();
            if (background == null) return;
            mBackground = background.mutate();
        }
        mBackground.setAlpha(Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
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
        if (mBusy || mReleased) return;
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

    /**
     * Reads the slots again off the main thread, then runs {@code then} with {@code mStored} fresh:
     * the binder calls under {@link WallpaperSlots#read} never run on a frame.
     */
    private void reloadStored(@NonNull Runnable then) {
        mIo.run(this::readSafe, state -> {
            if (mReleased || state == null) return;
            mStored = state;
            then.run();
        });
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
        if (mBusy || mReleased) return;
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
        if (mBusy || mReleased) return;
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
                if (mReleased) return;
                setBusy(false);
                reloadStored(this::refreshApply);
                showError(R.string.wallpaper_picker_apply_failed);
                return;
            }
            mListener.onApplied(slot, choice);
            reloadStored(next);
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
        if (mReleased) return;
        setBusy(false);
        refreshCards();
        refreshSameAsHomeThumb();
        refreshSelection();
        refreshApply();
        refreshMotionRow();
    }

    /** The split button's trailing half: Home screen only / Lock screen only. */
    private void showApplyMenu() {
        if (mBusy || mReleased) {
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

    private void onMotionToggled(boolean on) {
        if (mSettingMotion || mReleased) return;
        if (mBusy) {
            setMotionChecked(!on);
            return;
        }
        setBusy(true);
        final WallpaperSlots.Slot slot = mCentred;
        WallpaperSlots.Callback done = (ok, error) -> {
            if (mReleased) return;
            setBusy(false);
            if (!ok) {
                Logger.logError(LOG_TAG, "Motion " + on + " failed: " + error);
                setMotionChecked(!on);
                showError(R.string.wallpaper_picker_motion_failed);
                return;
            }
            reloadStored(() -> {
                setMotionChecked(motionOf(mCentred));
                refreshCards();
            });
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

    /** Look or Layout: the surface shows the editor; this page stays built, hidden, behind it. */
    private void openEditor(boolean layout) {
        if (mBusy || mReleased) return;
        if (layout) mListener.onOpenLayout();
        else mListener.onOpenLook();
    }

    /**
     * The surface closed (back, the arrow, or Photo…): the page lets go of everything. Thumbnails
     * are recycled here and only here, so no view may still hold one.
     */
    @Override
    public void release() {
        if (mReleased) return;
        mReleased = true;
        mShown = false;
        mMain.removeCallbacks(mClockTick);
        if (mLivingJob != null) mLivingJob.detach(mLivingListener);
        androidx.appcompat.app.AlertDialog missing = mMissingDialog;
        mMissingDialog = null;
        if (missing != null) missing.dismiss();
        for (WallpaperPreviewView card : mCards) {
            if (card == null) continue;
            card.setLive(false);
            card.show(null, null, false);
        }
        for (Tile tile : mTiles) tile.image.setImageDrawable(null);
        mThumbs.release();
        mIo.shutdown();
        if (!mHandedOff) discardPendingPhotos();
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

    /**
     * The living still of this photo, from the cache; null when it has none or the answer has not
     * come back yet. A photo not looked up is asked on the worker (the lookup hashes the photo) and
     * the page refreshes when it answers.
     */
    @Nullable
    private Manifest manifestFor(@NonNull File photo) {
        final String key = photo.getAbsolutePath();
        Manifest known = mLivingByPhoto.get(key);
        if (known != null) return known;
        if (mLivingAbsent.contains(key) || mReleased) return null;
        if (mLookingUp.add("photo:" + key)) {
            mIo.run(() -> {
                try {
                    return mSlots.livingFor(photo);
                } catch (RuntimeException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Looking for a living still failed", e);
                    return null;
                }
            }, found -> {
                mLookingUp.remove("photo:" + key);
                if (mReleased) return;
                if (found == null) {
                    mLivingAbsent.add(key);
                } else {
                    mLivingByPhoto.put(key, found);
                    ensureLiving(found.wallpaperId());
                }
                adoptLiving(WallpaperSlots.Slot.HOME);
                adoptLiving(WallpaperSlots.Slot.LOCK);
                refreshAfterLookup();
            });
        }
        return null;
    }

    /** Whether this photo's living still is still being looked for. */
    private boolean lookingUp(@NonNull File photo) {
        return mLookingUp.contains("photo:" + photo.getAbsolutePath());
    }

    /** The manifest behind a living choice, or null when its files are gone or are still being read. */
    @Nullable
    private Manifest livingManifest(@NonNull WallpaperSlots.Choice living) {
        String id = living.animatedId;
        if (id == null) return null;
        Manifest known = mLivingById.get(id);
        if (known == null) ensureLiving(id);
        return known;
    }

    /** Reads a living still's manifest and player on the worker, once; the page refreshes when it lands. */
    private void ensureLiving(@NonNull String id) {
        if (mReleased || mLivingIdAbsent.contains(id)
            || (mLivingById.containsKey(id) && mPlayers.containsKey(id))) return;
        if (!mLookingUp.add("living:" + id)) return;
        mIo.run(() -> {
            Loaded one = new Loaded(mStored, Collections.<File>emptyList());
            lookUpLiving(mSlots, one, id);
            return one;
        }, one -> {
            mLookingUp.remove("living:" + id);
            if (mReleased || one == null) return;
            if (one.manifestById.isEmpty()) {
                mLivingIdAbsent.add(id);
            } else {
                mLivingById.putAll(one.manifestById);
                mPlayers.putAll(one.players);
            }
            refreshAfterLookup();
        });
    }

    /** A lookup answered: whatever it was for is drawn from the caches now. */
    private void refreshAfterLookup() {
        refreshCards();
        refreshSameAsHomeThumb();
        refreshSelection();
        refreshApply();
        refreshMotionRow();
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
        if (photo != null && !hasLiving && lookingUp(photo)) {
            // Not known yet whether this photo has a living still: the row holds its place and
            // shows nothing, rather than offering Bring to life and then taking it back.
            mMotionRow.setVisibility(View.VISIBLE);
            mRoot.findViewById(R.id.wallpaper_picker_motion_group).setVisibility(View.GONE);
            mLivingOffer.setVisibility(View.GONE);
            mLivingWorking.setVisibility(View.GONE);
            return;
        }
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
        if (mReleased || mLivingJob == null) return;
        File photo = livingPhotoFor(shown(mCentred));
        if (photo != null) startLiving(photo);
    }

    /** Read again: the same analysis over the living still's own copy of the photo. */
    private void onReadAgain() {
        if (mReleased || mLivingJob == null || mBusy) return;
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
                if (!mReleased) mSlots.openModelCentre(first);
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
            if (mReleased) return;
            refreshMotionRow();
        }

        @Override public void onFinished(@NonNull LivingStillJob.Result result) {
            if (mReleased) return;
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
            mLivingById.put(manifest.wallpaperId(), manifest);
            mLivingIdAbsent.remove(manifest.wallpaperId());
            // The new still's player is made off the main thread; the card plays it when it lands.
            mPlayers.remove(manifest.wallpaperId());
            ensureLiving(manifest.wallpaperId());
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
        // The small label over the card takes its own height out of the card's.
        int[] size = WallpaperPickerLogic.cardSize(pagerW, pagerH, dp(CARD_VERTICAL_PAD_DP),
            labelBlockPx(), WallpaperPreviewView.OVERLAY_W, WallpaperPreviewView.OVERLAY_H,
            CARD_MAX_WIDTH_FRACTION);
        int cardW = size[0];
        int cardH = size[1];
        mCardW = cardW;
        mCardH = cardH;
        int itemW = cardW + 2 * dp(CARD_GAP_DP / 2);
        int side = Math.max(0, (pagerW - itemW) / 2);
        mPager.post(() -> {
            if (mReleased) return;
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

    /** The label over a card and the gap under it, px. */
    private int labelBlockPx() {
        return dp(CARD_LABEL_DP) + dp(CARD_LABEL_GAP_DP);
    }

    private final class CardAdapter extends RecyclerView.Adapter<CardHolder> {
        @NonNull @Override
        public CardHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            // A column centred in the cell: the label (Home screen / Lock screen) over the card.
            LinearLayout cell = new LinearLayout(context);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            TextView label = new TextView(context);
            label.setTextAppearance(resolveStyle(com.google.android.material.R.attr.textAppearanceLabelLarge));
            label.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(CARD_LABEL_DP));
            labelParams.bottomMargin = dp(CARD_LABEL_GAP_DP);
            cell.addView(label, labelParams);
            WallpaperPreviewView card = new WallpaperPreviewView(context);
            cell.addView(card, new LinearLayout.LayoutParams(1, 1));
            return new CardHolder(cell, card, label);
        }

        @Override
        public void onBindViewHolder(@NonNull CardHolder holder, int position) {
            int itemW = Math.max(1, mCardW + 2 * dp(CARD_GAP_DP / 2));
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(itemW,
                ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) holder.card.getLayoutParams();
            lp.width = Math.max(1, mCardW);
            lp.height = Math.max(1, mCardH);
            holder.card.setLayoutParams(lp);
            final WallpaperSlots.Slot slot = position == POS_LOCK ? WallpaperSlots.Slot.LOCK : WallpaperSlots.Slot.HOME;
            for (int i = 0; i < mCards.length; i++) if (mCards[i] == holder.card) mCards[i] = null;
            mCards[position] = holder.card;
            holder.label.setText(slot == WallpaperSlots.Slot.LOCK
                ? R.string.wallpaper_picker_slot_lock : R.string.wallpaper_picker_slot_home);
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
        @NonNull final TextView label;

        CardHolder(@NonNull View itemView, @NonNull WallpaperPreviewView card, @NonNull TextView label) {
            super(itemView);
            this.card = card;
            this.label = label;
        }
    }

    private boolean liveFor(int position) {
        if (mReleased || !mShown || !mAnimatedOffered) return false;
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
        // Nothing else a slot can hold has a picture here (a retired background is healed on
        // start-up): an empty card.
        card.show(null, null, true);
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
        AnimatedWallpaper w = c.animatedId == null ? null : mPlayers.get(c.animatedId);
        final File file = manifest.image();
        Bitmap still = mCardW <= 0 ? null : mThumbs.cachedPhoto(file, mCardW, mCardH);
        card.show(w, still, false);
        if (still != null || mCardW <= 0 || mCardH <= 0) return;
        mThumbs.requestPhoto(file, mCardW, mCardH, bmp -> {
            if (mReleased || mCards[position] != card) return;
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
            if (mReleased || mCards[position] != card) return;
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

    /**
     * Same as Home first (Lock only), then the photos last applied, as tiles of equal cells across
     * the row: each tile centred in its own share of the width, so the row reads as evenly spaced
     * whether it holds two tiles or four. No heading and no badge says what the photos are.
     */
    private void buildStrip(@NonNull List<File> recents) {
        mStrip.removeAllViews();
        mTiles.clear();
        mSameAsHomeTile = addTile(WallpaperSlots.Choice.sameAsHome(),
            mContext.getString(R.string.wallpaper_picker_same_as_home), true);
        refreshSameAsHomeThumb();
        mRecentTiles = 0;
        for (File photo : recents) {
            if (mRecentTiles >= RecentWallpapers.MAX) break;
            mRecentTiles++;
            Tile tile = addTile(WallpaperSlots.Choice.photo(photo),
                mContext.getString(R.string.wallpaper_picker_recent_photo, mRecentTiles), false);
            final ShapeableImageView image = tile.image;
            final String key = photo.getAbsolutePath();
            image.setTag(key);
            mThumbs.requestPhoto(photo, mThumbW, mThumbH, bmp -> {
                if (key.equals(image.getTag())) image.setImageBitmap(bmp);
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
        cell.addView(image, new FrameLayout.LayoutParams(mThumbW, mThumbH, Gravity.CENTER_HORIZONTAL));
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
        // Equal shares of the row, the tile centred in each.
        mStrip.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Tile tile = new Tile(choice, cell, image);
        mTiles.add(tile);
        return tile;
    }

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
        image.setTag(null);
        image.setImageDrawable(null);
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

    /** Back, as the arrow would close the surface. */
    @VisibleForTesting
    void close() {
        mClose.run();
    }

    /** The small label over {@code slot}'s card, or null while it is not bound. */
    @VisibleForTesting
    @Nullable
    TextView cardLabel(@NonNull WallpaperSlots.Slot slot) {
        WallpaperPreviewView card = mCards[slot == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME];
        if (card == null || !(card.getParent() instanceof LinearLayout)) return null;
        View first = ((LinearLayout) card.getParent()).getChildAt(0);
        return first instanceof TextView ? (TextView) first : null;
    }

    private int dp(int v) {
        return Math.round(v * mDensity);
    }
}
