package com.termux.app.editorshell;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The shared header's sizes, and the one rule about the chooser row under it.
 *
 * <p>Both editors include {@code editor_shell_header.xml} and fill the slots they have. What lives
 * here is what neither editor should be deciding for itself: how tall the bar stands at the height
 * the card has, that every action keeps a platform-sized target whichever height that is, and
 * whether the row under the header can afford to stay pinned.
 */
public final class EditorShellHeader {

    private EditorShellHeader() {}

    /** Every action slot the header offers, trailing edge inwards. */
    private static final int[] ACTION_IDS = {
        R.id.editor_shell_header_save, R.id.editor_shell_header_revert,
        R.id.editor_shell_header_discard, R.id.editor_shell_header_done,
        R.id.editor_shell_header_close};

    /**
     * Stands the header at the height the card can spend, and gives every action a target at least
     * as tall as the bar and at least 44dp wide.
     *
     * @param availableHeightPx the height the whole card has to live in
     */
    public static void apply(@NonNull View header, int availableHeightPx) {
        float density = header.getResources().getDisplayMetrics().density;
        int height = EditorShellMetrics.headerHeightPx(availableHeightPx, density);
        if (header.getMinimumHeight() != height) {
            header.setMinimumHeight(height);
            ViewGroup.LayoutParams params = header.getLayoutParams();
            if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                header.setLayoutParams(params);
            }
            header.requestLayout();
        }
        int target = Math.max(height, EditorShellMetrics.px(
            EditorShellMetrics.HEADER_COMPACT_DP, density));
        for (int id : ACTION_IDS) {
            View action = header.findViewById(id);
            if (action != null && action.getVisibility() == View.VISIBLE)
                EditorShellRows.expandTouchTarget(action, target);
        }
    }

    /**
     * Moves the chooser between the header's pinned slot and the top of the scrolling body.
     *
     * <p>Pinned chrome is paid for in rows, and the rows are the controls. On a short landscape
     * screen a 60dp chooser is nearly a third of everything the body has, so below the threshold
     * the chooser becomes the body's first row and scrolls away with it.
     *
     * @param pinnedSlot where the chooser stands while it is pinned
     * @param body       the scrolling column it joins when it is not
     */
    public static void applyChooserPin(@Nullable View chooser, @Nullable ViewGroup pinnedSlot,
                                       @Nullable ViewGroup body, boolean pinned) {
        if (chooser == null || pinnedSlot == null || body == null)
            return;
        ViewGroup wanted = pinned ? pinnedSlot : body;
        ViewGroup parent = chooser.getParent() instanceof ViewGroup
            ? (ViewGroup) chooser.getParent() : null;
        if (parent == wanted && (pinned || body.indexOfChild(chooser) == 0))
            return;
        if (parent != null)
            parent.removeView(chooser);
        if (pinned)
            pinnedSlot.addView(chooser);
        else
            body.addView(chooser, 0);
    }
}
