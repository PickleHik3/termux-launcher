package com.termux.app.launcher.data;

/**
 * Whether the launcher must refresh its catalogue when it reads the installed-app signature on
 * the way to the front. The provider outlives the activity, so a recreated activity's first read
 * says nothing about what the snapshot already holds: a snapshot loaded before this activity
 * existed may predate an install that happened in between.
 */
public final class CatalogueCatchUp {

    /** The signature of an activity that has not read one yet. */
    public static final int UNREAD = Integer.MIN_VALUE;

    private CatalogueCatchUp() {}

    /**
     * @param lastSignature the signature this activity last recorded, or {@link #UNREAD}
     * @param freshSignature the signature just read
     * @param snapshotHeldBeforeFirstRead whether the provider already held a loaded snapshot
     *     before this activity's first read
     */
    public static boolean shouldRefresh(int lastSignature, int freshSignature,
            boolean snapshotHeldBeforeFirstRead) {
        if (lastSignature == UNREAD) return snapshotHeldBeforeFirstRead;
        return lastSignature != freshSignature;
    }
}
