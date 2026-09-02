package com.termux.app.terminal.io;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.font.NerdFontSpans;

/**
 * Everything about one key, in a bottom sheet over the editor page: what a tap does, what a swipe
 * up does, and the label each of them shows. Edits land on the draft {@link ExtraKeysLayoutModel.Key}
 * as they happen and the page is told after each one, so its live row keeps up while the sheet is
 * open. Removing the key is the one action that closes the sheet by itself.
 */
public final class ExtraKeyDetailSheet {

    /** How the sheet reports back to the page that owns the draft. */
    public interface Host {
        /** The key changed; redraw whatever shows it. */
        void onKeyChanged();

        /** The user asked for this key to go. */
        void onRemoveKey();
    }

    private final Context context;
    private final ExtraKeysLayoutModel.Key key;
    private final ExtraKeysConstants.ExtraKeyDisplayMap displayMap;
    private final Host host;
    private final float density;

    private final int colorText;
    private final int colorSubtle;
    private final int colorOutline;
    private final int colorCap;
    private final int colorError;

    private BottomSheetDialog dialog;
    private LinearLayout body;
    private TextView headerCap;
    private TextView headerTitle;
    private TextView headerDetail;

    public ExtraKeyDetailSheet(@NonNull Context context, @NonNull ExtraKeysLayoutModel.Key key,
                               @NonNull ExtraKeysConstants.ExtraKeyDisplayMap displayMap,
                               @NonNull Host host) {
        this.context = context;
        this.key = key;
        this.displayMap = displayMap;
        this.host = host;
        this.density = context.getResources().getDisplayMetrics().density;
        this.colorText = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        this.colorSubtle = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY);
        this.colorOutline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant, 0x33FFFFFF);
        this.colorCap = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF2A3140);
        this.colorError = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorError, 0xFFF2B8B5);
    }

    public void show() {
        dialog = new BottomSheetDialog(context);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(8), dp(20), dp(8));

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

    /** Cap, action title and identifier; retitled in place as the fields below change. */
    private View header() {
        LinearLayout header = row();
        header.setPadding(0, dp(8), 0, dp(12));
        headerCap = cap(64, 44, 20f);
        header.addView(headerCap);

        LinearLayout titles = column();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dp(16));
        titles.setLayoutParams(titleParams);
        headerTitle = new TextView(context);
        headerTitle.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        headerTitle.setTextColor(colorText);
        headerTitle.setMaxLines(2);
        headerTitle.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(headerTitle);
        headerDetail = new TextView(context);
        headerDetail.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        headerDetail.setTextColor(colorSubtle);
        headerDetail.setSingleLine(true);
        headerDetail.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titles.addView(headerDetail);
        header.addView(titles);
        refreshHeader();
        return header;
    }

    private void refreshHeader() {
        headerCap.setText(NerdFontSpans.span(context, ExtraKeyActionLabels.capText(key, displayMap)));
        headerTitle.setText(ExtraKeyActionLabels.title(context, key));
        String detail = ExtraKeyActionLabels.detail(key);
        headerDetail.setText(detail == null ? "" : detail);
        headerDetail.setVisibility(detail == null ? View.GONE : View.VISIBLE);
    }

    /** The editable part. Rebuilt whenever an action changes; label typing updates in place. */
    private void renderBody() {
        body.removeAllViews();

        body.addView(sectionLabel(context.getString(R.string.settings_extra_keys_sheet_tap)));
        body.addView(selectorRow(ExtraKeyActionLabels.title(context, key), null, () ->
            openActionPicker(picked -> {
                key.key = picked.key;
                key.macro = picked.macro;
                // The label follows the action: a tool arrives with its title, a plain key with
                // none, and the field below is right there for anyone who wants their own.
                key.display = picked.display;
                changed(true);
            })));
        body.addView(labelField(context.getString(R.string.settings_extra_keys_sheet_label),
            context.getString(R.string.settings_extra_keys_sheet_label_hint), key));

        body.addView(sectionLabel(context.getString(R.string.settings_extra_keys_sheet_swipe_up)));
        if (key.popup == null) {
            body.addView(selectorRow(context.getString(R.string.settings_extra_keys_sheet_none),
                null, () -> openActionPicker(picked -> {
                    key.popup = picked;
                    changed(true);
                })));
        } else {
            final ExtraKeysLayoutModel.Key popup = key.popup;
            body.addView(selectorRow(ExtraKeyActionLabels.title(context, popup), () -> {
                key.popup = null;
                changed(true);
            }, () -> openActionPicker(picked -> {
                key.popup = picked;
                changed(true);
            })));
            body.addView(labelField(context.getString(R.string.settings_extra_keys_sheet_swipe_label),
                context.getString(R.string.settings_extra_keys_sheet_swipe_label_hint), popup));
        }
    }

    private void changed(boolean rebuild) {
        refreshHeader();
        host.onKeyChanged();
        if (rebuild) renderBody();
    }

    private View actions() {
        LinearLayout actions = row();
        actions.setPadding(0, dp(12), 0, dp(8));
        MaterialButton remove = new MaterialButton(context, null,
            com.google.android.material.R.attr.borderlessButtonStyle);
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

        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setHint(hint);
        field.setContentDescription(label);
        field.setTextColor(colorText);
        field.setHintTextColor(colorSubtle);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
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
        field.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fieldRow.addView(field);

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

    /** A tappable value row with a trailing chevron, and an optional clear control before it. */
    private View selectorRow(@NonNull String value, @Nullable Runnable onClear,
                             @NonNull Runnable onClick) {
        LinearLayout rowView = row();
        rowView.setMinimumHeight(dp(52));
        rowView.setPadding(dp(14), dp(6), dp(8), dp(6));
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.TRANSPARENT);
        outline.setStroke(Math.max(1, dp(1)), colorOutline);
        outline.setCornerRadius(dp(14));
        rowView.setBackground(outline);
        rowView.setOnClickListener(v -> onClick.run());

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(colorText);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        valueView.setMaxLines(2);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        rowView.addView(valueView, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (onClear != null) {
            ImageView clear = new ImageView(context);
            clear.setImageResource(R.drawable.ic_symbol_close);
            clear.setColorFilter(colorSubtle);
            clear.setContentDescription(context.getString(
                R.string.settings_extra_keys_sheet_clear_swipe_up));
            clear.setPadding(dp(10), dp(10), dp(10), dp(10));
            clear.setOnClickListener(v -> onClear.run());
            rowView.addView(clear, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }

        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextColor(colorSubtle);
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        chevron.setPadding(dp(6), 0, dp(6), 0);
        rowView.addView(chevron);
        return rowView;
    }

    private TextView sectionLabel(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        view.setTextColor(colorSubtle);
        view.setPadding(dp(2), dp(14), 0, dp(6));
        return view;
    }

    /** A key cap drawn the way the list draws one, at the size the header wants. */
    private TextView cap(int widthDp, int heightDp, float maxSp) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(colorText);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(6), 0, dp(6), 0);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(view, 10,
            Math.round(maxSp), 1, TypedValue.COMPLEX_UNIT_SP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colorCap);
        background.setCornerRadius(dp(12));
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
