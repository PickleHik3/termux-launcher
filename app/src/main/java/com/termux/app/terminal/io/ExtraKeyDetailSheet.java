package com.termux.app.terminal.io;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.material.M3;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.font.NerdFontSpans;

/**
 * The Edit key sheet, over the editor page: what a tap does, what a swipe up does, the label and
 * colour the cap wears, and where the key sits in its row. Edits land on the draft
 * {@link ExtraKeysLayoutModel.Key} as they happen and the page is told after each one, so its caps
 * keep up while the sheet is open; Done only puts the sheet down. Removing the key is the one
 * action that closes the sheet by itself.
 */
public final class ExtraKeyDetailSheet {

    /** How the sheet reports back to the page that owns the draft. */
    public interface Host {
        /** The key changed; redraw whatever shows it. */
        void onKeyChanged();

        /** The user asked for this key to go. */
        void onRemoveKey();

        /** Whether the key has a neighbour to swap with in that direction (-1 left, 1 right). */
        boolean canMoveKey(int delta);

        /** Swaps the key with its neighbour; the sheet stays open. */
        void onMoveKey(int delta);
    }

    private final Context context;
    private final ExtraKeysLayoutModel.Key key;
    private final ExtraKeysConstants.ExtraKeyDisplayMap displayMap;
    private final Host host;
    private final float density;

    private final int colorText;
    private final int colorSubtle;
    private final int colorCap;
    private final int colorError;

    private BottomSheetDialog dialog;
    private LinearLayout body;

    public ExtraKeyDetailSheet(@NonNull Context context, @NonNull ExtraKeysLayoutModel.Key key,
                               @NonNull ExtraKeysConstants.ExtraKeyDisplayMap displayMap,
                               @NonNull Host host) {
        this.context = context;
        this.key = key;
        this.displayMap = displayMap;
        this.host = host;
        this.density = context.getResources().getDisplayMetrics().density;
        this.colorText = M3.onSurface(context);
        this.colorSubtle = M3.onSurfaceVariant(context);
        this.colorCap = M3.color(context,
            com.google.android.material.R.attr.colorSurfaceContainerHighest,
            com.google.android.material.R.color.m3_sys_color_dark_surface_container_highest);
        this.colorError = M3.error(context);
    }

