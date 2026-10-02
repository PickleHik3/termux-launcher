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
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.app.layouteditor.EditorM3;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The full-screen wallpaper picker page (lock-live-wallpaper.md, "The picker page"): two slot
 * previews (Lock, then Home) in a snap pager, the Lock slot's Motion toggle, the Look / Icon pack /
 * Layout shortcuts, the thumbnail strip and Apply. It replaces the old bottom sheet and its
 * home / lock / both dialog.
 *
 * <p>The page reads and writes the slots only through {@link Slots}, which in the app wraps
 * {@link WallpaperSlots}; it never names WallpaperManager or a slot preference. Tapping a
 * thumbnail sets the centred slot's pending choice and previews it there, live; Apply commits it.
 * Swiping keeps each slot's pending choice. Back closes without applying.</p>
 *
 * <p>Hosted in a full-screen {@link AppCompatDialog} by {@link #show}; tests build the page on
 * its own with {@link #WallpaperPickerPage(Context, Slots, Listener, int, Runnable)}.</p>
 */
public final class WallpaperPickerPage {

    /** The page's one door to the slots. The app's is {@link #systemSlots}; tests pass a fake. */
    public interface Slots {
        @NonNull WallpaperSlots.State read();

        void apply(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                   @Nullable WallpaperSlots.Callback cb);

        void setLockMotion(boolean on, @Nullable WallpaperSlots.Callback cb);
    }

    /** What the host does. Every call is on the main thread. */
    public interface Listener {
        /** The page covers the launcher (true) or has gone (false): pause or resume its backdrop. */
        void onPageShown(boolean shown);

        /** Photo… was tapped for {@code slot}; the page has dismissed. Run the photo flow. */
        void onPickPhoto(@NonNull WallpaperSlots.Slot slot);

        /** Apply succeeded for {@code slot}; the page stays open. Home refreshes the glass. */
        void onApplied(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice);

        /** A shortcut; the page has dismissed. */
        void onOpenLook();

        void onOpenIconPack();

        void onOpenLayout();
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
        };
    }

    /** Shows the page full screen over {@code activity}. */
    @NonNull
    public static WallpaperPickerPage show(@NonNull AppCompatActivity activity, @NonNull Slots slots,
                                           @NonNull Listener listener) {
        AppCompatDialog dialog = new AppCompatDialog(activity, R.style.ThemeOverlay_Termux_WallpaperPickerPage);
        WallpaperPickerPage page = new WallpaperPickerPage(dialog.getContext(), slots, listener,
            Build.VERSION.SDK_INT, dialog::dismiss);
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

    private final Context mContext;
    private final Slots mSlots;
    private final Listener mListener;
    private final int mSdk;
    private final Runnable mDismiss;
    private final float mDensity;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final WallpaperThumbs mThumbs = new WallpaperThumbs();

    private final View mRoot;
    private final TextView mTitle;
    private final MaterialButton mApply;
    private final LinearProgressIndicator mProgress;
    private final RecyclerView mPager;
    private final LinearLayoutManager mPagerLayout;
    private final PagerSnapHelper mSnap = new PagerSnapHelper();
    private final View mMotionRow;
    private final MaterialSwitch mMotion;
    private final LinearLayout mStrip;
    private final MaterialButton mPhoto;

    @NonNull private WallpaperSlots.State mStored;
    /** Pending choices by {@link WallpaperSlots.Slot#ordinal()}. */
    private final WallpaperSlots.Choice[] mPending = new WallpaperSlots.Choice[2];
    @NonNull private WallpaperSlots.Slot mCentred = WallpaperSlots.Slot.LOCK;
    private boolean mBusy;
    private boolean mDismissed;
    private boolean mSettingMotion;

    private final WallpaperPreviewView[] mCards = new WallpaperPreviewView[2];
    private int mCardW;
    private int mCardH;
    private final int mThumbW;
    private final int mThumbH;

    /** The strip's tiles, in order: Same as Home first, then the backgrounds. */
    private final List<Tile> mTiles = new ArrayList<>();
    @Nullable private Tile mSameAsHomeTile;

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
        mContext = context;
        mSlots = slots;
        mListener = listener;
        mSdk = sdkInt;
        mDismiss = dismiss;
        mDensity = context.getResources().getDisplayMetrics().density;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mThumbH = dp(THUMB_HEIGHT_DP);
        mThumbW = WallpaperPickerLogic.thumbWidth(mThumbH, dm.widthPixels, dm.heightPixels);

        mStored = readSafe();
        mPending[WallpaperSlots.Slot.HOME.ordinal()] = mStored.home;
        mPending[WallpaperSlots.Slot.LOCK.ordinal()] = mStored.lock;

        mRoot = LayoutInflater.from(context).inflate(R.layout.wallpaper_picker_page, null, false);
        mTitle = mRoot.findViewById(R.id.wallpaper_picker_title);
        mApply = mRoot.findViewById(R.id.wallpaper_picker_apply);
        mProgress = mRoot.findViewById(R.id.wallpaper_picker_progress);
        mPager = mRoot.findViewById(R.id.wallpaper_picker_pager);
        mMotionRow = mRoot.findViewById(R.id.wallpaper_picker_motion_row);
        mMotion = mRoot.findViewById(R.id.wallpaper_picker_motion);
        mStrip = mRoot.findViewById(R.id.wallpaper_picker_strip);
        mPhoto = mRoot.findViewById(R.id.wallpaper_picker_photo);

        mRoot.findViewById(R.id.wallpaper_picker_back).setOnClickListener(v -> dismiss());
        mApply.setOnClickListener(v -> applyPending());
        mPhoto.setOnClickListener(v -> {
            if (mBusy) return;
            WallpaperSlots.Slot slot = mCentred;
            dismiss();
            mListener.onPickPhoto(slot);
        });
        mRoot.findViewById(R.id.wallpaper_picker_look).setOnClickListener(v -> shortcut(mListener::onOpenLook));
        mRoot.findViewById(R.id.wallpaper_picker_icon_pack).setOnClickListener(v -> shortcut(mListener::onOpenIconPack));
        mRoot.findViewById(R.id.wallpaper_picker_layout).setOnClickListener(v -> shortcut(mListener::onOpenLayout));

        mMotion.setChecked(mStored.lockMotion);
        mMotion.setOnCheckedChangeListener((b, on) -> onMotionToggled(on));

        mPagerLayout = new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
        mPager.setLayoutManager(mPagerLayout);
        mPager.setAdapter(new CardAdapter());
        mSnap.attachToRecyclerView(mPager);
        mPager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
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
    }

    @NonNull
    public View root() {
        return mRoot;
    }

    @NonNull
    WallpaperSlots.Slot centredSlot() {
        return mCentred;
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
        refreshCards();
        refreshSameAsHomeThumb();
        refreshSelection();
        refreshApply();
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
        if (!WallpaperPickerLogic.motionRowExists(mSdk)) {
            mMotionRow.setVisibility(View.GONE);
        } else {
            mMotionRow.setVisibility(View.VISIBLE);
            mMotion.setVisibility(WallpaperPickerLogic.showsMotion(mCentred, mSdk) ? View.VISIBLE : View.INVISIBLE);
        }
        if (mSameAsHomeTile != null) {
            mSameAsHomeTile.view.setVisibility(
                WallpaperPickerLogic.showsSameAsHome(mCentred) ? View.VISIBLE : View.GONE);
        }
        refreshLive();
        refreshSelection();
        refreshApply();
    }

    private void refreshApply() {
        mApply.setEnabled(WallpaperPickerLogic.applyEnabled(mPending[mCentred.ordinal()],
            stored(mCentred), mBusy));
    }

    private void setBusy(boolean busy) {
        mBusy = busy;
        mProgress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        mMotion.setEnabled(!busy);
        mStrip.setAlpha(busy ? 0.5f : 1f);
        refreshApply();
    }

    // --- apply, motion, photo, shortcuts ---

    private void applyPending() {
        if (mBusy || mDismissed) return;
        final WallpaperSlots.Slot slot = mCentred;
        final WallpaperSlots.Choice choice = mPending[slot.ordinal()];
        if (!WallpaperPickerLogic.applyEnabled(choice, stored(slot), false)) return;
        setBusy(true);
        try {
            mSlots.apply(slot, choice, (ok, error) -> onApplyDone(slot, choice, ok, error));
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Applying to " + slot + " failed", e);
            onApplyDone(slot, choice, false, e.getMessage());
        }
    }

    private void onApplyDone(@NonNull WallpaperSlots.Slot slot, @NonNull WallpaperSlots.Choice choice,
                             boolean ok, @Nullable String error) {
        if (mDismissed) {
            if (ok) mListener.onApplied(slot, choice);
            return;
        }
        setBusy(false);
        if (!ok) {
            Logger.logError(LOG_TAG, "Applying to " + slot + " failed: " + error);
            showError(R.string.wallpaper_picker_apply_failed);
            return;
        }
        mStored = readSafe();
        refreshApply();
        mListener.onApplied(slot, choice);
    }

    private void onMotionToggled(boolean on) {
        if (mSettingMotion || mDismissed) return;
        if (mBusy) {
            setMotionChecked(!on);
            return;
        }
        setBusy(true);
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
            setMotionChecked(mStored.lockMotion);
            refreshCards();
        };
        try {
            mSlots.setLockMotion(on, done);
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

    private void shortcut(@NonNull Runnable open) {
        if (mBusy) return;
        dismiss();
        open.run();
    }

    private void dismiss() {
        if (mDismissed) return;
        mDismiss.run();
        // A host without a dialog (tests) never calls back: settle here too.
        onDismissed();
    }

    /** The dialog went away (back, the arrow, a shortcut or Photo…). */
    void onDismissed() {
        if (mDismissed) return;
        mDismissed = true;
        mMain.removeCallbacks(mClockTick);
        for (WallpaperPreviewView card : mCards) if (card != null) card.setLive(false);
        mThumbs.release();
        mListener.onPageShown(false);
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
        if (mDismissed) return false;
        int centred = mCentred == WallpaperSlots.Slot.LOCK ? POS_LOCK : POS_HOME;
        if (position != centred) return false;
        // A Lock with Motion off shows its still, as the lock screen will.
        return position != POS_LOCK || mStored.lockMotion || !WallpaperSlots.lockLiveSupported(mSdk);
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
        for (AnimatedWallpaper w : AnimatedWallpapers.all()) {
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
                com.google.android.material.R.attr.shapeAppearanceCornerFull,
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

    /** Same as Home is drawn as the Home slot's thumbnail. */
    private void refreshSameAsHomeThumb() {
        if (mSameAsHomeTile == null) return;
        final ShapeableImageView image = mSameAsHomeTile.image;
        WallpaperSlots.Choice home = mPending[WallpaperSlots.Slot.HOME.ordinal()];
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

    private int dp(int v) {
        return Math.round(v * mDensity);
    }
}
