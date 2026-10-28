package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.surfaces.EditorCard;
import com.termux.app.surfaces.EditorRows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Use as (editor-redesign §1): the launcher is the phone's Home screen, or the terminal only. The
 * one place every surface asks what Terminal only takes away: the Home page from the wall, Home's
 * settings, Home from Start in, the Home card and both Match rows. Everything else (the
 * Workspace, its card, the dock, the keyboard, Global) is the same either way, and so is whether
 * the app is the system's home app: Terminal only still works as a home app showing the terminal.
 *
 * <p>A door that leads to something Terminal only took away opens nothing of it: the editor's
 * Home doors open the editor at rest, and each such door logs one line ({@link #doorLine}) so a
 * stray one on a phone names itself.
 *
 * <p>Pure: the stored value goes in through {@link #read}, and the answers come out.
 */
public enum UseAs {
    /** Home pages, widgets and the Workspace: the launcher as it always was. */
    HOME("home"),
    /** The Workspace alone: no Home page, no Home settings, no Home in Start in. */
    TERMINAL("terminal");

    /** The tag a hidden door's line is logged under. */
    public static final String LOG_TAG = "UseAs";

    /** The value the preference stores. */
    @NonNull public final String value;

    UseAs(@NonNull String value) {
        this.value = value;
    }

    /** A stored value as this version reads it: anything but {@code terminal} is Home screen. */
    @NonNull
    public static UseAs read(@Nullable String stored) {
        return TERMINAL.value.equals(stored) ? TERMINAL : HOME;
    }

    /** Whether the wall has the Home page. */
    public boolean showsHomePage() {
        return this == HOME;
    }

    /** Whether Settings shows Home's section (its root row, its page and its search hits). */
    public boolean showsHomeSettings() {
        return this == HOME;
    }

    /** Whether Start in offers Home. */
    public boolean offersHomeStartIn() {
        return this == HOME;
    }

    /**
     * The wall's row ({@link PaneWallPolicy#rowPages}): under Terminal only the Workspace alone,
     * held on the row even before its first window arrives, so the wall never falls back to a
     * Home page it does not have.
     */
    @NonNull
    public List<PaneWallPage> rowPages(boolean hasWindow) {
        if (this == HOME) return PaneWallPolicy.rowPages(hasWindow);
        return Collections.singletonList(PaneWallPage.TERMINAL);
    }

    /** Where the home press lands, and where a page taken away hands the wall. */
    @NonNull
    public PaneWallPage homePage() {
        return this == HOME ? PaneWallPolicy.homePage() : PaneWallPage.TERMINAL;
    }

    /**
     * Start in as this mode reads it ({@code startIn} already {@link StartInPolicy#effective}):
     * Home is the Workspace under Terminal only. The stored value is left alone, so going back to
     * Home screen finds it again.
     */
    @NonNull
    public String startIn(@Nullable String startIn) {
        String value = StartInPolicy.read(startIn);
        if (this == TERMINAL && WallRow.START_IN_HOME.equals(value))
            return WallRow.START_IN_WORKSPACE;
        return value;
    }

    /**
     * The Start in choices offered, in order, from {@code values} (each paired with the entry at
     * the same index of the caller's list): every one, less Home under Terminal only. Returns the
     * indexes kept.
     */
    @NonNull
    public List<Integer> startInChoices(@NonNull List<String> values) {
        List<Integer> kept = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (this == TERMINAL && WallRow.START_IN_HOME.equals(values.get(i))) continue;
            kept.add(i);
        }
        return Collections.unmodifiableList(kept);
    }

    /** Whether {@code card} can be opened: the Home card is gone under Terminal only. */
    public boolean reaches(@Nullable EditorCard card) {
        return card == null || this == HOME || card != EditorCard.HOME;
    }

    /**
     * Whether a card shows {@code row}: every row, under every mode. The Match rows that went under
     * Terminal only retired with the halves (ADR 0015 §6).
     */
    public boolean shows(@NonNull EditorRows.Row row) {
        return true;
    }

    /** The card a door opens under this mode: the editor at rest (null) for a card it took away. */
    @Nullable
    public EditorCard doorCard(@Nullable EditorCard card) {
        return reaches(card) ? card : null;
    }

    /**
     * The page the editor is asked to stand on: under Terminal only a Home page is never one, and
     * the editor stands on the page on screen (null) instead.
     */
    @Nullable
    public PaneWallPage editorPlace(@Nullable PaneWallPage place) {
        return this == TERMINAL && place == PaneWallPage.WIDGETS ? null : place;
    }

    /**
     * Whether a switch to this mode with {@code onScreen} in front moves the wall: choosing
     * Terminal only while on the Home page goes to the Workspace.
     */
    public boolean leaves(@Nullable PaneWallPage onScreen) {
        return this == TERMINAL && onScreen == PaneWallPage.WIDGETS;
    }

    /** The line a door to something this mode took away logs: {@code "<door> hidden by terminal -> REST"}. */
    @NonNull
    public String doorLine(@NonNull String door) {
        return door + " hidden by " + value + " -> REST";
    }
}
