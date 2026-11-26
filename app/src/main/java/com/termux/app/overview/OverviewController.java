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
     * The sessions of each Workspace's place (#357): its own seam, set by {@link #setSessionRows};
     * with none, Overview shows no session rows. {@code place} is a Workspace card's row place
     * ({@code card.target.index}), {@code slot} a session's position in that place's row.
     */
    public interface SessionRows {
        /** The sessions waiting in {@code place}, in row order; empty for none. */
        @NonNull List<OverviewModel.Waiting> waiting(int place);
        /** The facts a waiting session's card is drawn from, read as {@link Host#facts} reads them. */
        @Nullable CardFacts facts(int place, int slot, @NonNull TextBudget budget);
        /** The session at {@code slot} comes to the front of {@code place}; both keep running. */
        void bringToFront(int place, int slot);
        /** A new session joins {@code place} and stands in front of it. */
        void newSession(int place);
    }

    /**
     * Saved sessions (#359): its own seam, set by {@link #setSavedSessions}; with none, Overview
     * shows no Saved row and its menus offer no Save. {@code place} is a Workspace card's row
     * place ({@code card.target.index}); {@code slot} -1 for the session in front of the place,
     * else a waiting session's slot.
     */
    public interface SavedSessions {
        /**
         * The saved sessions, in order, delivered to {@code onRead} on the main thread once the
         * files are read off it; each as its name and the layout its card is drawn from.
         */
        void read(@NonNull java.util.function.Consumer<List<SavedCard>> onRead);
        /** Asks for a name and saves that session; {@code saved} runs once it is written. */
        void save(int place, int slot, @NonNull Runnable saved);
        /**
         * {@code name} opens as a new Workspace page right after the Workspace at row place
         * {@code afterPlace} (-1: after the one on screen), in front. False when it did not open.
         */
        boolean open(@NonNull String name, int afterPlace);
        /** {@code name} opens as a session of the Workspace at {@code place}, in front. */
        boolean openIn(@NonNull String name, int place);
        /** Asks for a new name for {@code name}; {@code renamed} runs once it is written. */
        void rename(@NonNull String name, @NonNull Runnable renamed);
        /** Deletes {@code name} for good. */
        void delete(@NonNull String name);
    }

    @Nullable private SavedSessions mSavedSessions;
    /** The Saved row as last read; kept so a rebuilt strip draws it again. */
    @NonNull private List<SavedCard> mSaved = new ArrayList<>();
    /** A saved session's deletion waiting behind the Undo bar. */
    @Nullable private SavedCard mPendingSaved;
    private int mPendingSavedIndex = -1;

    /** Who saves, opens, renames and deletes saved sessions; null for no Saved row. */
    public void setSavedSessions(@Nullable SavedSessions saved) {
        mSavedSessions = saved;
    }

    @Nullable private SessionRows mSessionRows;

    /** Who keeps the sessions of each Workspace's place; null for no session rows. */
    public void setSessionRows(@Nullable SessionRows rows) {
        mSessionRows = rows;
    }

    @Nullable
    private OverviewModel.Sessions sessionsSource() {
        SessionRows rows = mSessionRows;
        return rows == null ? null : rows::waiting;
    }

    @NonNull
    private OverviewModel model() {
        return OverviewModel.of(mHost.wall(), mHost.homePageId(), sessionsSource());
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
            @Override public void go(@NonNull OverviewGoTo.Entry entry) { goTo(entry); }
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
        view.setOnSession(this::chooseSession);
        view.setOnSessionHold(this::onSessionHold);
        view.setOnSaved(this::openSaved);
        view.setOnSavedHold(this::onSavedHold);
        mModel = model();
        TextBudget budget = TextBudget.standard();
        view.apply(mModel, factsFor(mModel, budget), sessionFactsFor(mModel, budget), false);
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
        readSaved(view);
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
    private List<CardFacts> factsFor(@NonNull OverviewModel model, @NonNull TextBudget budget) {
        List<CardFacts> facts = new ArrayList<>(model.cards().size());
        for (OverviewModel.Card card : model.cards()) facts.add(mHost.facts(card, budget));
        return facts;
    }

    /** Each session row's card facts, row by row, charged to the wall's own text budget. */
    @NonNull
    private List<List<CardFacts>> sessionFactsFor(@NonNull OverviewModel model,
                                                  @NonNull TextBudget budget) {
        List<OverviewModel.Row> rows = model.rows();
        if (rows.isEmpty() || mSessionRows == null) return Collections.emptyList();
        List<List<CardFacts>> out = new ArrayList<>(rows.size());
        for (OverviewModel.Row row : rows) {
            int place = model.cards().get(row.card).target.index;
            List<CardFacts> facts = new ArrayList<>(row.sessions.size());
            for (OverviewModel.Session s : row.sessions) {
                facts.add(mSessionRows.facts(place, s.slot, budget));
            }
            out.add(facts);
        }
        return out;
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
        mModel = model();
        TextBudget budget = TextBudget.standard();
        view.apply(mModel, factsFor(mModel, budget), sessionFactsFor(mModel, budget), true);
        view.setSaved(mSaved);
        if (mPendingSaved != null) view.setSavedHidden(mPendingSavedIndex, true);
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
        // The sessions waiting in each Workspace's place, by name (#357), after the wall.
        for (OverviewModel.Row row : model.rows()) {
            if (mPending != null && mPending.index == row.card) continue;
            for (OverviewModel.Session s : row.sessions) {
                entries.add(new OverviewGoTo.Entry(row.card, PageKind.TERMINAL,
                    OverviewView.sessionLabel(mActivity, s), null, s.slot));
            }
        }
        // The saved sessions, by name (#359), after the sessions.
        for (int i = 0; i < mSaved.size(); i++) {
            if (mPendingSaved != null && mPendingSavedIndex == i) continue;
            entries.add(OverviewGoTo.Entry.saved(i, mSaved.get(i).name));
        }
        view.showMatches(OverviewGoTo.match(entries, text));
    }

    private void goTo(@NonNull OverviewGoTo.Entry entry) {
        if (entry.saved >= 0) {
            if (entry.saved < mSaved.size()) openSaved(mSaved.get(entry.saved));
            return;
        }
        OverviewModel model = mModel;
        if (model == null || entry.card < 0 || entry.card >= model.cards().size()) return;
        if (entry.session >= 0) {
            OverviewModel.Row row = model.rowOf(entry.card);
            if (row != null && entry.session < row.sessions.size()) {
                chooseSession(row.sessions.get(entry.session));
            }
            return;
        }
        choose(model.cards().get(entry.card));
    }

    // ---- Session rows (#357) -----------------------------------------------------------------

    /**
     * A session card was tapped: it comes to the front of its Workspace's place, the one in front
     * takes its card in the row, and Overview closes into that Workspace.
     */
    private void chooseSession(@NonNull OverviewModel.Session session) {
        OverviewModel model = mModel;
        SessionRows rows = mSessionRows;
        if (model == null || rows == null) return;
        int index = session.card;
        Pending pending = mPending;
        if (pending != null) {
            // The Workspace standing aside for Undo has no sessions to go to.
            if (pending.index == index) return;
            index = OverviewMenuPolicy.indexAfterRemoval(index, pending.index);
            commitPending(false);
            model = model();
        }
        if (index < 0 || index >= model.cards().size()) return;
        OverviewModel.Card card = model.cards().get(index);
        rows.bringToFront(card.target.index, session.slot);
        close();
        PageSwitchTargets.carryOut(card.target, mHost.navigator());
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
            case NEW_SESSION: return R.string.overview_menu_new_session;
            case SAVE: return R.string.overview_menu_save;
            case SET_HOME: return R.string.page_overview_menu_set_home;
            case RENAME: return R.string.page_overview_menu_rename;
            case CLOSE: return R.string.overview_menu_close;
            case REMOVE:
            default: return R.string.page_overview_menu_remove;
        }
    }

    private static int glyphOf(@NonNull OverviewMenuPolicy.Row row) {
        switch (row) {
            case NEW_SESSION: return R.drawable.ic_rl_add;
            case SAVE: return R.drawable.ic_rl_save;
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
        if (mPending != null || mPendingSaved != null) commitPending(true);
        OverviewModel model = mModel;
        if (model == null || at < 0 || at >= model.cards().size()) return;
        OverviewModel.Card card = model.cards().get(at);
        switch (row) {
            case NEW_SESSION:
                // The new session stands in front of the Workspace and Overview closes into it;
                // the one it replaced waits in the Workspace's row (#357).
                if (mSessionRows == null) break;
                mSessionRows.newSession(card.target.index);
                close();
                PageSwitchTargets.carryOut(card.target, mHost.navigator());
                break;
            case SAVE:
                saveSession(card.target.index, -1);
                break;
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

    // ---- Saved sessions (#359) ---------------------------------------------------------------

    /** The saved files are read off the main thread; the row is drawn when they arrive. */
    private void readSaved(@NonNull OverviewView view) {
        SavedSessions saved = mSavedSessions;
        if (saved == null) return;
        saved.read(cards -> {
            if (mView != view) return;
            mSaved = new ArrayList<>(cards);
            // A deletion waiting stays aside across a re-read.
            if (mPendingSaved != null) {
                mPendingSavedIndex = indexOfSaved(mPendingSaved.name);
                if (mPendingSavedIndex < 0) mPendingSaved = null;
            }
            view.setSaved(mSaved);
            if (mPendingSaved != null) view.setSavedHidden(mPendingSavedIndex, true);
            if (view.hasQuery()) showMatches(view.query());
        });
    }

    private int indexOfSaved(@NonNull String name) {
        for (int i = 0; i < mSaved.size(); i++) if (mSaved.get(i).name.equals(name)) return i;
        return -1;
    }

    /** Asks for a name for the session at {@code slot} of {@code place} and saves it. */
    private void saveSession(int place, int slot) {
        SavedSessions saved = mSavedSessions;
        if (saved == null || place < 0) return;
        saved.save(place, slot, () -> {
            if (mView != null) readSaved(mView);
        });
    }

    /** The place of the Workspace card Overview was opened from; -1 when it was another page. */
    private int focusPlace() {
        OverviewModel model = mModel;
        if (model == null) return -1;
        int origin = model.originIndex();
        if (origin < 0 || origin >= model.cards().size()) return -1;
        OverviewModel.Card card = model.cards().get(origin);
        return card.kind == PageKind.TERMINAL ? card.target.index : -1;
    }

    /**
     * A saved card was tapped: it opens as a new Workspace page after the card in focus, where
     * the + card puts new pages, and Overview closes into that page.
     */
    private void openSaved(@NonNull SavedCard card) {
        SavedSessions saved = mSavedSessions;
        if (saved == null || mModel == null) return;
        if (mPendingSaved != null && mPendingSaved.name.equals(card.name)) return;
        if (mPending != null || mPendingSaved != null) commitPending(true);
        OverviewModel model = mModel;
        if (model == null) return;
        long[] before = keysOf(model);
        boolean opened = saved.open(card.name, focusPlace());
        if (!opened) {
            com.termux.app.notice.AppNotice.show(mActivity, R.string.overview_save_failed, true);
            return;
        }
        OverviewModel now = model();
        int at = OverviewAddPolicy.addedIndex(before, keysOf(now));
        close();
        if (at >= 0 && at < now.cards().size()) {
            PageSwitchTargets.carryOut(now.cards().get(at).target, mHost.navigator());
        }
    }

    /** A saved card was held: Open in the Workspace in focus, Rename, Delete. */
    private void onSavedHold(@NonNull SavedCard card, @NonNull View cardView) {
        OverviewView view = mView;
        OverviewModel model = mModel;
        if (view == null || model == null) return;
        int place = focusPlace();
        OverviewModel.Card focus = null;
        if (place >= 0) {
            int origin = model.originIndex();
            if (origin >= 0 && origin < model.cards().size()) focus = model.cards().get(origin);
        }
        List<OverviewMenuPolicy.SavedRow> rows = OverviewMenuPolicy.savedRows(focus != null);
        dismissMenu();
        com.termux.app.haptics.Haptics.tick(view, HapticFeedbackConstants.LONG_PRESS);
        List<AppMenuCard.Item> items = new ArrayList<>(rows.size() + 1);
        items.add(AppMenuCard.Item.header(card.name));
        final OverviewModel.Card inFocus = focus;
        for (OverviewMenuPolicy.SavedRow row : rows) {
            int label;
            int glyph;
            String text;
            switch (row) {
                case OPEN_IN_FOCUS:
                    text = mActivity.getString(R.string.overview_saved_open_in,
                        inFocus == null ? "" : inFocus.label);
                    glyph = R.drawable.ic_rl_load;
                    break;
                case RENAME:
                    text = mActivity.getString(R.string.overview_saved_rename);
                    glyph = R.drawable.ic_rl_edit;
                    break;
                case DELETE:
                default:
                    text = mActivity.getString(R.string.overview_saved_delete);
                    glyph = R.drawable.ic_rl_delete;
                    break;
            }
            items.add(new AppMenuCard.Item(text, glyph, () -> {
                dismissMenu();
                actSaved(card, row, inFocus);
            }));
        }
        showMenuAt(view, cardView, items, card.name);
    }

    private void actSaved(@NonNull SavedCard card, @NonNull OverviewMenuPolicy.SavedRow row,
                          @Nullable OverviewModel.Card focus) {
        SavedSessions saved = mSavedSessions;
        OverviewView view = mView;
        if (saved == null || view == null) return;
        if (mPending != null || mPendingSaved != null) commitPending(true);
        switch (row) {
            case OPEN_IN_FOCUS:
                if (focus == null) return;
                if (!saved.openIn(card.name, focus.target.index)) {
                    com.termux.app.notice.AppNotice.show(mActivity,
                        R.string.overview_save_failed, true);
                    return;
                }
                close();
                PageSwitchTargets.carryOut(focus.target, mHost.navigator());
                break;
            case RENAME:
                saved.rename(card.name, () -> {
                    if (mView != null) readSaved(mView);
                });
                break;
            case DELETE:
            default:
                deleteSaved(card);
                break;
        }
    }

    /** The saved card stands aside behind the Undo bar; its file goes when the bar's time passes. */
    private void deleteSaved(@NonNull SavedCard card) {
        OverviewView view = mView;
        int index = indexOfSaved(card.name);
        if (view == null || index < 0) return;
        mPendingSaved = card;
        mPendingSavedIndex = index;
        view.setSavedHidden(index, true);
        view.showUndo(mActivity.getString(R.string.overview_saved_deleted, card.name), this::undo);
        view.removeCallbacks(mCommit);
        view.postDelayed(mCommit, UNDO_MS);
    }

    /** A waiting saved deletion is carried out now. */
    private void commitSavedDeletion() {
        SavedCard card = mPendingSaved;
        if (card == null) return;
        OverviewView view = mView;
        int index = mPendingSavedIndex;
        mPendingSaved = null;
        mPendingSavedIndex = -1;
        if (view != null) {
            view.removeCallbacks(mCommit);
            view.hideUndo();
        }
        SavedSessions saved = mSavedSessions;
        if (saved != null) saved.delete(card.name);
        if (index >= 0 && index < mSaved.size() && mSaved.get(index) == card) mSaved.remove(index);
        if (view != null) view.setSaved(mSaved);
    }

    /** A session card was held: Save. */
    private void onSessionHold(@NonNull OverviewModel.Session session, @NonNull View cardView) {
        OverviewView view = mView;
        OverviewModel model = mModel;
        if (view == null || model == null || mSavedSessions == null) return;
        if (session.card < 0 || session.card >= model.cards().size()) return;
        final int place = model.cards().get(session.card).target.index;
        dismissMenu();
        com.termux.app.haptics.Haptics.tick(view, HapticFeedbackConstants.LONG_PRESS);
        List<AppMenuCard.Item> items = new ArrayList<>(2);
        String label = OverviewView.sessionLabel(mActivity, session);
        items.add(AppMenuCard.Item.header(label));
        items.add(new AppMenuCard.Item(mActivity.getString(R.string.overview_menu_save),
            R.drawable.ic_rl_save, () -> {
                dismissMenu();
                saveSession(place, session.slot);
            }));
        showMenuAt(view, cardView, items, label);
    }

    /** A menu over a card of a row (not a strip card): no lift, closed like the others. */
    private void showMenuAt(@NonNull OverviewView view, @NonNull View cardView,
                            @NonNull List<AppMenuCard.Item> items, @NonNull String label) {
        com.termux.app.launcher.popup.AnchoredMenuTheme theme = menuTheme();
        AnchoredMenu shown = new AnchoredMenu(view, theme);
        AppMenuCard built = AppMenuCard.build(mActivity, new MenuRowFactory(mActivity, theme),
            Collections.singletonList(items));
        Rect bounds = new Rect();
        if (!OverviewView.boundsOnScreen(cardView, bounds)) return;
        mMenu = shown;
        shown.showAppMenu(built, bounds, () -> {
            if (mMenu == shown) mMenu = null;
        }, label);
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
        if (view != null && pending == null && mPendingSaved != null) {
            int index = mPendingSavedIndex;
            mPendingSaved = null;
            mPendingSavedIndex = -1;
            view.removeCallbacks(mCommit);
            view.hideUndo();
            view.setSavedHidden(index, false);
            return;
        }
        if (view == null || pending == null) return;
        mPending = null;
        view.removeCallbacks(mCommit);
        view.hideUndo();
        view.setCardHidden(pending.index, false);
    }

    /** The waiting removal is carried out now; {@code rebuild} reads the strip again after. */
    private void commitPending(boolean rebuild) {
        commitSavedDeletion();
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
        mSaved = new ArrayList<>();
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