    public void show() {
        dialog = new BottomSheetDialog(context);
        LinearLayout root = column();
        root.setPadding(dp(20), 0, dp(20), dp(8));
        root.addView(new BottomSheetDragHandleView(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(header());

        body = column();
        ScrollView scroller = new ScrollView(context);
        scroller.addView(body);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroller);
        renderBody();

        root.addView(actions());

        dialog.setContentView(root);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        dialog.show();
    }

    /** "Edit key" and the close control; the cap and action title live in the On tap row. */
    private View header() {
        LinearLayout header = row();
        header.setPadding(0, dp(4), 0, dp(4));
        TextView title = new TextView(context);
        title.setText(R.string.settings_extra_keys_sheet_title);
        M3.textAppearance(title, com.google.android.material.R.attr.textAppearanceTitleLarge);
        title.setTextColor(colorText);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton close = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        close.setIconResource(R.drawable.ic_symbol_close);
        close.setContentDescription(context.getString(R.string.settings_extra_keys_sheet_done));
        close.setOnClickListener(v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    /** The editable part. Rebuilt whenever an action changes; label typing updates in place. */
    private void renderBody() {
        body.removeAllViews();

        body.addView(sectionLabel(context.getString(R.string.settings_extra_keys_sheet_tap)));
        body.addView(selectorRow(NerdFontSpans.span(context,
                ExtraKeyActionLabels.capText(key, displayMap)),
            ExtraKeyActionLabels.title(context, key), null, () ->
            openActionPicker(picked -> {
                key.key = picked.key;
                key.macro = picked.macro;
                // The label follows the action: a tool arrives with its title, a plain key with
                // none, and the field below is right there for anyone who wants their own.
                key.display = picked.display;
                changed(true);
            })));

        body.addView(sectionLabel(context.getString(R.string.settings_extra_keys_sheet_swipe_up)));
        if (key.popup == null) {
            body.addView(selectorRow(null, context.getString(R.string.settings_extra_keys_sheet_none),
                null, () -> openActionPicker(picked -> {
                    key.popup = picked;
                    changed(true);
                })));
        } else {
            final ExtraKeysLayoutModel.Key popup = key.popup;
            body.addView(selectorRow(null, ExtraKeyActionLabels.title(context, popup), () -> {
                key.popup = null;
                changed(true);
            }, () -> openActionPicker(picked -> {
                key.popup = picked;
                changed(true);
            })));
            body.addView(labelField(context.getString(R.string.settings_extra_keys_sheet_swipe_label),
                context.getString(R.string.settings_extra_keys_sheet_swipe_label_hint), popup));
        }

        body.addView(sectionLabel(context.getString(R.string.settings_extra_keys_sheet_label)));
        body.addView(labelField(context.getString(R.string.settings_extra_keys_sheet_label),
            context.getString(R.string.settings_extra_keys_sheet_label_hint), key));
        TextView note = new TextView(context);
        note.setText(R.string.settings_extra_keys_sheet_label_note);
        M3.textAppearance(note, com.google.android.material.R.attr.textAppearanceBodySmall);
        note.setTextColor(colorSubtle);
        note.setPaddingRelative(dp(2), 0, 0, 0);
        body.addView(note);

        body.addView(sectionLabel(context.getString(R.string.settings_extra_keys_color)));
        body.addView(ExtraKeyColorSwatches.build(context, key.color, role -> {
            key.color = role;
            changed(false);
        }));

        body.addView(moveRow());
    }

    /** "Move key" with a left and a right arrow, each dimmed at its end of the row. */
    private View moveRow() {
        LinearLayout moveRow = row();
        moveRow.setPadding(0, dp(12), 0, 0);
        TextView label = new TextView(context);
        label.setText(R.string.settings_extra_keys_sheet_move);
        M3.textAppearance(label, com.google.android.material.R.attr.textAppearanceLabelLarge);
        label.setTextColor(colorSubtle);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        moveRow.addView(label, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton left = moveButton("\u2190", R.string.settings_extra_keys_sheet_move_left, -1);
        MaterialButton right = moveButton("\u2192", R.string.settings_extra_keys_sheet_move_right, 1);
        moveRow.addView(left);
        moveRow.addView(right);
        Runnable refresh = () -> {
            left.setEnabled(host.canMoveKey(-1));
            right.setEnabled(host.canMoveKey(1));
        };
        left.setOnClickListener(v -> {
            host.onMoveKey(-1);
            refresh.run();
        });
        right.setOnClickListener(v -> {
            host.onMoveKey(1);
            refresh.run();
        });
        refresh.run();
        return moveRow;
    }

    private MaterialButton moveButton(@NonNull String arrow, int descriptionRes, int delta) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(arrow);
        button.setContentDescription(context.getString(descriptionRes));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(56), dp(48));
        params.setMarginStart(dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private void changed(boolean rebuild) {
        host.onKeyChanged();
        if (rebuild) renderBody();
    }

    private View actions() {
        LinearLayout actions = row();
        actions.setPadding(0, dp(12), 0, dp(8));
        MaterialButton remove = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        remove.setText(R.string.settings_extra_keys_sheet_remove);
        remove.setTextColor(colorError);
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            host.onRemoveKey();
        });
        actions.addView(remove);

        View spacer = new View(context);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));

        MaterialButton done = new MaterialButton(context);
        done.setText(R.string.settings_extra_keys_sheet_done);
        done.setOnClickListener(v -> dialog.dismiss());
        actions.addView(done);
        return actions;
    }

    /** One sheet at a time: this one hides while a picker is up and returns when it closes. */
    private void openActionPicker(@NonNull ExtraKeyActionPicker.OnPicked onPicked) {
        dialog.hide();
        ExtraKeyActionPicker.show(context, onPicked, () -> dialog.show());
    }

    private void openGlyphPicker(@NonNull EditText field) {
        dialog.hide();
        ExtraKeyGlyphPicker.show(context, field.getText().toString(), glyph -> {
            Editable text = field.getText();
            int start = clampToText(field.getSelectionStart(), text.length());
            int end = clampToText(field.getSelectionEnd(), text.length());
            text.replace(Math.min(start, end), Math.max(start, end), glyph);
        }, () -> dialog.show());
    }

