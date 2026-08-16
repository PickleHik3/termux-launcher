package com.termux.app.terminal.io;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.settings.TermuxPropertiesFile;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Visual editor for the terminal extra-keys row, shown as a bottom sheet from anywhere with a
 * {@link Context} (Settings, the live tuning entry, and the {@code extrakeys.edit} action all use
 * it). Reads and writes the {@code extra-keys} / {@code extra-keys2} properties through
 * {@link TermuxPropertiesFile}, so a hand-written file keeps its comments and every other setting.
 *
 * <p>All edits are a draft: nothing touches the properties file until Save, and closing a dirty
 * draft asks before discarding. The sheet opens full height and stays there — only the Close
 * button, back, or Save can dismiss it, so the layout never jumps while a key is being edited.
 *
 * <p>There is deliberately no long-press-the-row entry: every key already owns long press for
 * auto-repeat, modifier toggles and the hold visual, and swipe-up is the popup key.
 */
public final class ExtraKeysRowEditor {

    /** Called after a successful save so a live toolbar can reload. */
    public interface OnSaved {
        void onSaved();
    }

    private static final long UNDO_BAR_TIMEOUT_MS = 5000;

    private final Context context;
    @Nullable private final OnSaved onSaved;
    private final float density;
    private final int colorText;
    private final int colorSubtle;
    private final int colorOutline;
    private final int colorAccent;
    private final int colorOnAccent;
    private final int colorPanel;
    private final int colorError;

    private final List<ExtraKeysLayoutModel> pages = new ArrayList<>();
    private int currentPage;

    /**
     * The same key-name to glyph map the live toolbar draws with, read from {@code extra-keys-style}
     * when the draft is loaded. Without it a cap showed the raw key name — {@code KEYBOARD} rather
     * than {@code ⌨} — for a key the row itself renders as one glyph.
     */
    @NonNull
    private ExtraKeysConstants.ExtraKeyDisplayMap displayMap =
        ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY;

    private BottomSheetDialog dialog;
    private ScrollView bodyScroller;
    private LinearLayout undoBar;
    @Nullable private TextView saveButton;

    /** F-02: whether the draft differs from what was loaded; Save is only live while true. */
    private boolean dirty;

    /** The tapped cap whose edit panel is open, -1/-1 when nothing is selected. */
    private int selectedRow = -1;
    private int selectedIndex = -1;
    /** The selected cap's main label, so the display editor can retitle it without a rebuild. */
    @Nullable private TextView selectedCapLabel;
    /** The selected key's row and panel views, so the scroller can restore its anchor (F-01). */
    @Nullable private View selectedRowView;
    @Nullable private View selectedPanelView;

    /** Where a long-pressed cap came from while its drag shadow is in flight. */
    private int dragSourceRow = -1;
    private int dragSourceIndex = -1;

    /** The last removed row, kept while the undo bar offers to restore it (F-03). */
    @Nullable private List<ExtraKeysLayoutModel.Key> removedRowKeys;
    private int removedRowIndex = -1;
    private int removedRowPage = -1;
    private final Runnable undoBarHide = this::hideUndoBar;

