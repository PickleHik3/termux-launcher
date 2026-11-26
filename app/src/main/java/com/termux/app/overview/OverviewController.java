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
    @NonNull private final LayersUnderOverview mUnder = new LayersUnderOverview();
    @Nullable private OverviewView mView;
    /** The cards as the strip shows them (a pending removal's card still in it, hidden). */
    @Nullable private OverviewModel mModel;
    @Nullable private Pending mPending;
    @Nullable private AnchoredMenu mMenu;
    private int mLifted = -1;
    /** The Assistant card's facts once read, kept so a rebuilt strip draws them again. */
    @Nullable private CardFacts mAssistantFacts;
    @NonNull private final Runnable mCommit = () -> commitPending(true);

    public OverviewController(@NonNull Activity activity, @NonNull Host host) {
        mActivity = activity;
        mHost = host;
    }

    /** Opens Overview, or closes it when it is up: the session chip's tap. */
    public void toggle() {
        if (mView != null) close();
        else open();
    }

    public void open() {
        if (mView != null) return;
        OverviewView view = new OverviewView(mActivity, mHost.look(), this::choose, this::close);
        view.setOnHold(this::onHold);
        mModel = OverviewModel.of(mHost.wall(), mHost.homePageId());
        view.apply(mModel, factsFor(mModel));
        // A sheet (spec, "Look"): over the pages and the chrome, so the status row, the keyboard
        // and the Home pill never draw over it.
        boolean added = WindowLayers.add(mActivity, WindowLayer.SHEETS, view,
            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (!added) {
            mModel = null;
            return;
        }
        mView = view;
        mUnder.hide(WindowLayers.host(mActivity, WindowLayer.PAGES),
            WindowLayers.host(mActivity, WindowLayer.CHROME));
        view.requestFocus();
        // The conversation sits in a database: read off the main thread, drawn when it arrives
        // if Overview is still the same screen.
        if (hasAssistant(mModel)) {
            mHost.assistantFacts(f -> {
                if (mView != view) return;
                mAssistantFacts = f;
                view.setAssistantFacts(f);
            });
        }
    }

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
        if (rows.isEmpty()) return;
        dismissMenu();
        com.termux.app.haptics.Haptics.tick(view, HapticFeedbackConstants.LONG_PRESS);
        view.setCardLifted(index, true);
        mLifted = index;

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
        // Closing Overview commits a removal still waiting.
        commitPending(false);
        mView = null;
        mModel = null;
        mAssistantFacts = null;
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
        close();
        return true;
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