    /** An unfocused field reports a -1 selection, which {@code replace} would throw on. */
    private static int clampToText(int index, int length) {
        return index < 0 ? length : Math.min(index, length);
    }

    /**
     * A label field with the glyph catalogue beside it. Typing writes straight to the key and
     * retitles the header; the page redraws its row on every change too.
     */
    private View labelField(@NonNull String label, @NonNull String hint,
                            @NonNull ExtraKeysLayoutModel.Key target) {
        LinearLayout fieldRow = row();
        fieldRow.setPadding(0, dp(4), 0, dp(4));

        TextInputLayout fieldLayout = new TextInputLayout(context);
        fieldLayout.setHint(hint);
        TextInputEditText field = new TextInputEditText(fieldLayout.getContext());
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setContentDescription(label);
        fieldLayout.addView(field, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (target.display != null) field.setText(NerdFontSpans.span(context, target.display));
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                NerdFontSpans.applyTo(context, s);
                String text = s.toString().trim();
                target.display = text.isEmpty() ? null : text;
                changed(false);
            }
        });
        fieldLayout.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fieldRow.addView(fieldLayout);

        MaterialButton glyphs = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        glyphs.setText("Ω");
        glyphs.setContentDescription(context.getString(R.string.settings_extra_keys_glyph_insert));
        glyphs.setMinWidth(dp(56));
        glyphs.setOnClickListener(v -> openGlyphPicker(field));
        LinearLayout.LayoutParams glyphParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glyphParams.setMarginStart(dp(8));
        glyphs.setLayoutParams(glyphParams);
        fieldRow.addView(glyphs);
        return fieldRow;
    }

    /**
     * A tappable value row with a trailing chevron, an optional cap in front of the value and an
     * optional clear control before the chevron.
     */
    private View selectorRow(@Nullable CharSequence capText, @NonNull String value,
                             @Nullable Runnable onClear, @NonNull Runnable onClick) {
        MaterialCardView card = M3.clickableCard(context, true);
        card.setOnClickListener(v -> onClick.run());
        LinearLayout rowView = row();
        rowView.setMinimumHeight(dp(52));
        rowView.setPaddingRelative(dp(14), dp(6), dp(8), dp(6));
        card.addView(rowView);

        if (capText != null) {
            TextView cap = cap(44, 36, 16);
            cap.setText(capText);
            LinearLayout.LayoutParams capParams = (LinearLayout.LayoutParams) cap.getLayoutParams();
            capParams.setMarginEnd(dp(12));
            rowView.addView(cap);
        }

        TextView valueView = new TextView(context);
        valueView.setText(value);
        M3.textAppearance(valueView, com.google.android.material.R.attr.textAppearanceBodyLarge);
        valueView.setTextColor(colorText);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        rowView.addView(valueView, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (onClear != null) {
            MaterialButton clear = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialIconButtonStyle);
            clear.setIconResource(R.drawable.ic_symbol_close);
            clear.setIconTint(android.content.res.ColorStateList.valueOf(colorSubtle));
            clear.setContentDescription(context.getString(
                R.string.settings_extra_keys_sheet_clear_swipe_up));
            clear.setOnClickListener(v -> onClear.run());
            rowView.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView chevron = new TextView(context);
        chevron.setText("\u203a");
        M3.textAppearance(chevron, com.google.android.material.R.attr.textAppearanceTitleLarge);
        chevron.setTextColor(colorSubtle);
        chevron.setPadding(dp(6), 0, dp(6), 0);
        rowView.addView(chevron);
        return card;
    }

    private TextView sectionLabel(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        M3.textAppearance(view, com.google.android.material.R.attr.textAppearanceLabelLarge);
        view.setTextColor(colorSubtle);
        view.setPaddingRelative(dp(2), dp(14), 0, dp(6));
        return view;
    }

    /** A key cap drawn the way the page draws one, at the size the row wants. */
    private TextView cap(int widthDp, int heightDp, int maxSp) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(colorText);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(6), 0, dp(6), 0);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(view, 10,
            maxSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        MaterialShapeDrawable background = M3.surface(context,
            com.google.android.material.R.attr.shapeAppearanceCornerMedium, colorCap);
        view.setBackground(background);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        return view;
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private int dp(float dp) {
        return Math.round(dp * density);
    }
}
