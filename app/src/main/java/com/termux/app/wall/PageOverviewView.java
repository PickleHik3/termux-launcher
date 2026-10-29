package com.termux.app.wall;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.termux.R;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The page overview (Launcher3's overview for this wall): the wall zoomed out to one strip of
 * cards, Home's pages first and then the current session's windows, with a gap at the seam. Each
 * card can be dragged within its own half, removed by its red button, and made Home by its home
 * button — a Home page, the first terminal window or the display window ({@link StartInPolicy});
 * the pills at the bottom add a page, a terminal or a display after the focused card. Back, or a tap on a card, zooms back into that card.
 *
 * <p>The wallpaper stays full-screen behind the cards. The card the wall rests on is the live
 * launcher itself, drawn scaled: the activity's root view is hidden (alpha 0, so it keeps drawing
 * its children's display lists) and redrawn into the card, so its widgets, dock and terminal text
 * stay live without any repaint of the overview's own. As the strip settles on a card the wall
 * underneath moves to it, so the focused card is the live one. A card the wall has left shows the
 * snapshot taken of it when it was live; a terminal window never shown here is drawn from its
 * screen's text, refreshed as it comes near. Snapshots live only while the overview is open, in a
 * small bounded store.
 *
 * <p>Removing something that holds anything (a page with items or floats, a terminal running a
 * process, an open display) only hides its card behind an undo snackbar; it is really removed —
 * a terminal's shells killed — once the snackbar is gone without Undo: it timed out, another
 * action replaced it, or the overview closed. Empty things go at once. Every rule is
 * {@link PageOverviewPolicy}'s.
 */
@SuppressLint("ViewConstructor")
public final class PageOverviewView extends FrameLayout {

    /** The launcher's side: the pages and windows as they stand, and the changes. */
    public interface Host {
        // Home's pages (Edit pages' operations).
        int pageCount();
        /** Widgets, icons and folders on page {@code page}. */
        int itemsOn(int page);
        /** Floats standing on page {@code page}. */
        int floatsOn(int page);
        int defaultPage();
        void movePage(int from, int to);
        void removePage(int page);
        /** An empty page after the last. */
        void addPage();
        /**
         * The card of {@code kind} at {@code index} (a Home page's index, or a window's row place)
         * becomes Home: what is written is {@link StartInPolicy#choose}'s.
         */
        void chooseHome(@NonNull PageOverviewPolicy.Kind kind, int index);
        /** Start in as it stands ({@link StartInPolicy#effective}), which the Home marker follows. */
        @NonNull String startIn();

        // The current session's row of windows, the display window among them.
        int rowCount();
        /** The display window's row place, {@link DisplaySlot#NONE} when it is not on the row. */
        int displaySlot();
        /** Something that stays the same for the window at row place {@code row} while it lives. */
        @NonNull Object windowKey(int row);
        /** Whether the terminal window at {@code row} is running a process. */
        boolean windowBusy(int row);
        void moveWindow(int fromRow, int toRow);
        /** The window at {@code row} goes now: a terminal's shells are killed, the display stops. */
        void removeWindow(int row);
        /** A new terminal window at row place {@code row}. */
        void addTerminal(int row);
        /** Whether this install runs a display at all. */
        boolean displaySupported();
        /** Whether a display is open; only one can be. */
        boolean displayOpen();
        /** The display window opens and stands at row place {@code row}. */
        void addDisplay(int row);
        /** The shell the window at {@code row} shows, for its card; null for the display. */
        @Nullable TerminalSession windowSession(int row);

        // The wall underneath.
        boolean onHome();
        int homePage();
        int rowCurrent();
        /** The wall moves to {@code position} at once, under the overview. */
        void preview(@NonNull WallRow.Position position);
        /** The wall lands on {@code position} as the overview closes into it. */
        void land(@NonNull WallRow.Position position);
        /** The launcher's root view, drawn into the live card. */
        @NonNull View liveRoot();
        /**
         * The page frame while the surface editor has posed it (scaled, moved), null when it
         * stands as laid out. The live card then draws this view alone, at its natural size.
         */
        @Nullable default View posedFrame() { return null; }
        /** Whether the surface editor is open: its chrome and sheet then stand beside the page. */
        default boolean editorOpen() { return false; }
        /** The height of the editor's folded sheet along the bottom, which the chips stand above. */
        default int coveredBottom() { return 0; }
        /** The launcher's own wallpaper, drawn full-screen behind the cards when it is showing. */
        @Nullable View backdrop();
        /** The display's surface, for its card's picture; null while there is none. */
        @Nullable SurfaceView displaySurface();
        boolean reducedMotion();
        /** The overview is gone; {@code byBack} when Back closed it. */
        void onClosed(boolean byBack);
    }

    private static final long ZOOM_MS = 320L;
    private static final long SNAP_MS = 260L;
    /** How long the focused card rests before its picture is taken, past the wall's own settle. */
    private static final long CAPTURE_DELAY_MS = 450L;
    /** At most this many card pictures are kept; the least recently drawn goes first. */
    private static final int MAX_SNAPSHOTS = 8;
    /** A card picture's size against the card's: enough to read, a third of the pixels. */
    private static final float SNAPSHOT_SCALE = 0.6f;
    /** How far from the focused card a terminal card's text is kept fresh. */
    private static final int TEXT_RANGE = 2;

    private enum Part { BODY, DELETE, HOME }

    /** One card of the strip as it stands. */
    private static final class Card {
        @NonNull final PageOverviewPolicy.Kind kind;
        @NonNull final Object key;
        /** The Home page's index, or the window's row place, as the host counts them. */
        final int index;
        /** The number its label carries: the page's, or the terminal's among the terminals. */
        final int number;
        /** Where it is drawn, in strip places; eases toward its place while a drag makes room. */
        float place;

        Card(@NonNull PageOverviewPolicy.Kind kind, @NonNull Object key, int index, int number) {
            this.kind = kind;
            this.key = key;
            this.index = index;
            this.number = number;
        }

        boolean isHome() {
            return kind == PageOverviewPolicy.Kind.HOME_PAGE;
        }
    }

    /** A card's picture: the whole card, its text over the wallpaper, or the display's surface. */
    private static final class Snapshot {
        static final int FULL = 0;
        static final int TEXT = 1;
        static final int SURFACE = 2;
        /** The page alone, without the card's buttons, drawn in software. */
        static final int PAGE = 3;

        @NonNull final Bitmap bitmap;
        final int type;
        /** What the card's buttons looked like when it was taken, which the picture holds too. */
        final int buttons;
        /** The focus generation it was made in, for text that refreshes as the card comes near. */
        final int generation;

        Snapshot(@NonNull Bitmap bitmap, int type, int buttons, int generation) {
            this.bitmap = bitmap;
            this.type = type;
            this.buttons = buttons;
            this.generation = generation;
        }
    }

    /** A removal held behind the undo snackbar. */
    private static final class Pending {
        @NonNull final PageOverviewPolicy.Kind kind;
        @NonNull final Object key;

        Pending(@NonNull PageOverviewPolicy.Kind kind, @NonNull Object key) {
            this.kind = kind;
            this.key = key;
        }
    }

    @NonNull private final Host host;
    @NonNull private final View root;
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper());
    @NonNull private final List<Card> cards = new ArrayList<>();
    /** One key per Home page, in their order, so a page keeps its picture as it moves. */
    @NonNull private final List<Object> homeKeys = new ArrayList<>();
    @NonNull private final LinkedHashMap<Object, Snapshot> snapshots =
        new LinkedHashMap<>(16, 0.75f, true);
    private int homeCount;
    /** The add chips' icon and label colour, on their dark glass. */
    private static final int PILL_INK = 0xFFE6E1E5;
    private int rowCount;

    /** The strip's scroll, in card places: the focused card is the nearest whole one. */
    private float focus;
    /** The card the wall underneath shows, drawn live; null while none is. */
    @Nullable private Object liveKey;
    /** Bumped each time the strip settles; terminal text older than this is drawn again. */
    private int generation;
    /** 0 = the focused card fills the screen, 1 = the overview at rest. */
    private float zoom;
    private boolean closing;
    private boolean closed;
    private float savedRootAlpha = 1f;

    @Nullable private Pending pending;
    /**
     * The display was closed here and is still stopping (its apps are asked to close first): its
     * card stays gone until it is.
     */
    private boolean displayGoing;
    /** How deep the overview is inside a change of its own, which it reads back itself. */
    private int mutating;
    @Nullable private Snackbar snackbar;

    @Nullable private ValueAnimator zoomAnimator;
    @Nullable private ValueAnimator scrollAnimator;

    // Geometry, from the last layout.
    private float cardWidth;
    private float cardHeight;
    private float cardTop;
    private float gap;
    private float seamGap;
    private float corner;
    private float buttonRadius;
    private float dotsY;
    private float rootWidth = 1f;
    private float rootHeight = 1f;
    @NonNull private final int[] rootOffset = new int[2];

    // Touch.
    private final int touchSlop;
    private final int longPressMs;
    @Nullable private VelocityTracker velocity;
    private float downX;
    private float downY;
    private float lastX;
    private float downFocus;
    private boolean scrolling;
    @Nullable private Card downCard;
    @Nullable private Part downPart;
    /** The card held by a long press and following the finger; null when none is. */
    @Nullable private Card dragCard;
    private int dragFrom;
    private int dragTarget = PageOverviewPolicy.NONE;
    private float dragX;
    private float dragGrab;
    private int edgeScroll;
    private final Runnable longPress = this::startDrag;
    private final Runnable edgeScroller = this::scrollAtEdge;
    private final Runnable capture = this::captureLiveCard;

    // Paint.
    @NonNull private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    @NonNull private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final Path clip = new Path();
    @NonNull private final RectF rect = new RectF();
    @NonNull private final RectF scratch = new RectF();
    @Nullable private final Drawable deleteIcon;
    @Nullable private final Drawable homeIcon;
    private final int errorColor;
    private final int onErrorColor;
    private final int primaryColor;
    private final int onPrimaryColor;
    private final int surfaceColor;
    @Nullable private TerminalRenderer textRenderer;
    private int textRendererSize;

    @NonNull private final LinearLayout pills;
    @NonNull private final TextView addPage;
    @NonNull private final TextView addTerminal;
    @NonNull private final TextView addDisplay;
    @NonNull private final Touch accessibility;

    public PageOverviewView(@NonNull Context context, @NonNull Host host) {
        super(context);
        this.host = host;
        this.root = host.liveRoot();
        setWillNotDraw(false);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription(context.getString(R.string.home_pages_editor_title));
        ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledTouchSlop();
        longPressMs = ViewConfiguration.getLongPressTimeout();

        errorColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorError, 0xFFB3261E);
        onErrorColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnError, Color.WHITE);
        primaryColor = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary,
            0xFFD0BCFF);
        onPrimaryColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnPrimary, 0xFF381E72);
        surfaceColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF2B2930);
        deleteIcon = icon(context, R.drawable.ic_symbol_close);
        homeIcon = icon(context, R.drawable.ic_symbol_home);

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(1));
        outlinePaint.setColor(0x80FFFFFF);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setShadowLayer(dp(3), 0f, dp(1), 0x99000000);

        pills = new LinearLayout(context);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER);
        addPage = pill(context, R.string.page_overview_add_page, R.drawable.ic_symbol_add,
            v -> onAddPage());
        addTerminal = pill(context, R.string.page_overview_add_terminal,
            R.drawable.ic_symbol_terminal, v -> onAddWindow(false));
        addDisplay = pill(context, R.string.page_overview_add_display,
            R.drawable.ic_symbol_desktop_windows, v -> onAddWindow(true));
        pills.addView(addPage);
        pills.addView(addTerminal);
        pills.addView(addDisplay);
        addView(pills, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        accessibility = new Touch(this);
        ViewCompat.setAccessibilityDelegate(this, accessibility);
        reload();
        focus = PageOverviewPolicy.openingFocus(host.onHome(), host.homePage(), host.rowCurrent(),
            homeCount, rowCount);
        if (focus < 0) focus = 0;
        for (Card card : cards) card.place = cards.indexOf(card);
        Card focused = focusedCard();
        liveKey = focused == null || focused.kind == PageOverviewPolicy.Kind.DISPLAY ? null
            : focused.key;
    }

    // ---- Opening and closing ---------------------------------------------------------------

    /** Puts the overview over {@code parent}, the activity's content, and zooms out into it. */
    public void open(@NonNull ViewGroup parent) {
        parent.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        requestFocus();
        takeDisplayPicture();
        // The display's surface stands outside the view tree and would show through the cards.
        Card focused = focusedCard();
        if (focused != null && focused.kind == PageOverviewPolicy.Kind.DISPLAY) {
            host.preview(positionOf(focused));
        }
        savedRootAlpha = root.getAlpha();
        // Hidden, not gone: it keeps drawing its children, which the live card shows.
        root.setAlpha(0f);
        // Neighbours have their text from the first frame; the live page its picture.
        refreshNearbyText();
        captureLive(true);
        animateZoom(1f, null);
    }

    /** Whether the overview is up and not on its way out. */
    public boolean isOpen() {
        return !closing && !closed;
    }

    /** Back: zooms into the focused card. */
    public void onBack() {
        Card card = focusedCard();
        closeInto(card == null ? -1 : cards.indexOf(card), true);
    }

    /** Drops the overview at once, landing on the focused card; any held removal is carried out. */
    public void closeImmediately() {
        if (closed) return;
        commitPending();
        Card card = focusedCard();
        finish(card == null ? null : positionOf(card), false);
    }

    /** The row or the pages changed under the overview: its cards follow. */
    public void refresh() {
        // A change the overview is making itself is read back once it is made.
        if (closed || dragCard != null || mutating > 0) return;
        reload();
        invalidate();
    }

    private void closeInto(int index, boolean byBack) {
        if (closing || closed) return;
        commitPending();
        if (index < 0 || index >= cards.size()) {
            finish(null, byBack);
            return;
        }
        closing = true;
        cancelTouch();
        Card card = cards.get(index);
        Runnable zoomIn = () -> {
            WallRow.Position position = positionOf(card);
            host.preview(position);
            if (card.kind != PageOverviewPolicy.Kind.DISPLAY) liveKey = card.key;
            animateZoom(0f, () -> finish(position, byBack));
        };
        if (Math.abs(focus - index) < 0.01f) zoomIn.run();
        else scrollTo(index, zoomIn);
    }

    private void finish(@Nullable WallRow.Position position, boolean byBack) {
        if (closed) return;
        closed = true;
        closing = true;
        handler.removeCallbacksAndMessages(null);
        removeCallbacks(longPress);
        removeCallbacks(edgeScroller);
        if (zoomAnimator != null) zoomAnimator.cancel();
        if (scrollAnimator != null) scrollAnimator.cancel();
        if (snackbar != null) {
            Snackbar bar = snackbar;
            snackbar = null;
            bar.dismiss();
        }
        if (position != null) host.land(position);
        root.setAlpha(savedRootAlpha);
        root.invalidate();
        ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) parent.removeView(this);
        // The pictures held pixels only for the overview; nothing recycles them while a display
        // list may still draw them, the collector takes them.
        snapshots.clear();
        if (textRenderer != null) {
            textRenderer.release();
            textRenderer = null;
        }
        host.onClosed(byBack);
    }

    private void animateZoom(float to, @Nullable Runnable then) {
        if (zoomAnimator != null) zoomAnimator.cancel();
        if (host.reducedMotion()) {
            zoom = to;
            invalidate();
            if (then != null) then.run();
            else onSettled();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(zoom, to);
        animator.setDuration(ZOOM_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            zoom = (float) a.getAnimatedValue();
            pills.setAlpha(zoom);
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (zoomAnimator == animation) zoomAnimator = null;
                if (cancelled) return;
                if (then != null) then.run();
                else onSettled();
            }
        });
        zoomAnimator = animator;
        pills.setAlpha(zoom);
        animator.start();
    }

    // ---- The model ------------------------------------------------------------------------

    /** Reads the pages and the row from the host again, the held removal's card left out. */
    private void reload() {
        int pages = Math.max(1, host.pageCount());
        while (homeKeys.size() < pages) homeKeys.add(new Object());
        while (homeKeys.size() > pages) homeKeys.remove(homeKeys.size() - 1);
        Map<Object, Float> places = new java.util.HashMap<>();
        for (Card card : cards) places.put(card.key, card.place);
        cards.clear();
        for (int page = 0; page < pages; page++) {
            Object key = homeKeys.get(page);
            if (isPending(key)) continue;
            cards.add(new Card(PageOverviewPolicy.Kind.HOME_PAGE, key, page, page + 1));
        }
        homeCount = cards.size();
        int rows = Math.max(0, host.rowCount());
        int slot = host.displaySlot();
        if (displayGoing && !host.displayOpen()) displayGoing = false;
        int terminals = 0;
        for (int row = 0; row < rows; row++) {
            boolean display = DisplaySlot.isDisplay(row, slot);
            if (!display) terminals++;
            Object key = host.windowKey(row);
            if (isPending(key) || (display && displayGoing)) continue;
            cards.add(new Card(display ? PageOverviewPolicy.Kind.DISPLAY
                : PageOverviewPolicy.Kind.TERMINAL, key, row, terminals));
        }
        rowCount = cards.size() - homeCount;
        for (int i = 0; i < cards.size(); i++) {
            Float was = places.get(cards.get(i).key);
            cards.get(i).place = was == null ? i : was;
        }
        focus = Math.max(0, Math.min(Math.max(0, cards.size() - 1), focus));
        // Pictures of what is gone go with it.
        Iterator<Object> keys = snapshots.keySet().iterator();
        while (keys.hasNext()) {
            Object key = keys.next();
            if (indexOfKey(key) < 0 && !isPending(key)) keys.remove();
        }
        updatePills();
        accessibility.invalidateRoot();
    }

    private boolean isPending(@NonNull Object key) {
        return pending != null && pending.key == key;
    }

    private int indexOfKey(@NonNull Object key) {
        for (int i = 0; i < cards.size(); i++) if (cards.get(i).key == key) return i;
        return -1;
    }

    @Nullable
    private Card focusedCard() {
        int index = Math.round(focus);
        return index >= 0 && index < cards.size() ? cards.get(index) : null;
    }

    /** The card the wall underneath shows; null while none is. */
    @Nullable
    private Card liveCard() {
        if (liveKey == null) return null;
        int index = indexOfKey(liveKey);
        return index < 0 ? null : cards.get(index);
    }

    @NonNull
    private WallRow.Position positionOf(@NonNull Card card) {
        return card.isHome() ? WallRow.homePage(card.index) : WallRow.window(card.index);
    }

    private void updatePills() {
        boolean supported = host.displaySupported();
        addDisplay.setVisibility(supported ? VISIBLE : GONE);
        boolean canAdd = PageOverviewPolicy.canAddDisplay(host.displayOpen());
        addDisplay.setEnabled(canAdd);
        addDisplay.setAlpha(canAdd ? 1f : 0.38f);
    }

    // ---- Actions ----------------------------------------------------------------------------

    private void onAddPage() {
        if (!isOpen()) return;
        commitPending();
        int at = PageOverviewPolicy.addPageIndex(Math.round(focus), homeCount);
        int before = host.pageCount();
        mutate(host::addPage);
        int after = host.pageCount();
        if (after <= before) {
            reload();
            invalidate();
            return;
        }
        while (homeKeys.size() < after) homeKeys.add(new Object());
        Object key = homeKeys.get(after - 1);
        if (at < after - 1) {
            mutate(() -> host.movePage(after - 1, at));
            homeKeys.remove(after - 1);
            homeKeys.add(Math.min(at, homeKeys.size()), key);
        }
        reload();
        int index = indexOfKey(key);
        if (index >= 0) {
            for (int i = index + 1; i < cards.size(); i++) cards.get(i).place = i - 1;
            cards.get(index).place = index;
            scrollTo(index, this::onSettled);
        }
        invalidate();
    }

    private void onAddWindow(boolean display) {
        if (!isOpen()) return;
        if (display && !PageOverviewPolicy.canAddDisplay(host.displayOpen())) return;
        commitPending();
        // Counted in the host's row places: a stopping display's hidden card still holds one.
        Card focused = focusedCard();
        int at = focused == null || focused.isHome() ? Math.round(focus)
            : PageOverviewPolicy.positionOfRow(focused.index, homeCount);
        int row = PageOverviewPolicy.addWindowRow(at, homeCount, host.rowCount());
        int before = host.rowCount();
        if (display) mutate(() -> host.addDisplay(row));
        else mutate(() -> host.addTerminal(row));
        reload();
        if (host.rowCount() > before) {
            int index = cards.size() - 1;
            for (int i = homeCount; i < cards.size(); i++) {
                if (cards.get(i).index == row) index = i;
            }
            for (int i = index + 1; i < cards.size(); i++) cards.get(i).place = i - 1;
            if (index >= 0) {
                cards.get(index).place = index;
                scrollTo(index, this::onSettled);
            }
        }
        // A display opens over the wall; the overview keeps the wall off its surface.
        if (display) {
            Card shown = liveCard();
            host.preview(shown != null ? positionOf(shown) : WallRow.homePage(host.homePage()));
        }
        invalidate();
    }

    private void onDelete(@NonNull Card target) {
        Object key = target.key;
        commitPending();
        reload();
        int index = indexOfKey(key);
        if (index < 0) return;
        Card card = cards.get(index);
        int contents = card.isHome() ? host.itemsOn(card.index) + host.floatsOn(card.index) : 0;
        boolean busy = card.kind == PageOverviewPolicy.Kind.TERMINAL && host.windowBusy(card.index);
        PageOverviewPolicy.Removal removal =
            PageOverviewPolicy.removal(card.kind, homeCount, contents, busy);
        if (removal == PageOverviewPolicy.Removal.REFUSED) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (removal == PageOverviewPolicy.Removal.AT_ONCE) {
            removeNow(card);
        } else {
            pending = new Pending(card.kind, card.key);
            reload();
            showUndo(card);
        }
        if (liveKey == key) liveKey = null;
        focus = Math.max(0, Math.min(PageOverviewPolicy.focusAfterRemoval(index, cards.size()),
            cards.size() - 1));
        invalidate();
        onSettled();
    }

    private void removeNow(@NonNull Card card) {
        if (card.isHome()) {
            int before = host.pageCount();
            mutate(() -> host.removePage(card.index));
            if (host.pageCount() < before && card.index < homeKeys.size()) {
                homeKeys.remove(card.index);
            }
        } else {
            if (card.kind == PageOverviewPolicy.Kind.DISPLAY) displayGoing = true;
            mutate(() -> host.removeWindow(card.index));
        }
        snapshots.remove(card.key);
        reload();
    }

    private void showUndo(@NonNull Card card) {
        String text;
        switch (card.kind) {
            case HOME_PAGE:
                text = getContext().getString(R.string.page_overview_page_removed, card.number);
                break;
            case TERMINAL:
                text = getContext().getString(R.string.page_overview_terminal_closed);
                break;
            default:
                text = getContext().getString(R.string.page_overview_display_closed);
                break;
        }
        Snackbar bar = Snackbar.make(this, text, Snackbar.LENGTH_LONG);
        bar.setAnchorView(pills);
        bar.setAction(R.string.page_overview_undo, v -> undo());
        bar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override public void onDismissed(Snackbar dismissed, int event) {
                if (dismissed != snackbar) return;
                snackbar = null;
                if (event == DISMISS_EVENT_ACTION) return;
                // The undo window has passed: the removal is carried out.
                commitPending();
                invalidate();
            }
        });
        snackbar = bar;
        bar.show();
    }

    private void undo() {
        if (pending == null) return;
        Object key = pending.key;
        pending = null;
        snackbar = null;
        reload();
        int index = indexOfKey(key);
        if (index >= 0) scrollTo(index, this::onSettled);
        invalidate();
    }

    private void mutate(@NonNull Runnable change) {
        mutating++;
        try {
            change.run();
        } finally {
            mutating--;
        }
    }

    /** The removal held for undo is carried out now. */
    private void commitPending() {
        Pending held = pending;
        if (held == null) return;
        pending = null;
        if (snackbar != null) {
            Snackbar bar = snackbar;
            snackbar = null;
            bar.dismiss();
        }
        if (held.kind == PageOverviewPolicy.Kind.HOME_PAGE) {
            int page = homeKeys.indexOf(held.key);
            if (page >= 0) {
                int before = host.pageCount();
                mutate(() -> host.removePage(page));
                if (host.pageCount() < before) homeKeys.remove(page);
            }
        } else {
            int rows = host.rowCount();
            for (int row = 0; row < rows; row++) {
                if (host.windowKey(row) == held.key) {
                    if (held.kind == PageOverviewPolicy.Kind.DISPLAY) displayGoing = true;
                    int at = row;
                    mutate(() -> host.removeWindow(at));
                    break;
                }
            }
        }
        snapshots.remove(held.key);
        if (!closed) reload();
    }

    private void onMakeHome(@NonNull Card card) {
        if (!showsHomeButton(card)) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        host.chooseHome(card.kind, card.index);
        accessibility.invalidateRoot();
        invalidate();
    }

    private void drop(@NonNull Card card, int from, int to) {
        commitPending();
        reload();
        int now = indexOfKey(card.key);
        if (now < 0 || to == PageOverviewPolicy.NONE || to == now) {
            invalidate();
            return;
        }
        Card moving = cards.get(now);
        if (moving.isHome()) {
            int toPage = cards.get(to).index;
            mutate(() -> host.movePage(moving.index, toPage));
            Object key = homeKeys.remove(moving.index);
            homeKeys.add(toPage, key);
        } else {
            int toRow = cards.get(to).index;
            mutate(() -> host.moveWindow(moving.index, toRow));
        }
        Map<Object, Float> places = new java.util.HashMap<>();
        for (Card c : cards) places.put(c.key, c.place);
        reload();
        // Each card goes on from where it was drawn, which is already its new place.
        for (Card c : cards) {
            Float was = places.get(c.key);
            if (was != null) c.place = was;
        }
        invalidate();
        onSettled();
    }

    // ---- Settling, the live card and pictures -----------------------------------------------

    /** The strip came to rest: the wall moves to the focused card, which becomes the live one. */
    private void onSettled() {
        if (!isOpen()) return;
        generation++;
        Card card = focusedCard();
        if (card != null && card.key != liveKey) {
            host.preview(positionOf(card));
            liveKey = card.kind == PageOverviewPolicy.Kind.DISPLAY ? null : card.key;
        }
        handler.removeCallbacks(capture);
        if (liveKey != null) handler.postDelayed(capture, CAPTURE_DELAY_MS);
        refreshNearbyText();
        invalidate();
    }

    /**
     * The terminal cards near the focused one, which the wall is not showing, are drawn again from
     * their screens' text, one per frame.
     */
    private void refreshNearbyText() {
        int at = Math.round(focus);
        for (int i = Math.max(0, at - TEXT_RANGE); i <= Math.min(cards.size() - 1, at + TEXT_RANGE); i++) {
            Card card = cards.get(i);
            if (card.kind != PageOverviewPolicy.Kind.TERMINAL || card.key == liveKey) continue;
            Snapshot shot = snapshots.get(card.key);
            if (shot != null && (shot.type == Snapshot.FULL || shot.generation == generation)) continue;
            Object key = card.key;
            int row = card.index;
            int made = generation;
            postOnAnimation(() -> renderText(key, row, made, false));
        }
    }

    private void renderText(@NonNull Object key, int row, int made, boolean live) {
        if (!isOpen() || made != generation) return;
        int index = indexOfKey(key);
        if (index < 0 || cards.get(index).index != row || (key == liveKey && !live)) return;
        TerminalSession session = host.windowSession(row);
        TerminalEmulator emulator = session == null ? null : session.getEmulator();
        if (emulator == null || cardWidth <= 0f) return;
        int width = Math.max(1, Math.round(cardWidth * SNAPSHOT_SCALE));
        int height = Math.max(1, Math.round(cardHeight * SNAPSHOT_SCALE));
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            int background = emulator.mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND];
            canvas.drawColor((background & 0x00FFFFFF) | 0xE0000000);
            // The window's text below the status band, sized so its columns fill the card.
            float top = topInset() * (height / rootHeight);
            int columns = Math.max(1, emulator.mColumns);
            int size = Math.max(4, Math.round(width / (columns * 0.6f)));
            if (textRenderer == null || textRendererSize != size) {
                if (textRenderer != null) textRenderer.release();
                textRenderer = new TerminalRenderer(size, Typeface.MONOSPACE, Typeface.MONOSPACE);
                textRendererSize = size;
            }
            float scale = Math.min(1f, width / Math.max(1f, textRenderer.getFontWidth() * columns));
            canvas.translate(0f, top);
            canvas.scale(scale, scale);
            textRenderer.render(emulator, canvas, 0, -1, -1, -1, -1, true, Color.TRANSPARENT, 0f);
            putSnapshot(key, new Snapshot(bitmap, Snapshot.TEXT, buttonsOf(cards.get(index)), made));
            invalidate();
        } catch (RuntimeException | OutOfMemoryError e) {
            // A card without its text is a card still; the overview never falls over for it.
        }
    }

    /** The live card's picture, for when the wall has moved on from it. */
    private void captureLiveCard() {
        if (!isOpen() || liveKey == null || zoom < 1f || dragCard != null || scrolling
            || snackbar != null || scrollAnimator != null) return;
        captureLive(false);
    }

    /**
     * The live card's picture now, which a swipe or the opening wants before the card leaves the
     * wall. {@code early} lets it run while zooming or scrolling: only a software draw of the
     * page does, a window copy needs the card at rest.
     */
    private void captureLive(boolean early) {
        if (!isOpen() || liveKey == null || dragCard != null || cardWidth <= 0f) return;
        Card card = focusedCard();
        if (card == null || card.key != liveKey) return;
        Object key = card.key;
        int buttons = buttonsOf(card);
        boolean covered = posedFrame() != null;
        OverviewGeometry.Capture how = OverviewGeometry.captureFor(covered,
            card.kind == PageOverviewPolicy.Kind.TERMINAL);
        if (how == OverviewGeometry.Capture.TEXT) {
            // Under the editor's sheet a terminal is drawn from its text, never from pixels.
            int row = card.index;
            int made = generation;
            postOnAnimation(() -> renderText(key, row, made, true));
            return;
        }
        if (how == OverviewGeometry.Capture.SUBTREE_DRAW) {
            View frame = posedFrame();
            if (frame == null) return;
            try {
                Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(cardWidth * SNAPSHOT_SCALE)),
                    Math.max(1, Math.round(cardHeight * SNAPSHOT_SCALE)), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(bitmap.getWidth() / rootWidth, bitmap.getHeight() / rootHeight);
                drawPageNatural(canvas, frame);
                // No buttons are in it, and the card draws its own over it.
                putSnapshot(key, new Snapshot(bitmap, Snapshot.PAGE, buttons, generation));
            } catch (RuntimeException | OutOfMemoryError e) {
                // No picture: the card shows its label until it is live again.
            }
            return;
        }
        if (early || zoom < 1f || scrolling || snackbar != null || scrollAnimator != null) return;
        Window window = windowOf(getContext());
        if (window == null) return;
        cardRect(cards.indexOf(card), rect);
        int[] at = new int[2];
        getLocationInWindow(at);
        Rect source = new Rect(Math.round(rect.left) + at[0], Math.round(rect.top) + at[1],
            Math.round(rect.right) + at[0], Math.round(rect.bottom) + at[1]);
        if (source.width() <= 0 || source.height() <= 0) return;
        try {
            Bitmap bitmap = Bitmap.createBitmap(Math.round(cardWidth * SNAPSHOT_SCALE),
                Math.round(cardHeight * SNAPSHOT_SCALE), Bitmap.Config.ARGB_8888);
            PixelCopy.request(window, source, bitmap, result -> {
                // A copy made while a sheet came up over the page is thrown away.
                if (result != PixelCopy.SUCCESS || !isOpen() || posedFrame() != null) return;
                putSnapshot(key, new Snapshot(bitmap, Snapshot.FULL, buttons, generation));
            }, handler);
        } catch (RuntimeException | OutOfMemoryError e) {
            // No picture: the card shows its label until it is live again.
        }
    }

    /** The display's picture, taken from its surface as the overview opens. */
    private void takeDisplayPicture() {
        SurfaceView surface = host.displaySurface();
        if (surface == null || surface.getWidth() <= 0 || surface.getHeight() <= 0
            || !surface.getHolder().getSurface().isValid()) return;
        Object key = null;
        for (Card card : cards) if (card.kind == PageOverviewPolicy.Kind.DISPLAY) key = card.key;
        if (key == null) return;
        Object displayKey = key;
        float scale = Math.min(1f, 720f / Math.max(surface.getWidth(), surface.getHeight()));
        try {
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(surface.getWidth() * scale)),
                Math.max(1, Math.round(surface.getHeight() * scale)), Bitmap.Config.ARGB_8888);
            PixelCopy.request(surface, bitmap, result -> {
                if (result != PixelCopy.SUCCESS || closed) return;
                putSnapshot(displayKey, new Snapshot(bitmap, Snapshot.SURFACE, 0, generation));
            }, handler);
        } catch (RuntimeException | OutOfMemoryError e) {
            // The display card shows its label instead.
        }
    }

    private void putSnapshot(@NonNull Object key, @NonNull Snapshot shot) {
        snapshots.put(key, shot);
        while (snapshots.size() > MAX_SNAPSHOTS) {
            Iterator<Map.Entry<Object, Snapshot>> eldest = snapshots.entrySet().iterator();
            eldest.next();
            eldest.remove();
        }
        invalidate();
    }

    /** What a card's buttons look like, which a picture of the whole card holds. */
    private int buttonsOf(@NonNull Card card) {
        int bits = PageOverviewPolicy.canDelete(card.kind, homeCount) ? 1 : 0;
        if (showsHomeButton(card)) {
            bits |= 2;
            if (isDefault(card)) bits |= 4;
        }
        return bits;
    }

    /** Whether {@code card} wears the Home marker: the page, or the window, Start in names. */
    private boolean isDefault(@NonNull Card card) {
        boolean defaultPage = card.isHome() && card.index
            == com.termux.app.launcher.home.HomePageEdits.clampDefault(host.defaultPage(),
                host.pageCount());
        return StartInPolicy.isHome(card.kind, defaultPage, isFirstTerminal(card), host.startIn());
    }

    private boolean showsHomeButton(@NonNull Card card) {
        return PageOverviewPolicy.showsHomeButton(card.kind, isFirstTerminal(card));
    }

    /** Whether {@code card} is the first terminal window's, the one a start in the Workspace opens. */
    private boolean isFirstTerminal(@NonNull Card card) {
        if (card.kind != PageOverviewPolicy.Kind.TERMINAL) return false;
        for (Card each : cards) {
            if (each.kind == PageOverviewPolicy.Kind.TERMINAL) return each == card;
        }
        return false;
    }

    @Nullable
    private static Window windowOf(@NonNull Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof android.app.Activity) return ((android.app.Activity) context).getWindow();
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    // ---- Geometry ---------------------------------------------------------------------------

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float width = right - left;
        float height = bottom - top;
        rootWidth = Math.max(1, root.getWidth());
        rootHeight = Math.max(1, root.getHeight());
        int[] mine = new int[2];
        int[] theirs = new int[2];
        getLocationInWindow(mine);
        root.getLocationInWindow(theirs);
        rootOffset[0] = theirs[0] - mine[0];
        rootOffset[1] = theirs[1] - mine[1];
        Insets insets = insets();
        pills.setPadding(Math.max(dp(16), insets.left), dp(8), Math.max(dp(16), insets.right),
            Math.max(dp(16), Math.max(insets.bottom, host.coveredBottom()) + dp(12)));
        gap = dp(16);
        seamGap = dp(28);
        corner = dp(24);
        buttonRadius = dp(20);
        float topSpace = insets.top + dp(36);
        float pillsSpace = pills.getHeight() > 0 ? pills.getHeight() : dp(84) + insets.bottom;
        float bottomSpace = pillsSpace + dp(64);
        float available = Math.max(dp(120), height - topSpace - bottomSpace);
        float scale = Math.min(available / rootHeight, 0.72f * width / rootWidth);
        cardWidth = rootWidth * scale;
        cardHeight = rootHeight * scale;
        cardTop = topSpace + (available - cardHeight) / 2f;
        dotsY = cardTop + cardHeight + buttonRadius + dp(20);
        labelPaint.setTextSize(dp(16));
    }

    private static final class Insets {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Insets(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    @NonNull
    private Insets insets() {
        WindowInsetsCompat compat = ViewCompat.getRootWindowInsets(this);
        if (compat == null) return new Insets(0, 0, 0, 0);
        androidx.core.graphics.Insets bars = compat.getInsets(WindowInsetsCompat.Type.systemBars()
            | WindowInsetsCompat.Type.displayCutout());
        return new Insets(bars.left, bars.top, bars.right, bars.bottom);
    }

    private float topInset() {
        return insets().top;
    }

    /** Where place {@code place} stands along the strip, the seam's extra gap counted. */
    private float placeOffset(float place) {
        float seam = Math.max(0f, Math.min(1f, place - (homeCount - 1)));
        if (homeCount <= 0 || rowCount <= 0) seam = 0f;
        return place * (cardWidth + gap) + seam * seamGap;
    }

    /** Card {@code index}'s rect at rest (zoom 1), drawn where its place stands. */
    private void cardRect(int index, @NonNull RectF out) {
        Card card = cards.get(index);
        float left = (getWidth() - cardWidth) / 2f + placeOffset(card.place) - placeOffset(focus);
        if (card == dragCard) left = dragX - dragGrab;
        out.set(left, cardTop, left + cardWidth, cardTop + cardHeight);
    }

    /**
     * The zoom's transform: at 0 the focused card covers the root view's rect, at 1 the strip
     * stands at rest. Scale {@code k} about pivot (px, py), applied to the whole strip.
     */
    private boolean zoomTransform(@NonNull float[] out) {
        if (zoom >= 1f || cards.isEmpty() || cardWidth <= 0f) return false;
        int index = Math.max(0, Math.min(cards.size() - 1, Math.round(focus)));
        cardRect(index, scratch);
        float full = rootWidth / cardWidth;
        float k = full + (1f - full) * zoom;
        // The pivot the card scales about so that at k = full it lands on the root's rect.
        float px = full == 1f ? scratch.left
            : (rootOffset[0] - full * scratch.left) / (1f - full);
        float py = full == 1f ? scratch.top
            : (rootOffset[1] - full * scratch.top) / (1f - full);
        out[0] = k;
        out[1] = px;
        out[2] = py;
        return true;
    }

    // ---- Drawing ----------------------------------------------------------------------------

    private final float[] zoomScratch = new float[3];

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        drawBackdrop(canvas);
        boolean easing = easePlaces();
        int save = canvas.save();
        if (zoomTransform(zoomScratch)) canvas.scale(zoomScratch[0], zoomScratch[0],
            zoomScratch[1], zoomScratch[2]);
        if (homeCount > 0 && rowCount > 0) drawSeam(canvas);
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            if (card == dragCard) continue;
            cardRect(i, rect);
            if (rect.right < -cardWidth || rect.left > getWidth() + cardWidth) continue;
            drawCard(canvas, card, rect);
        }
        if (dragCard != null) {
            int index = cards.indexOf(dragCard);
            if (index >= 0) {
                cardRect(index, rect);
                float lift = 1.04f;
                int lifted = canvas.save();
                canvas.scale(lift, lift, rect.centerX(), rect.centerY());
                drawCard(canvas, dragCard, rect);
                canvas.restoreToCount(lifted);
            }
        }
        canvas.restoreToCount(save);
        drawDots(canvas);
        if (easing) postInvalidateOnAnimation();
    }

    /** The launcher's own wallpaper, full-screen behind the cards, while it is showing one. */
    private void drawBackdrop(@NonNull Canvas canvas) {
        View backdrop = host.backdrop();
        if (backdrop == null || backdrop.getVisibility() != VISIBLE) return;
        int[] at = new int[2];
        int[] mine = new int[2];
        backdrop.getLocationInWindow(at);
        getLocationInWindow(mine);
        int save = canvas.save();
        canvas.translate(at[0] - mine[0], at[1] - mine[1]);
        backdrop.draw(canvas);
        canvas.restoreToCount(save);
    }

    private void drawSeam(@NonNull Canvas canvas) {
        float x = (getWidth() - cardWidth) / 2f + placeOffset(homeCount - 1) - placeOffset(focus)
            + cardWidth + (gap + seamGap) / 2f;
        fillPaint.setColor(0x66FFFFFF);
        fillPaint.setAlpha(Math.round(0x66 * zoom));
        canvas.drawRoundRect(x - dp(1), cardTop + cardHeight * 0.3f, x + dp(1),
            cardTop + cardHeight * 0.7f, dp(1), dp(1), fillPaint);
    }

    private void drawCard(@NonNull Canvas canvas, @NonNull Card card, @NonNull RectF at) {
        int save = canvas.save();
        clip.reset();
        float radius = corner * (zoom < 1f ? zoom : 1f);
        clip.addRoundRect(at, radius, radius, Path.Direction.CW);
        int clipped = canvas.save();
        canvas.clipPath(clip);
        drawContent(canvas, card, at);
        canvas.restoreToCount(clipped);
        outlinePaint.setAlpha(Math.round(0x80 * zoom));
        canvas.drawRoundRect(at, radius, radius, outlinePaint);
        if (zoom > 0f) drawButtons(canvas, card, at);
        canvas.restoreToCount(save);
    }

    private void drawContent(@NonNull Canvas canvas, @NonNull Card card, @NonNull RectF at) {
        if (card.key == liveKey) {
            // The launcher itself, scaled: its children draw their current display lists.
            int save = canvas.save();
            canvas.translate(at.left, at.top);
            canvas.scale(at.width() / rootWidth, at.height() / rootHeight);
            View frame = posedFrame();
            if (frame != null) drawPageNatural(canvas, frame);
            else root.draw(canvas);
            canvas.restoreToCount(save);
            return;
        }
        Snapshot shot = snapshots.get(card.key);
        if (shot != null && shot.type == Snapshot.FULL && shot.buttons == buttonsOf(card)) {
            canvas.drawBitmap(shot.bitmap, null, at, bitmapPaint);
            return;
        }
        if (shot != null && shot.type == Snapshot.PAGE) {
            canvas.drawBitmap(shot.bitmap, null, at, bitmapPaint);
            return;
        }
        drawScaledBackdrop(canvas, at);
        if (shot != null && shot.type == Snapshot.TEXT) {
            canvas.drawBitmap(shot.bitmap, null, at, bitmapPaint);
            return;
        }
        if (shot != null && shot.type == Snapshot.SURFACE) {
            float scale = Math.min(at.width() / shot.bitmap.getWidth(),
                at.height() / shot.bitmap.getHeight());
            float w = shot.bitmap.getWidth() * scale;
            float h = shot.bitmap.getHeight() * scale;
            scratch.set(at.centerX() - w / 2f, at.centerY() - h / 2f,
                at.centerX() + w / 2f, at.centerY() + h / 2f);
            canvas.drawBitmap(shot.bitmap, null, scratch, bitmapPaint);
            return;
        }
        if (card.kind == PageOverviewPolicy.Kind.TERMINAL) {
            fillPaint.setColor(0xCC101010);
            canvas.drawRect(at, fillPaint);
        }
        canvas.drawText(labelOf(card), at.centerX(), at.centerY(), labelPaint);
    }

    /** The page frame when the surface editor has posed it, else null. */
    @Nullable
    private View posedFrame() {
        View frame = host.posedFrame();
        if (frame == null || frame.getWidth() <= 0) return null;
        // With the editor open its label and sheet stand beside the page in the root, so the page
        // is drawn alone even while the frame stands unposed.
        if (host.editorOpen()) return frame;
        return OverviewGeometry.framePosed(frame.getScaleX(), frame.getScaleY(),
            frame.getTranslationX(), frame.getTranslationY()) ? frame : null;
    }

    /**
     * The wallpaper and then {@code frame} alone, in the root's coordinates at the frame's laid-out
     * size: a view's own draw leaves its scale and translation to the parent, so the editor's pose
     * is not in it, and only this subtree is drawn, so the editor's sheet and the overview are not.
     */
    private void drawPageNatural(@NonNull Canvas canvas, @NonNull View frame) {
        View backdrop = host.backdrop();
        // The backdrop is posed with the page, so it too is drawn at its laid-out place.
        if (backdrop != null && backdrop.getVisibility() == VISIBLE) drawNatural(canvas, backdrop);
        drawNatural(canvas, frame);
    }

    /** {@code view} at its laid-out position in the root, its own scale and translation left out. */
    private void drawNatural(@NonNull Canvas canvas, @NonNull View view) {
        float left = view.getLeft();
        float top = view.getTop();
        for (android.view.ViewParent p = view.getParent(); p instanceof View && p != root; p = p.getParent()) {
            left += ((View) p).getLeft() - ((View) p).getScrollX();
            top += ((View) p).getTop() - ((View) p).getScrollY();
        }
        int save = canvas.save();
        canvas.translate(left, top);
        canvas.clipRect(0, 0, view.getWidth(), view.getHeight());
        view.draw(canvas);
        canvas.restoreToCount(save);
    }

    /** The launcher's own wallpaper inside a card that has no picture of its own. */
    private void drawScaledBackdrop(@NonNull Canvas canvas, @NonNull RectF at) {
        View backdrop = host.backdrop();
        if (backdrop == null || backdrop.getVisibility() != VISIBLE) return;
        int[] there = new int[2];
        int[] rootAt = new int[2];
        backdrop.getLocationInWindow(there);
        root.getLocationInWindow(rootAt);
        int save = canvas.save();
        canvas.translate(at.left, at.top);
        canvas.scale(at.width() / rootWidth, at.height() / rootHeight);
        canvas.translate(there[0] - rootAt[0], there[1] - rootAt[1]);
        backdrop.draw(canvas);
        canvas.restoreToCount(save);
    }

    private void drawButtons(@NonNull Canvas canvas, @NonNull Card card, @NonNull RectF at) {
        int alpha = Math.round(255 * zoom);
        if (PageOverviewPolicy.canDelete(card.kind, homeCount)) {
            fillPaint.setColor(errorColor);
            fillPaint.setAlpha(alpha);
            canvas.drawCircle(at.centerX(), at.top, buttonRadius, fillPaint);
            drawIcon(canvas, deleteIcon, at.centerX(), at.top, onErrorColor, alpha);
        }
        if (showsHomeButton(card)) {
            boolean isDefault = isDefault(card);
            fillPaint.setColor(isDefault ? primaryColor : surfaceColor);
            fillPaint.setAlpha(alpha);
            canvas.drawCircle(at.centerX(), at.bottom, buttonRadius, fillPaint);
            if (!isDefault) {
                fillPaint.setStyle(Paint.Style.STROKE);
                fillPaint.setStrokeWidth(dp(2));
                fillPaint.setColor(primaryColor);
                fillPaint.setAlpha(alpha);
                canvas.drawCircle(at.centerX(), at.bottom, buttonRadius - dp(1), fillPaint);
                fillPaint.setStyle(Paint.Style.FILL);
            }
            drawIcon(canvas, homeIcon, at.centerX(), at.bottom,
                isDefault ? onPrimaryColor : primaryColor, alpha);
        }
    }

    private void drawIcon(@NonNull Canvas canvas, @Nullable Drawable icon, float cx, float cy,
                          int color, int alpha) {
        if (icon == null) return;
        int half = dp(11);
        icon.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half,
            Math.round(cy) + half);
        icon.setTint(color);
        icon.setAlpha(alpha);
        icon.draw(canvas);
    }

    private void drawDots(@NonNull Canvas canvas) {
        int count = cards.size();
        if (count < 2 || zoom <= 0f) return;
        float step = dp(12);
        float seam = homeCount > 0 && rowCount > 0 ? dp(10) : 0f;
        float total = (count - 1) * step + seam;
        float x = (getWidth() - total) / 2f;
        int at = Math.round(focus);
        for (int i = 0; i < count; i++) {
            if (i == homeCount && i > 0) x += seam;
            boolean focused = i == at;
            dotPaint.setColor(Color.WHITE);
            dotPaint.setAlpha(Math.round((focused ? 255 : 110) * zoom));
            canvas.drawCircle(x, dotsY, focused ? dp(4) : dp(3), dotPaint);
            x += step;
        }
    }

    /** Eases every card toward its place, the drag's room made; true while any is still moving. */
    private boolean easePlaces() {
        boolean moving = false;
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            float target = dragCard == null ? i
                : PageOverviewPolicy.shiftedPlace(i, dragFrom,
                    dragTarget == PageOverviewPolicy.NONE ? dragFrom : dragTarget);
            float d = target - card.place;
            if (Math.abs(d) < 0.005f) {
                card.place = target;
            } else {
                card.place += host.reducedMotion() ? d : d * 0.3f;
                moving = true;
            }
        }
        return moving;
    }

    @NonNull
    private String labelOf(@NonNull Card card) {
        switch (card.kind) {
            case HOME_PAGE:
                return getContext().getString(R.string.home_pages_editor_page, card.number);
            case TERMINAL:
                return getContext().getString(R.string.page_overview_terminal, card.number);
            default:
                return getContext().getString(R.string.page_overview_display);
        }
    }

    // ---- Touch ------------------------------------------------------------------------------

    @Nullable
    private Card cardAt(float x, float y, @Nullable Part[] part) {
        for (int i = cards.size() - 1; i >= 0; i--) {
            Card card = cards.get(i);
            cardRect(i, rect);
            float hit = Math.max(buttonRadius, dp(24));
            if (PageOverviewPolicy.canDelete(card.kind, homeCount)
                && dist(x, y, rect.centerX(), rect.top) <= hit) {
                if (part != null) part[0] = Part.DELETE;
                return card;
            }
            if (showsHomeButton(card)
                && dist(x, y, rect.centerX(), rect.bottom) <= hit) {
                if (part != null) part[0] = Part.HOME;
                return card;
            }
            if (rect.contains(x, y)) {
                if (part != null) part[0] = Part.BODY;
                return card;
            }
        }
        return null;
    }

    private static float dist(float x, float y, float cx, float cy) {
        return (float) Math.hypot(x - cx, y - cy);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!isOpen() || zoom < 1f) return true;
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (velocity == null) velocity = VelocityTracker.obtain();
                velocity.clear();
                velocity.addMovement(event);
                if (scrollAnimator != null) scrollAnimator.cancel();
                downX = x;
                downY = y;
                lastX = x;
                downFocus = focus;
                scrolling = false;
                Part[] part = new Part[1];
                downCard = cardAt(x, y, part);
                downPart = downCard == null ? null : part[0];
                if (downCard != null && downPart == Part.BODY) postDelayed(longPress, longPressMs);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(event);
                lastX = x;
                if (dragCard != null) {
                    dragX = x;
                    updateDragTarget();
                    edgeScroll = x < dp(48) ? -1 : x > getWidth() - dp(48) ? 1 : 0;
                    if (edgeScroll != 0) {
                        removeCallbacks(edgeScroller);
                        postOnAnimation(edgeScroller);
                    }
                    invalidate();
                    return true;
                }
                if (!scrolling && (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop)) {
                    removeCallbacks(longPress);
                    scrolling = Math.abs(x - downX) > touchSlop;
                    downPart = null;
                    if (scrolling) {
                        handler.removeCallbacks(capture);
                        // The page leaves the wall's view as the swipe goes on: keep its picture.
                        captureLive(true);
                    }
                }
                if (scrolling) {
                    float stride = cardWidth + gap;
                    focus = clampFocus(downFocus - (x - downX) / Math.max(1f, stride));
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPress);
                if (dragCard != null) {
                    endDrag(true);
                    return true;
                }
                if (scrolling) {
                    float vx = 0f;
                    if (velocity != null) {
                        velocity.addMovement(event);
                        velocity.computeCurrentVelocity(1000);
                        vx = velocity.getXVelocity();
                    }
                    scrolling = false;
                    float stride = Math.max(1f, cardWidth + gap);
                    float projected = focus - vx * 0.18f / stride;
                    int target = Math.round(clampFocus(projected));
                    int from = Math.round(downFocus);
                    target = Math.max(from - 3, Math.min(from + 3, target));
                    scrollTo(target, this::onSettled);
                    return true;
                }
                if (downCard != null && downPart != null) {
                    Card card = downCard;
                    switch (downPart) {
                        case DELETE: onDelete(card); break;
                        case HOME: onMakeHome(card); break;
                        default: {
                            int index = cards.indexOf(card);
                            // Only the centred card zooms in; another one is brought to the centre.
                            if (OverviewGeometry.tapZooms(index, focus)) closeInto(index, false);
                            else if (index >= 0) {
                                captureLive(true);
                                scrollTo(index, this::onSettled);
                            }
                            break;
                        }
                    }
                }
                downCard = null;
                downPart = null;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelTouch();
                return true;
            default:
                return true;
        }
    }

    private void cancelTouch() {
        removeCallbacks(longPress);
        removeCallbacks(edgeScroller);
        if (dragCard != null) endDrag(false);
        if (scrolling) {
            scrolling = false;
            scrollTo(Math.round(focus), this::onSettled);
        }
        downCard = null;
        downPart = null;
    }

    private float clampFocus(float value) {
        return Math.max(0f, Math.min(Math.max(0, cards.size() - 1), value));
    }

    private void scrollTo(int index, @Nullable Runnable then) {
        if (scrollAnimator != null) scrollAnimator.cancel();
        float target = clampFocus(index);
        if (host.reducedMotion() || Math.abs(target - focus) < 0.001f) {
            focus = target;
            invalidate();
            if (then != null) then.run();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(focus, target);
        animator.setDuration(SNAP_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(a -> {
            focus = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (scrollAnimator == animation) scrollAnimator = null;
                if (!cancelled && then != null) then.run();
            }
        });
        scrollAnimator = animator;
        animator.start();
    }

    private void startDrag() {
        Card card = downCard;
        if (card == null || scrolling || !isOpen()) return;
        // A held removal is carried out first, so the strip the drag reorders is the real one.
        commitPending();
        reload();
        int index = indexOfKey(card.key);
        if (index < 0) return;
        dragCard = cards.get(index);
        dragFrom = index;
        dragTarget = index;
        cardRect(index, rect);
        dragGrab = lastX - rect.left;
        dragX = lastX;
        handler.removeCallbacks(capture);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
    }

    /** The place under the dragged card's centre, if its own half takes it; else none. */
    private void updateDragTarget() {
        if (dragCard == null) return;
        float centre = dragX - dragGrab + cardWidth / 2f;
        float base = (getWidth() - cardWidth) / 2f - placeOffset(focus) + cardWidth / 2f;
        int over = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < cards.size(); i++) {
            float d = Math.abs(base + placeOffset(i) - centre);
            if (d < best) {
                best = d;
                over = i;
            }
        }
        dragTarget = PageOverviewPolicy.dropTarget(dragFrom, over, homeCount, rowCount);
    }

    private void scrollAtEdge() {
        if (dragCard == null || edgeScroll == 0) return;
        focus = clampFocus(focus + edgeScroll * 0.04f);
        updateDragTarget();
        invalidate();
        postOnAnimation(edgeScroller);
    }

    private void endDrag(boolean dropIt) {
        Card card = dragCard;
        int from = dragFrom;
        int to = dragTarget;
        edgeScroll = 0;
        removeCallbacks(edgeScroller);
        if (card == null) return;
        int index = cards.indexOf(card);
        // The card stays where it was let go and eases into its place from there.
        if (index >= 0) {
            cardRect(index, rect);
            float left0 = (getWidth() - cardWidth) / 2f - placeOffset(focus);
            card.place = placeFor(rect.left - left0);
        }
        dragCard = null;
        dragTarget = PageOverviewPolicy.NONE;
        if (dropIt && to != PageOverviewPolicy.NONE && to != from) {
            drop(card, from, to);
        } else {
            // Across the seam, or back where it came from: it springs back.
            invalidate();
            scrollTo(Math.round(focus), this::onSettled);
        }
    }

    /** The fractional place whose offset along the strip is {@code offset}. */
    private float placeFor(float offset) {
        float stride = Math.max(1f, cardWidth + gap);
        float place = offset / stride;
        // Past the seam the extra gap counts too.
        if (homeCount > 0 && rowCount > 0 && place > homeCount - 1) {
            place = (offset - seamGap) / stride;
            if (place < homeCount - 1) place = homeCount - 1;
        }
        return place;
    }

    // ---- Small pieces ---------------------------------------------------------------------------

    /**
     * An add chip: its icon over a label that wraps to a second line when its share of the row
     * is narrow (a 420 dpi phone, a large font), so the label is never cut short.
     */
    @NonNull
    private TextView pill(@NonNull Context context, int text, int icon,
                          @NonNull OnClickListener action) {
        TextView pill = new TextView(context);
        pill.setText(text);
        pill.setGravity(Gravity.CENTER);
        pill.setMaxLines(2);
        pill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pill.setTextColor(PILL_INK);
        pill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Drawable symbol = ContextCompat.getDrawable(context, icon);
        if (symbol != null) {
            symbol = symbol.mutate();
            symbol.setTint(PILL_INK);
            int side = dp(22);
            symbol.setBounds(0, 0, side, side);
            pill.setCompoundDrawablesRelative(null, symbol, null, null);
            pill.setCompoundDrawablePadding(dp(4));
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xE61D1B20);
        background.setCornerRadius(dp(20));
        pill.setBackground(background);
        int ripple = rippleOf(context);
        if (ripple != 0) pill.setForeground(ContextCompat.getDrawable(context, ripple));
        pill.setPadding(dp(8), dp(10), dp(8), dp(10));
        pill.setMinimumWidth(0);
        pill.setMinHeight(dp(64));
        pill.setClickable(true);
        pill.setFocusable(true);
        pill.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        pill.setLayoutParams(params);
        return pill;
    }

    private static int rippleOf(@NonNull Context context) {
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            ? value.resourceId : 0;
    }

    @Nullable
    private static Drawable icon(@NonNull Context context, int id) {
        Drawable drawable = ContextCompat.getDrawable(context, id);
        return drawable == null ? null : drawable.mutate();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---- Accessibility --------------------------------------------------------------------------

    /** Each card's body, its remove button and its home button as virtual views. */
    private final class Touch extends ExploreByTouchHelper {
        Touch(@NonNull View host) {
            super(host);
        }

        @Override protected int getVirtualViewAt(float x, float y) {
            Part[] part = new Part[1];
            Card card = cardAt(x, y, part);
            if (card == null) return INVALID_ID;
            return cards.indexOf(card) * 3 + part[0].ordinal();
        }

        @Override protected void getVisibleVirtualViews(List<Integer> ids) {
            for (int i = 0; i < cards.size(); i++) {
                cardRect(i, rect);
                if (rect.right < 0 || rect.left > getWidth()) continue;
                Card card = cards.get(i);
                ids.add(i * 3);
                if (PageOverviewPolicy.canDelete(card.kind, homeCount)) ids.add(i * 3 + 1);
                if (showsHomeButton(card)) ids.add(i * 3 + 2);
            }
        }

        @Override protected void onPopulateNodeForVirtualView(int id,
                @NonNull AccessibilityNodeInfoCompat node) {
            int index = id / 3;
            if (index < 0 || index >= cards.size()) {
                node.setContentDescription("");
                node.setBoundsInParent(new Rect());
                return;
            }
            Card card = cards.get(index);
            Part part = Part.values()[id % 3];
            cardRect(index, rect);
            Rect bounds = new Rect();
            float r = Math.max(buttonRadius, dp(24));
            switch (part) {
                case DELETE:
                    bounds.set(Math.round(rect.centerX() - r), Math.round(rect.top - r),
                        Math.round(rect.centerX() + r), Math.round(rect.top + r));
                    node.setContentDescription(removeLabel(card));
                    break;
                case HOME:
                    bounds.set(Math.round(rect.centerX() - r), Math.round(rect.bottom - r),
                        Math.round(rect.centerX() + r), Math.round(rect.bottom + r));
                    node.setContentDescription(homeButtonLabel(card));
                    node.setCheckable(true);
                    node.setChecked(isDefault(card));
                    break;
                default:
                    rect.round(bounds);
                    String label = !isDefault(card) ? labelOf(card)
                        : card.isHome()
                        ? getContext().getString(R.string.page_overview_default_page, card.number)
                        : getContext().getString(R.string.page_overview_start_here, labelOf(card));
                    node.setContentDescription(getContext().getString(R.string.page_overview_open,
                        label));
                    break;
            }
            if (bounds.isEmpty()) bounds.set(0, 0, 1, 1);
            node.setBoundsInParent(bounds);
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
        }

        @Override protected boolean onPerformActionForVirtualView(int id, int action,
                @Nullable Bundle arguments) {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false;
            int index = id / 3;
            if (index < 0 || index >= cards.size() || !isOpen()) return false;
            Card card = cards.get(index);
            switch (Part.values()[id % 3]) {
                case DELETE: onDelete(card); break;
                case HOME: onMakeHome(card); break;
                default: closeInto(index, false); break;
            }
            return true;
        }
    }

    @NonNull
    private String homeButtonLabel(@NonNull Card card) {
        switch (card.kind) {
            case HOME_PAGE:
                return getContext().getString(R.string.home_pages_editor_make_default, card.number);
            case TERMINAL:
                return getContext().getString(R.string.page_overview_start_in_terminal);
            default:
                return getContext().getString(R.string.page_overview_start_on_display);
        }
    }

    @NonNull
    private String removeLabel(@NonNull Card card) {
        switch (card.kind) {
            case HOME_PAGE:
                return getContext().getString(R.string.home_pages_editor_remove, card.number);
            case TERMINAL:
                return getContext().getString(R.string.page_overview_close_terminal, card.number);
            default:
                return getContext().getString(R.string.page_overview_close_display);
        }
    }

    @Override protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        return accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }
}
