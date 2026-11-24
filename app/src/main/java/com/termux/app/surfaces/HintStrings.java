package com.termux.app.surfaces;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Scope;
import com.termux.app.layouteditor.HintText;

/** Maps the editor's {@link HintText} to its copy: every sentence is a string resource. */
final class HintStrings {

    private HintStrings() {}

    /** The title line (the selected bar's name), or null for a hint that has none. */
    @Nullable
    static String titleOf(@NonNull Context context, @NonNull HintText hint) {
        return titled(hint.line) && hint.bar != null ? context.getString(nameOf(hint.bar)) : null;
    }

    /** The sentence under the title. */
    @NonNull
    static String of(@NonNull Context context, @NonNull HintText hint) {
        int line = lineRes(hint.line);
        if (hint.bar == null)
            return context.getString(line);
        // The line, then one full sentence for how far the change reaches.
        String reach = context.getString(reachOf(hint.scope));
        if (titled(hint.line))
            return context.getString(line) + " " + reach;
        return context.getString(line, context.getString(nameOf(hint.bar))) + " " + reach;
    }

    private static boolean titled(@NonNull HintText.Line line) {
        return line == HintText.Line.SELECTED_MOVABLE || line == HintText.Line.SELECTED_HIDE_ONLY
            || line == HintText.Line.SELECTED_HIDDEN;
    }

    @StringRes
    static int lineRes(@NonNull HintText.Line line) {
        switch (line) {
            case REST: return R.string.editor_hint_rest;
            case REST_HIDDEN: return R.string.editor_hint_rest_hidden;
            case SELECTED_MOVABLE: return R.string.editor_hint_selected_movable;
            case SELECTED_HIDE_ONLY: return R.string.editor_hint_selected_hide_only;
            case SELECTED_HIDDEN: return R.string.editor_hint_selected_hidden;
            case DRAG_PLACE: return R.string.editor_hint_drag_place;
            case DRAG_PANE: return R.string.editor_hint_drag_pane;
            case DRAG_HIDE: return R.string.editor_hint_drag_hide;
            default: return R.string.editor_hint_drag_refused;
        }
    }

    @StringRes
    static int reachOf(@Nullable Scope scope) {
        if (scope == null)
            return R.string.editor_hint_reach_every_page;
        switch (scope) {
            case HOME_PAGES: return R.string.editor_hint_reach_home_pages;
            case WORKSPACE_PAGES: return R.string.editor_hint_reach_workspace_pages;
            case MINIMAL_LAYOUT: return R.string.editor_hint_reach_minimal_layout;
            default: return R.string.editor_hint_reach_every_page;
        }
    }

    @StringRes
    static int nameOf(@NonNull Bar bar) {
        switch (bar) {
            case STATUS_BAR: return R.string.editor_bar_status;
            case APPS_ROW: return R.string.editor_bar_apps;
            case AZ_INDEX: return R.string.editor_bar_az;
            case EXTRA_KEYS: return R.string.editor_bar_extra_keys;
            case PAGE_BAR: return R.string.editor_bar_page_bar;
            default: return R.string.editor_bar_keyboard;
        }
    }
}
