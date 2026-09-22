package com.termux.app.help;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.termux.app.notice.TerminalDress;
import com.termux.app.statusbar.StatusBarLensView;
import com.termux.app.wall.PaneWallPage;

/**
 * How the help centre is dressed, and the handful of view shapes it is built from: the launcher's
 * own fill, hairline and text colours, and the accent of the place help was opened from. Nothing
 * here reads a theme resource directly, so light, dark, black, Material You and the launcher's own
 * scheme all arrive through the dress.
 *
 * <p>Sizes are the reading sizes, not the card sizes: the panel is a document. Anything with a
 * fixed height is single-line and ellipsized, and everything else wraps, so a 1.3× font scale
 * makes the page longer rather than clipping it.
 */
final class HelpStyle {

    /** The reading column never grows past this, however wide the screen is. */
    static final int MAX_WIDTH_DP = 560;

    final TerminalDress dress;
    final int accent;

    private final Context context;
    private final float density;

    private HelpStyle(Context context, PaneWallPage place) {
        this.context = context;
        this.dress = TerminalDress.stored(context);
        this.accent = StatusBarLensView.accentFor(context,
            place == null ? PaneWallPage.TERMINAL : place);
        this.density = context.getResources().getDisplayMetrics().density;
    }

    static HelpStyle of(Context context, PaneWallPage place) {
        return new HelpStyle(context, place);
    }

    int dp(float value) {
        return Math.round(value * density);
    }

    /** The opaque page fill help reads on: nothing showing through it and no card edge around it. */
    int pageColor() {
        return ColorUtils.setAlphaComponent(dress.fillColor, 255);
    }

    int textColor() {
        return dress.textColor;
    }

    int dimTextColor() {
        return ColorUtils.setAlphaComponent(dress.textColor, 168);
    }

    /** A section heading: the shared heading over a group of groups. */
    TextView sectionLabel(String label) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(12);
        view.setAllCaps(true);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(dimTextColor());
        view.setPadding(0, dp(14), 0, dp(4));
        return view;
    }

    /** A group's own label, under its section. */
    TextView groupLabel(String label) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(dress.textColor);
        view.setPadding(0, dp(10), 0, dp(2));
        return view;
    }

    /** A sentence of the page. */
    TextView body(String copy) {
        TextView view = new TextView(context);
        view.setText(copy);
        view.setTextSize(14);
        view.setTextColor(dress.textColor);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(0, dp(4), 0, dp(2));
        return view;
    }

    /** The one instruction the topic is really about. */
    TextView instruction(String copy) {
        TextView view = body(copy);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(accent);
        return view;
    }

    /** The state line: not visible, already running, or the reason an action cannot run. */
    TextView note(String copy) {
        TextView view = body(copy);
        view.setTextColor(dimTextColor());
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(dress.textColor, 20));
        shape.setCornerRadius(dp(10));
        view.setBackground(shape);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        return view;
    }

    /**
     * The card a recorded clip plays in: the same corner and the same faint fill as the note and
     * the rows around it, so a moving picture sits in the page like everything else. The fill is
     * only ever seen for the instant before the first frame arrives.
     */
    GradientDrawable clipCard() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(dress.textColor, 20));
        shape.setCornerRadius(dp(10));
        return shape;
    }

    /**
     * One thing to read next: its name, and the sentence that says what it is. A row is a whole
     * touch target, never smaller than a thumb, and named for a reader.
     */
    TextView row(String name, String summary, String label, boolean enabled, Runnable onClick) {
        TextView view = new TextView(context);
        StringBuilder copy = new StringBuilder();
        if (label != null) copy.append(label).append(" · ");
        copy.append(name);
        if (summary != null && !summary.isEmpty()) copy.append('\n').append(summary);
        view.setText(copy.toString());
        view.setContentDescription(name);
        view.setTextSize(14);
        view.setTextColor(enabled ? dress.textColor : dimTextColor());
        view.setLineSpacing(dp(2), 1f);
        view.setMinHeight(dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(accent, enabled ? 20 : 8));
        shape.setCornerRadius(dp(10));
        view.setBackground(shape);
        view.setEnabled(enabled);
        view.setFocusable(enabled);
        view.setClickable(enabled);
        if (enabled && onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** A button of the panel. */
    TextView button(String label, boolean enabled, Runnable onClick) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setContentDescription(label);
        view.setTextSize(14);
        view.setAllCaps(false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setMinWidth(dp(48));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setTextColor(enabled ? accent : ColorUtils.setAlphaComponent(dress.textColor, 97));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(accent, enabled ? 28 : 12));
        shape.setCornerRadius(dp(10));
        view.setBackground(shape);
        view.setEnabled(enabled);
        view.setFocusable(enabled);
        view.setClickable(enabled);
        if (enabled && onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** A link in the page: the utility links, and the two that leave for the web. */
    TextView link(String label, Runnable onClick) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setContentDescription(label);
        view.setTextSize(14);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(accent);
        view.setMinHeight(dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        view.setClickable(true);
        view.setFocusable(true);
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** Help home's search row: it looks like the field it opens, and summons no keyboard. */
    TextView fieldButton(String hint, Runnable onClick) {
        TextView view = new TextView(context);
        view.setText(hint);
        view.setContentDescription(hint);
        view.setTextSize(14);
        view.setTextColor(dimTextColor());
        view.setMinHeight(dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackground(fieldBackground());
        view.setClickable(true);
        view.setFocusable(true);
        if (onClick != null) view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** The real text field, for search and for the glossary's filter. */
    EditText field(String hint) {
        EditText view = new EditText(context);
        view.setHint(hint);
        view.setContentDescription(hint);
        view.setTextSize(14);
        view.setTextColor(dress.textColor);
        view.setHintTextColor(dimTextColor());
        view.setMinHeight(dp(48));
        view.setSingleLine(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        view.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setBackground(fieldBackground());
        return view;
    }

    private GradientDrawable fieldBackground() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(ColorUtils.setAlphaComponent(dress.textColor, 16));
        shape.setStroke(Math.max(1, Math.round(dress.strokeWidthPx)), dress.strokeColor);
        shape.setCornerRadius(dp(12));
        return shape;
    }

    View divider() {
        View view = new View(context);
        view.setBackgroundColor(ColorUtils.setAlphaComponent(dress.textColor, 32));
        return view;
    }

    LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    /** A hairline's own row: a plain view has no height of its own to wrap. */
    LinearLayout.LayoutParams hairline(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
        params.topMargin = topMargin;
        return params;
    }

    LinearLayout.LayoutParams stacked(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    LinearLayout.LayoutParams beside(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = leftMargin;
        return params;
    }

    LinearLayout.LayoutParams filling() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }
}
