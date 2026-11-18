package com.termux.app.x11;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;
import com.termux.app.page.PageKind;
import com.termux.app.wall.DisplayPagePolicy;
import com.termux.app.wall.PageStillPolicy;
import com.termux.app.wall.PaneWallLayout;
import com.termux.app.wall.WallDisplay;
import com.termux.app.wall.WallPages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * Which Display page the one display view stands on, and what the frame shows there: live, the
 * page's kept picture, or its card (a Linux app's name and icon, read off the main thread). The
 * wall tells it where the view is bound and where it lands ({@link WallDisplay}); the landing's
 * own work (starting the display, raising or running the page's app) is the landing's ({@link DisplayLanding}). Main thread
 * only.
 */
public final class DisplayPageBinding implements WallDisplay {

    /** What the binding asks of its window. */
    public interface Host {
        /** The display's frame on the wall; null before it is built. */
        @Nullable X11PaneFrame frame();
        /** The wall's page {@code pageId}; null when it has none such. */
        @Nullable WallPages.Page page(int pageId);
        /** The wall's pages, in order. */
        @NonNull List<WallPages.Page> pages();
        /** The wall's view, which keeps the pages' pictures. */
        @Nullable PaneWallLayout wall();
        boolean isRunning();
        /** The window is finishing: a late answer is dropped. */
        boolean isGone();
        /** Builds the display's frame when the Display is enabled and none is built; true when it built one. */
        boolean buildFrameIfMissing();
    }

    /** How long a landed page keeps its still while its app comes forward, in ms. */
    private static final long LIVE_DELAY_MS = 300L;
    private static final ExecutorService CARD_READER =
        Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "display-cards");
            thread.setDaemon(true);
            return thread;
        });

    @NonNull private final Context mContext;
    @NonNull private final Host mHost;
    /** The landing's work (start, raise or run) once the wall rests on a page; set once. */
    @Nullable private IntConsumer mLanding;
    @NonNull private final Handler mHandler = new Handler(Looper.getMainLooper());
    /** The Display page the display view stands on (A3b); -1 while the wall has none. */
    private int mBound = -1;
    /** The frame came to its bound page and has not landed there yet: the page's still shows. */
    private boolean mStillPending;
    /** Hides the bound page's still once its app was raised or run there. */
    private final Runnable mLive = () -> {
        mStillPending = false;
        applyStill();
    };
    @Nullable private DisplayCardCache<Drawable> mCards;
    /** Bindings whose card a refresh was asked for since the catalog last changed. */
    private final Set<String> mCardsAsked = new HashSet<>();

    public DisplayPageBinding(@NonNull Context context, @NonNull Host host) {
        mContext = context;
        mHost = host;
    }

    /** Who does the landing's work: start the display, raise or run the page's app. */
    public void setLanding(@NonNull IntConsumer landing) {
        mLanding = landing;
    }

    /** The Display page the display view stands on; -1 while the wall has none. */
    public int boundPage() {
        return mBound;
    }

    /**
     * The wall bound the display view to Display page {@code pageId}: the page gets the frame,
     * built as soon as the wall has a Display page so the slide onto it shows the page's card,
     * not an empty page. Building the frame alone starts nothing.
     */
    @Override
    public void bind(int pageId) {
        int left = mBound;
        mBound = pageId;
        if (left != pageId) {
            // The frame leaves page `left`: what it shows now is that page's last picture. The
            // page it comes to shows its own picture, or its card, until the landing makes it live.
            keepPicture(left);
            mStillPending = pageId >= 0;
            applyStill();
        }
        if (pageId >= 0) mHandler.post(this::ensureFrame);
    }

    /**
     * The wall came to rest on Display page {@code pageId}: the page goes live once its app had
     * a moment to come forward (A3c2), and the landing does its work.
     */
    @Override
    public void landed(int pageId) {
        if (pageId == mBound) {
            mHandler.removeCallbacks(mLive);
            mHandler.postDelayed(mLive, LIVE_DELAY_MS);
        }
        if (mLanding != null) mLanding.accept(pageId);
    }

    /** Keep what the running display shows as Display page {@code pageId}'s picture. */
    private void keepPicture(int pageId) {
        X11PaneFrame frame = mHost.frame();
        if (pageId < 0 || frame == null || !mHost.isRunning()) return;
        frame.copyPagePicture(full -> {
            if (full == null) return;
            WallPages.Page page = mHost.page(pageId);
            PaneWallLayout wall = mHost.wall();
            if (mHost.isGone() || page == null || wall == null
                    || page.kind != PageKind.DISPLAY) {
                full.recycle();
                return;
            }
            // Pictures are kept at half size (ADR 0016 section 2): a still is seen in motion.
            Bitmap picture = PaneWallLayout.halfPicture(full, frame.getWidth(), frame.getHeight());
            if (picture == null) return;
            // The wall keeps the one copy: the page's cell shows it while the display is bound
            // elsewhere, and the frame's still while the page's app comes forward.
            wall.keepPicture(pageId, picture);
            // A picture that lands after the frame came back to its page is that page's newest.
            if (pageId == mBound) applyStill();
        });
    }

    /**
     * What the display frame shows on its bound page ({@link PageStillPolicy#shows}): live, the
     * page's picture, or its card. A page the frame just came to counts as not live until its
     * landing. With the display down and its keyboard data missing, the empty state's install
     * prompt shows instead of a card.
     */
    public void applyStill() {
        X11PaneFrame frame = mHost.frame();
        PaneWallLayout wall = mHost.wall();
        if (frame == null || wall == null) return;
        int pageId = mBound;
        boolean running = mHost.isRunning();
        WallPages.Page page = pageId < 0 ? null : mHost.page(pageId);
        if (page == null || (!running && !X11CliInstaller.hasKeyboardData())) {
            frame.hidePageStill();
            return;
        }
        // Landed with the display down: the empty state is all there is. A picture kept from
        // the last live visit is of a display that is gone, worth nothing in front of it.
        if (!running && !mStillPending) {
            frame.hidePageStill();
            return;
        }
        Bitmap picture = wall.pictureOf(pageId);
        switch (PageStillPolicy.shows(pageId, mStillPending ? -1 : pageId, running,
                picture != null)) {
            case LIVE:
            case NONE:
                frame.hidePageStill();
                return;
            case PICTURE:
                frame.showPageStill(picture, null, "");
                return;
            case CARD:
            default:
                break;
        }
        // The card's name and icon come from the cache (W2): read off the main thread once per
        // binding, never on a drag's re-bind.
        DisplayCardCache.Card<Drawable> card = page.binding == null ? null : cards().card(page.binding);
        if (page.binding != null && card == null && mCardsAsked.add(page.binding)) {
            refreshCards();
        }
        LinuxAppCatalog.LinuxApp app = card == null ? null : card.app;
        Drawable icon = card == null ? null : card.icon;
        if (icon == null) {
            icon = ContextCompat.getDrawable(mContext, R.drawable.ic_symbol_desktop_windows);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(0xFFE8EAED);
            }
        }
        frame.showPageStill(null, icon, DisplayPagePolicy.cardName(page,
            app == null ? null : app.name));
    }

    /** The Linux app catalog and the bound Display pages' cards, read off the main thread. */
    @NonNull
    public DisplayCardCache<Drawable> cards() {
        if (mCards == null) {
            final Resources resources = mContext.getResources();
            mCards = new DisplayCardCache<>(
                () -> LinuxAppCatalog.scan(LinuxAppCatalog.roots()),
                app -> {
                    java.io.File file = LinuxAppIcons.find(app);
                    return file == null ? null : LinuxAppIcons.load(resources, file);
                },
                CARD_READER, mHandler::post);
        }
        return mCards;
    }

    /** Swaps the catalog and card reader, so a test needs no real catalog. */
    void setCardsForTest(@NonNull DisplayCardCache<Drawable> cards) {
        mCards = cards;
    }

    /** Reads the catalog and every bound Display page's card again; the still follows. */
    public void refreshCards() {
        List<String> bindings = new ArrayList<>();
        for (WallPages.Page p : mHost.pages()) {
            if (p.kind == PageKind.DISPLAY && p.binding != null) bindings.add(p.binding);
        }
        cards().refresh(bindings, () -> {
            if (!mHost.isGone()) applyStill();
        });
    }

    /** The Linux app catalog changed: every card is read again, if any was read before. */
    public void onCatalogChanged() {
        mCardsAsked.clear();
        if (mCards != null) refreshCards();
    }

    /** The display's frame on the wall while the wall has a Display page, the display up or not. */
    public void ensureFrame() {
        if (mBound < 0) return;
        if (mHost.buildFrameIfMissing()) applyStill();
    }
}
