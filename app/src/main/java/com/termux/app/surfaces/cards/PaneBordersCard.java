package com.termux.app.surfaces.cards;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.surfaces.EditorRows.Spec;
import com.termux.app.surfaces.PaneBorderHex;
import com.termux.app.surfaces.rows.ActionRow;
import com.termux.app.surfaces.rows.RowKit;
import com.termux.app.surfaces.rows.SegmentRow;
import com.termux.app.terminal.PaneBorderColours.ColourChoice;
import com.termux.app.terminal.PaneBorderColours.Mode;
import com.termux.app.terminal.PaneBorderStyle;
import com.termux.app.terminal.PaneRim;
import com.termux.app.terminal.PaneSurfaceStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Pane borders card under the Terminal card (#206, pane-borders-2026-11-01): a strip of
 * three frames in the panes' own drawables, the Rim (the Terminal card's own row), the border
 * colour, the focus colour, the glow and its colour, a Custom colour field under each chooser
 * set to Custom (its field in a dialog), and Reset. The editor shows it in the Terminal card's place; its header's back
 * arrow and system Back return to the Terminal card.
 *
 * <p>{@link #rows} and the choosers' lists are pure; {@link #view} builds the rows over a
 * {@link Host}, which reads and writes the {@code terminal_pane_*} preferences.</p>
 */
public final class PaneBordersCard {

    private PaneBordersCard() {}

    /** Every row of the card, in the order it shows them. */
    @NonNull
    public static List<Spec> rows() {
        return new EditorRows.CardRows().tier(1, Row.PANE_PREVIEW, Row.RIM,
            Row.PANE_BORDER_COLOUR, Row.PANE_BORDER_CUSTOM,
            Row.PANE_FOCUS_COLOUR, Row.PANE_FOCUS_CUSTOM,
            Row.PANE_GLOW, Row.PANE_GLOW_COLOUR, Row.PANE_GLOW_CUSTOM,
            Row.PANE_BORDERS_RESET).build();
    }

    /** Whether {@code row} is one this card draws (Rim is drawn as on the Terminal card). */
    public static boolean owns(@NonNull Row row) {
        switch (row) {
            case PANE_PREVIEW:
            case PANE_BORDER_COLOUR:
            case PANE_BORDER_CUSTOM:
            case PANE_FOCUS_COLOUR:
            case PANE_FOCUS_CUSTOM:
            case PANE_GLOW:
            case PANE_GLOW_COLOUR:
            case PANE_GLOW_CUSTOM:
            case PANE_BORDERS_RESET:
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------------------ policy

    /** The border's choices: the theme outline, the three roles, and Custom. */
    public static final List<Mode> BORDER_MODES = Collections.unmodifiableList(Arrays.asList(
        Mode.OUTLINE, Mode.PRIMARY, Mode.SECONDARY, Mode.TERTIARY, Mode.CUSTOM));

    /** The focus and glow colours' choices: the border's own first, then the border's choices. */
    public static final List<Mode> FOLLOW_MODES = Collections.unmodifiableList(Arrays.asList(
        Mode.SAME_AS_BORDER, Mode.OUTLINE, Mode.PRIMARY, Mode.SECONDARY, Mode.TERTIARY,
        Mode.CUSTOM));

    /** A chooser's choices: {@link #BORDER_MODES} for the border, {@link #FOLLOW_MODES} else. */
    @NonNull
    public static List<Mode> modes(@NonNull Row chooser) {
        return chooser == Row.PANE_BORDER_COLOUR ? BORDER_MODES : FOLLOW_MODES;
    }

    /** The chooser a Custom colour row belongs to, or null for any other row. */
    @Nullable
    public static Row chooserOf(@NonNull Row custom) {
        switch (custom) {
            case PANE_BORDER_CUSTOM: return Row.PANE_BORDER_COLOUR;
            case PANE_FOCUS_CUSTOM: return Row.PANE_FOCUS_COLOUR;
            case PANE_GLOW_CUSTOM: return Row.PANE_GLOW_COLOUR;
            default: return null;
        }
    }

    /**
     * Whether {@code row} stands now: a Custom colour row only while its chooser is Custom, and
     * the glow's colour only while the glow is on.
     */
    public static boolean shown(@NonNull Row row, @NonNull Host host) {
        boolean glowRow = row == Row.PANE_GLOW_COLOUR || row == Row.PANE_GLOW_CUSTOM;
        if (glowRow && !host.glow())
            return false;
        Row chooser = chooserOf(row);
        return chooser == null || host.choice(chooser).mode == Mode.CUSTOM;
    }

    @StringRes
    private static int modeName(@NonNull Mode mode) {
        switch (mode) {
            case PRIMARY: return R.string.appearance_pane_colour_primary;
            case SECONDARY: return R.string.appearance_pane_colour_secondary;
            case TERTIARY: return R.string.appearance_pane_colour_tertiary;
            case CUSTOM: return R.string.appearance_pane_colour_custom;
            case SAME_AS_BORDER: return R.string.appearance_pane_colour_same;
            case OUTLINE:
            default: return R.string.appearance_pane_colour_outline;
        }
    }

    // ------------------------------------------------------------------------------- views

    /** What the card's rows read and write: the editor's side. */
    public interface Host {
        /** The stored choice of a chooser row. */
        @NonNull ColourChoice choice(@NonNull Row chooser);
        void setMode(@NonNull Row chooser, @NonNull Mode mode);
        /** A Custom colour row's field committed {@code argb} (never 0). */
        void setCustom(@NonNull Row custom, int argb);
        boolean glow();
        void setGlow(boolean on);
        /** Reset pane borders: every value of the card back as it ships. */
        void resetPaneBorders();
        /** The palette the panes draw with now. */
        @NonNull PaneBorderStyle.Palette palette();
        /** The system keyboard is about to show for the Custom colour field. */
        void onSystemImeRequested();
        /** The Custom colour dialog is gone: the system keyboard goes with it. */
        void onSystemImeReleased();
    }

    /** The card's row for {@code row}, {@code widthPx} wide, or null for a row it does not draw. */
    @Nullable
    public static CardRowView view(@NonNull Context context, @NonNull Row row, int widthPx,
                                   @NonNull Host host) {
        switch (row) {
            case PANE_PREVIEW: return preview(context, host);
            case PANE_BORDER_COLOUR:
            case PANE_FOCUS_COLOUR:
            case PANE_GLOW_COLOUR:
                return chooser(context, row, widthPx, host);
            case PANE_BORDER_CUSTOM:
            case PANE_FOCUS_CUSTOM:
            case PANE_GLOW_CUSTOM:
                return custom(context, row, host);
            case PANE_GLOW: return glow(context, widthPx, host);
            case PANE_BORDERS_RESET: return reset(context, host);
            default: return null;
        }
    }

    /** The frames' height and corner radius, a pane in small. */
    static final int FRAME_HEIGHT_DP = 72;
    static final int FRAME_RADIUS_DP = 12;
    static final int FRAME_GAP_DP = 8;

    @NonNull
    private static CardRowView preview(@NonNull Context context, @NonNull Host host) {
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        int edge = RowKit.dp(context, RowKit.EDGE_DP);
        strip.setPadding(edge, RowKit.dp(context, 8), edge, RowKit.dp(context, 8));
        @StringRes int[] names = {R.string.appearance_pane_preview_normal,
            R.string.appearance_pane_preview_focused, R.string.appearance_pane_preview_attention};
        FrameLayout[] frames = new FrameLayout[names.length];
        PaneRim[] rims = new PaneRim[names.length];
        for (int i = 0; i < names.length; i++) {
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            FrameLayout frame = new FrameLayout(context);
            GradientDrawable ground = new GradientDrawable();
            ground.setCornerRadius(RowKit.dp(context, FRAME_RADIUS_DP));
            ground.setColor(RowKit.color(context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh));
            frame.setBackground(ground);
            TextView prompt = new TextView(context);
            prompt.setText("$ _");
            prompt.setTypeface(Typeface.MONOSPACE);
            prompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            prompt.setSingleLine(true);
            prompt.setTextColor(RowKit.color(context,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
            prompt.setAlpha(0.6f);
            int inner = RowKit.dp(context, 8);
            prompt.setPadding(inner, inner, inner, inner);
            prompt.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            frame.addView(prompt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP));
            column.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, RowKit.dp(context, FRAME_HEIGHT_DP)));
            TextView label = RowKit.caption(context, context.getString(names[i]));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = RowKit.dp(context, 4);
            column.addView(label, labelParams);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0)
                columnParams.setMarginStart(RowKit.dp(context, FRAME_GAP_DP));
            strip.addView(column, columnParams);
            frames[i] = frame;
            rims[i] = new PaneRim();
        }
        float radius = RowKit.dp(context, FRAME_RADIUS_DP);
        Runnable restate = () -> {
            PaneBorderStyle.Palette palette = host.palette();
            // Normal is a lone pane; Focused the focused pane of a split; Attention outranks both.
            PaneBorderStyle.Decision[] decisions = {
                PaneBorderStyle.decide(1, false, false, palette),
                PaneBorderStyle.decide(2, true, false, palette),
                PaneBorderStyle.decide(2, true, true, palette)};
            for (int i = 0; i < frames.length; i++)
                rims[i].apply(frames[i], false, radius, decisions[i], PREVIEW_STYLE);
        };
        restate.run();
        return new CardRowView(strip, restate);
    }

    /**
     * What the preview frames are drawn as: plain panes (no glass slab, the plain line where the
     * rim would be), with the attention glow held still.
     */
    private static final PaneSurfaceStyle PREVIEW_STYLE = new PaneSurfaceStyle() {
        @Override public boolean isPaneGlassActive() { return false; }
        @Nullable @Override public android.graphics.Bitmap paneGlassBlurFrame() { return null; }
        @NonNull @Override public android.graphics.Rect paneGlassBlurFrameRect() {
            return new android.graphics.Rect();
        }
        @Nullable @Override public android.graphics.ColorFilter paneGlassFrostFilter() {
            return null;
        }
        @Override public int paneGlassTintColor() { return 0; }
        @Nullable @Override public android.graphics.drawable.Drawable paneGlassGrainLayer() {
            return null;
        }
        @Override public int paneGlassGrainStrength() { return 0; }
        @Override public boolean paneAttentionPulses() { return false; }
        @Override public float paneGlassCornerRadiusPx() { return 0f; }
        @Override public int paneGapDp() { return 0; }
    };

    @NonNull
    private static CardRowView chooser(@NonNull Context context, @NonNull Row row, int widthPx,
                                       @NonNull Host host) {
        List<Mode> modes = modes(row);
        List<SegmentRow.Segment> segments = new ArrayList<>(modes.size());
        for (Mode mode : modes)
            segments.add(SegmentRow.Segment.glyph(mode == Mode.SAME_AS_BORDER
                ? R.drawable.ic_ms_link : R.drawable.ic_editor_tint,
                context.getString(modeName(mode))));
        SegmentRow segmentRow = SegmentRow.build(context, row.iconRes,
            context.getString(row.labelRes), segments, modes.indexOf(host.choice(row).mode),
            widthPx, index -> {
                if (index >= 0 && index < modes.size())
                    host.setMode(row, modes.get(index));
            });
        Runnable restate = () -> {
            ColourChoice choice = host.choice(row);
            for (int i = 0; i < segmentRow.buttons.size() && i < modes.size(); i++) {
                Mode mode = modes.get(i);
                if (mode == Mode.SAME_AS_BORDER)
                    continue;
                MaterialButton button = segmentRow.buttons.get(i);
                button.setIcon(swatch(context, swatchColour(context, mode, choice), 18));
                button.setIconTint(null);
            }
            segmentRow.select(modes.indexOf(choice.mode));
        };
        restate.run();
        return new CardRowView(segmentRow.root, restate);
    }

    /** The colour a chooser's {@code mode} segment shows: its role, or the stored custom. */
    private static int swatchColour(@NonNull Context context, @NonNull Mode mode,
                                    @NonNull ColourChoice choice) {
        switch (mode) {
            case PRIMARY:
                return RowKit.color(context, androidx.appcompat.R.attr.colorPrimary);
            case SECONDARY:
                return RowKit.color(context, com.google.android.material.R.attr.colorSecondary);
            case TERTIARY:
                return RowKit.color(context, com.google.android.material.R.attr.colorTertiary);
            case CUSTOM:
                return choice.custom;
            case OUTLINE:
            default:
                return PaneRim.lineColour(context);
        }
    }

    /** A colour swatch: a filled circle ringed in the outline variant, alpha showing. */
    @NonNull
    private static GradientDrawable swatch(@NonNull Context context, int colour, int sizeDp) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(colour);
        dot.setStroke(Math.max(1, RowKit.dp(context, 1)), RowKit.color(context,
            com.google.android.material.R.attr.colorOutlineVariant));
        int size = RowKit.dp(context, sizeDp);
        dot.setSize(size, size);
        return dot;
    }

    /**
     * A Custom colour row: its swatch, the name and the stored hex. A tap anywhere on it opens
     * the field in a dialog of its own: the system keyboard serves only a window that asks for
     * it, never the launcher's own (stateAlwaysHidden), so a field inline in the sheet would
     * never get one.
     */
    @NonNull
    private static CardRowView custom(@NonNull Context context, @NonNull Row row,
                                      @NonNull Host host) {
        Row chooser = chooserOf(row);
        LinearLayout root = RowKit.line(context);
        root.setMinimumHeight(RowKit.dp(context, RowKit.TOUCH_DP));
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple,
                true) && ripple.resourceId != 0)
            root.setBackgroundResource(ripple.resourceId);
        View swatch = new View(context);
        swatch.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(swatch, RowKit.glyphParams(context));
        TextView title = RowKit.title(context, context.getString(row.labelRes));
        root.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = RowKit.caption(context, null);
        value.setTypeface(Typeface.MONOSPACE);
        value.setSingleLine(true);
        value.setPadding(RowKit.dp(context, RowKit.EDGE_DP), 0, 0, 0);
        root.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        Runnable showStored = () -> {
            int stored = chooser == null ? 0 : host.choice(chooser).custom;
            swatch.setBackground(swatch(context, stored, RowKit.GLYPH_DP));
            String text = stored == 0 ? "" : PaneBorderHex.format(stored);
            if (!text.contentEquals(value.getText()))
                value.setText(text);
        };
        root.setOnClickListener(v -> {
            if (chooser != null)
                editCustom(context, row, host.choice(chooser).custom, host);
        });
        showStored.run();
        return new CardRowView(root, showStored);
    }

    /**
     * The Custom colour dialog, built as the Tasks widget's: the field prefilled with
     * {@code stored} ({@code #} for none), Done or OK commits through {@link PaneBorderHex},
     * and a colour it cannot read keeps the dialog open with the text selected.
     */
    private static void editCustom(@NonNull Context context, @NonNull Row row, int stored,
                                   @NonNull Host host) {
        EditText input = new EditText(context);
        input.setTypeface(Typeface.MONOSPACE);
        input.setSingleLine(true);
        input.setHint(R.string.appearance_pane_custom_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(9)});
        input.setText(stored == 0 ? "#" : PaneBorderHex.format(stored));
        input.setSelection(input.length());
        FrameLayout holder = new FrameLayout(context);
        int pad = RowKit.dp(context, 24);
        holder.setPadding(pad, pad / 3, pad, 0);
        holder.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        host.onSystemImeRequested();
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(row.labelRes)
            .setView(holder)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        // True once the colour is taken and the dialog may close.
        java.util.function.BooleanSupplier commit = () -> {
            Integer argb = PaneBorderHex.parse(input.getText().toString());
            if (argb == null) {
                input.selectAll();
                return false;
            }
            if (argb != stored)
                host.setCustom(row, argb);
            return true;
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE)
                return false;
            if (commit.getAsBoolean())
                dialog.dismiss();
            return true;
        });
        // OK is wired after show, so a colour it cannot read leaves the dialog up.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                if (commit.getAsBoolean())
                    dialog.dismiss();
            }));
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        input.requestFocus();
        dialog.setOnDismissListener(d -> host.onSystemImeReleased());
        dialog.show();
    }

    @NonNull
    private static CardRowView glow(@NonNull Context context, int widthPx, @NonNull Host host) {
        SegmentRow row = SegmentRow.words(context, Row.PANE_GLOW.iconRes,
            context.getString(Row.PANE_GLOW.labelRes), new CharSequence[] {
                context.getString(R.string.appearance_pane_glow_off),
                context.getString(R.string.appearance_pane_glow_steady)},
            host.glow() ? 1 : 0, widthPx, index -> host.setGlow(index == 1));
        return new CardRowView(row.root, () -> row.select(host.glow() ? 1 : 0));
    }

    @NonNull
    private static CardRowView reset(@NonNull Context context, @NonNull Host host) {
        ActionRow row = ActionRow.build(context, Row.PANE_BORDERS_RESET.iconRes,
            context.getString(Row.PANE_BORDERS_RESET.labelRes),
            context.getString(R.string.appearance_pane_borders_reset_action),
            v -> host.resetPaneBorders());
        return new CardRowView(row.root, () -> {});
    }
}
