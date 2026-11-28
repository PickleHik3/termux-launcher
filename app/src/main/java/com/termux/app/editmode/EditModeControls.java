package com.termux.app.editmode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.TerlLookIds;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlProperty;
import com.termux.app.look.TerlSurface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The look controls edit mode holds, and the card each lives on, derived from the look ids (the
 * one owner of which ids exist, {@link TerlLookIds}): a surface's own Opacity and Blur sit on
 * that surface's card, the Look, Corners, Tint and Custom on the Global card. Settings search
 * lists these ({@code EditModeSearch}) and a hit opens edit mode on the card.
 */
public final class EditModeControls {

    private EditModeControls() { }

    /** One control: the look id it writes and the card it opens on. */
    public static final class Control {
        @NonNull public final String id;
        @NonNull public final EditModeController.Card card;
        /** The surface of an own value; null for a Global control. */
        @Nullable public final TerlSurface surface;
        /** The property of an own value; null for a Global control. */
        @Nullable public final TerlProperty property;

        Control(@NonNull String id, @NonNull EditModeController.Card card,
                @Nullable TerlSurface surface, @Nullable TerlProperty property) {
            this.id = id;
            this.card = card;
            this.surface = surface;
            this.property = property;
        }
    }

    /** Every searchable control: each own value a surface may hold, then the Global card's. */
    @NonNull
    public static List<Control> all() {
        List<Control> controls = new ArrayList<>();
        for (TerlSurface surface : TerlSurface.values()) {
            if (!surface.acceptsOwn) continue;
            for (TerlProperty property : TerlProperty.values()) {
                String id = TerlLookIds.of(surface, property);
                controls.add(new Control(id, cardOf(id), surface, property));
            }
        }
        for (String id : new String[] {TerlLookIds.LOOK, TerlLookIds.CORNERS, TerlLookIds.TINT}) {
            controls.add(new Control(id, EditModeController.Card.GLOBAL, null, null));
        }
        return Collections.unmodifiableList(controls);
    }

    /** The card holding the control for look id {@code id}; null for an id that is no look control. */
    @Nullable
    public static EditModeController.Card cardOf(@NonNull String id) {
        TerlLookStore.OwnValue own = TerlLookIds.parseOwn(id);
        if (own != null) {
            switch (own.surface) {
                case CONTROL_BAR: return EditModeController.Card.BAR;
                case KEYBOARD: return EditModeController.Card.KEYBOARD;
                case WINDOWS: return EditModeController.Card.WINDOWS;
                default: return null;
            }
        }
        if (TerlLookIds.LOOK.equals(id) || TerlLookIds.CORNERS.equals(id)
            || TerlLookIds.TINT.equals(id) || TerlLookIds.parseCustom(id) != null) {
            return EditModeController.Card.GLOBAL;
        }
        return null;
    }
}
