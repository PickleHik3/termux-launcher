package com.termux.app.place.layout;

import androidx.annotation.NonNull;

import com.termux.app.place.Element;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.app.place.PlaceOrientation;

/**
 * The one-way step that makes the dock's look and the status bar one value for every page
 * (developer, 2026-11-08, ADR 0023 §1), as Margin was made one in round 21. Run once per install
 * by {@code PlaceLayoutStore} behind its own flag.
 *
 * <p>Per kind a layout held the dock's design, search field, drawer tile and the tile's place, the
 * status bar's edge and whether it rests compact, so a swipe between Home and the Workspace laid
 * the chrome out again mid-slide. Now the Home document holds each of them for every page
 * ({@link LayoutsJson#SHARED_WIDE_FIELDS}, {@link LayoutsJson#SHARED_ORIENTATION_FIELDS}): what
 * Home wore stays, every other kind's copy is dropped, and a kind's {@code status_bar} keeps only
 * its put-away mark. The dock's height was already one value, in {@code sizes}.</p>
 */
public final class SharedChromeFold {

    private SharedChromeFold() {}

    /** The preferences key saying the fold has run. */
    public static final String KEY_MIGRATED = "chrome_shared_global";

    /** {@code layouts} with Home's dock look and status bar the one every kind reads. */
    @NonNull
    public static Layouts fold(@NonNull Layouts layouts) {
        Layouts out = layouts;
        KindLayout home = layouts.kind(LayoutKind.HOME);
        for (LayoutKind kind : LayoutKind.values()) {
            KindLayout before = layouts.kind(kind);
            KindLayout after = before;
            if (kind != LayoutKind.HOME) {
                for (String field : LayoutsJson.SHARED_WIDE_FIELDS) after = after.withWide(field, null);
            }
            for (PlaceOrientation orientation : PlaceOrientation.values()) {
                LayoutFields fields = after.orientation(orientation);
                if (kind == LayoutKind.HOME) {
                    // Home's own edge: the bar's edge, or where a put-away bar comes back.
                    Edge edge = fields.has(LayoutsJson.STATUS_EDGE)
                        ? Edge.parse(fields.string(LayoutsJson.STATUS_EDGE), Edge.TOP) : edgeOf(fields);
                    fields = fields.with(LayoutsJson.STATUS_EDGE,
                        edge == Edge.TOP ? null : edge.storageValue());
                } else {
                    fields = fields.without(LayoutsJson.STATUS_COMPACT);
                }
                fields = fields.without(restoreEdge());
                String bar = fields.string(Element.STATUS.storageKey());
                if (bar != null && !"hidden".equals(bar)) fields = fields.without(Element.STATUS.storageKey());
                after = after.withOrientation(orientation, fields);
            }
            out = out.withKind(kind, after);
        }
        return out;
    }

    @NonNull
    private static String restoreEdge() {
        return Element.STATUS.storageKey() + LayoutsJson.RESTORE_EDGE_SUFFIX;
    }

    /** The edge a kind's status bar names, as {@code PlaceLayoutStore} reads it: the top if none. */
    @NonNull
    private static Edge edgeOf(@NonNull LayoutFields fields) {
        String raw = fields.string(Element.STATUS.storageKey());
        if (raw != null && !"hidden".equals(raw)) return Edge.parse(raw, Edge.TOP);
        return Edge.parse(fields.string(restoreEdge()), Edge.TOP);
    }
}