    public ExtraKeysRowEditor(@NonNull Context context, @Nullable OnSaved onSaved) {
        this.context = context;
        this.onSaved = onSaved;
        this.density = context.getResources().getDisplayMetrics().density;
        this.colorText = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurface, 0xFFFFFFFF);
        this.colorSubtle = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnSurfaceVariant, 0xFFB0B0B0);
        this.colorOutline = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOutlineVariant, 0x33FFFFFF);
        this.colorAccent = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorPrimary, 0xFF80DEEA);
        this.colorOnAccent = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorOnPrimary, 0xFF00363D);
        this.colorPanel = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorSurfacePanelHigh, 0xFF202837);
        this.colorError = MaterialColors.getColor(context,
            com.termux.shared.R.attr.termuxColorError, 0xFFF2B8B5);
    }

    public static void show(@NonNull Context context, @Nullable OnSaved onSaved) {
        new ExtraKeysRowEditor(context, onSaved).open();
    }

    public void open() {
        loadPages();
        dirty = false;
        dialog = new BottomSheetDialog(context);

        LinearLayout root = column();
        root.setPadding(pad(20), pad(16), pad(20), 0);
        GradientDrawable sheetBackground = new GradientDrawable();
        sheetBackground.setCornerRadii(new float[] {
            pad(20), pad(20), pad(20), pad(20), 0, 0, 0, 0});
        // F-13: near-opaque surface — the editor sits over the live wallpaper and terminal, which
        // must not compete with small key labels.
        sheetBackground.setColor(withAlpha(colorPanel, 0xF7));
        root.setBackground(sheetBackground);

        // Fixed header: title, helper and the page tabs never scroll.
        root.addView(title(context.getString(R.string.settings_extra_keys_editor_title)));
        root.addView(caption(context.getString(R.string.settings_extra_keys_editor_summary)));
        LinearLayout tabs = row();
        root.addView(tabs);
        // The live toolbar changes pages with a horizontal swipe (TerminalToolbarViewPager), which
        // nothing inside this sheet otherwise reveals.
        root.addView(caption(context.getString(R.string.settings_extra_keys_editor_page_hint)));

        // One scroller owns all vertical scrolling for rows and the edit panel (F-01).
        LinearLayout body = column();
        body.setPadding(0, 0, 0, pad(12));
        bodyScroller = new ScrollView(context);
        bodyScroller.addView(body);
        bodyScroller.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bodyScroller);

        LinearLayout actions = row();

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            renderTabs(tabs, rebuild[0]);
            renderPage(body, rebuild[0]);
            renderActions(actions, rebuild[0]);
        };

        root.addView(buildUndoBar(rebuild));

        // Sticky action bar outside the scroller, padded past the system bars and the IME.
        actions.setPadding(0, pad(8), 0, pad(12));
        actions.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bottom = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.ime()).bottom;
            } else {
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, pad(8), 0, pad(12) + bottom);
            return insets;
        });
        root.addView(actions);

        rebuild[0].run();

        dialog.setContentView(root);
        stabilizeSheet();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) confirmClose();
            return true;
        });
        dialog.show();
    }

    /** F-01: the sheet opens full height and stays there; only Close, back or Save dismiss it. */
    private void stabilizeSheet() {
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
        behavior.setDraggable(false);
    }

    private void loadPages() {
        pages.clear();
        Properties properties = TermuxPropertiesFile.load(context);
        displayMap = ExtraKeysInfo.getCharDisplayMapForStyle(
            properties.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE,
                TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE));
        for (int page = 0; page < TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length; page++) {
            String value = properties.getProperty(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS[page]);
            if (value == null) value = TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES[page];
            ExtraKeysLayoutModel model = ExtraKeysLayoutModel.parse(value);
            // A page that exists only as an empty value is kept as "not there": the tab strip
            // offers to add it back, which is the same decision the pager makes when rendering.
            pages.add(model);
        }
        if (pages.isEmpty()) pages.add(ExtraKeysLayoutModel.empty());
        currentPage = 0;
    }

    private void renderTabs(@NonNull LinearLayout tabs, @NonNull Runnable rebuild) {
        tabs.removeAllViews();
        for (int page = 0; page < pages.size(); page++) {
            if (page > 0 && pages.get(page).isEmpty() && page != currentPage) continue;
            final int index = page;
            boolean selected = page == currentPage;
            TextView tab = chip(context.getString(R.string.settings_extra_keys_editor_page,
                page + 1), false);
            tab.setMinimumHeight(pad(48));
            tab.setGravity(Gravity.CENTER);
            tab.setContentDescription(context.getString(selected
                ? R.string.settings_extra_keys_editor_page_selected
                : R.string.settings_extra_keys_editor_page, page + 1));
            if (selected) {
                // F-07/F-13: the selected page is a filled pill with an outline, not tint alone.
                GradientDrawable pill = accentOutline();
                pill.setColor(withAlpha(colorAccent, 0x33));
                tab.setBackground(pill);
            }
            tab.setOnClickListener(v -> {
                currentPage = index;
                clearSelection();
                rebuild.run();
            });
            tabs.addView(tab);
        }
        int nextEmpty = -1;
        for (int page = 1; page < pages.size(); page++) {
            if (pages.get(page).isEmpty()) {
                nextEmpty = page;
                break;
            }
        }
        if (nextEmpty >= 0) {
            final int index = nextEmpty;
            TextView add = chip(context.getString(R.string.settings_extra_keys_editor_add_page), true);
            add.setMinimumHeight(pad(48));
            add.setGravity(Gravity.CENTER);
            // A dashed outline keeps the add control visually apart from the page pills.
            GradientDrawable dashed = new GradientDrawable();
            dashed.setColor(0x00000000);
            dashed.setStroke(Math.max(1, pad(0.5f)), colorOutline, pad(4), pad(3));
            dashed.setCornerRadius(pad(12));
            add.setBackground(dashed);
            add.setOnClickListener(v -> {
                pages.get(index).addRow();
                currentPage = index;
                clearSelection();
                markDirty();
                rebuild.run();
            });
            tabs.addView(add);
        }
    }

    private void renderPage(@NonNull LinearLayout body, @NonNull Runnable rebuild) {
        body.removeAllViews();
        selectedCapLabel = null;
        selectedRowView = null;
        selectedPanelView = null;
        ExtraKeysLayoutModel model = pages.get(currentPage);
        if (model.rowCount() == 0) model.addRow();
        if (selectedRow >= model.rowCount()
            || (selectedRow >= 0 && selectedIndex >= model.row(selectedRow).size())) {
            clearSelection();
        }

        for (int rowIndex = 0; rowIndex < model.rowCount(); rowIndex++) {
            final int row = rowIndex;
            LinearLayout capRow = row();
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowIndex > 0) rowParams.topMargin = pad(10);
            capRow.setLayoutParams(rowParams);
            capRow.setOnDragListener(rowDropListener(model, row, rebuild));

            List<ExtraKeysLayoutModel.Key> keys = model.row(rowIndex);
            for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
                capRow.addView(keyCap(model, row, keyIndex, keys.get(keyIndex), rebuild));
            }
            if (keys.isEmpty()) capRow.addView(emptyRowCap());
            capRow.addView(addKeyCap(model, row, keys.isEmpty(), rebuild));
            capRow.addView(removeRowCap(model, row, rebuild));
            body.addView(capRow);
            if (row == selectedRow) selectedRowView = capRow;

            if (row == selectedRow && selectedIndex >= 0 && selectedIndex < keys.size()) {
                View panel = editPanel(model, row, selectedIndex, rebuild);
                selectedPanelView = panel;
                body.addView(panel);
            }
        }

        TextView addRow = chip(context.getString(R.string.settings_extra_keys_editor_add_row), false);
        addRow.setMinimumHeight(pad(48));
        addRow.setGravity(Gravity.CENTER);
        addRow.setOnClickListener(v -> {
            model.addRow();
            markDirty();
            rebuild.run();
        });
        body.addView(addRow);
    }

    /**
     * One key cap mirroring the live toolbar: the swipe-up assignment as a small badge on top, the
     * key's own label below. Tap selects it (tap again deselects), long press starts a drag.
     */
    private View keyCap(@NonNull ExtraKeysLayoutModel model, int row, int index,
                        @NonNull ExtraKeysLayoutModel.Key key, @NonNull Runnable rebuild) {
        boolean selected = row == selectedRow && index == selectedIndex;
        GradientDrawable background = selected ? accentOutline() : outline();
        LinearLayout cap = column();
        cap.setGravity(Gravity.CENTER);
        cap.setMinimumHeight(pad(48));
        cap.setMinimumWidth(pad(56));
        cap.setPadding(pad(4), pad(6), pad(4), pad(6));
        cap.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(pad(2), 0, pad(2), 0);
        cap.setLayoutParams(params);

        TextView popup = new TextView(context);
        // Kept in the tree even when empty so every cap has the same height (F-06).
        popup.setText(key.popup == null ? "" : "↑ " + capText(key.popup));
        popup.setTextColor(colorSubtle);
        popup.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        popup.setGravity(Gravity.CENTER_HORIZONTAL);
        popup.setSingleLine(true);
        popup.setEllipsize(TextUtils.TruncateAt.END);
        cap.addView(popup);

        TextView label = new TextView(context);
        label.setText(capText(key));
        label.setTextColor(colorText);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        applyCapLabelSizing(label);
        cap.addView(label);
        if (selected) selectedCapLabel = label;

        cap.setOnClickListener(v -> {
            if (row == selectedRow && index == selectedIndex) {
                clearSelection();
            } else {
                selectedRow = row;
                selectedIndex = index;
            }
            rebuild.run();
            scrollSelectionIntoView();
        });
        cap.setOnLongClickListener(v -> {
            dragSourceRow = row;
            dragSourceIndex = index;
            v.setElevation(pad(6));
            return v.startDragAndDrop(ClipData.newPlainText("", ""),
                new View.DragShadowBuilder(v), null, 0);
        });
        cap.setOnDragListener(capDropListener(model, row, index, background, rebuild));
        return cap;
    }

    /**
     * What a cap reads: the same resolution the live toolbar performs, so an editor cap never shows
     * an action id for a key the row draws as a glyph.
     */
    @NonNull
    private String capText(@NonNull ExtraKeysLayoutModel.Key key) {
        return ExtraKeyButton.resolveDisplay(key.key, key.display, displayMap);
    }

    /**
     * A cap label never wraps: one line, shrinking to a 9sp floor and only then ellipsizing.
     *
     * <p>Two clipped lines inside a 56dp cap was the worst of both — a long label lost its tail
     * <em>and</em> the cap grew. The full action name belongs in the edit panel, which has the width
     * for it.
     */
    private void applyCapLabelSizing(@NonNull TextView label) {
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        // Autosizing owns the text size from here; setTextSize would be overridden anyway.
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 9, 15, 1,
            TypedValue.COMPLEX_UNIT_SP);
    }

    /** The "+" cap ending every row; picks an action and appends it. Labelled in empty rows. */
    private View addKeyCap(@NonNull ExtraKeysLayoutModel model, int row, boolean emptyRow,
                           @NonNull Runnable rebuild) {
        TextView cap = new TextView(context);
        cap.setText(emptyRow
            ? "+ " + context.getString(R.string.settings_extra_keys_editor_add_key) : "+");
        cap.setContentDescription(context.getString(
            R.string.settings_extra_keys_editor_add_key_to_row, row + 1));
        cap.setTextColor(colorSubtle);
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, emptyRow ? 13f : 15f);
        cap.setGravity(Gravity.CENTER);
        cap.setMinimumHeight(pad(48));
        cap.setBackground(outline());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            emptyRow ? ViewGroup.LayoutParams.WRAP_CONTENT : pad(48),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(pad(2), 0, pad(2), 0);
        cap.setLayoutParams(params);
        if (emptyRow) cap.setPadding(pad(12), 0, pad(12), 0);
        cap.setOnClickListener(v -> openPicker(picked -> {
            model.row(row).add(picked);
            // Select the new key so the anchor restore lands on its edit panel.
            selectedRow = row;
            selectedIndex = model.row(row).size() - 1;
            markDirty();
            rebuild.run();
        }));
        return cap;
    }

    /** F-03: every row carries the same trailing remove control; removal is undoable. */
    private View removeRowCap(@NonNull ExtraKeysLayoutModel model, int row,
                              @NonNull Runnable rebuild) {
        TextView cap = new TextView(context);
        cap.setText("✕");
        cap.setContentDescription(context.getString(
            R.string.settings_extra_keys_editor_remove_row, row + 1));
        cap.setTextColor(colorError);
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        cap.setGravity(Gravity.CENTER);
        cap.setMinimumHeight(pad(48));
        cap.setBackground(outline());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(pad(48),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(pad(2), 0, pad(2), 0);
        cap.setLayoutParams(params);
        cap.setOnClickListener(v -> {
            List<ExtraKeysLayoutModel.Key> removed = new ArrayList<>(model.row(row));
            model.removeRow(row);
            clearSelection();
            markDirty();
            showUndoBar(removed, row);
            rebuild.run();
        });
        return cap;
    }

    /** Placeholder cap a row shows while it has no keys; empty rows are never persisted (F-04). */
    private View emptyRowCap() {
        TextView cap = new TextView(context);
        cap.setText(R.string.settings_extra_keys_editor_empty_row);
        cap.setTextColor(colorSubtle);
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        cap.setGravity(Gravity.CENTER);
        cap.setMinimumHeight(pad(48));
        cap.setBackground(outline());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(pad(2), 0, pad(2), 0);
        cap.setLayoutParams(params);
        return cap;
    }

    /**
     * The inline panel under the selected cap: tap action, swipe-up action, display override,
     * arrow moves and removal. The display override applies live through {@link #selectedCapLabel}
     * rather than a rebuild, which would destroy the focused EditText on every keystroke.
     */
    private View editPanel(@NonNull ExtraKeysLayoutModel model, int row, int index,
                           @NonNull Runnable rebuild) {
        ExtraKeysLayoutModel.Key key = model.row(row).get(index);
        LinearLayout panel = column();
        panel.setBackground(outline());
        panel.setPadding(pad(12), pad(4), pad(12), pad(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(pad(2), pad(6), pad(2), 0);
        panel.setLayoutParams(params);

        String action = key.macro
            ? context.getString(R.string.settings_extra_keys_editor_macro_summary, key.key)
            : key.key;
        panel.addView(selectorRow(context.getString(R.string.settings_extra_keys_editor_does),
            action, () -> openPicker(picked -> {
                key.key = picked.key;
                key.macro = picked.macro;
                if (key.display == null) key.display = picked.display;
                markDirty();
                rebuild.run();
            })));

        String popupLabel = key.popup == null
            ? context.getString(R.string.settings_extra_keys_editor_none) : capText(key.popup);
        panel.addView(selectorRow(context.getString(R.string.settings_extra_keys_editor_swipe_up),
            popupLabel, () -> openPicker(picked -> {
                key.popup = picked;
                markDirty();
                rebuild.run();
            })));
        if (key.popup != null) {
            panel.addView(destructiveEntry(
                context.getString(R.string.settings_extra_keys_editor_clear_swipe_up), () -> {
                    key.popup = null;
                    markDirty();
                    rebuild.run();
                }));
        }

        panel.addView(header(context.getString(R.string.settings_extra_keys_editor_shows)));
        EditText display = new EditText(context);
        display.setSingleLine(true);
        display.setTextColor(colorText);
        display.setHintTextColor(colorSubtle);
        if (key.display != null) display.setText(key.display);
        display.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                key.display = text.isEmpty() ? null : text;
                if (selectedCapLabel != null) selectedCapLabel.setText(capText(key));
                markDirty();
            }
        });
        panel.addView(labelField(display));
        TextView supporting = caption(context.getString(
            R.string.settings_extra_keys_editor_shows_hint));
        supporting.setPadding(pad(4), 0, 0, pad(4));
        panel.addView(supporting);

        // The swipe-up key gets the same field. Without it the badge kept whatever the action picker
        // wrote — "Previous session" for tool:session.previous — which is a whole word inside a 9sp
        // badge, and there was no way to shorten it to a glyph.
        if (key.popup != null) {
            final ExtraKeysLayoutModel.Key popupKey = key.popup;
            panel.addView(header(context.getString(
                R.string.settings_extra_keys_editor_swipe_up_shows)));
            EditText popupDisplay = new EditText(context);
            popupDisplay.setSingleLine(true);
            popupDisplay.setTextColor(colorText);
            popupDisplay.setHintTextColor(colorSubtle);
            if (popupKey.display != null) popupDisplay.setText(popupKey.display);
            popupDisplay.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    popupKey.display = text.isEmpty() ? null : text;
                    markDirty();
                }
            });
            panel.addView(labelField(popupDisplay));
            TextView popupSupporting = caption(context.getString(
                R.string.settings_extra_keys_editor_swipe_up_shows_hint));
            popupSupporting.setPadding(pad(4), 0, 0, pad(4));
            panel.addView(popupSupporting);
        }

        panel.addView(header(context.getString(R.string.settings_extra_keys_editor_move)));
        panel.addView(moveButtons(model, row, index, rebuild));

        panel.addView(destructiveEntry(
            context.getString(R.string.settings_extra_keys_editor_remove_key), () -> {
                model.row(row).remove(index);
                clearSelection();
                markDirty();
                rebuild.run();
            }));
        return panel;
    }

    /** F-09: bounded arrow moves matching the drag semantics, for one-handed and a11y reordering. */
    private View moveButtons(@NonNull ExtraKeysLayoutModel model, int row, int index,
                             @NonNull Runnable rebuild) {
        LinearLayout buttons = row();
        int rowSize = model.row(row).size();
        buttons.addView(moveButton("←", R.string.settings_extra_keys_editor_move_left,
            index > 0, () -> {
                model.move(row, index, row, index - 1);
                selectedIndex = index - 1;
            }, rebuild));
        buttons.addView(moveButton("→", R.string.settings_extra_keys_editor_move_right,
            index < rowSize - 1, () -> {
                model.move(row, index, row, index + 1);
                selectedIndex = index + 1;
            }, rebuild));
        buttons.addView(moveButton("↑", R.string.settings_extra_keys_editor_move_up,
            row > 0, () -> {
                int target = Math.min(index, model.row(row - 1).size());
                model.move(row, index, row - 1, target);
                selectedRow = row - 1;
                selectedIndex = target;
            }, rebuild));
        buttons.addView(moveButton("↓", R.string.settings_extra_keys_editor_move_down,
            row < model.rowCount() - 1, () -> {
                int target = Math.min(index, model.row(row + 1).size());
                model.move(row, index, row + 1, target);
                selectedRow = row + 1;
                selectedIndex = target;
            }, rebuild));
        return buttons;
    }

    private View moveButton(@NonNull String glyph, int descriptionRes, boolean enabled,
                            @NonNull Runnable move, @NonNull Runnable rebuild) {
        TextView button = new TextView(context);
        button.setText(glyph);
        button.setContentDescription(context.getString(descriptionRes));
        button.setTextColor(colorText);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(pad(48));
        button.setBackground(outline());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(pad(48),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, pad(8), pad(4));
        button.setLayoutParams(params);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.35f);
        button.setOnClickListener(v -> {
            move.run();
            markDirty();
            rebuild.run();
            scrollSelectionIntoView();
        });
        return button;
    }

    /** Receives a dragged cap: highlight while hovered, drop moves the key into this slot. */
    private View.OnDragListener capDropListener(@NonNull ExtraKeysLayoutModel model, int targetRow,
                                                int targetIndex, @NonNull GradientDrawable restore,
                                                @NonNull Runnable rebuild) {
        return (v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setBackground(accentOutline());
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setBackground(restore);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setBackground(restore);
                    v.setElevation(0);
                    dragSourceRow = -1;
                    dragSourceIndex = -1;
                    return true;
                case DragEvent.ACTION_DROP:
                    if (dragSourceRow < 0) return false;
                    if (dragSourceRow == targetRow && dragSourceIndex == targetIndex) return true;
                    model.move(dragSourceRow, dragSourceIndex, targetRow, targetIndex);
                    clearSelection();
                    markDirty();
                    rebuild.run();
                    return true;
                default:
                    return true;
            }
        };
    }

    /** Receives a cap dropped on a row but not on any cap: the key moves to that row's end. */
    private View.OnDragListener rowDropListener(@NonNull ExtraKeysLayoutModel model, int targetRow,
                                                @NonNull Runnable rebuild) {
        return (v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setBackground(accentOutline());
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setBackground(null);
                    return true;
                case DragEvent.ACTION_DROP:
                    if (dragSourceRow < 0) return false;
                    int end = model.row(targetRow).size();
                    if (targetRow == dragSourceRow) end -= 1;
                    if (targetRow == dragSourceRow && end == dragSourceIndex) return true;
                    model.move(dragSourceRow, dragSourceIndex, targetRow, end);
                    clearSelection();
                    markDirty();
                    rebuild.run();
                    return true;
                default:
                    return true;
            }
        };
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedIndex = -1;
        selectedCapLabel = null;
        selectedRowView = null;
        selectedPanelView = null;
    }

    /** F-05: one sheet at a time — the editor hides while the picker is up, then restores. */
    private void openPicker(@NonNull ExtraKeyActionPicker.OnPicked onPicked) {
        dialog.hide();
        ExtraKeyActionPicker.show(context, onPicked, () -> {
            dialog.show();
            scrollSelectionIntoView();
        });
    }

    /**
     * A label field with the glyph picker beside it. A cap label is usually a character no soft
     * keyboard offers — ⌘, ⇥, a Powerline separator — so the catalogue sits next to the field
     * rather than behind a menu the field never mentions.
     */
    private View labelField(@NonNull EditText field) {
        LinearLayout fieldRow = row();
        field.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        fieldRow.addView(field);

        TextView insert = new TextView(context);
        insert.setText("Ω");
        insert.setContentDescription(context.getString(R.string.settings_extra_keys_glyph_insert));
        insert.setTextColor(colorAccent);
        insert.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        insert.setGravity(Gravity.CENTER);
        insert.setMinimumHeight(pad(48));
        insert.setBackground(outline());
        LinearLayout.LayoutParams insertParams = new LinearLayout.LayoutParams(pad(48),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        insertParams.setMargins(pad(8), 0, 0, 0);
        insert.setLayoutParams(insertParams);
        insert.setOnClickListener(v -> openGlyphPicker(field));
        fieldRow.addView(insert);
        return fieldRow;
    }

    /**
     * F-05 again: one sheet at a time. The glyph lands at the caret rather than replacing the
     * field, so a label may mix a glyph with text, and the field's own watcher still owns the
     * model — a field left blank keeps writing {@code null}.
     */
    private void openGlyphPicker(@NonNull EditText field) {
        dialog.hide();
        ExtraKeyGlyphPicker.show(context, field.getText().toString(), glyph -> {
            Editable text = field.getText();
            int start = clampToText(field.getSelectionStart(), text.length());
            int end = clampToText(field.getSelectionEnd(), text.length());
            text.replace(Math.min(start, end), Math.max(start, end), glyph);
        }, () -> {
            dialog.show();
            scrollSelectionIntoView();
        });
    }

    /** An unfocused field reports a -1 selection, which {@code replace} would throw on. */
    private static int clampToText(int index, int length) {
        return index < 0 ? length : Math.min(index, length);
    }

    /** F-01: after a rebuild, bring the selected key's row and its edit panel back into view. */
    private void scrollSelectionIntoView() {
        if (bodyScroller == null) return;
        bodyScroller.post(() -> {
            if (selectedRowView == null) return;
            int top = selectedRowView.getTop();
            int bottom = selectedPanelView != null
                ? selectedPanelView.getBottom() : selectedRowView.getBottom();
            int viewportTop = bodyScroller.getScrollY();
            int viewportBottom = viewportTop + bodyScroller.getHeight();
            if (top < viewportTop) {
                bodyScroller.smoothScrollTo(0, top);
            } else if (bottom > viewportBottom) {
                bodyScroller.smoothScrollTo(0, Math.min(top, bottom - bodyScroller.getHeight()));
            }
        });
    }

    /** F-02: edits are a draft; anything that changes the model arms Save and drops stale undo. */
    private void markDirty() {
        dirty = true;
        hideUndoBar();
        styleSaveButton();
    }

    private View buildUndoBar(@NonNull Runnable[] rebuild) {
        undoBar = row();
        undoBar.setVisibility(View.GONE);
        GradientDrawable background = outline();
        background.setColor(withAlpha(colorPanel, 0xFF));
        undoBar.setBackground(background);
        undoBar.setPadding(pad(12), pad(2), pad(2), pad(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = pad(4);
        undoBar.setLayoutParams(params);

        TextView label = new TextView(context);
        label.setText(R.string.settings_extra_keys_editor_row_removed);
        label.setTextColor(colorText);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setLayoutParams(new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        undoBar.addView(label);

        TextView undo = chip(context.getString(R.string.settings_extra_keys_editor_undo), true);
        undo.setMinimumHeight(pad(48));
        undo.setGravity(Gravity.CENTER);
        undo.setOnClickListener(v -> undoRemoveRow(rebuild[0]));
        undoBar.addView(undo);
        return undoBar;
    }

    private void showUndoBar(@NonNull List<ExtraKeysLayoutModel.Key> keys, int rowIndex) {
        removedRowKeys = keys;
        removedRowIndex = rowIndex;
        removedRowPage = currentPage;
        undoBar.setVisibility(View.VISIBLE);
        undoBar.removeCallbacks(undoBarHide);
        undoBar.postDelayed(undoBarHide, UNDO_BAR_TIMEOUT_MS);
    }

    private void hideUndoBar() {
        removedRowKeys = null;
        removedRowIndex = -1;
        removedRowPage = -1;
        if (undoBar == null) return;
        undoBar.removeCallbacks(undoBarHide);
        undoBar.setVisibility(View.GONE);
    }

    private void undoRemoveRow(@NonNull Runnable rebuild) {
        if (removedRowKeys == null || removedRowPage < 0 || removedRowPage >= pages.size()) return;
        pages.get(removedRowPage).insertRow(removedRowIndex, removedRowKeys);
        currentPage = removedRowPage;
        hideUndoBar();
        rebuild.run();
    }

    private void renderActions(@NonNull LinearLayout actions, @NonNull Runnable rebuild) {
        actions.removeAllViews();

        TextView close = chip(context.getString(R.string.settings_extra_keys_editor_close), false);
        close.setMinimumHeight(pad(48));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> confirmClose());
        actions.addView(close);

        TextView reset = chip(context.getString(R.string.settings_extra_keys_editor_reset), false);
        reset.setTextColor(colorError);
        reset.setMinimumHeight(pad(48));
        reset.setGravity(Gravity.CENTER);
        reset.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
            .setMessage(R.string.settings_extra_keys_editor_reset_confirm)
            .setPositiveButton(R.string.settings_extra_keys_editor_reset, (d, w) -> {
                pages.set(currentPage, ExtraKeysLayoutModel.parse(
                    TermuxTerminalExtraKeys.PAGE_DEFAULT_VALUES[currentPage]));
                clearSelection();
                markDirty();
                rebuild.run();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show());
        actions.addView(reset);

        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        actions.addView(spacer);

        TextView save = chip(context.getString(R.string.settings_extra_keys_editor_save), false);
        save.setMinimumHeight(pad(48));
        save.setGravity(Gravity.CENTER);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setOnClickListener(v -> {
            if (!dirty) return;
            save();
            Toast.makeText(context, R.string.settings_extra_keys_editor_saved_toast,
                Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        saveButton = save;
        styleSaveButton();
        actions.addView(save);
    }

    /** F-02/F-08: Save is the primary filled action and only live while the draft is dirty. */
    private void styleSaveButton() {
        if (saveButton == null) return;
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(pad(12));
        fill.setColor(colorAccent);
        saveButton.setBackground(fill);
        saveButton.setTextColor(colorOnAccent);
        saveButton.setEnabled(dirty);
        saveButton.setAlpha(dirty ? 1f : 0.45f);
    }

    /** F-02: a dirty draft is never dropped silently. */
    private void confirmClose() {
        if (!dirty) {
            dialog.dismiss();
            return;
        }
        new MaterialAlertDialogBuilder(context)
            .setMessage(R.string.settings_extra_keys_editor_discard_title)
            .setPositiveButton(R.string.settings_extra_keys_editor_discard,
                (d, w) -> dialog.dismiss())
            .setNegativeButton(R.string.settings_extra_keys_editor_keep_editing, null)
            .show();
    }

    private void save() {
        for (int page = 0; page < pages.size()
            && page < TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length; page++) {
            ExtraKeysLayoutModel model = pages.get(page);
            model.pruneEmptyRows();
            TermuxPropertiesFile.write(TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS[page],
                model.serialize());
        }
        if (onSaved != null) onSaved.onSaved();
    }

    /** Style helpers — same programmatic-sheet idiom as the pinned apps editor. */
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
        return view;
    }

    private TextView caption(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(colorSubtle);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        view.setPadding(0, pad(4), 0, pad(8));
        return view;
    }

    private TextView header(@NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setAllCaps(true);
        view.setTextColor(colorSubtle);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        view.setPadding(0, pad(12), pad(8), pad(4));
        return view;
    }

    /** F-12: a tappable label/value selector row with a trailing chevron, 48dp minimum. */
    private View selectorRow(@NonNull String label, @NonNull String value,
                             @NonNull Runnable onClick) {
        LinearLayout rowView = row();
        rowView.setMinimumHeight(pad(48));
        rowView.setPadding(pad(4), pad(4), pad(4), pad(4));
        rowView.setOnClickListener(v -> onClick.run());

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(colorText);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        rowView.addView(labelView);

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(colorSubtle);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        valueView.setMaxLines(3);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.setMargins(pad(12), 0, 0, 0);
        valueView.setLayoutParams(valueParams);
        rowView.addView(valueView);

        TextView chevron = new TextView(context);
        chevron.setText("›");
        chevron.setTextColor(colorSubtle);
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        chevron.setPadding(pad(6), 0, 0, 0);
        rowView.addView(chevron);
        return rowView;
    }

    private TextView entry(@NonNull String text, @NonNull Runnable onClick) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(colorText);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setMinimumHeight(pad(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(pad(4), pad(10), pad(4), pad(10));
        view.setOnClickListener(v -> onClick.run());
        return view;
    }

    /** F-08: destructive entries (remove key, remove swipe-up) read as destructive. */
    private TextView destructiveEntry(@NonNull String text, @NonNull Runnable onClick) {
        TextView view = entry(text, onClick);
        view.setTextColor(colorError);
        return view;
    }

    private TextView chip(@NonNull String text, boolean accented) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(accented ? colorAccent : colorText);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        view.setPadding(pad(12), pad(6), pad(12), pad(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, pad(6), pad(8), pad(6));
        view.setLayoutParams(params);
        view.setBackground(outline());
        return view;
    }

    private GradientDrawable outline() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x00000000);
        drawable.setStroke(Math.max(1, pad(0.5f)), colorOutline);
        drawable.setCornerRadius(pad(12));
        return drawable;
    }

    /** Selection/drop-target look: same shape, accent stroke plus a subtle fill (F-13). */
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

    /** The property key of a page, exposed for the settings entry that names them. */
    @NonNull
    public static String pagePropertyKey(int page) {
        if (page < 0 || page >= TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS.length)
            return TermuxPropertyConstants.KEY_EXTRA_KEYS;
        return TermuxTerminalExtraKeys.PAGE_PROPERTY_KEYS[page];
    }
}
