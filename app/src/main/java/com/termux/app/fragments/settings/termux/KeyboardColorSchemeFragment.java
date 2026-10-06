package com.termux.app.fragments.settings.termux;

import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.termux.app.notice.AppNotice;
import com.termux.app.place.PlaceLayoutStore;
import com.termux.app.place.PlaceOrientation;
import com.termux.R;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardColorScheme;
import com.termux.app.terminal.inappkeyboard.InAppKeyboardPaletteFactory;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import juloo.keyboard2.Config;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.LayoutModifier;
import juloo.keyboard2.Pointers;
import juloo.keyboard2.Theme;

/** Interactive swatch-based, per-key keyboard color scheme editor. */
@Keep
public class KeyboardColorSchemeFragment extends Fragment {

    private TermuxAppSharedPreferences mPreferences;
    private InAppKeyboardColorScheme mScheme;
    private Keyboard2View mKeyboard;
    private LinearLayout mSwatchGrid;
    private final List<View> mSwatchViews = new ArrayList<>();
    private final List<View> mSwatchBadges = new ArrayList<>();
    private final List<View> mSwatchItems = new ArrayList<>();
    /** The six role chips by {@link #ROLE_ORDER} index; the one at {@link #mSelectedRoleIndex} is on. */
    private Chip[] mRoleChips = new Chip[0];
    private static final int KEY_FILL_INDEX = 0;
    private int mSelectedRoleIndex;
    private TextView mEditingTitle;
    private int mSelectedSwatch;
    private InAppKeyboardColorScheme.Role mSelectedRole =
        InAppKeyboardColorScheme.Role.KEY_BACKGROUND;
    /** The Background chip paints through swatch taps alone, so it sits outside the roles. */
    private boolean mPaintingBackground;
    private boolean mEditingSwatches;
    private static final int SWATCH_GRID_COLUMNS = 8;
    private static final int SWATCH_DP = 32;
    /** The page's one gap between blocks, in dp. */
    private static final int GAP_DP = 8;
    /** The smallest share of its natural size the preview shrinks to before the page scrolls. */
    private static final float PREVIEW_MIN_SCALE = 0.4f;
    static final String TAG_SWATCH_GRID = "keyboard_theme_swatch_grid";
    static final String TAG_PALETTE_CARD = "keyboard_theme_palette_card";
    /**
     * Material role behind every slot, mirroring
     * {@link InAppKeyboardPaletteFactory#defaultEditorSwatches}. These stay untranslated on
     * purpose: they are the API role names a theme author matches against, and they only appear in
     * content descriptions and the hex dialog, never as on-screen chip text. Slots 06 and 10 are
     * both {@code colorSurface} in the factory, which is named rather than hidden.
     */
    private static final String[] SLOT_ROLE_IDS = {
        "surfaceContainerHigh", "primary", "secondary", "onSurface", "onSurfaceVariant",
        "secondaryContainer", "surface", "surfaceContainerHighest", "error", "tertiary",
        "primaryContainer", "onPrimary", "onSecondary", "onTertiary", "outlineVariant",
        "errorContainer", "surface (same as base06)", "surfaceContainer",
        "error + onSurface 20%", "tertiary + onSurface 20%", "primary + onSurface 20%",
        "secondary + onSurface 20%", "tertiary + primary 50%", "primary + secondary 50%"
    };
    private static final String FONT_DIR_NAME = "inapp-keyboard";
    private static final String FONT_FILE_NAME = "label-font.ttf";

