package com.termux.app.terminal.io;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.PaneWallPage;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.List;

/**
 * The shipped key row, cut to the wall it is built for.
 *
 * <p>{@link TermuxPropertyConstants#DEFAULT_IVALUE_EXTRA_KEYS} carries a switch for each of the
 * three places. An install whose wall lacks a place has no use for that place's switch — and a
 * wall of one place, terminal mode, has no use for a switcher at all — so the switches for the
 * places the wall does not have are left out of the row rather than built and greyed. Only the
 * shipped row is cut: a row the user wrote is theirs, every key of it, and the ones that cannot
 * act are drawn dead by {@link ExtraKeyEligibility} instead.
 *
 * <p>Pure, and cheap enough to run every time a row is built: the wall changes on a mode or a
 * display switch, both of which rebuild the row.
 */
public final class ExtraKeysDefaultRow {

    private ExtraKeysDefaultRow() {}

    /**
     * {@code value} with the place switches the wall has no place for removed, when {@code value}
     * is the shipped row; any other value is returned untouched.
     */
    @NonNull
    public static String forWall(@Nullable String value, @NonNull List<PaneWallPage> pages) {
        if (!isShippedRow(value)) return value == null ? "" : value;
        ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(value);
        boolean changed = false;
        for (List<ExtraKeysLayoutModel.Key> row : model.rows()) {
            for (int i = row.size() - 1; i >= 0; i--) {
                if (dropped(row.get(i), pages)) {
                    row.remove(i);
                    changed = true;
                }
            }
        }
        if (!changed) return value;
        model.pruneEmptyRows();
        return model.serialize();
    }

    /** Whether {@code value} is the row the launcher ships, as opposed to one the user wrote. */
    public static boolean isShippedRow(@Nullable String value) {
        return value != null
            && value.trim().equals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS.trim());
    }

    /**
     * A place switch goes when its place is not on the wall, and every place switch goes when
     * the wall is a single place: with nowhere to switch to, the switch for the place in front
     * would only ever say where the user already is.
     */
    private static boolean dropped(@NonNull ExtraKeysLayoutModel.Key key,
                                   @NonNull List<PaneWallPage> pages) {
        PaneWallPage target = ExtraKeyEligibility.placeSwitchTarget(key.key);
        if (target == null) return false;
        return pages.size() < 2 || !pages.contains(target);
    }
}
