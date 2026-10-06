package com.termux.app.chrome.wallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.util.DisplayMetrics;
import android.view.Gravity;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.fragments.settings.termux.TermuxStylePreferencesFragment;
import com.termux.app.layouteditor.EditorM3;
import com.termux.app.surfaces.AppearanceSurfaceController;
import com.termux.shared.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Appearance surface's Overview (it began as the wallpaper picker page): the fixed heading
 * Appearance, two slot previews (Lock, then Home, each under a small label) in a snap pager over
 * the thumbnail strip.
 *
 * <p>The page reads and writes the slots only through {@link Slots}, which in the app wraps
 * {@link WallpaperSlots}; it never names WallpaperManager or a slot preference. Tapping a
 * thumbnail sets the centred slot's pending choice and previews it there. Swiping keeps each
 * slot's pending choice. The surface's Done applies each card's own pending choice to its own slot
 * ({@link #commit}); Back and HOME ask first when one is pending.</p>
 *
 * <p>It is a page of the surface ({@link AppearanceSurfaceController.OverviewPage}), not a
 * window: built once per session, hidden (not destroyed) while Look, Layout or Icons shows, and
 * released when the surface closes. What it reads from disk (the slots, the recent photos) is read
 * off the main thread ({@link #load}, then {@link Io}). Look, Layout and Icon pack go to
 * the surface through {@link Listener}; Photo… closes the surface and hands the host a
 * {@link ReturnState}, with which the host opens it again when the photo pick and the crop end. A
 * cropped photo comes back as the centred slot's pending choice ({@link ReturnState#withPhoto}),
 * previewed in its card and set only by Done. Leaving without Done discards a pending photo's
 * file.</p>
 *
 * <p>Photos are first-class on every API level: a slot holding a photo shows it in its card, and
 * the strip holds Same as Home (Lock only) then the photos last applied, evenly spaced across the
 * row. There are no pre-made backgrounds. More settings opens the old Look page.</p>
 *
 * <p>Tests build the page on its own with
 * {@link #WallpaperPickerPage(Context, Slots, Listener, Runnable, ReturnState)}, which loads
 * synchronously.</p>
 */
public final class WallpaperPickerPage implements AppearanceSurfaceController.OverviewPage {

    /** The page's one door to the slots. The app's is {@link #systemSlots}; tests pass a fake. */
    public interface Slots {
        @NonNull WallpaperSlots.State read();

        void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                   @Nullable WallpaperSlots.Callback cb);

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

        /**
         * A fresh page centred on {@code slot}, with nothing pending: where the Appearance surface
         * reopens when it remembers the card the person last had in the middle.
         */
        @NonNull
        public static ReturnState centredOn(@NonNull WallpaperSlots.Slot slot, @NonNull Loaded loaded) {
            return new ReturnState(slot, loaded.state.home, loaded.state.lock);
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
     * What the page needs from disk before it can draw: the slots and the recent photos. Read by
     * {@link #load} off the main thread.
     */
    public static final class Loaded {
        @NonNull final WallpaperSlots.State state;
        @NonNull final List<File> recents;

        Loaded(@NonNull WallpaperSlots.State state, @NonNull List<File> recents) {
            this.state = state;
            this.recents = recents;
        }
    }

    /**
     * Reads everything the page opens with. Blocking: the surface calls it on its worker, tests
     * call it inline.
     */
    @WorkerThread
    @NonNull
    public static Loaded load(@NonNull Slots slots) {
        WallpaperSlots.State state;
        try {
            state = slots.read();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the wallpaper slots failed", e);
            state = new WallpaperSlots.State(WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
        }
        List<File> recents;
        try {
            recents = slots.recents();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the recent photos failed", e);
            recents = Collections.emptyList();
        }
        return new Loaded(state, new ArrayList<>(recents));
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

    private final Context mContext;
    private final Slots mSlots;
    private final Listener mListener;
    /** The back arrow: the surface closes. */
    private final Runnable mClose;
    private final Io mIo;
    private final float mDensity;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final WallpaperThumbs mThumbs;

    private final View mRoot;
    private final LinearProgressIndicator mProgress;
    private final RecyclerView mPager;
    private final LinearLayoutManager mPagerLayout;
    private final PagerSnapHelper mSnap = new PagerSnapHelper();
    private final LinearLayout mStrip;
    private final MaterialButton mPhoto;
    private final View mStripCard;
    @Nullable private Drawable mBackground;

    @NonNull private WallpaperSlots.State mStored;
    /** Pending choices by {@link WallpaperSlots.Slot#ordinal()}. */
    private final WallpaperSlots.Choice[] mPending = new WallpaperSlots.Choice[2];
    /** The page opens on Home: what the user does first goes to the home screen. */
    @NonNull private WallpaperSlots.Slot mCentred = WallpaperSlots.Slot.HOME;
    private boolean mBusy;
    /** The surface closed: nothing here may touch a view or a callback any more. */
    private boolean mReleased;
    /** On screen. False while another page shows. */
    private boolean mShown = true;
    /** Closed for Photo…: the host brings the page back, pending photos and all. */
    private boolean mHandedOff;
    private final WallpaperPreviewView[] mCards = new WallpaperPreviewView[2];
    private int mCardW;
    private int mCardH;
    /** Strip thumbnail size: shrunk to fit the row once its width is known ({@link #fitStripThumbs}). */
    private int mThumbW;
    private int mThumbH;
    private List<File> mStripRecents = Collections.emptyList();
    private int mStripFitWidth = -1;

    /** The strip's tiles, in order: Same as Home first, then the recently applied photos. */
    private final List<Tile> mTiles = new ArrayList<>();
    @Nullable private Tile mSameAsHomeTile;
    private int mRecentTiles;

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
                               @NonNull Runnable close) {
        this(context, slots, listener, close, null);
    }

    @VisibleForTesting
    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               @NonNull Runnable close, @Nullable ReturnState restore) {
        this(context, slots, listener, close, restore, new WallpaperThumbs());
    }

    /** Loads on the calling thread: what a test wants. The app builds the page with {@link #load} and {@link Io#background}. */
    @VisibleForTesting
    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               @NonNull Runnable close, @Nullable ReturnState restore,
                               @NonNull WallpaperThumbs thumbs) {
        this(context, slots, listener, close, restore, thumbs, load(slots), Io.INLINE);
    }

    public WallpaperPickerPage(@NonNull Context context, @NonNull Slots slots, @NonNull Listener listener,
                               @NonNull Runnable close, @Nullable ReturnState restore,
                               @NonNull WallpaperThumbs thumbs, @NonNull Loaded loaded, @NonNull Io io) {
        mContext = context;
        mSlots = slots;
        mListener = listener;
        mThumbs = thumbs;
        mClose = close;
        mIo = io;
        mDensity = context.getResources().getDisplayMetrics().density;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mThumbH = dp(THUMB_HEIGHT_DP);
        mThumbW = WallpaperPickerLogic.thumbWidth(mThumbH, dm.widthPixels, dm.heightPixels);

        mStored = loaded.state;
        mPending[WallpaperSlots.Slot.HOME.ordinal()] = mStored.home;
        mPending[WallpaperSlots.Slot.LOCK.ordinal()] = mStored.lock;
        if (restore != null) {
            mPending[WallpaperSlots.Slot.HOME.ordinal()] = restore.pendingHome;
            mPending[WallpaperSlots.Slot.LOCK.ordinal()] = restore.pendingLock;
            mCentred = restore.centred;
        }

        mRoot = LayoutInflater.from(context).inflate(R.layout.wallpaper_picker_page, null, false);
        mStripCard = mRoot.findViewById(R.id.wallpaper_picker_strip_card);
        mProgress = mRoot.findViewById(R.id.wallpaper_picker_progress);
        mPager = mRoot.findViewById(R.id.wallpaper_picker_pager);
        mStrip = mRoot.findViewById(R.id.wallpaper_picker_strip);
        mPhoto = mRoot.findViewById(R.id.wallpaper_picker_photo);

        // The bar (back, the Wallpaper | Look | Layout pill, Done) is the surface's shared frame:
        // Done applies what the cards hold; the page has nothing above or under them but the strip.
        mPhoto.setOnClickListener(v -> {
            if (mBusy || mReleased) return;
            WallpaperSlots.Slot slot = mCentred;
            ReturnState back = returnState();
            // The host closes the surface and brings it back with the cropped photo.
            mHandedOff = true;
            mListener.onPickPhoto(slot, back);
        });
        mRoot.findViewById(R.id.wallpaper_picker_more_settings).setOnClickListener(v -> {
            if (!mReleased) mSlots.openMoreSettings();
        });

        mPagerLayout = new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
        mPager.setLayoutManager(mPagerLayout);
        mPager.setAdapter(new CardAdapter());
        // Start laid out at the centred card (Home): the first layout pass must not centre the
        // Lock card at position 0, which is bound and played for a frame before sizeCards scrolls.
        mPagerLayout.scrollToPositionWithOffset(initialPosition(mCentred), 0);
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
        mStrip.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitStripThumbs(r - l));
        applyCentred();
    }

    // --- the surface's page ---

    @NonNull
    @Override
    public CharSequence title() {
        return mContext.getString(R.string.wallpaper_picker_title);
    }

    /** On screen again (or for the first time). */
    @Override
    public void onShown() {
        if (mReleased) return;
        mShown = true;
    }

    /** Another page is showing; nothing is released. */
    @Override
    public void onHidden() {
        if (mReleased) return;
        mShown = false;
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
        return Collections.singletonList(mStripCard);
    }

    @NonNull
    @Override
    public List<View> fadingViews() {
        return Arrays.asList(mProgress, mPager);
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

    /** The card standing in the middle: the one Photo… and the strip act on. */
    @NonNull
    public WallpaperSlots.Slot centredSlot() {
        return mCentred;
    }

    @Nullable
    @Override
    public String centredSlotName() {
        return mCentred.name();
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
        refreshCards();
        refreshSameAsHomeThumb();
        refreshSelection();
    }

    // --- state ---

    @NonNull
    private WallpaperSlots.State readSafe() {
        try {
            return mSlots.read();
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Reading the wallpaper slots failed", e);
            return new WallpaperSlots.State(WallpaperSlots.Choice.photo(), WallpaperSlots.Choice.sameAsHome());
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
        if (mSameAsHomeTile != null) {
            mSameAsHomeTile.view.setVisibility(
                WallpaperPickerLogic.showsSameAsHome(mCentred) ? View.VISIBLE : View.GONE);
        }
        refreshSelection();
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mProgress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        mStrip.setAlpha(busy ? 0.5f : 1f);
    }

    // --- apply, photo, shortcuts ---

    /** Whether either card holds a choice that is not what the slots store: what Done would apply. */
    @Override
    public boolean hasPendingChanges() {
        return !mReleased
            && (WallpaperPickerLogic.pendingApplies(WallpaperSlots.Slot.HOME,
                    mPending[WallpaperSlots.Slot.HOME.ordinal()], mStored.home)
                || WallpaperPickerLogic.pendingApplies(WallpaperSlots.Slot.LOCK,
                    mPending[WallpaperSlots.Slot.LOCK.ordinal()], mStored.lock));
    }

    /**
     * Done: each card's own pending choice goes to its own slot, Home first (Lock's Same as Home
     * copies Home's picture, and a Home set already covers a Lock that follows it). Runs
     * {@code onDone} when every slot is set and {@code onFailed} when one is not (after the error
     * toast), with the page usable again either way. Nothing pending runs {@code onDone} at once.
     */
    @Override
    public void commit(@NonNull Runnable onDone, @NonNull Runnable onFailed) {
        commitPending(onDone, onFailed);
    }

    @VisibleForTesting
    void commitPending(@NonNull Runnable onDone, @NonNull Runnable onFailed) {
        if (mReleased || mBusy) {
            onFailed.run();
            return;
        }
        if (!hasPendingChanges()) {
            onDone.run();
            return;
        }
        setBusy(true);
        applyPending(WallpaperSlots.Slot.HOME, () -> applyPending(WallpaperSlots.Slot.LOCK, () -> {
            if (mReleased) return;
            setBusy(false);
            refreshCards();
            refreshSameAsHomeThumb();
            refreshSelection();
            onDone.run();
        }, onFailed), onFailed);
    }

    /** Applies {@code slot}'s pending choice when it differs from what is stored, then {@code next}. */
    private void applyPending(@NonNull WallpaperSlots.Slot slot, @NonNull Runnable next,
                              @NonNull Runnable failed) {
        final WallpaperSlots.Choice choice = mPending[slot.ordinal()];
        if (!WallpaperPickerLogic.pendingApplies(slot, choice, stored(slot))) {
            next.run();
            return;
        }
        runApply(slot, choice, () -> {
            // A photo is stored as the slot's own kept copy, not the pending file.
            mPending[slot.ordinal()] = choice.photo ? stored(slot) : choice;
            next.run();
        }, failed);
    }

    /** Applies one slot; {@code next} runs on success, and a failure ends the run with an error. */
    private void runApply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                          @NonNull Runnable next, @NonNull Runnable failed) {
        WallpaperSlots.Callback done = (ok, error) -> {
            if (!ok) {
                Logger.logError(LOG_TAG, "Applying to " + slot + " failed: " + error);
                if (!mReleased) {
                    setBusy(false);
                    reloadStored(() -> { });
                    showError(R.string.wallpaper_picker_apply_failed);
                }
                failed.run();
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

    /**
     * A toast, not an AppNotice: the notice pill draws in the activity's own window, which this
     * full-screen page covers.
     */
    private void showError(int messageRes) {
        android.widget.Toast.makeText(mContext, messageRes, android.widget.Toast.LENGTH_LONG).show();
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
        for (WallpaperPreviewView card : mCards) {
            if (card == null) continue;
            card.show(null, false);
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

    // --- pager ---

    /** The pager position a page opens at: the centred slot's card (Home unless restored on Lock). */
    static int initialPosition(@NonNull WallpaperSlots.Slot centred) {
        return centred == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME;
    }

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

    private void refreshCards() {
        for (int i = 0; i < mCards.length; i++) if (mCards[i] != null) bindCard(i);
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
        // Nothing else a slot can hold has a picture here: an empty card.
        card.show(null, true);
    }

    /** A photo card: its picture centre-cropped, as the system shows it; the photo glyph while it loads. */
    private void bindPhotoCard(int position, @NonNull WallpaperPreviewView card,
                               @NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice c) {
        final File file = c.photoFile;
        Bitmap still = file == null || mCardW <= 0 ? null : mThumbs.cachedPhoto(file, mCardW, mCardH);
        card.show(still, true);
        if (file == null || still != null || mCardW <= 0 || mCardH <= 0) return;
        mThumbs.requestPhoto(file, mCardW, mCardH, bmp -> {
            if (mReleased || mCards[position] != card) return;
            WallpaperSlots.Choice now = shown(slot);
            if (now.photo && now.photoFile != null && now.photoFile.equals(file)) card.setStill(bmp);
        });
    }

    // --- strip ---

    /**
     * Same as Home first (Lock only), then the photos last applied, as tiles of equal cells across
     * the row: each tile centred in its own share of the width, so the row reads as evenly spaced
     * whether it holds two tiles or four. No heading and no badge says what the photos are.
     */
    private void buildStrip(@NonNull List<File> recents) {
        mStripRecents = recents;
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

    /**
     * Sizes the thumbnails to the strip's real width ({@link WallpaperPickerLogic#stripThumbSize}):
     * never clipped, aspect kept. Rebuilds the row only when the size actually changes.
     */
    private void fitStripThumbs(int stripWidth) {
        if (stripWidth <= 0 || stripWidth == mStripFitWidth || mReleased) return;
        mStripFitWidth = stripWidth;
        int avail = stripWidth - mStrip.getPaddingStart() - mStrip.getPaddingEnd();
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        int[] size = WallpaperPickerLogic.stripThumbSize(avail,
            WallpaperPickerLogic.stripTileCount(mStripRecents.size(), RecentWallpapers.MAX),
            dm.widthPixels, dm.heightPixels, dp(THUMB_HEIGHT_DP), dp(4));
        if (size[0] == mThumbW && size[1] == mThumbH) return;
        mThumbW = size[0];
        mThumbH = size[1];
        // Rebuilding inside a layout pass is not allowed: post it.
        mMain.post(() -> {
            if (mReleased) return;
            buildStrip(mStripRecents);
            applyCentred();
        });
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

    /** Same as Home is drawn as the Home slot's thumbnail: its photo. */
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
