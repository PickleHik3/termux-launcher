package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a page's cell shows, for every page kind (ADR 0016 section 2). Each kind has one view,
 * bound to one of its pages: that page is live. Every other page of the kind stands as a still:
 * its last picture once it has one, else its card. A picture is taken when the page stops being
 * live (a binding moving to another page takes the leaving page's picture first, W5), at half the
 * wall's width and height, and is dropped when the page is live again or leaves the wall; one per
 * page, no cap.
 *
 * <p>Pure and tested; the layout and the display frame apply the answers.
 */
public final class PageStillPolicy {

    private PageStillPolicy() {}

    /** What a page's cell shows. */
    public enum Shows {
        /** The kind's view itself: the page its binding is on, the view ready to show it. */
        LIVE,
        /** The page's last picture, kept from when it was last live. */
        PICTURE,
        /** A card: the page's name over its kind's icon. */
        CARD
    }

    /**
     * What page {@code pageId} shows, with its kind's view bound to {@code boundPageId} (-1: to
     * none) and {@code ready} to show it (built, or for the display: running).
     *
     * @param hasPicture the page has a picture kept from when it was last live
     */
    @NonNull
    public static Shows shows(int pageId, int boundPageId, boolean ready, boolean hasPicture) {
        if (ready && pageId == boundPageId) return Shows.LIVE;
        return hasPicture ? Shows.PICTURE : Shows.CARD;
    }

    /**
     * The page whose picture a binding takes as it moves from {@code boundPageId} to
     * {@code nextPageId}: the page it leaves, which stops being live here; -1 when the binding
     * stays where it is or was on no page. The picture is taken before the view turns (W5), so
     * it is the leaving page's and never the arriving one's.
     */
    public static int pictureOnRebind(int boundPageId, int nextPageId) {
        return boundPageId >= 0 && boundPageId != nextPageId ? boundPageId : -1;
    }

    /**
     * A picture's side for a wall side of {@code wallPx}: half of it, never under one pixel. Half
     * the width and half the height is a quarter of the pixels, about 2.5 MB on a 1080-wide
     * phone; a still is only ever seen in motion, so the full size would buy nothing.
     */
    public static int pictureSide(int wallPx) {
        return Math.max(1, wallPx / 2);
    }

    /**
     * One picture per page, kept by page id, with no cap: a page's picture is taken as its kind's
     * view leaves it and shows in its still while another page has the view. Generic over the
     * picture so it stays pure; what a dropped picture needs (a bitmap's recycle) is the
     * caller's, from what {@link #put} and {@link #retain} hand back.
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

        /** Whether page {@code pageId} has a picture to show in its still. */
        public boolean has(int pageId) {
            return mByPage.containsKey(pageId);
        }

        /**
         * Keep {@code picture} as page {@code pageId}'s (null forgets it: the page is live
         * again).
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
         * Keep only the pictures of {@code pageIds}, the pages still on the wall: a removed
         * page's picture goes with it.
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
         * Let go of every picture: the view that took them goes for good.
         *
         * @return the pictures let go of
         */
        @NonNull
        public List<P> clear() {
            List<P> dropped = new ArrayList<>(mByPage.values());
            mByPage.clear();
            return dropped;
        }

        /** How many pages have a picture kept. */
        public int size() {
            return mByPage.size();
        }
    }
}
