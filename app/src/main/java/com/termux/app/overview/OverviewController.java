package com.termux.app.overview;

import android.app.Activity;
import android.graphics.Rect;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.OverlayRegistry;
import com.termux.app.launcher.popup.AnchoredMenu;
import com.termux.app.launcher.popup.AppMenuCard;
import com.termux.app.launcher.popup.MenuRowFactory;
import com.termux.app.page.PageKind;
import com.termux.app.wall.OverviewEdge;
import com.termux.app.wall.OverviewEdgePolicy;
import com.termux.app.wall.PageOverviewPolicy;
import com.termux.app.wall.pageswitch.PageBarModel;
import com.termux.app.wall.pageswitch.PageSwitchTargets;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Overview as a screen on the launcher screens host: opens {@link OverviewView} in the sheets
 * window layer with the pages and chrome layers hidden under it ({@link LayersUnderOverview}), and
 * is the {@link OverlayRegistry.Overlay} the activity registers, so Back, a home press and the
 * lifecycle drops reach it. The wall never moves while it is up, so closing it returns to the page
 * it was opened from; a card's tap closes it first and then goes.
 *
 * <p>A hold on a card lifts it and opens its menu ({@link OverviewMenuPolicy}). A removal of
 * something that holds anything stands the card aside behind an Undo bar and is carried out when
 * the bar's time passes, another card is acted on, or Overview closes; an empty page goes at once.
 * At most one removal waits, so the strip's positions stay the model's but for that one card.
 *
 * <p>Overview stands before the wall's first page (#356): the wall's swipe past the first page
 * brings it in under the finger through {@link #wallEdge}, and a swipe the other way on Overview's
 * free area hands the finger back to the wall ({@link WallReturn#begin}), which lands on the first
 * page. Every other way in opens it in place.
 */
public final class OverviewController implements OverlayRegistry.Overlay {

    /** How long a removal waits for Undo. */
    static final long UNDO_MS = 5000L;

    /** The launcher's side: what the wall is, how it travels, and the look. */
    public interface Host {
        @NonNull PageBarModel wall();
        @NonNull PageSwitchTargets.Navigator navigator();
        @NonNull OverviewLook look();
        /**
         * The facts card {@code card} is drawn from, read now from the page's layout; null for a
         * card that is a plain face (a Linux window or Display page). Text is charged to
         * {@code budget}. Runs on the main thread when Overview opens: it must only read what is
         * already in memory.
         */
        @Nullable CardFacts facts(@NonNull OverviewModel.Card card, @NonNull TextBudget budget);
        /**
         * The Assistant card's facts, delivered to {@code onFacts} on the main thread once they
         * are read off the main thread; never called when the chat cannot be read.
         */
        void assistantFacts(@NonNull java.util.function.Consumer<CardFacts> onFacts);

        /** The wall page id of the home page, -1 for none. */
        int homePageId();
        /** How many Home pages the wall has. */
        int homePageCount();
        /**
         * What {@code card} holds, which decides whether removing it asks for Undo: a Home page's
         * items and floats, a window's panes, 0 for an empty page.
         */
        int contentsOf(@NonNull OverviewModel.Card card);
        /** {@code card}'s page becomes the home page. */
        void setHome(@NonNull OverviewModel.Card card);
        /** Asks for a new name for {@code card}; {@code renamed} runs once it is written. */
        void rename(@NonNull OverviewModel.Card card, @NonNull Runnable renamed);
        /** Removes a Home page, closes a window or removes the Assistant page, for good. */
        void remove(@NonNull OverviewModel.Card card);
        /**
         * Wall page {@code pageId} moves to stand just before wall page {@code refPageId}, or just
         * after it when {@code after}; the order is saved.
         */
        void moveWallPage(int pageId, int refPageId, boolean after);
        /** The Workspace's window at row place {@code from} moves to place {@code to}. */
        void moveWindow(int from, int to);
        /**
         * What runs in front in {@code card}'s window where the terminal already knows it (a
         * cached reading, never a new one); null for none.
         */
        @Nullable String commandOf(@NonNull OverviewModel.Card card);
    }

    /** The keyboard for Go to's field: its own seam, set by {@link #setTextInput}. */
    public interface TextInput {
        /**
         * Go to's {@code field} took focus: the keyboard comes up for it the way the drawer's
         * search brings it (the system IME, after {@code onSystemImeRequested()}; the in-app
         * keyboard yields).
         */
        void beginTextInput(@NonNull android.widget.EditText field);
        /** The field is done with the keyboard: it goes, and the in-app keyboard is released. */
        void endTextInput(@NonNull android.widget.EditText field);
    }

    /**
     * The launcher's side of the + card: its own seam, set by {@link #setPageAdder}, so
     * {@link Host} stays under its method cap. With none, the + button adds nothing.
     */
    public interface PageAdder {
        /** Whether the wall shows Home pages; false under Use as Terminal only. */
        boolean homePagesShown();
        /**
         * A blank Home page goes on the wall after wall page {@code afterPageId} (the end for
         * {@link OverviewAddPolicy#END}); true when one was added.
         */
        boolean addHomePage(int afterPageId);
        /**
         * A new Workspace window, as the sessions manager's + made one; with no Terminal page on
         * the wall, that page goes after {@code afterPageId} first. True when added.
         */
        boolean addWorkspace(int afterPageId);
        /** The Assistant page goes after {@code afterPageId}; true when it was added. */
        boolean addAssistant(int afterPageId);
    }

    /** A removal waiting behind the Undo bar. */
    private static final class Pending {
        final int index;
        @NonNull final OverviewModel.Card card;

        Pending(int index, @NonNull OverviewModel.Card card) {
            this.index = index;
            this.card = card;
        }
    }

    @NonNull private final Activity mActivity;
    @NonNull private final Host mHost;
    @Nullable private TextInput mTextInput;
    @Nullable private PageAdder mPageAdder;
    @Nullable private Runnable mBackReturn;
    @NonNull private final LayersUnderOverview mUnder = new LayersUnderOverview();
    @Nullable private OverviewView mView;
    /** The cards as the strip shows them (a pending removal's card still in it, hidden). */
    @Nullable private OverviewModel mModel;
    @Nullable private Pending mPending;
    @Nullable private AnchoredMenu mMenu;
    private int mLifted = -1;
    /** Go to's field holds the keyboard (begun, not yet ended). */
    private boolean mTyping;
    /** The strip's cards as the drop policy reads them; built when a drag starts. */
    @NonNull private final List<OverviewDropPolicy.Stop> mStops = new ArrayList<>();
    /** The Assistant card's facts once read, kept so a rebuilt strip draws them again. */
    @Nullable private CardFacts mAssistantFacts;
    @NonNull private final Runnable mCommit = () -> commitPending(true);
    /**
     * The swipe between Overview and the first page under way: {@link OverviewEdgePolicy#PULL},
     * {@link OverviewEdgePolicy#RETURN}, or 0 for none.
     */
    private int mSwipe;
    /** The pages and chrome layers, read once per swipe rather than per frame. */
    @Nullable private View mPagesLayer;
    @Nullable private View mChromeLayer;
    @NonNull private final OverviewEdge mEdge = new OverviewEdge() {
        @Override public boolean available() {
            return mView == null && mSwipe == 0;
        }

        @Override public boolean beginPull(int widthPx) {
            if (!available()) return false;
            readLayers();
            OverviewView view = show();
            if (view == null) return false;
            mSwipe = OverviewEdgePolicy.PULL;
            pose(0f, widthPx);
            return true;
        }

        @Override public void pose(float revealPx, int widthPx) {
            OverviewView view = mView;
            if (view == null || mSwipe == 0) return;
            view.setTranslationX(OverviewEdgePolicy.overviewTranslationX(revealPx, widthPx));
            mUnder.showUnder(mPagesLayer, mChromeLayer,
                OverviewEdgePolicy.chromeShown(revealPx, widthPx));
        }

        @Override public void end(boolean open) {
            mSwipe = 0;
            OverviewView view = mView;
            if (view == null) {
                // Closed under the finger (a home press): the layers only come back.
                mUnder.restore();
                return;
            }
            if (!open) {
                close();
                return;
            }
            view.setTranslationX(0f);
            mUnder.hide(mPagesLayer, mChromeLayer);
            view.requestFocus();
        }
    };

    public OverviewController(@NonNull Activity activity, @NonNull Host host) {
        mActivity = activity;
        mHost = host;
    }

    /** Opens Overview, or closes it when it is up: the session chip's tap. */
    public void toggle() {
        if (mView != null) close();
        else open();
    }

    /** Opens Overview in place, with no slide. */
    public void open() {
        if (mView != null) return;
        readLayers();
        OverviewView view = show();
        if (view == null) return;
        mUnder.hide(mPagesLayer, mChromeLayer);
        view.requestFocus();
    }

    /**
     * Overview before the wall's first page, for the wall to pull in and land; set on the wall
     * once ({@code PaneWallLayout#setOverviewEdge}).
     */
    @NonNull
    public OverviewEdge wallEdge() {
        return mEdge;
    }

    private void readLayers() {
        mPagesLayer = WindowLayers.host(mActivity, WindowLayer.PAGES);
        mChromeLayer = WindowLayers.host(mActivity, WindowLayer.CHROME);
    }

    /** Builds the screen and puts it on the sheets layer; the layers under it are the caller's. */
    @Nullable
    private OverviewView show() {
        OverviewView view = new OverviewView(mActivity, mHost.look(), this::choose, this::close);
        view.setOnHold(this::onHold);
        view.setOnAddToggle(this::toggleAddCard);
        view.setGoToHost(new OverviewView.GoToHost() {
            @Override public void queryChanged(@NonNull String text) { showMatches(text); }
            @Override public void go(int card) { goTo(card); }
            @Override public void fieldFocus(@NonNull android.widget.EditText field, boolean focused) {
                if (focused && !mTyping) {
                    mTyping = true;
                    if (mTextInput != null) mTextInput.beginTextInput(field);
                } else if (!focused && mTyping) {
                    mTyping = false;
                    if (mTextInput != null) mTextInput.endTextInput(field);
                }
            }
        });
        view.setDragHost(new OverviewView.DragHost() {
            @Override public boolean start(int index) { return startDrag(index); }
            @Override public int landing(int from, int gap) {
                return OverviewDropPolicy.landing(mStops, from, gap);
            }
            @Override public void drop(int from, int gap) { dropCard(from, gap); }
            @Override public void released(int index) {
                // A hold with no menu (a Linux window) has nothing to keep the card lifted for.
                if (mMenu == null) lower();
            }
        });
        view.setReturnSwipe(mReturn);
        mModel = OverviewModel.of(mHost.wall(), mHost.homePageId());
        view.apply(mModel, factsFor(mModel));
        // A sheet (spec, "Look"): over the pages and the chrome, so the status row, the keyboard
        // and the Home pill never draw over it.
        boolean added = WindowLayers.add(mActivity, WindowLayer.SHEETS, view,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (!added) {
            mModel = null;
            return null;
        }
        mView = view;
        // The conversation sits in a database: read off the main thread, drawn when it arrives
        // if Overview is still the same screen.
        if (hasAssistant(mModel)) {
            mHost.assistantFacts(f -> {
                if (mView != view) return;
                mAssistantFacts = f;
                view.setAssistantFacts(f);
            });
        }
        return view;
    }

    // ---- Swipe back to the first page (#356) --------------------------------------------------

    /**
     * The wall's side of the swipe from Overview back to the first page (#356). Its own seam,
     * set by {@link #setWallReturn}; with none, the swipe is Overview's own.
     */
    public interface WallReturn {
        /**
         * The swipe starts: the wall lands on its first page and stands it under Overview, ready
         * to come in ({@code PaneWallController#beginOverviewReturn}). False refuses, and the
         * touch is Overview's own.
         */
        boolean begin();
        /** The finger has gone {@code dxPx} since the return was claimed (negative, leftward). */
        void drag(float dxPx);
        /** The finger let go at {@code velocityPxPerSec}, {@code leadPx} past the claim's down. */
        void end(float velocityPxPerSec, float leadPx);
        /** The return's touch stream was cancelled. */
        void cancel();
    }

    @Nullable private WallReturn mWallReturn;

    /** Who brings the keyboard for Go to's field; with none, the field takes no typing. */
    public void setTextInput(@Nullable TextInput textInput) {
        mTextInput = textInput;
    }

    /** Who adds the pages of the + card; with none, the button adds nothing. */
    public void setPageAdder(@Nullable PageAdder pageAdder) {
        mPageAdder = pageAdder;
    }

    /** Who takes the finger when Overview is swiped back to the first page; null for no one. */
    public void setWallReturn(@Nullable WallReturn wallReturn) {
        mWallReturn = wallReturn;
    }

    /** Overview's free area swiped the other way: the wall takes the finger to the first page. */
    @NonNull private final OverviewView.ReturnSwipe mReturn = new OverviewView.ReturnSwipe() {
        @Override public boolean begin() {
            if (mView == null || mSwipe != 0 || mMenu != null) return false;
            mSwipe = OverviewEdgePolicy.RETURN;
            if (mWallReturn != null && mWallReturn.begin()) return true;
            mSwipe = 0;
            return false;
        }

        @Override public void drag(float dxPx) {
            if (mWallReturn != null) mWallReturn.drag(dxPx);
        }

        @Override public void end(float velocityPxPerSec, float leadPx) {
            if (mWallReturn != null) mWallReturn.end(velocityPxPerSec, leadPx);
        }

        @Override public void cancel() {
            if (mWallReturn != null) mWallReturn.cancel();
        }
    };

    @NonNull
    private List<CardFacts> factsFor(@NonNull OverviewModel model) {
        TextBudget budget = TextBudget.standard();
        List<CardFacts> facts = new ArrayList<>(model.cards().size());
        for (OverviewModel.Card card : model.cards()) facts.add(mHost.facts(card, budget));
        return facts;
    }

    private static boolean hasAssistant(@NonNull OverviewModel model) {
        for (OverviewModel.Card card : model.cards()) {
            if (card.kind == PageKind.ASSISTANT) return true;
        }
        return false;
    }

    /** The strip is read again from the wall, left where it is scrolled. */
    private void rebuild() {
        OverviewView view = mView;
        if (view == null) return;
        mModel = OverviewModel.of(mHost.wall(), mHost.homePageId());
        view.apply(mModel, factsFor(mModel), true);
        if (mAssistantFacts != null) view.setAssistantFacts(mAssistantFacts);
        // A rename or a move while the list is up: the matches are read again.
        if (view.hasQuery()) showMatches(view.query());
    }

    // ---- Go to -------------------------------------------------------------------------------

    /** The list of matches for {@code text} replaces the strip; blank brings the strip back. */
    private void showMatches(@NonNull String text) {
        OverviewView view = mView;
        OverviewModel model = mModel;
        if (view == null || model == null) return;
        if (text.trim().isEmpty()) {
            view.showMatches(null);
            return;
        }
        List<OverviewModel.Card> cards = model.cards();
        List<OverviewGoTo.Entry> entries = new ArrayList<>(cards.size());
        for (int i = 0; i < cards.size(); i++) {
            // A card standing aside for Undo is not a place to go to.
            if (mPending != null && mPending.index == i) continue;
            OverviewModel.Card card = cards.get(i);
            String command = card.kind == PageKind.TERMINAL ? mHost.commandOf(card) : null;
            entries.add(new OverviewGoTo.Entry(i, card.kind, card.label, command));
        }
        view.showMatches(OverviewGoTo.match(entries, text));
    }

    private void goTo(int index) {
        OverviewModel model = mModel;
        if (model == null || index < 0 || index >= model.cards().size()) return;
        choose(model.cards().get(index));
    }

    // ---- The + card --------------------------------------------------------------------------

    private void toggleAddCard() {
        OverviewView view = mView;
        PageAdder adder = mPageAdder;
        if (view == null || adder == null) return;
        if (view.addCardOpen()) {
            view.hideAddCard();
            return;
        }
        dismissMenu();
        OverviewModel model = mModel;
        boolean hasAssistant = model != null && hasAssistant(model);
        view.showAddCard(OverviewAddPolicy.rows(adder.homePagesShown(), hasAssistant),
            this::addPage, view::hideAddCard);
    }

    /** A row of the + card: the page goes right after the card in focus, and the strip scrolls to it. */
    private void addPage(@NonNull OverviewAddPolicy.Row row) {
        OverviewView view = mView;
        PageAdder adder = mPageAdder;
        if (view == null || adder == null || mModel == null) return;
        view.hideAddCard();
        // A removal waiting goes first, so the strip the new page lands in is the wall's own.
        if (mPending != null) commitPending(true);
        OverviewModel model = mModel;
        if (model == null) return;
        long[] before = keysOf(model);
        int origin = model.originIndex();
        int after = OverviewAddPolicy.afterPageId(
            origin >= 0 && origin < model.cards().size()
                ? model.cards().get(origin).target.pageId : -1);
        boolean added;
        switch (row) {
            case HOME_PAGE: added = adder.addHomePage(after); break;
            case ASSISTANT: added = adder.addAssistant(after); break;
            case WORKSPACE:
            default: added = adder.addWorkspace(after); break;
        }
        if (!added) return;
        rebuild();
        OverviewModel now = mModel;
        if (now == null) return;
        int at = OverviewAddPolicy.addedIndex(before, keysOf(now));
        if (at >= 0) view.scrollToCard(at);
    }

    @NonNull
    private static long[] keysOf(@NonNull OverviewModel model) {
        List<OverviewModel.Card> cards = model.cards();
        long[] keys = new long[cards.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = OverviewAddPolicy.key(cards.get(i).target.pageId, cards.get(i).target.index);
        }
        return keys;
    }

    // ---- Tap ---------------------------------------------------------------------------------

    private void choose(@NonNull OverviewModel.Card card) {
        OverviewModel.Card target = card;
        Pending pending = mPending;
        if (pending != null && mModel != null) {
            // A removal still waiting goes first, and the tapped card is found again where the
            // wall now holds it: its window or page place moved up if the removed one stood before.
            int at = OverviewMenuPolicy.indexAfterRemoval(mModel.cards().indexOf(card),
                pending.index);
            commitPending(false);
            OverviewModel now = OverviewModel.of(mHost.wall(), mHost.homePageId());
            if (at >= 0 && at < now.cards().size()) target = now.cards().get(at);
        }
        close();
        PageSwitchTargets.carryOut(target.target, mHost.navigator());
    }

    // ---- Hold: the menu ----------------------------------------------------------------------

    private void onHold(int index, @NonNull View cardView) {
        OverviewView view = mView;
        OverviewModel model = mModel;
        if (view == null || model == null || index < 0 || index >= model.cards().size()) return;
        OverviewModel.Card card = model.cards().get(index);
        // A minimised window's card only brings it back: no menu, no drag.
        if (card.target.action == com.termux.app.wall.pageswitch.PageBarModel.Action.RESTORE) return;
        List<OverviewMenuPolicy.Row> rows = OverviewMenuPolicy.rows(card.kind, card.home,
            mHost.homePageCount(), card.target.index >= 0);
        fillStops(model);
        // A card with no menu is still held when it can be dragged.
        if (rows.isEmpty() && !OverviewDropPolicy.canDrag(mStops, index)) return;
        dismissMenu();
        com.termux.app.haptics.Haptics.tick(view, HapticFeedbackConstants.LONG_PRESS);
        view.setCardLifted(index, true);
        mLifted = index;
        if (rows.isEmpty()) return;

        List<AppMenuCard.Item> items = new ArrayList<>(rows.size() + 1);
        items.add(AppMenuCard.Item.header(card.label));
        for (OverviewMenuPolicy.Row row : rows) {
            items.add(new AppMenuCard.Item(mActivity.getString(labelOf(row)), glyphOf(row), () -> {
                dismissMenu();
                act(index, row);
            }));
        }
        com.termux.app.launcher.popup.AnchoredMenuTheme theme = menuTheme();
        AnchoredMenu shown = new AnchoredMenu(view, theme);
        AppMenuCard built = AppMenuCard.build(mActivity, new MenuRowFactory(mActivity, theme),
            Collections.singletonList(items));
        Rect bounds = new Rect();
        if (!view.cardBoundsOnScreen(index, bounds)) {
            view.setCardLifted(index, false);
            mLifted = -1;
            return;
        }
        // Against the whole card: the menu takes the side that has room inside the screen, below
        // it on a tall phone where the strip stands in the top half.
        mMenu = shown;
        shown.showAppMenu(built, bounds, () -> {
            if (mMenu == shown) {
                mMenu = null;
                lower();
            }
        }, card.label);
    }

    // ---- Drag --------------------------------------------------------------------------------

    private void fillStops(@NonNull OverviewModel model) {
        mStops.clear();
        for (OverviewModel.Card card : model.cards()) {
            mStops.add(new OverviewDropPolicy.Stop(card.kind, card.target.pageId,
                card.target.index));
        }
    }

    /** A held card starts to move: its menu goes; refused while a removal waits behind Undo. */
    private boolean startDrag(int index) {
        OverviewModel model = mModel;
        if (mView == null || model == null || mPending != null) return false;
        fillStops(model);
        if (!OverviewDropPolicy.canDrag(mStops, index)) return false;
        dismissMenu();
        return true;
    }

    /** The card held from {@code from} was let go at {@code gap}: the wall takes the new order. */
    private void dropCard(int from, int gap) {
        OverviewDropPolicy.Drop drop = OverviewDropPolicy.drop(mStops, from, gap);
        switch (drop.type) {
            case WINDOW:
                mHost.moveWindow(drop.from, drop.to);
                break;
            case PAGE:
                mHost.moveWallPage(drop.pageId, drop.refPageId, drop.after);
                break;
            default:
                return;
        }
        rebuild();
    }

    private static int labelOf(@NonNull OverviewMenuPolicy.Row row) {
        switch (row) {
            case SET_HOME: return R.string.page_overview_menu_set_home;
            case RENAME: return R.string.page_overview_menu_rename;
            case CLOSE: return R.string.overview_menu_close;
            case REMOVE:
            default: return R.string.page_overview_menu_remove;
        }
    }

    private static int glyphOf(@NonNull OverviewMenuPolicy.Row row) {
        switch (row) {
            case SET_HOME: return R.drawable.ic_symbol_home;
            case RENAME: return R.drawable.ic_rl_edit;
            case CLOSE: return R.drawable.ic_rl_close;
            case REMOVE:
            default: return R.drawable.ic_rl_delete;
        }
    }

    @NonNull
    private com.termux.app.launcher.popup.AnchoredMenuTheme menuTheme() {
        final int ink = com.termux.app.material.M3.onSurface(mActivity);
        return new com.termux.app.launcher.popup.AnchoredMenuTheme() {
            @Override public int textColor() { return ink; }
            @Override public int selectedTextColor() { return ink; }
            @Override public int opacityPercent() { return 100; }
            @Override public boolean blurEnabled() { return false; }
            @Override public int blurRadiusDp() { return 0; }
        };
    }

    private void dismissMenu() {
        AnchoredMenu shown = mMenu;
        mMenu = null;
        if (shown != null) shown.dismiss();
        lower();
    }

    private void lower() {
        OverviewView view = mView;
        if (view != null && mLifted >= 0) view.setCardLifted(mLifted, false);
        mLifted = -1;
    }

    /** A menu row on the card at {@code index} of the strip as it stood when it was held. */
    private void act(int index, @NonNull OverviewMenuPolicy.Row row) {
        OverviewView view = mView;
        if (view == null || mModel == null) return;
        int at = OverviewMenuPolicy.indexAfterRemoval(index,
            mPending == null ? -1 : mPending.index);
        // Acting on another card carries a waiting removal out first; the strip is read again.
        if (mPending != null) commitPending(true);
        OverviewModel model = mModel;
        if (model == null || at < 0 || at >= model.cards().size()) return;
        OverviewModel.Card card = model.cards().get(at);
        switch (row) {
            case SET_HOME:
                mHost.setHome(card);
                rebuild();
                break;
            case RENAME:
                mHost.rename(card, () -> {
                    if (mView != null) rebuild();
                });
                break;
            case REMOVE:
            case CLOSE:
            default:
                remove(at, card);
                break;
        }
    }

    // ---- Removal and Undo --------------------------------------------------------------------

    private void remove(int index, @NonNull OverviewModel.Card card) {
        OverviewView view = mView;
        if (view == null) return;
        PageOverviewPolicy.Removal removal = OverviewMenuPolicy.removal(card.kind,
            mHost.homePageCount(), mHost.contentsOf(card));
        if (removal == PageOverviewPolicy.Removal.REFUSED) return;
        if (removal == PageOverviewPolicy.Removal.AT_ONCE) {
            mHost.remove(card);
            rebuild();
            return;
        }
        mPending = new Pending(index, card);
        view.setCardHidden(index, true);
        view.showUndo(undoText(card), this::undo);
        view.removeCallbacks(mCommit);
        view.postDelayed(mCommit, UNDO_MS);
    }

    @NonNull
    private String undoText(@NonNull OverviewModel.Card card) {
        switch (card.kind) {
            case HOME:
                return mActivity.getString(R.string.page_overview_page_removed, card.number);
            case ASSISTANT:
                return mActivity.getString(R.string.page_overview_assistant_removed);
            default:
                return mActivity.getString(R.string.overview_window_closed, card.label);
        }
    }

    /** The card comes back in its place; nothing was removed yet. */
    private void undo() {
        OverviewView view = mView;
        Pending pending = mPending;
        if (view == null || pending == null) return;
        mPending = null;
        view.removeCallbacks(mCommit);
        view.hideUndo();
        view.setCardHidden(pending.index, false);
    }

    /** The waiting removal is carried out now; {@code rebuild} reads the strip again after. */
    private void commitPending(boolean rebuild) {
        OverviewView view = mView;
        Pending pending = mPending;
        if (pending == null) return;
        mPending = null;
        if (view != null) {
            view.removeCallbacks(mCommit);
            view.hideUndo();
        }
        mHost.remove(pending.card);
        if (rebuild) rebuild();
    }

    /** Removes the screen and shows the layers under it again; the wall is where it was. */
    public void close() {
        OverviewView view = mView;
        if (view == null) return;
        dismissMenu();
        if (mTyping) {
            mTyping = false;
            if (mTextInput != null) mTextInput.endTextInput(view.field());
        }
        // Closing Overview commits a removal still waiting.
        commitPending(false);
        mView = null;
        mModel = null;
        mAssistantFacts = null;
        mBackReturn = null;
        mUnder.restore();
        view.release();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    @Override public boolean onBack() {
        if (mView == null) return false;
        // A menu closes first; the next Back closes Overview.
        if (mMenu != null) {
            dismissMenu();
            return true;
        }
        // The + card closes next, adding nothing.
        if (mView.addCardOpen()) {
            mView.hideAddCard();
            return true;
        }
        // Text in Go to clears first, and the strip comes back.
        if (mView.hasQuery()) {
            mView.clearQuery();
            return true;
        }
        Runnable back = mBackReturn;
        close();
        if (back != null) back.run();
        return true;
    }

    /**
     * Where Back out of this Overview goes once it has closed (Settings' door); dropped when
     * Overview closes any other way. Its own setter, so {@link Host} stays under its cap.
     */
    public void setBackReturn(@Nullable Runnable backReturn) {
        mBackReturn = backReturn;
    }

    @Override public boolean isOpen() {
        return mView != null;
    }

    @Override public void closeImmediately(@NonNull OverlayRegistry.CloseReason reason) {
        close();
    }

    @Override public void exitViews(@NonNull List<View> out) {
        // Faded by the Home exit and closed at its end, with no animation of its own.
        if (mView != null) out.add(mView);
    }

    @Override public void exitStarted(long durationMs) {
        // The fade reveals the page the press lands on, not the bare ground.
        mUnder.restore();
    }
}
