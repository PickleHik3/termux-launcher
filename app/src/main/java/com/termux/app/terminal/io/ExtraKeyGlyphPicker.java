package com.termux.app.terminal.io;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.settings.preferences.SharedPreferenceUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The glyph half of the extra-keys editor: a searchable sheet of characters worth putting on a key
 * cap, feeding the display and swipe-up label fields.
 *
 * <p>Caps draw with the UI font, not the terminal font, so a Nerd-Font or Powerline code point only
 * exists here when the system font happens to carry it. Every candidate is therefore measured with
 * {@link Paint#hasGlyph(String)} against a paint set up like the cap label before it is offered —
 * the alternative was a picker full of tofu boxes that each looked like a working key until it was
 * saved.
 *
 * <p>Like {@link ExtraKeyActionPicker} the caller may pass an {@code onClosed} runnable, invoked
 * once when the sheet is gone, because the row editor hides itself while a picker is up (F-05).
 */
public final class ExtraKeyGlyphPicker {

    /** What the picker hands back: the character to insert, never a name or a code point. */
    public interface OnPicked {
        void onPicked(@NonNull String glyph);
    }

    /** Preference holding recently picked glyphs as space separated hex code points. */
    private static final String KEY_RECENT_GLYPHS = "extra_key_glyph_recents";
    /** Two rows of recents at the widest column count; beyond that the shelf stops being a shelf. */
    private static final int RECENT_LIMIT = 16;
    private static final int COLUMNS = 6;

    /** Parsed once per process: the file never changes at runtime and parsing it is not free. */
    @Nullable private static ExtraKeyGlyphCatalogue shippedCatalogue;

    private final Context context;
    private final float density;
    private final int colorText;
    private final int colorSubtle;
    private final int colorOutline;
    private final int colorAccent;
    private final int colorPanel;

    @NonNull private final ExtraKeyGlyphCatalogue catalogue;
    @Nullable private Runnable onClosed;

    @Nullable private TextView previewCap;
    @Nullable private TextView previewName;

    public ExtraKeyGlyphPicker(@NonNull Context context) {
        this.context = context;
        this.density = context.getResources().getDisplayMetrics().density;
        this.colorText = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface, 0xFFFFFFFF);
        this.colorSubtle = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFFB0B0B0);
        this.colorOutline = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOutlineVariant, 0x33FFFFFF);
        this.colorAccent = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorPrimary, 0xFF80DEEA);
        this.colorPanel = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh, 0xFF202837);
        this.catalogue = drawableCatalogue(context);
    }

    public static void show(@NonNull Context context, @NonNull OnPicked onPicked,
                            @Nullable Runnable onClosed) {
        ExtraKeyGlyphPicker picker = new ExtraKeyGlyphPicker(context);
        picker.onClosed = onClosed;
        picker.open(onPicked);
    }

    /** The shipped catalogue as parsed, unfiltered; the picker itself always uses the filtered one. */
    @NonNull
    public static synchronized ExtraKeyGlyphCatalogue shippedCatalogue(@NonNull Resources resources) {
        if (shippedCatalogue != null) return shippedCatalogue;
        ExtraKeyGlyphCatalogue parsed = ExtraKeyGlyphCatalogue.empty();
        try (InputStream input = resources.openRawResource(R.raw.extra_key_glyphs)) {
            parsed = ExtraKeyGlyphCatalogue.parse(input);
        } catch (IOException | Resources.NotFoundException e) {
            android.util.Log.w("ExtraKeyGlyphs", "Glyph catalogue unreadable", e);
        }
        shippedCatalogue = parsed;
        return parsed;
    }

    /** The catalogue reduced to what this device's UI font can actually draw at cap size. */
    @NonNull
    public static ExtraKeyGlyphCatalogue drawableCatalogue(@NonNull Context context) {
        ExtraKeyGlyphCatalogue source = shippedCatalogue(context.getResources());
        Paint paint = capPaint(context);
        return source.filter(glyph -> paint.hasGlyph(glyph.text));
    }

    /**
     * A paint matching the cap label: same typeface and the largest size the cap autosizer may
     * choose, because a font can carry a glyph at one size and fall back at another.
     */
    @NonNull
    private static Paint capPaint(@NonNull Context context) {
        Paint paint = new Paint();
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f,
            context.getResources().getDisplayMetrics()));
        return paint;
    }

    public void open(@NonNull OnPicked onPicked) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout root = column();
        root.setPadding(pad(20), pad(12), pad(20), 0);
        GradientDrawable sheetBackground = new GradientDrawable();
        sheetBackground.setCornerRadii(new float[] {
            pad(20), pad(20), pad(20), pad(20), 0, 0, 0, 0});
        sheetBackground.setColor(withAlpha(colorPanel, 0xF7));
        root.setBackground(sheetBackground);

        LinearLayout headerRow = row();
        TextView back = new TextView(context);
        back.setText("←");
        back.setContentDescription(context.getString(R.string.settings_extra_keys_action_back));
        back.setTextColor(colorText);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        back.setGravity(Gravity.CENTER);
        back.setMinimumWidth(pad(48));
        back.setMinimumHeight(pad(48));
        back.setOnClickListener(v -> dialog.dismiss());
        headerRow.addView(back);
        TextView titleView = title(context.getString(
            R.string.settings_extra_keys_glyph_picker_title));
        headerRow.addView(titleView);
        root.addView(headerRow);

        root.addView(buildPreview());

        LinearLayout searchRow = row();
        EditText search = new EditText(context);
        search.setHint(R.string.settings_extra_keys_glyph_search_hint);
        search.setSingleLine(true);
        search.setTextColor(colorText);
        search.setHintTextColor(colorSubtle);
        search.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchRow.addView(search);
        TextView clear = new TextView(context);
        clear.setText("×");
        clear.setContentDescription(context.getString(
            R.string.settings_extra_keys_action_clear_search));
        clear.setTextColor(colorSubtle);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        clear.setGravity(Gravity.CENTER);
        clear.setMinimumWidth(pad(48));
        clear.setMinimumHeight(pad(48));
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(v -> search.setText(""));
        searchRow.addView(clear);
        root.addView(searchRow);

        LinearLayout results = column();
        ScrollView scroller = new ScrollView(context);
        scroller.addView(results);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroller.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottom = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.ime()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, 0, 0, bottom);
            return insets;
        });
        root.addView(scroller);

        OnPicked pick = glyph -> {
            rememberRecent(glyph);
            dialog.dismiss();
            onPicked.onPicked(glyph);
        };
        renderResults(results, "", pick);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                renderResults(results, s.toString(), pick);
                scroller.scrollTo(0, 0);
            }
        });

        dialog.setContentView(root);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            sheet.setBackgroundColor(0x00000000);
            ViewGroup.LayoutParams params = sheet.getLayoutParams();
            if (params != null) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                sheet.setLayoutParams(params);
            }
        }
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
        dialog.setOnDismissListener(d -> {
            if (onClosed != null) onClosed.run();
        });
        dialog.show();
    }

    /** The sticky swatch: a cap drawn exactly as the row will draw it, plus the glyph's name. */
    @NonNull
    private View buildPreview() {
        LinearLayout preview = row();
        preview.setPadding(0, pad(4), 0, pad(4));

        TextView cap = new TextView(context);
        cap.setGravity(Gravity.CENTER);
        cap.setTextColor(colorText);
        cap.setMinimumWidth(pad(56));
        cap.setMinimumHeight(pad(48));
        cap.setBackground(outline());
        applyCapLabelSizing(cap);
        preview.addView(cap);
        previewCap = cap;

        TextView name = new TextView(context);
        name.setTextColor(colorSubtle);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMargins(pad(12), 0, 0, 0);
        name.setLayoutParams(nameParams);
        preview.addView(name);
        previewName = name;
        return preview;
    }

    private void showPreview(@Nullable ExtraKeyGlyphCatalogue.Glyph glyph) {
        if (previewCap == null || previewName == null) return;
        previewCap.setText(glyph == null ? "" : glyph.text);
        previewName.setText(glyph == null ? "" : glyph.name + " · U+" + glyph.hex());
    }

    private void renderResults(@NonNull LinearLayout results, @NonNull String query,
                               @NonNull OnPicked pick) {
        results.removeAllViews();
        String needle = query.trim();

        if (needle.isEmpty()) {
            List<ExtraKeyGlyphCatalogue.Glyph> recent = recentGlyphs();
            if (!recent.isEmpty()) {
                results.addView(header(context.getString(
                    R.string.settings_extra_keys_glyph_recent)));
                addGrid(results, recent, pick);
            }
            for (String category : ExtraKeyGlyphCatalogue.CATEGORIES) {
                List<ExtraKeyGlyphCatalogue.Glyph> glyphs = catalogue.byCategory(category);
                if (glyphs.isEmpty()) continue;
                results.addView(header(categoryLabel(category)));
                addGrid(results, glyphs, pick);
            }
            showPreview(null);
            return;
        }

        List<ExtraKeyGlyphCatalogue.Glyph> hits = catalogue.search(needle);
        if (hits.isEmpty()) {
            results.addView(header(context.getString(R.string.settings_extra_keys_action_no_match)));
            showPreview(null);
            return;
        }
        // The best hit fills the swatch straight away, so a search that already found the glyph
        // needs no press to be read at cap size.
        showPreview(hits.get(0));
        addGrid(results, hits, pick);
    }

    private void addGrid(@NonNull LinearLayout results,
                         @NonNull List<ExtraKeyGlyphCatalogue.Glyph> glyphs,
                         @NonNull OnPicked pick) {
        LinearLayout gridRow = null;
        for (int index = 0; index < glyphs.size(); index++) {
            if (index % COLUMNS == 0) {
                gridRow = row();
                results.addView(gridRow);
            }
            gridRow.addView(swatch(glyphs.get(index), pick));
        }
        // The last row keeps cap-sized cells rather than stretching the leftovers across the sheet.
        int remainder = glyphs.size() % COLUMNS;
        if (remainder != 0 && gridRow != null) {
            for (int filler = remainder; filler < COLUMNS; filler++) {
                View spacer = new View(context);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                spacer.setLayoutParams(params);
                gridRow.addView(spacer);
            }
        }
    }

    /** One candidate, cap-shaped and cap-sized: what is shown here is what the row will draw. */
    @NonNull
    private View swatch(@NonNull ExtraKeyGlyphCatalogue.Glyph glyph, @NonNull OnPicked pick) {
        TextView cap = new TextView(context);
        cap.setText(glyph.text);
        cap.setContentDescription(glyph.name);
        cap.setTextColor(colorText);
        cap.setGravity(Gravity.CENTER);
        cap.setMinimumHeight(pad(48));
        cap.setPadding(pad(4), pad(6), pad(4), pad(6));
        cap.setBackground(outline());
        applyCapLabelSizing(cap);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(pad(2), pad(2), pad(2), pad(2));
        cap.setLayoutParams(params);
        cap.setOnClickListener(v -> {
            showPreview(glyph);
            pick.onPicked(glyph.text);
        });
        cap.setOnLongClickListener(v -> {
            // Long press previews without committing: the name and code point are the only way to
            // tell two near-identical marks apart at 48dp.
            showPreview(glyph);
            v.setBackground(accentOutline());
            v.postDelayed(() -> v.setBackground(outline()), 600);
            return true;
        });
        return cap;
    }

    /** Same one-line, autosized label the editor gives a real cap, so the preview does not lie. */
    private void applyCapLabelSizing(@NonNull TextView label) {
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 9, 15, 1,
            TypedValue.COMPLEX_UNIT_SP);
    }

    @NonNull
    private String categoryLabel(@NonNull String category) {
        switch (category) {
            case ExtraKeyGlyphCatalogue.CATEGORY_ARROWS:
                return context.getString(R.string.settings_extra_keys_glyph_category_arrows);
            case ExtraKeyGlyphCatalogue.CATEGORY_BLOCKS:
                return context.getString(R.string.settings_extra_keys_glyph_category_blocks);
            case ExtraKeyGlyphCatalogue.CATEGORY_SHAPES:
                return context.getString(R.string.settings_extra_keys_glyph_category_shapes);
            case ExtraKeyGlyphCatalogue.CATEGORY_POWERLINE:
                return context.getString(R.string.settings_extra_keys_glyph_category_powerline);
            case ExtraKeyGlyphCatalogue.CATEGORY_TECHNICAL:
                return context.getString(R.string.settings_extra_keys_glyph_category_technical);
            case ExtraKeyGlyphCatalogue.CATEGORY_TERMINAL_MARKS:
                return context.getString(R.string.settings_extra_keys_glyph_category_terminal_marks);
            default:
                return category;
        }
    }

    /**
     * Recents are stored as code points and resolved through the filtered catalogue, so a glyph
     * the current font can no longer draw silently leaves the shelf instead of returning as tofu.
     */
    @NonNull
    private List<ExtraKeyGlyphCatalogue.Glyph> recentGlyphs() {
        List<ExtraKeyGlyphCatalogue.Glyph> recent = new ArrayList<>();
        for (String hex : storedRecents().split("\\s+")) {
            int codePoint = ExtraKeyGlyphCatalogue.parseCodePoint(hex.trim().toUpperCase(Locale.ROOT));
            if (codePoint < 0) continue;
            ExtraKeyGlyphCatalogue.Glyph glyph = catalogue.byCodePoint(codePoint);
            if (glyph != null && !recent.contains(glyph)) recent.add(glyph);
            if (recent.size() >= RECENT_LIMIT) break;
        }
        return recent;
    }

    private void rememberRecent(@NonNull String glyph) {
        if (glyph.isEmpty()) return;
        String hex = String.format(Locale.ROOT, "%04X", glyph.codePointAt(0));
        StringBuilder updated = new StringBuilder(hex);
        int kept = 1;
        for (String previous : storedRecents().split("\\s+")) {
            String candidate = previous.trim().toUpperCase(Locale.ROOT);
            if (candidate.isEmpty() || candidate.equals(hex)) continue;
            if (ExtraKeyGlyphCatalogue.parseCodePoint(candidate) < 0) continue;
            updated.append(' ').append(candidate);
            if (++kept >= RECENT_LIMIT) break;
        }
        SharedPreferences preferences = preferences();
        if (preferences == null) return;
        SharedPreferenceUtils.setString(preferences, KEY_RECENT_GLYPHS, updated.toString(), false);
    }

    @NonNull
    private String storedRecents() {
        SharedPreferences preferences = preferences();
        if (preferences == null) return "";
        String value = SharedPreferenceUtils.getString(preferences, KEY_RECENT_GLYPHS, "", false);
        return value == null ? "" : value;
    }

    @Nullable
    private SharedPreferences preferences() {
        TermuxAppSharedPreferences appPreferences = TermuxAppSharedPreferences.build(context);
        return appPreferences == null ? null : appPreferences.getSharedPreferences();
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

    private TextView title(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(colorText);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        view.setPadding(pad(4), 0, 0, 0);
        return view;
    }

    private TextView header(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setAllCaps(true);
        view.setTextColor(colorSubtle);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        view.setPadding(0, pad(12), 0, pad(4));
        return view;
    }

    private GradientDrawable outline() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x00000000);
        drawable.setStroke(Math.max(1, pad(0.5f)), colorOutline);
        drawable.setCornerRadius(pad(12));
        return drawable;
    }

    private GradientDrawable accentOutline() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(withAlpha(colorAccent, 0x22));
        drawable.setStroke(Math.max(1, pad(1.5f)), colorAccent);
        drawable.setCornerRadius(pad(12));
        return drawable;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int pad(float dp) {
        return Math.round(dp * density);
    }
}