    private ActivityResultLauncher<String[]> mFontPickerLauncher;
    private FrameLayout mPreviewHolder;
    private TextView mFontSummary;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFontPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onFontPicked);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        android.content.Context context = requireContext();
        mPreferences = TermuxAppSharedPreferences.build(context);
        if (mPreferences == null)
            throw new IllegalStateException("Termux preferences unavailable");
        mScheme = InAppKeyboardColorScheme.fromJson(context,
            mPreferences.getInAppKeyboardColorScheme());

        FitRoot root = new FitRoot(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));

        syncThemePreference();

        // 1. The live preview, which every control below repaints.
        mPreviewHolder = new FrameLayout(context);
        root.addView(mPreviewHolder, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildPreview();

        // 2. One supporting line; the pencil and reset glyphs sit in the sentences, the same marks
        // as the card's buttons.
        TextView instructions = new TextView(context);
        instructions.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        int instructionColor = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);
        instructions.setTextColor(instructionColor);
        instructions.setText(android.text.TextUtils.expandTemplate(
            getText(R.string.termux_keyboard_color_scheme_instructions),
            inlineGlyph(context, R.drawable.ic_symbol_edit, instructionColor, instructions),
            inlineGlyph(context, R.drawable.ic_symbol_restart, instructionColor, instructions)));
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        instructionParams.topMargin = dp(GAP_DP);
        root.addView(instructions, instructionParams);

        // 3. The role chips, one wrapping group; each picks the part the swatches below paint.
        // Hints, keyboard and labels need no row titles of their own: the chip names say it.
        mRoleChips = new Chip[ROLE_ORDER.length];
        addRoleChips(context, root);

        // 4. The 24 swatches: the colour choices for the selected role.
        mSwatchGrid = new LinearLayout(context);
        mSwatchGrid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = dp(GAP_DP);
        mSwatchGrid.setTag(TAG_SWATCH_GRID);
        root.addView(mSwatchGrid, gridParams);

        // 5. The card: the editing capsule, then the Font chooser.
        // The card carries its own params, so the gap above it is not thrown away.
        View card = buildPaletteCard(context);
        root.addView(card, card.getLayoutParams());
        selectRole(KEY_FILL_INDEX);
        createSwatches();

        android.widget.ScrollView scroll = new android.widget.ScrollView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                // The viewport is the preview's budget: what the controls leave of it.
                root.mViewportPx = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                    ? 0 : MeasureSpec.getSize(heightSpec);
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        scroll.setFillViewport(true);
        scroll.addView(root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /**
     * The page column. It sizes the preview last: every other block is measured first, and the
     * keyboard takes the height that is left, scaled down as a whole (width and height by the same
     * factor, so the keys keep their shape) rather than squeezed, floored at
     * {@link #PREVIEW_MIN_SCALE} so a huge font scale scrolls instead of erasing it.
     */
    private final class FitRoot extends LinearLayout {
        /** Height the page may fill, 0 while unknown (the preview then keeps its own size). */
        int mViewportPx;

        FitRoot(@NonNull android.content.Context context) { super(context); }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            if (mPreviewHolder != null && mKeyboard != null
                    && mKeyboard.getParent() == mPreviewHolder
                    && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED)
                sizePreview(MeasureSpec.getSize(widthSpec));
            super.onMeasure(widthSpec, heightSpec);
        }

        private void sizePreview(int rootWidth) {
            int contentWidth = rootWidth - getPaddingLeft() - getPaddingRight();
            if (contentWidth <= 0) return;
            int others = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == mPreviewHolder || child.getVisibility() == GONE) continue;
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
                child.measure(
                    MeasureSpec.makeMeasureSpec(
                        Math.max(0, contentWidth - lp.leftMargin - lp.rightMargin),
                        MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                others += child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }
            // The keyboard's own height at full width, with nothing capping it.
            mKeyboard.measure(MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(2000), MeasureSpec.AT_MOST));
            int natural = mKeyboard.getMeasuredHeight();
            if (natural <= 0) return;
            float scale = 1f;
            if (mViewportPx > 0) {
                int budget = mViewportPx - getPaddingTop() - getPaddingBottom() - others;
                scale = Math.max(PREVIEW_MIN_SCALE, Math.min(1f, budget / (float) natural));
            }
            int width = Math.round(contentWidth * scale);
            int height = Math.round(natural * scale);
            ViewGroup.LayoutParams holderParams = mPreviewHolder.getLayoutParams();
            if (holderParams.height != height) {
                holderParams.height = height;
                mPreviewHolder.setLayoutParams(holderParams);
            }
            ViewGroup.LayoutParams keyboardParams = mKeyboard.getLayoutParams();
            if (keyboardParams.width != width || keyboardParams.height != height) {
                keyboardParams.width = width;
                keyboardParams.height = height;
                mKeyboard.setLayoutParams(keyboardParams);
            }
        }
    }

    /** (Re)creates the preview keyboard; Config is immutable, so a new typeface needs a new view. */
    private void rebuildPreview() {
        android.content.Context context = requireContext();
        Config.Builder config = new Config.Builder(getResources(), new Config.IKeyEventHandler() {
            @Override public void key_down(KeyValue value, boolean isSwipe) {}
            @Override public void key_up(KeyValue value, Pointers.Modifiers modifiers) {}
            @Override public void mods_changed(Pointers.Modifiers modifiers) {}
            @Override public void suggestion_entered(String text) {}
        });
        config.hapticEnabled = false;
        config.keySoundEnabled = false;
        config.labelFont = previewLabelFont(context, mPreferences.getInAppKeyboardFontPath());
        mKeyboard = new Keyboard2View(context, config.build(), buildPreviewPalette(context));
        KeyboardData previewLayout = KeyboardData.load(getResources(),
            juloo.keyboard2.R.xml.termux_launcher_qwerty);
        if (previewLayout != null) {
            LayoutModifier.LayoutOptions options = new LayoutModifier.LayoutOptions(
                true, false, true,
                com.termux.app.terminal.inappkeyboard.InAppKeyboardExtraKeys.resolve(
                    mPreferences.getInAppKeyboardExtraKeys()));
            mKeyboard.setKeyboard(LayoutModifier.modify(previewLayout, options, getResources()));
        }
        // The height is the layout's, in the orientation the phone is being held in.
        mKeyboard.setHeightScale(new PlaceLayoutStore(mPreferences).keyboardHeightScale(
            PlaceOrientation.of(getResources().getConfiguration())));
        mKeyboard.setKeyMarginScale(mPreferences.getInAppKeyboardKeyMarginScale());
        float radiusDp = mPreferences.getInAppKeyboardKeyCornerRadiusDp();
        mKeyboard.setKeyCornerRadiusOverride(radiusDp < 0f ? -1f : dpFloat(radiusDp));
        mKeyboard.setKeyColorOverrides(mScheme.resolvedOverrides());
        mKeyboard.setOnKeyPaintListener(keyId -> {
            // The Background chip assigns through swatch taps alone; a key tap paints nothing.
            if (mPaintingBackground)
                return;
            mScheme.paint(keyId, mSelectedRole, mSelectedSwatch);
            persistAndRender();
        });
        mPreviewHolder.removeAllViews();
        // FitRoot gives the keyboard its real size at measure time; until then it is natural.
        mPreviewHolder.addView(mKeyboard, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL));
    }

    /** The face the keyboard itself would use: the picked file, else the bundled symbols font. */
    @Nullable
    private static Typeface previewLabelFont(@NonNull android.content.Context context,
                                             @Nullable String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            File file = new File(fontPath);
            if (file.isFile()) {
                try {
                    Typeface typeface = com.termux.shared.termux.font.FileTypefaces.load(file);
                    if (typeface != null && !Typeface.DEFAULT.equals(typeface)) return typeface;
                } catch (RuntimeException ignored) {
                    // Falls through to the bundled face, like the keyboard does.
                }
            }
        }
        return com.termux.shared.termux.font.NerdFontSpans.typeface(context);
    }

    /**
     * The keyboard theme is no longer chosen by hand: "custom" while an imported palette exists,
     * otherwise "system" (which follows day/night).
     */
    private void syncThemePreference() {
        String wanted = mScheme.hasImportedPalette() ? "custom" : "system";
        if (!wanted.equals(mPreferences.getInAppKeyboardTheme()))
            mPreferences.setInAppKeyboardTheme(wanted);
    }

    /** A two-line row, title over summary, that opens the pick/reset flow. */
    @NonNull
    private View buildTypefaceRow(@NonNull android.content.Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        android.util.TypedValue ripple = new android.util.TypedValue();
        context.getTheme().resolveAttribute(
            android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(dp(48));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(8), dp(8), dp(8), dp(8));
        TextView title = new TextView(context);
        title.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        title.setText(R.string.termux_in_app_keyboard_font_title);
        row.addView(title);
        mFontSummary = new TextView(context);
        mFontSummary.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        mFontSummary.setTextColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY));
        row.addView(mFontSummary);
        row.setOnClickListener(view -> onFontRowClicked());
        updateFontSummary();
        return row;
    }

    private void onFontRowClicked() {
        if (mPreferences.getInAppKeyboardFontPath().isEmpty()) {
            launchFontPicker();
            return;
        }
        new MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.termux_in_app_keyboard_font_title)
            .setItems(new CharSequence[]{
                getString(R.string.termux_in_app_keyboard_font_pick),
                getString(R.string.termux_in_app_keyboard_font_reset)
            }, (dialog, which) -> {
                if (which == 0) {
                    launchFontPicker();
                } else {
                    clearCustomFont();
                }
            })
            .show();
    }

    private void launchFontPicker() {
        // SAF mime coverage for ttf/otf across providers; octet-stream catches
        // file managers that don't map font extensions.
        mFontPickerLauncher.launch(new String[]{
            "font/ttf", "font/otf", "font/*",
            "application/x-font-ttf", "application/x-font-otf",
            "application/octet-stream"
        });
    }

    private void onFontPicked(@Nullable Uri uri) {
        android.content.Context context = getContext();
        if (uri == null || context == null || mPreferences == null || mPreviewHolder == null)
            return;
        File fontDir = new File(context.getFilesDir(), FONT_DIR_NAME);
        File fontFile = new File(fontDir, FONT_FILE_NAME);
        File stagedFile = new File(fontDir, FONT_FILE_NAME + ".tmp");
        try {
            if (!fontDir.isDirectory() && !fontDir.mkdirs())
                throw new java.io.IOException("Cannot create " + fontDir);
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(stagedFile)) {
                if (in == null)
                    throw new java.io.IOException("Cannot open " + uri);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1)
                    out.write(buffer, 0, read);
            }
            // createFromFile returns DEFAULT (or throws) when the bytes are not a usable font.
            Typeface typeface = Typeface.createFromFile(stagedFile);
            if (typeface == null || Typeface.DEFAULT.equals(typeface))
                throw new java.io.IOException("Unreadable font " + uri);
            if (!stagedFile.renameTo(fontFile))
                throw new java.io.IOException("Cannot replace " + fontFile);
            mPreferences.setInAppKeyboardFontPath(fontFile.getAbsolutePath());
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            stagedFile.delete();
            AppNotice.show(context, R.string.termux_in_app_keyboard_font_error, false);
        }
        updateFontSummary();
        rebuildPreview();
    }

    private void clearCustomFont() {
        String path = mPreferences.getInAppKeyboardFontPath();
        mPreferences.setInAppKeyboardFontPath("");
        if (!path.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
        updateFontSummary();
        rebuildPreview();
    }

    private void updateFontSummary() {
        if (mFontSummary == null) return;
        String path = mPreferences.getInAppKeyboardFontPath();
        if (path.isEmpty() || !new File(path).isFile()) {
            mFontSummary.setText(R.string.termux_in_app_keyboard_font_summary_default);
        } else {
            mFontSummary.setText(getString(
                R.string.termux_in_app_keyboard_font_summary_custom, new File(path).getName()));
        }
    }

    /** Glass preview palette: the imported palette when active, then the background override. */
    @NonNull
    private Theme.Palette buildPreviewPalette(@NonNull android.content.Context context) {
        String theme = mPreferences.getInAppKeyboardTheme();
        Theme.Palette palette = InAppKeyboardPaletteFactory.createGlass(context, theme,
            com.termux.app.chrome.GlassLook.of(mPreferences));
        if (mScheme.shouldApplyImportedPalette(theme))
            palette = mScheme.applyToPalette(palette);
        // Production paints this color into the activity's glass backdrop behind a transparent
        // keyboard; the preview has no backdrop, so its palette carries the color itself.
        Integer background = mScheme.resolvedKeyboardBackground();
        return background == null ? palette : withKeyboardBackground(palette, background);
    }

    /** Copy of a palette with only the keyboard background replaced. */
    @NonNull
    static Theme.Palette withKeyboardBackground(@NonNull Theme.Palette base, int color) {
        return new Theme.Palette(color, base.keyBackground, base.actionKeyBackground,
            base.spaceBarBackground, base.activatedKeyBackground, base.labelColor,
            base.subLabelColor, base.activatedLabelColor, base.pressedLabelColor,
            base.lockedModifierColor, base.borderColor, base.borderEnabled, base.borderWidth,
            base.borderRadius, base.opacity, base.secondaryDimming, base.greyedDimming,
            base.actionLabelColor, base.actionSubLabelColor, base.indicatorColors,
            base.keyGradientTopOverlay, base.keyGradientBottomOverlay,
            base.functionKeyBackground, base.functionLabelColor);
    }

    /** Card holding the editing capsule (reset, edit) and the Font chooser. */
    @NonNull
    private View buildPaletteCard(@NonNull android.content.Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(com.termux.app.chrome.ShapeTokens.cornerPx(context,
            com.google.android.material.R.attr.shapeAppearanceCornerExtraLarge, 28));
        card.setCardElevation(0f);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOutlineVariant, Color.GRAY));
        card.setCardBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurface, Color.DKGRAY)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(GAP_DP);
        card.setLayoutParams(cardParams);
        card.setTag(TAG_PALETTE_CARD);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        // The capsule: full shape, highest surface container.
        MaterialShapeDrawable capsule = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
            .setAllCornerSizes(ShapeAppearanceModel.PILL).build());
        capsule.setFillColor(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorSurfaceContainerHighest,
            Color.DKGRAY)));
        heading.setBackground(capsule);
        heading.setPaddingRelative(dp(16), 0, dp(4), 0);
        // Names the role being painted, so the glyphs below need no text of their own.
        mEditingTitle = new TextView(context);
        mEditingTitle.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        mEditingTitle.setSingleLine(true);
        mEditingTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(mEditingTitle, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // Reset and edit sit on the heading's own line as icons, so the card's height goes to the
        // role filters instead.
        MaterialButton reset = headingIconButton(context, R.drawable.ic_symbol_restart,
            R.string.termux_keyboard_color_scheme_follow_theme);
        reset.setOnClickListener(view -> showFollowThemeDialog());
        heading.addView(reset);
        MaterialButton edit = headingIconButton(context, R.drawable.ic_symbol_edit,
            R.string.termux_keyboard_color_scheme_edit_colors);
        edit.setCheckable(true);
        edit.setOnClickListener(view -> {
            mEditingSwatches = !mEditingSwatches;
            edit.setIconResource(mEditingSwatches ? R.drawable.ic_symbol_check
                : R.drawable.ic_symbol_edit);
            edit.setContentDescription(getString(mEditingSwatches
                ? R.string.termux_keyboard_color_scheme_save_colors
                : R.string.termux_keyboard_color_scheme_edit_colors));
            edit.setChecked(mEditingSwatches);
            updateSwatches();
        });
        heading.addView(edit);
        content.addView(heading);

        content.addView(buildTypefaceRow(context), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(content);
        return card;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.keyboard_theme_title);
        // The wallpaper may have changed while this screen sat in the background; dynamic slots
        // have to show what the keyboard will actually use.
        if (mScheme != null && mKeyboard != null
                && mScheme.refreshDynamicSwatches(requireContext())) {
            persistAndRender();
            updateSwatches();
        }
    }

    /** Scheme-level reset: every slot goes back to following the system Material theme. */
    private void showFollowThemeDialog() {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.termux_keyboard_color_scheme_follow_theme_title)
            .setMessage(R.string.termux_keyboard_color_scheme_follow_theme_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.termux_keyboard_color_scheme_follow_theme_clear_keys,
                (dialog, which) -> applyFollowTheme(true))
            .setPositiveButton(R.string.termux_keyboard_color_scheme_follow_theme,
                (dialog, which) -> applyFollowTheme(false))
            .show();
    }

    private void applyFollowTheme(boolean clearPaintedKeys) {
        if (clearPaintedKeys) {
            mPreferences.setInAppKeyboardColorScheme("");
            mScheme = InAppKeyboardColorScheme.fromJson(requireContext(), "");
        } else {
            resetSchemeToTheme(mScheme);
        }
        mSelectedSwatch = 0;
        persistAndRender();
        createSwatches();
        AppNotice.show(requireContext(),
            R.string.termux_keyboard_color_scheme_follow_theme_done, false);
    }

    /** Reset seam used by the confirmation dialog: unpins every slot and drops the import. */
    static void resetSchemeToTheme(@NonNull InAppKeyboardColorScheme scheme) {
        scheme.unpinAllSwatches();
    }

    @NonNull
    static String slotName(int index) {
        return String.format(Locale.ROOT, "base%02X", index);
    }

    /** Material role identifier behind a slot, or an empty string for an unmapped slot. */
    @NonNull
    static String slotRoleId(int index) {
        return index >= 0 && index < SLOT_ROLE_IDS.length ? SLOT_ROLE_IDS[index] : "";
    }

    /** Short, translated role name used when a slot has no Material role identifier. */
    @NonNull
    static String slotRoleLabel(@NonNull android.content.Context context, int index) {
        String[] labels = context.getResources().getStringArray(
            R.array.termux_keyboard_color_scheme_slot_labels);
        return index >= 0 && index < labels.length ? labels[index] : "";
    }

    /** "base00, surfaceContainerHigh, follows theme" — slot, role, and pinned state. */
    @NonNull
    static String slotDescription(@NonNull android.content.Context context,
                                  @NonNull InAppKeyboardColorScheme scheme, int index) {
        String role = slotRoleId(index);
        if (role.isEmpty()) role = slotRoleLabel(context, index);
        return context.getString(R.string.termux_keyboard_color_scheme_slot_description,
            slotName(index), role,
            context.getString(scheme.isSwatchPinned(index)
                ? R.string.termux_keyboard_color_scheme_slot_pinned
                : R.string.termux_keyboard_color_scheme_slot_dynamic));
    }

    static int pinnedSwatchCount(@NonNull InAppKeyboardColorScheme scheme) {
        int pinned = 0;
        for (int i = 0; i < scheme.swatchCount(); i++) {
            if (scheme.isSwatchPinned(i)) pinned++;
        }
        return pinned;
    }

    /**
     * One line saying whether the keyboard still follows the wallpaper: an imported palette wins
     * over the pinned count, because an import pins every slot it fills.
     */
    @NonNull
    static String statusText(@NonNull android.content.Context context,
                             @NonNull InAppKeyboardColorScheme scheme) {
        if (scheme.hasImportedPalette()) {
            String themeId = scheme.getImportedThemeId();
            return themeId.isEmpty()
                ? context.getString(
                    R.string.termux_keyboard_color_scheme_status_imported_unnamed)
                : context.getString(R.string.termux_keyboard_color_scheme_status_imported,
                    themeId);
        }
        if (scheme.isFullyDynamic())
            return context.getString(R.string.termux_keyboard_color_scheme_status_dynamic);
        return context.getString(R.string.termux_keyboard_color_scheme_status_pinned,
            pinnedSwatchCount(scheme), scheme.swatchCount());
    }

    /** A borderless icon button for the Colors heading, named for talkback by {@code label}. */
    @NonNull
    private MaterialButton headingIconButton(@NonNull android.content.Context context, int icon,
                                             int label) {
        MaterialButton button = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonStyle);
        button.setIconResource(icon);
        button.setContentDescription(getString(label));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(button, getString(label));
        return button;
    }

    /** One icon as a run of text, sized to the line it sits in. */
    @NonNull
    private static CharSequence inlineGlyph(@NonNull android.content.Context context, int icon,
                                            int color, @NonNull TextView line) {
        android.graphics.drawable.Drawable drawable =
            androidx.core.content.ContextCompat.getDrawable(context, icon);
        if (drawable == null) return "";
        drawable = drawable.mutate();
        drawable.setTint(color);
        int size = Math.round(line.getTextSize() * 1.2f);
        drawable.setBounds(0, 0, size, size);
        android.text.SpannableString glyph = new android.text.SpannableString("\uFFFC");
        glyph.setSpan(new android.text.style.ImageSpan(drawable,
                android.os.Build.VERSION.SDK_INT >= 29
                    ? android.text.style.DynamicDrawableSpan.ALIGN_CENTER
                    : android.text.style.DynamicDrawableSpan.ALIGN_BOTTOM),
            0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return glyph;
    }

    /** What each chip paints, by index; null is the Background. */
    private static final InAppKeyboardColorScheme.Role[] ROLE_ORDER = {
        InAppKeyboardColorScheme.Role.KEY_BACKGROUND, InAppKeyboardColorScheme.Role.KEY_BORDER,
        InAppKeyboardColorScheme.Role.PRIMARY, InAppKeyboardColorScheme.Role.SECONDARY,
        InAppKeyboardColorScheme.Role.SECONDARY_BOTTOM, null
    };
    private static final int[] ROLE_LABELS = {
        R.string.termux_keyboard_color_scheme_key_bg,
        R.string.termux_keyboard_color_scheme_key_border,
        R.string.termux_keyboard_color_scheme_primary,
        R.string.termux_keyboard_color_scheme_secondary,
        R.string.termux_keyboard_color_scheme_secondary_bottom,
        R.string.termux_keyboard_color_scheme_background
    };
    private static final int[] ROLE_ICONS = {
        R.drawable.ic_keyboard_color_role_key_bg, R.drawable.ic_keyboard_color_role_key_border,
        R.drawable.ic_keyboard_color_role_primary, R.drawable.ic_keyboard_color_role_secondary,
        R.drawable.ic_keyboard_color_role_secondary_bottom,
        R.drawable.ic_keyboard_color_role_background
    };

    /** All six role chips in one group, wrapping to as many lines as the width needs. */
    private void addRoleChips(@NonNull android.content.Context context,
                              @NonNull LinearLayout root) {
        ChipGroup chips = new ChipGroup(context);
        chips.setChipSpacingHorizontal(dp(GAP_DP));
        chips.setChipSpacingVertical(dp(4));
        for (int index = 0; index < ROLE_ORDER.length; index++)
            chips.addView(createRoleChip(context, index));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(GAP_DP);
        root.addView(chips, params);
    }

    /** One role chip: the role glyph and name; checked = secondaryContainer (stock filter chip). */
    @NonNull
    private Chip createRoleChip(@NonNull android.content.Context context, int index) {
        Chip chip = new Chip(context);
        chip.setChipDrawable(ChipDrawable.createFromAttributes(context, null, 0,
            com.google.android.material.R.style.Widget_Material3_Chip_Filter));
        chip.setText(ROLE_LABELS[index]);
        chip.setChipIconResource(ROLE_ICONS[index]);
        chip.setChipIconTint(null);
        chip.setChipIconVisible(true);
        chip.setCheckedIconVisible(false);
        chip.setCheckable(true);
        chip.setContentDescription(getString(ROLE_LABELS[index]));
        chip.setOnClickListener(view -> selectRole(index));
        mRoleChips[index] = chip;
        return chip;
    }

    /** Check one chip, uncheck the rest, and name the choice in the capsule. */
    private void selectRole(int index) {
        mSelectedRoleIndex = index;
        InAppKeyboardColorScheme.Role role = ROLE_ORDER[index];
        mPaintingBackground = role == null;
        if (role != null) mSelectedRole = role;
        for (int i = 0; i < mRoleChips.length; i++) {
            if (mRoleChips[i] != null) mRoleChips[i].setChecked(i == index);
        }
        if (mEditingTitle != null) {
            mEditingTitle.setText(getString(R.string.termux_keyboard_color_scheme_editing,
                getString(ROLE_LABELS[index])));
        }
    }

    /**
     * Every slot is always visible: 8x3 fixed circles instead of a scrolling row, so choosing a
     * color never means hunting off-screen. The circles carry no text; the role and pinned state
     * live in each slot's content description and in the hex dialog.
     */
    private void createSwatches() {
        android.content.Context context = requireContext();
        mSwatchViews.clear();
        mSwatchBadges.clear();
        mSwatchItems.clear();
        mSwatchGrid.removeAllViews();
        if (mSelectedSwatch < 0 || mSelectedSwatch >= mScheme.swatchCount())
            mSelectedSwatch = 0;
        LinearLayout row = null;
        for (int i = 0; i < mScheme.swatchCount(); i++) {
            final int index = i;
            if (i % SWATCH_GRID_COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = dp(4);
                mSwatchGrid.addView(row, rowParams);
            }

            // The pinned badge overlays the swatch, so the two share a well; the well floats in
            // an equal-weight cell, which is what spaces the columns evenly on any width.
            FrameLayout cell = new FrameLayout(context);
            cell.setOnClickListener(view -> onSwatchTapped(index));
            FrameLayout well = new FrameLayout(context);
            View swatch = new View(context);
            well.addView(swatch, new FrameLayout.LayoutParams(dp(SWATCH_DP), dp(SWATCH_DP)));
            View badge = new View(context);
            badge.setBackground(pinnedBadge(context));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(11), dp(11));
            badgeParams.gravity = Gravity.TOP | Gravity.END;
            well.addView(badge, badgeParams);
            FrameLayout.LayoutParams wellParams = new FrameLayout.LayoutParams(dp(SWATCH_DP), dp(SWATCH_DP));
            wellParams.gravity = Gravity.CENTER;
            cell.addView(well, wellParams);

            row.addView(cell, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mSwatchViews.add(swatch);
            mSwatchBadges.add(badge);
            mSwatchItems.add(cell);
        }
        updateSwatches();
    }

    private void onSwatchTapped(int index) {
        mSelectedSwatch = index;
        if (mEditingSwatches) {
            showHexEditor(index);
        } else if (mPaintingBackground) {
            // Re-tapping the assigned swatch is the way back to the theme's own surface.
            if (mScheme.getKeyboardBackgroundSwatch() == index)
                mScheme.clearKeyboardBackgroundSwatch();
            else
                mScheme.setKeyboardBackgroundSwatch(index);
            persistAndRender();
        }
        updateSwatches();
    }

    /** Dot marking a pinned slot, ringed in the page surface so it reads over any swatch. */
    @NonNull
    private GradientDrawable pinnedBadge(@NonNull android.content.Context context) {
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        badge.setStroke(dp(2), MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, Color.DKGRAY));
        return badge;
    }

    /** Dashed ring = follows the theme, solid ring plus dot = pinned to a fixed color. */
    private void updateSwatches() {
        android.content.Context context = requireContext();
        int outline = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        int faint = Color.argb(90, Color.red(outline), Color.green(outline), Color.blue(outline));
        for (int i = 0; i < mSwatchViews.size(); i++) {
            boolean selected = i == mSelectedSwatch;
            boolean pinned = mScheme.isSwatchPinned(i);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(mScheme.getSwatch(i));
            int strokeWidth = dp(selected ? 3 : 1);
            int strokeColor = selected ? outline : faint;
            if (pinned)
                drawable.setStroke(strokeWidth, strokeColor);
            else
                drawable.setStroke(strokeWidth, strokeColor, dpFloat(4), dpFloat(3));
            mSwatchViews.get(i).setBackground(drawable);
            mSwatchViews.get(i).setAlpha(mEditingSwatches && !selected ? 0.72f : 1f);
            mSwatchBadges.get(i).setVisibility(pinned ? View.VISIBLE : View.INVISIBLE);
            mSwatchItems.get(i).setContentDescription(slotDescription(context, mScheme, i));
        }
    }

    private void showHexEditor(int index) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText(String.format("#%08X", mScheme.getSwatch(index)));
        input.selectAll();
        int horizontal = dp(24);
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.termux_keyboard_color_scheme_hex_title)
            .setMessage(slotDescription(requireContext(), mScheme, index))
            .setView(input, horizontal, 0, horizontal, 0)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null);
        // Pinning must not be one-way: the way back sits next to the hex field that pinned it.
        if (mScheme.isSwatchPinned(index))
            builder.setNeutralButton(R.string.termux_keyboard_color_scheme_slot_unpin,
                (unused, which) -> unpinSwatch(index));
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(view -> {
                Integer color = parseHexColor(input.getText().toString());
                if (color == null) {
                    input.setError(getString(R.string.termux_keyboard_color_scheme_hex_error));
                    return;
                }
                mScheme.setSwatch(index, color);
                persistAndRender();
                updateSwatches();
                dialog.dismiss();
            }));
        dialog.show();
    }

    private void unpinSwatch(int index) {
        mScheme.unpinSwatch(index);
        persistAndRender();
        updateSwatches();
        AppNotice.show(requireContext(),
            getString(R.string.termux_keyboard_color_scheme_slot_unpinned, slotName(index)), false);
    }

    @Nullable
    static Integer parseHexColor(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (!value.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return null;
        try {
            long parsed = Long.parseLong(value, 16);
            if (value.length() == 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void persistAndRender() {
        mPreferences.setInAppKeyboardColorScheme(mScheme.toJson());
        mKeyboard.setPalette(buildPreviewPalette(requireContext()));
        mKeyboard.setKeyColorOverrides(mScheme.resolvedOverrides());
        syncThemePreference();
    }

    private int dp(float value) { return Math.round(dpFloat(value)); }

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
