package com.termux.app.terminal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The terminal long-press menu: icon and name per row, on the in-app sheet plane.
 *
 * <p>It used to be an {@code AlertDialog} over an {@code ArrayAdapter} of plain strings — a dialog
 * window, which takes focus, which pulls the {@code InputConnection} off {@code TerminalView} and
 * summons the system IME. It is on {@link TerminalSheetController} for the same reason the session
 * browser is.
 *
 * <p>Only five actions are promoted to the first card; everything else is one tap further in, on a
 * second card the stack pops back rather than closing. A long-press menu is read at a glance, and
 * eleven rows is not a glance.
 *
 * <p>Rows dispatch through {@link TerminalActionDispatcher} wherever a registry tool already means
 * what the row means, so the menu, the palette, a keybind and a remote caller all run the same code.
 * The two rows that carry {@link TermuxActivity#handleTerminalAction(int)} instead are the two with
 * no tool behind them: <em>Style</em> hands off to the Termux:Styling plugin, and <em>Kill
 * process</em> is the confirmation dialog rather than {@code session.close_current}'s silent close.
 */
public final class TerminalActionMenu {

    private static final String LOG_TAG = "TerminalActionMenu";

    /** Not every row is a registry tool; these two are activity handlers. */
    public static final int NO_LEGACY_ACTION = -1;

    private TerminalActionMenu() {}

    /** One line of the menu. Public so the contents can be asserted without inflating the plane. */
    public static final class Row {
        @DrawableRes public final int iconRes;
        @NonNull public final CharSequence title;
        /** Registry tool name, or null when {@link #legacyActionId} carries the row instead. */
        @Nullable public final String toolName;
        public final int legacyActionId;
        /** True for the single row that pushes the second card instead of acting. */
        public final boolean opensMore;

        private Row(@DrawableRes int iconRes, @NonNull CharSequence title,
                    @Nullable String toolName, int legacyActionId, boolean opensMore) {
            this.iconRes = iconRes;
            this.title = title;
            this.toolName = toolName;
            this.legacyActionId = legacyActionId;
            this.opensMore = opensMore;
        }

        static Row tool(@DrawableRes int iconRes, @NonNull CharSequence title,
                        @NonNull String toolName) {
            return new Row(iconRes, title, toolName, NO_LEGACY_ACTION, false);
        }

        static Row legacy(@DrawableRes int iconRes, @NonNull CharSequence title, int actionId) {
            return new Row(iconRes, title, null, actionId, false);
        }

        static Row more(@NonNull CharSequence title) {
            return new Row(R.drawable.ic_symbol_more_horiz, title, null, NO_LEGACY_ACTION, true);
        }

        @NonNull
        @Override
        public String toString() {
            return title.toString();
        }
    }

    /**
     * The promoted rows, in the order the user asked for them.
     *
     * <p>Two of them are conditional. <em>Paste</em> is only offered when the clipboard actually
     * holds text — the user's rule. <em>Copy</em> follows the same shape for a different reason:
     * {@code clipboard.copy_selected} answers {@code no_selection} when nothing is selected, so an
     * always-present Copy would be a row whose only possible outcome is a failure toast.
     */
    @VisibleForTesting
    @NonNull
    public static List<Row> buildTopRows(@NonNull TermuxActivity activity) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.tool(R.drawable.ic_symbol_link, activity.getString(R.string.action_select_url),
            TerminalActionDispatcher.TOOL_TERMINAL_SELECT_URL));
        rows.add(Row.tool(R.drawable.ic_symbol_search,
            activity.getString(R.string.action_search_scrollback),
            TerminalActionDispatcher.TOOL_TERMINAL_SEARCH_SCROLLBACK));
        rows.add(Row.tool(R.drawable.ic_settings, activity.getString(R.string.action_open_settings),
            TerminalActionDispatcher.TOOL_APP_OPEN_SETTINGS));
        if (hasSelection(activity)) {
            rows.add(Row.tool(R.drawable.ic_symbol_content_copy,
                activity.getString(R.string.action_copy_selection),
                TerminalActionDispatcher.TOOL_CLIPBOARD_COPY_SELECTED));
        }
        if (hasClipboardText(activity)) {
            rows.add(Row.tool(R.drawable.ic_symbol_content_paste,
                activity.getString(R.string.action_paste),
                TerminalActionDispatcher.TOOL_CLIPBOARD_PASTE));
        }
        rows.add(Row.more(activity.getString(R.string.action_more)));
        return rows;
    }

    /** Everything the first card does not promote, in its old order. */
    @VisibleForTesting
    @NonNull
    public static List<Row> buildMoreRows(@NonNull TermuxActivity activity, int sessionPid) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.tool(R.drawable.ic_symbol_terminal,
            activity.getString(R.string.action_command_palette),
            TerminalActionDispatcher.TOOL_APP_COMMAND_PALETTE));
        rows.add(Row.tool(R.drawable.ic_symbol_share,
            activity.getString(R.string.action_share_transcript),
            TerminalActionDispatcher.TOOL_TERMINAL_SHARE_TRANSCRIPT));
        rows.add(Row.legacy(R.drawable.ic_symbol_palette,
            activity.getString(R.string.action_style_terminal),
            TermuxActivity.CONTEXT_MENU_STYLE_ID));
        rows.add(Row.tool(R.drawable.ic_symbol_wallpaper,
            activity.getString(R.string.action_set_background_image),
            TerminalActionDispatcher.TOOL_APPEARANCE_SET_WALLPAPER));
        rows.add(Row.tool(R.drawable.ic_symbol_image,
            activity.getString(activity.isWallpaperModeEnabled()
                ? R.string.action_disable_background_image
                : R.string.action_enable_background_image),
            TerminalActionDispatcher.TOOL_APPEARANCE_TOGGLE_WALLPAPER));
        rows.add(Row.tool(R.drawable.ic_tune_sliders_24,
            activity.getString(R.string.action_glass_lab),
            TerminalActionDispatcher.TOOL_APPEARANCE_GLASS_LAB));
        rows.add(Row.tool(R.drawable.ic_symbol_restart_alt,
            activity.getString(R.string.action_reset_terminal),
            TerminalActionDispatcher.TOOL_TERMINAL_RESET));
        rows.add(Row.legacy(R.drawable.ic_symbol_cancel,
            activity.getString(R.string.action_kill_process, sessionPid),
            TermuxActivity.CONTEXT_MENU_KILL_PROCESS_ID));
        return rows;
    }

    /** @return false when there is no plane to put the card on; the long press then goes unspent. */
    public static boolean show(@NonNull TermuxActivity activity, int sessionPid) {
        return show(activity, sessionPid, null);
    }

    /**
     * @param anchor screen point to open the menu at, or null to centre it.
     * @return false when there is no plane to put the card on; the long press then goes unspent.
     */
    public static boolean show(@NonNull TermuxActivity activity, int sessionPid,
                               @Nullable PointF anchor) {
        TerminalSheetController sheet = activity.getTerminalSheetController();
        // No heading: an anchored menu at the finger does not need a word telling the user they are
        // in the terminal they just long-pressed.
        return sheet.show("", buildCard(activity, buildTopRows(activity), sessionPid, anchor),
            false, null,
            // Parity with the dialog this replaced: closing the menu releases the copy of the
            // selection TerminalView was holding for it. Hung on the first card only, so popping
            // the More card back to it does not throw the selection away mid-menu.
            () -> {
                com.termux.view.TerminalView view = activity.getTerminalView();
                if (view != null) view.onContextMenuClosed(null);
            },
            false, anchor);
    }

    private static void showMore(@NonNull TermuxActivity activity, int sessionPid,
                                 @Nullable PointF anchor) {
        // Covers the first card rather than stacking over it: both are translucent glass, so two
        // menus at once read as one card with both sets of rows showing through each other. It
        // opens at the same point, so More reads as the first card turning over rather than a
        // second surface arriving from somewhere else.
        activity.getTerminalSheetController().show(activity.getString(R.string.action_more),
            buildCard(activity, buildMoreRows(activity, sessionPid), sessionPid, anchor), false,
            null, null, true, anchor);
    }

    @NonNull
    private static LinearLayout buildCard(@NonNull TermuxActivity activity, @NonNull List<Row> rows,
                                          int sessionPid, @Nullable PointF anchor) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        for (Row row : rows) body.addView(buildRow(activity, row, sessionPid, anchor));
        return body;
    }

    @NonNull
    private static ViewGroup buildRow(@NonNull TermuxActivity activity, @NonNull Row row,
                                      int sessionPid, @Nullable PointF anchor) {
        Context context = activity;
        float density = context.getResources().getDisplayMetrics().density;
        int colorText = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface, 0xFFFFFFFF);
        int colorIcon = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFFB0B0B0);

        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setMinimumHeight(Math.round(48 * density));
        int inset = Math.round(8 * density);
        view.setPadding(inset, 0, inset, 0);
        view.setBackground(rowRipple(context, density));
        view.setOnClickListener(v -> run(activity, row, sessionPid, anchor));

        ImageView icon = new ImageView(context);
        icon.setImageResource(row.iconRes);
        // Tinted here rather than left to each drawable: the set is a mix of Material Symbols and
        // older icons, and only one of them may decide what colour a menu row's glyph is.
        icon.setImageTintList(ColorStateList.valueOf(colorIcon));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            Math.round(22 * density), Math.round(22 * density));
        iconParams.rightMargin = Math.round(18 * density);
        view.addView(icon, iconParams);

        TextView label = new TextView(context);
        label.setText(row.title);
        label.setTextColor(colorText);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.addView(label, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return view;
    }

    /** A rounded press highlight; a square one would cut the corners of the card it sits in. */
    @NonNull
    private static RippleDrawable rowRipple(@NonNull Context context, float density) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(14 * density);
        int ripple = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorPrimary, 0xFF80DEEA);
        return new RippleDrawable(
            ColorStateList.valueOf(MaterialColors.compositeARGBWithAlpha(ripple, 60)), null, mask);
    }

    private static void run(@NonNull TermuxActivity activity, @NonNull Row row, int sessionPid,
                            @Nullable PointF anchor) {
        if (row.opensMore) {
            showMore(activity, sessionPid, anchor);
            return;
        }
        // Acted on before the stack is popped, because popping the menu is what clears
        // TerminalView's stored selection — which is the very text Copy is about to read. The menu
        // cards standing at this moment are counted first, so that popping them afterwards cannot
        // take down a card the action itself opened.
        TerminalSheetController sheet = activity.getTerminalSheetController();
        int menuDepth = sheet.depth();
        if (row.toolName != null) {
            JSONObject result = TerminalActionDispatcher.getInstance()
                .execute(row.toolName, new JSONObject());
            if (!result.optBoolean("ok", false)) {
                String message = result.optString("message", "");
                Logger.logWarn(LOG_TAG, "Menu action " + row.toolName + " failed: " + message);
                if (!message.isEmpty()) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        } else {
            activity.handleTerminalAction(row.legacyActionId);
        }
        if (sheet.depth() > menuDepth) sheet.dismissUnder(menuDepth);
        else sheet.dismissAll();
    }

    private static boolean hasSelection(@NonNull TermuxActivity activity) {
        com.termux.view.TerminalView view = activity.getTerminalView();
        String selected = view == null ? null : view.getStoredSelectedText();
        return selected != null && !selected.isEmpty();
    }

    private static boolean hasClipboardText(@NonNull TermuxActivity activity) {
        return ShareUtils.getTextStringFromClipboardIfSet(activity, true) != null;
    }
}
