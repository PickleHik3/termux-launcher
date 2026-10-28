package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Display page is on the wall (ADR 0015, A3b): each Display page is a slot of its own, and
 * the one display view is the bound page's ({@link PaneWallPolicy#boundDisplaySlot}). Only the
 * bound page, with the display running, is live; every other Display page stands as a still: its
 * last picture once it has one, else a card naming what it shows. Landing on a Display page starts
 * the display; leaving keeps it running.
 *
 * <p>Pure and tested; the activity and the display frame apply the answers.
 */
public final class DisplayPagePolicy {

    private DisplayPagePolicy() {}

    /** What a Display page shows. */
    public enum Shows {
        /** The display itself: the bound page, the display running. */
        LIVE,
        /** The page's last picture, kept from when it was last live. */
        PICTURE,
        /** A card: the bound app's icon and name, or "Display" for an unbound page. */
        CARD
    }

    /** The card's name for an unbound page, or a bound one whose app could not be named. */
    public static final String UNBOUND_NAME = "Display";

    /**
     * What Display page {@code pageId} shows, with the display view bound to {@code boundPageId}
     * (-1: none) and the display {@code running} or not.
     *
     * @param hasPicture the page has a picture kept from when it was last live
     */
    @NonNull
    public static Shows shows(int pageId, int boundPageId, boolean running, boolean hasPicture) {
        if (running && pageId == boundPageId) return Shows.LIVE;
        return hasPicture ? Shows.PICTURE : Shows.CARD;
    }

    /**
     * The name on a Display page's card: the bound app's ({@code appName}, as the caller resolved
     * the binding), else {@link #UNBOUND_NAME}. The page's own name is the chip's, not the card's.
     */
    @NonNull
    public static String cardName(@NonNull WallPages.Page page, @Nullable String appName) {
        if (page.binding != null && appName != null && !appName.trim().isEmpty()) return appName;
        return UNBOUND_NAME;
    }

    /**
     * Whether landing on a page of {@code kind} starts the display: any Display page, while the
     * display is neither running nor already on its way up.
     */
    public static boolean startsOnLanding(@NonNull WallPages.PageKind kind, boolean running,
                                          boolean starting) {
        return kind == WallPages.PageKind.DISPLAY && !running && !starting;
    }

    /**
     * The Display page the home press and the cold start land on: home itself when it is a
     * Display page; -1 when home is anything else, or the old display window.
     */
    public static int homeDisplayPage(@Nullable WallPages pages) {
        WallPages.Page home = pages == null ? null : pages.home();
        return home != null && home.kind == WallPages.PageKind.DISPLAY ? home.id : -1;
    }

    /**
     * One picture per Display page, kept by page id: the bound page's is taken as the display
     * view leaves it, and stands for it while another page has the view or the display is down.
     * Generic over the picture so it stays pure; what a dropped picture needs (a bitmap's
     * recycle) is the caller's, from what {@link #put} and {@link #retain} hand back.
     *
     * @param <P> the picture
     */
    public static final class Pictures<P> {

        @NonNull private final Map<Integer, P> mByPage = new HashMap<>();

        /** The picture of page {@code pageId}; null while it has none. */
        @Nullable
        public P get(int pageId) {
            return mByPage.get(pageId);
        }

        public boolean has(int pageId) {
            return mByPage.containsKey(pageId);
        }

        /**
         * Keep {@code picture} as page {@code pageId}'s (null forgets it).
         *
         * @return the picture it replaces, for the caller to let go of; null when none, or when
         *     it is {@code picture} itself
         */
        @Nullable
        public P put(int pageId, @Nullable P picture) {
            P previous = picture == null ? mByPage.remove(pageId) : mByPage.put(pageId, picture);
            return previous == picture ? null : previous;
        }

        /**
         * Keep only the pictures of {@code pageIds}, the Display pages still on the wall.
         *
         * @return the pictures let go of, for the caller to release
         */
        @NonNull
        public List<P> retain(@NonNull Collection<Integer> pageIds) {
            List<P> dropped = new ArrayList<>();
            java.util.Iterator<Map.Entry<Integer, P>> it = mByPage.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, P> entry = it.next();
                if (pageIds.contains(entry.getKey())) continue;
                dropped.add(entry.getValue());
                it.remove();
            }
            return dropped;
        }

        /**
         * Let go of every picture: the display went down for good, or the frame goes.
         *
         * @return the pictures let go of
         */
        @NonNull
        public List<P> clear() {
            List<P> dropped = new ArrayList<>(mByPage.values());
            mByPage.clear();
            return dropped;
        }

        public int size() {
            return mByPage.size();
        }
    }
}
