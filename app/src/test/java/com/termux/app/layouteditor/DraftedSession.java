package com.termux.app.layouteditor;

import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.wall.PaneWallPage;

/**
 * Opens a layout session the way the Appearance editor does: its draft over the layouts first,
 * unless one is open already (a relaunch carrying the session on). What a test wrote before is
 * the store; what the session writes is the draft.
 */
final class DraftedSession {

    private DraftedSession() {}

    static LayoutEditorPlan enter(PlaceLayoutStore places, PaneWallPage place,
                                  PlaceOrientation orientation) {
        LayoutStore documents = places.layouts();
        if (documents != null && !documents.isDraftOpen())
            documents.beginDraft();
        return LayoutEditorPlan.enter(places, place, orientation);
    }

    /** Done: the draft is stored and closes. */
    static void done(PlaceLayoutStore places) {
        LayoutStore documents = places.layouts();
        if (documents != null)
            documents.commitDraft();
    }
}
