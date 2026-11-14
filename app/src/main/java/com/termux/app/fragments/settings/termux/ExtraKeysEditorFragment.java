package com.termux.app.fragments.settings.termux;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.MenuProvider;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.notice.AppNotice;
import com.termux.app.settings.TermuxPropertiesFile;
import com.termux.app.surfaces.rows.SliderRow;
import com.termux.app.terminal.io.ExtraKeyActionLabels;
import com.termux.app.terminal.io.ExtraKeyActionPicker;
import com.termux.app.terminal.io.ExtraKeyEligibility;
import com.termux.app.terminal.io.ExtraKeySetResolver;
import com.termux.app.terminal.extrakeys.ExtraKeyCapView;
import com.termux.app.terminal.io.ExtraKeysTarget;
import com.termux.app.terminal.io.ExtraKeysTargetDrafts;
import com.termux.app.terminal.io.ExtraKeyDetailSheet;
import com.termux.app.terminal.io.ExtraKeyOptionsSheets;
import com.termux.app.terminal.io.ExtraKeysDraft;
import com.termux.app.terminal.io.ExtraKeysLayoutModel;
import com.termux.app.terminal.io.ExtraKeysPresets;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.font.NerdFontSpans;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The extra-keys editor as a Settings page: pages, the rows on a page, the keys in a row.
 *
 * <p>Each row is a card of key caps that scrolls sideways, with a dashed + pinned at its end. A
 * tap on a cap opens the Edit key sheet, the + opens the Choose a key sheet, a row's ⋮ opens its
 * options, and a row reorders by dragging its handle. Everything edits an {@link ExtraKeysDraft};
 * nothing reaches {@code termux.properties} until Save changes, Cancel puts the draft away, and
 * leaving with unsaved edits asks first. Removals offer Undo.
 *
 * <p>Reached from Keyboard settings, from the {@code extrakeys.edit} action and from the old
 * edit-row deep link, all of which now land in Settings rather than in a sheet over the terminal.
 */
@Keep
public class ExtraKeysEditorFragment extends Fragment
    implements com.termux.app.fragments.settings.SettingsUnsavedChanges {

    @Override
    public boolean hasUnsavedChanges() {
        return backCallback.isEnabled();
    }

    private static final int KEY_CAP_DP = 48;
    private static final int GAP_DP = 8;

    /** Every target's set; Global always has one. */
    private ExtraKeysTargetDrafts drafts = ExtraKeysTargetDrafts.load(key -> null);
    private ExtraKeysTarget target = ExtraKeysTarget.GLOBAL;
    /** The selected target's draft; an empty stand-in (never saved) while it has no set of its own. */
    private ExtraKeysDraft draft = ExtraKeysDraft.of(new ArrayList<>());
    private final ExtraKeysDraft standIn = ExtraKeysDraft.of(new ArrayList<>());
    /** The drafts as loaded or last saved, to tell whether anything is left to save. */
    private String savedFingerprint = "";
    private int currentPage;

    @NonNull
    private ExtraKeysConstants.ExtraKeyDisplayMap displayMap =
        ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY;

    private int colorText;
    private int colorSubtle;
    private int colorCap;

    private View root;
    private ChipGroup pageChips;
    private MaterialButton pageAddButton;
    private ImageView pageMenuButton;
    private TextView countLabel;
    private RowAdapter adapter;
    private ItemTouchHelper touchHelper;
    private View saveBar;
    private View hintView;
    private View keySizeBlock;
    private SliderRow keySizeSlider;
    private TextView keySizeNote;
    private MaterialButton useDefaultButton;
    private View pagesView;
    private View rowList;
    private View addRowView;
    private View notOwnBlock;
    private TextView notOwnTitle;
    private TextView notOwnBody;
    private MaterialButton giveOwnButton;
    private int[][] stockButton;
    private MaterialButton cancelBtn;
    private MaterialButton saveButton;

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            confirmDiscard(() -> {
                setEnabled(false);
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            });
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        colorText = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        colorSubtle = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY);
        colorCap = MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF2A3140);
        loadDraft(context);

        LinearLayout column = column(context);
        NestedScrollView scroller = new NestedScrollView(context);
        scroller.setFillViewport(true);
        LinearLayout content = column(context);
        content.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroller.addView(content);
        column.addView(scroller, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(targetBar(context));
        notOwnBlock = notOwnBlock(context);
        content.addView(notOwnBlock);
        keySizeBlock = keySizeRow(context);
        content.addView(keySizeBlock);
        pagesView = pagesBar(context);
        content.addView(pagesView);
        hintView = hintRow(context);
        content.addView(hintView);

        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setNestedScrollingEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new DragCallback());
        touchHelper.attachToRecyclerView(list);
        rowList = list;
        content.addView(list, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addRowView = addRowButton(context);
        content.addView(addRowView);
        content.addView(guideView(context));

        saveBar = saveBar(context);
        column.addView(saveBar);
        root = column;
        applyCompact();

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
            backCallback);
        requireActivity().addMenuProvider(new ResetMenu(), getViewLifecycleOwner());
        refreshAll();
        return column;
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.settings_edit_extra_keys_title);
    }

    // ---------------------------------------------------------------- draft

    private void loadDraft(@NonNull Context context) {
        Properties properties = TermuxPropertiesFile.load(context);
        String style = properties.getProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE);
        displayMap = ExtraKeysInfo.getCharDisplayMapForStyle(style);

        drafts = ExtraKeysTargetDrafts.load(properties::getProperty);
        syncDraft();
        savedFingerprint = drafts.fingerprint();
    }

    private boolean isDirty() {
        return !drafts.fingerprint().equals(savedFingerprint);
    }

    /** Points {@link #draft} at the selected target's set and keeps the page in range. */
    private void syncDraft() {
        ExtraKeysDraft own = drafts.draftOf(target);
        draft = own != null ? own : standIn;
        currentPage = Math.max(0, Math.min(currentPage, draft.pageCount() - 1));
    }

    /** After any edit: the save bar and the Back guard follow whether the draft differs. */
    private void edited() {
        backCallback.setEnabled(isDirty());
        updateSaveBar();
    }

    private void refreshAll() {
        refreshTarget();
        refreshPages();
        refreshRows();
        updateSaveBar();
    }

    private void refreshRows() {
        ExtraKeysLayoutModel page = draft.page(currentPage);
        // An empty page shows one empty row, so its + is the way in. Empty rows are never saved.
        if (page.rowCount() == 0) page.addRow();
        adapter.notifyDataSetChanged();
        int rows = page.rowCount();
        countLabel.setText(getResources().getQuantityString(
            R.plurals.settings_extra_keys_row_count, rows, rows));
    }

    private void save() {
        drafts.pruneEmptyRows();
        for (ExtraKeysTargetDrafts.Write write : drafts.writes()) {
            if (write.value == null) {
                TermuxPropertiesFile.remove(write.key);
            } else {
                TermuxPropertiesFile.write(write.key, write.value);
            }
        }
        drafts.markSaved();
        savedFingerprint = drafts.fingerprint();
        backCallback.setEnabled(false);
        Context context = requireContext();
        // The terminal rebuilds its toolbar pages when it next comes to the front.
        TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
        AppNotice.show(context, R.string.settings_extra_keys_saved);
        refreshAll();
    }

    private void confirmDiscard(@NonNull Runnable onDiscard) {
        if (!isDirty()) {
            onDiscard.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_extra_keys_discard_title)
            .setMessage(R.string.settings_extra_keys_discard_message)
            .setNegativeButton(R.string.settings_extra_keys_keep_editing, null)
            .setPositiveButton(R.string.settings_extra_keys_discard, (d, w) -> onDiscard.run())
            .show();
    }

    /** Puts the draft away and leaves the page, as Back does once the reader has confirmed. */
    private void cancel() {
        confirmDiscard(() -> {
            backCallback.setEnabled(false);
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });
    }

    /** Takes back the last removal: the draft returns to how it was, on the page it was on. */
    private void offerUndo(@StringRes int message, @Nullable Object arg,
                           @NonNull ExtraKeysTargetDrafts.Snapshot before, int pageBefore) {
        String text = arg == null ? getString(message) : getString(message, arg);
        Snackbar snackbar = Snackbar.make(root, text, Snackbar.LENGTH_LONG);
        snackbar.setAnchorView(saveBar);
        snackbar.setAction(R.string.settings_extra_keys_undo, v -> {
            drafts.restore(before);
            currentPage = Math.max(0, pageBefore);
            syncDraft();
            edited();
            refreshAll();
        });
        snackbar.show();
    }

    // ---------------------------------------------------------------- key size

    /** Key size, a property of the whole bar, so it stands above the page tabs. */
    private View keySizeRow(@NonNull Context context) {
        LinearLayout block = column(context);
        SliderRow.Track track = new SliderRow.Track(R.drawable.ic_ms_height,
            getString(R.string.settings_extra_keys_key_size),
            ExtraKeysDraft.KEY_SIZE_MIN_TENTHS, ExtraKeysDraft.KEY_SIZE_MAX_TENTHS, 1,
            tenths -> getString(R.string.settings_extra_keys_key_size_value,
                ExtraKeysDraft.keySizeProperty(tenths)));
        keySizeSlider = SliderRow.build(context, java.util.Collections.singletonList(track),
            new int[]{draft.keySizeTenths()}, new SliderRow.Listener() {
                @Override
                public void onValue(int index, int value, boolean dragging) {
                    if (draft.keySizeTenths() == value) return;
                    draft.setKeySizeTenths(value);
                    edited();
                }

                @Override
                public void onReleased(int index) {}
            });
        block.addView(keySizeSlider.root);
        keySizeNote = new TextView(context);
        keySizeNote.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        keySizeNote.setTextColor(colorSubtle);
        keySizeNote.setPadding(dp(4), 0, dp(4), dp(8));
        block.addView(keySizeNote);
        useDefaultButton = new MaterialButton(context, null,
            androidx.appcompat.R.attr.borderlessButtonStyle);
        useDefaultButton.setMinHeight(dp(48));
        LinearLayout.LayoutParams useParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        useParams.bottomMargin = dp(8);
        block.addView(useDefaultButton, useParams);
        return block;
    }

    // --------------------------------------------------------------- targets

    @NonNull
    private String targetName(@NonNull ExtraKeysTarget of) {
        switch (of) {
            case TERMINAL: return getString(R.string.settings_extra_keys_target_terminal);
            case DISPLAY: return getString(R.string.settings_extra_keys_target_display);
            case HOME: return getString(R.string.settings_extra_keys_target_home);
            case ASSISTANT: return getString(R.string.settings_extra_keys_target_assistant);
            case GLOBAL:
            default: return getString(R.string.settings_extra_keys_target_global);
        }
    }

    /** One chip per target, above everything else; Global is selected on open. */
    private View targetBar(@NonNull Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ChipGroup chips = new ChipGroup(context);
        chips.setSingleLine(true);
        chips.setSingleSelection(true);
        chips.setSelectionRequired(true);
        for (ExtraKeysTarget each : ExtraKeysTarget.values()) {
            Chip chip = new Chip(context);
            chip.setText(targetName(each));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setId(View.generateViewId());
            chip.setChecked(each == target);
            chip.setOnClickListener(v -> {
                if (target == each) return;
                target = each;
                currentPage = 0;
                refreshAll();
            });
            chips.addView(chip);
        }
        scroll.addView(chips, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** What a target without a set of its own shows instead of the editor: a fact and the way in. */
    private View notOwnBlock(@NonNull Context context) {
        LinearLayout block = column(context);
        block.setPadding(dp(4), dp(16), dp(4), dp(8));
        notOwnTitle = new TextView(context);
        notOwnTitle.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        notOwnTitle.setTextColor(colorText);
        block.addView(notOwnTitle);
        notOwnBody = new TextView(context);
        notOwnBody.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        notOwnBody.setTextColor(colorSubtle);
        notOwnBody.setPadding(0, dp(4), 0, dp(12));
        block.addView(notOwnBody);
        giveOwnButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        giveOwnButton.setMinHeight(dp(48));
        giveOwnButton.setOnClickListener(v -> {
            drafts.giveOwnSet(target);
            currentPage = 0;
            edited();
            refreshAll();
        });
        block.addView(giveOwnButton);
        return block;
    }

    /**
     * Shows the editor for a target with a set of its own and the one block for a target without;
     * everything compares before it sets, so the page does not relayout for nothing.
     */
    private void refreshTarget() {
        syncDraft();
        boolean own = drafts.isOwn(target);
        String name = targetName(target);
        setShown(notOwnBlock, !own);
        setShown(keySizeBlock, own);
        setShown(pagesView, own);
        setShown(rowList, own);
        setShown(addRowView, own);
        setShown(hintView, own && !isShort());
        if (own) {
            keySizeSlider.setValue(0, draft.keySizeTenths());
            setText(keySizeNote, target == ExtraKeysTarget.GLOBAL
                ? getString(R.string.settings_extra_keys_key_size_note)
                : getString(R.string.settings_extra_keys_key_size_note_target, name));
            boolean builtIn = target.shippedPage1() != null && target != ExtraKeysTarget.GLOBAL;
            setShown(useDefaultButton, target != ExtraKeysTarget.GLOBAL);
            setText(useDefaultButton, getString(builtIn
                ? R.string.settings_extra_keys_use_builtin : R.string.settings_extra_keys_use_global));
            final ExtraKeysTarget shown = target;
            useDefaultButton.setOnClickListener(v -> {
                ExtraKeysTargetDrafts.Snapshot before = drafts.snapshot();
                drafts.useDefault(shown);
                currentPage = 0;
                edited();
                refreshAll();
                offerUndo(R.string.settings_extra_keys_target_reset, name, before, 0);
            });
        } else {
            boolean shipped = drafts.sourceOf(target) == ExtraKeySetResolver.Source.SHIPPED;
            setText(notOwnTitle, getString(shipped
                ? R.string.settings_extra_keys_using_builtin_title
                : R.string.settings_extra_keys_using_global_title));
            setText(notOwnBody, shipped
                ? getString(R.string.settings_extra_keys_using_builtin_body, name)
                : getString(R.string.settings_extra_keys_using_global_body, name));
            setText(giveOwnButton, getString(R.string.settings_extra_keys_give_own, name));
        }
        if (isAdded()) requireActivity().invalidateMenu();
    }

    private static void setShown(@NonNull View view, boolean shown) {
        int visibility = shown ? View.VISIBLE : View.GONE;
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
    }

    private static void setText(@NonNull TextView view, @NonNull CharSequence text) {
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    // ---------------------------------------------------------------- pages

    private View pagesBar(@NonNull Context context) {
        LinearLayout block = column(context);

        LinearLayout line = row(context);
        pageChips = new ChipGroup(context);
        pageChips.setSingleLine(true);
        pageChips.setSingleSelection(true);
        pageChips.setSelectionRequired(true);
        line.addView(pageChips, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        pageMenuButton = new ImageView(context);
        pageMenuButton.setImageResource(R.drawable.ic_editor_more_vert);
        pageMenuButton.setColorFilter(colorText);
        pageMenuButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        pageMenuButton.setBackgroundResource(android.R.drawable.list_selector_background);
        pageMenuButton.setContentDescription(getString(R.string.settings_extra_keys_page_options_cd));
        pageMenuButton.setOnClickListener(v -> showPageOptions());
        line.addView(pageMenuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        pageAddButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialIconButtonOutlinedStyle);
        pageAddButton.setIconResource(R.drawable.ic_symbol_add);
        pageAddButton.setContentDescription(getString(R.string.settings_extra_keys_add_page_cd));
        pageAddButton.setOnClickListener(v -> {
            int added = draft.addPage();
            if (added < 0) return;
            currentPage = added;
            edited();
            refreshAll();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        addParams.setMarginStart(dp(4));
        line.addView(pageAddButton, addParams);
        block.addView(line);
        return block;
    }

    /** One chip per page; the + goes away once both pages exist. */
    private void refreshPages() {
        Context context = requireContext();
        pageChips.removeAllViews();
        for (int page = 0; page < draft.pageCount(); page++) {
            final int index = page;
            Chip chip = new Chip(context);
            chip.setText(getString(R.string.settings_extra_keys_page, page + 1));
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setId(View.generateViewId());
            chip.setChecked(page == currentPage);
            chip.setOnClickListener(v -> {
                if (currentPage == index) return;
                currentPage = index;
                refreshAll();
            });
            pageChips.addView(chip);
        }
        pageAddButton.setVisibility(draft.canAddPage() ? View.VISIBLE : View.GONE);
    }

    private void showPageOptions() {
        final int page = currentPage;
        ExtraKeyOptionsSheets.showPage(requireContext(), page + 1,
            draft.canMovePage(page, -1), draft.canMovePage(page, 1), draft.canRemovePage(page),
            new ExtraKeyOptionsSheets.PageActions() {
                @Override
                public void moveLeft() {
                    movePage(page, -1);
                }

                @Override
                public void moveRight() {
                    movePage(page, 1);
                }

                @Override
                public void remove() {
                    ExtraKeysTargetDrafts.Snapshot before = drafts.snapshot();
                    currentPage = draft.removePage(page);
                    edited();
                    refreshAll();
                    offerUndo(R.string.settings_extra_keys_page_removed, page + 1, before, page);
                }
            });
    }

    private void movePage(int page, int delta) {
        if (!draft.movePage(page, delta)) return;
        currentPage = page + delta;
        edited();
        refreshAll();
    }

    // ---------------------------------------------------------------- reset

    /** Reset in the app bar; its presets drop down from the action itself. */
    private final class ResetMenu implements MenuProvider {
        private final int itemId = View.generateViewId();

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            menu.add(Menu.NONE, itemId, Menu.NONE, R.string.settings_extra_keys_reset)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuItem item = menu.findItem(itemId);
            if (item != null) item.setEnabled(drafts.isOwn(target));
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item) {
            if (item.getItemId() != itemId) return false;
            View anchor = requireActivity().findViewById(itemId);
            showResetMenu(anchor != null ? anchor : root);
            return true;
        }
    }

    /** Presets for the page in view, under Reset. */
    private void showResetMenu(@NonNull View anchor) {
        Context context = requireContext();
        // The row the user had before they took this release's row, when they took it: the one
        // preset that is theirs alone, so it leads the list and stays after it is used.
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, true);
        String previous = preferences == null || target != ExtraKeysTarget.GLOBAL
            ? null : preferences.getPreviousExtraKeys(currentPage);
        List<ExtraKeysPresets.Preset> presets =
            ExtraKeysPresets.presetsForPage(currentPage, previous);
        android.widget.PopupMenu menu = new android.widget.PopupMenu(context, anchor);
        for (int i = 0; i < presets.size(); i++) {
            menu.getMenu().add(0, i, i, presets.get(i).titleRes);
        }
        menu.setOnMenuItemClickListener(item -> {
            applyPreset(presets.get(item.getItemId()));
            return true;
        });
        menu.show();
    }

    private void applyPreset(@NonNull ExtraKeysPresets.Preset preset) {
        final int page = currentPage;
        Runnable apply = () -> {
            ExtraKeysTargetDrafts.Snapshot before = drafts.snapshot();
            draft.setPage(page, preset.model());
            edited();
            refreshAll();
            offerUndo(R.string.settings_extra_keys_page_reset, page + 1, before, page);
        };
        if (draft.page(page).isEmpty()) {
            apply.run();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(preset.titleRes)
            .setMessage(getString(R.string.settings_extra_keys_preset_replace_message, page + 1))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_extra_keys_preset_replace, (d, w) -> apply.run())
            .show();
    }

    // ----------------------------------------------------------------- rows

    private View hintRow(@NonNull Context context) {
        LinearLayout line = row(context);
        line.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView hint = new TextView(context);
        hint.setText(R.string.settings_extra_keys_hint);
        hint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        hint.setTextColor(colorSubtle);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(hint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countLabel = new TextView(context);
        countLabel.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        countLabel.setTextColor(colorSubtle);
        countLabel.setSingleLine(true);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countParams.setMarginStart(dp(8));
        line.addView(countLabel, countParams);
        return line;
    }

    /** What a key can be: a heading in the page's label style, then one fact per line. */
    private View guideView(@NonNull Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(4), dp(24), dp(4), dp(8));
        TextView title = new TextView(context);
        title.setText(R.string.settings_extra_keys_guide_title);
        title.setTextAppearance(
            com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
        title.setTextColor(colorSubtle);
        column.addView(title);
        TextView guide = new TextView(context);
        guide.setText(R.string.settings_extra_keys_guide_body);
        guide.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        guide.setTextColor(colorSubtle);
        guide.setLineSpacing(dp(4), 1f);
        guide.setPadding(0, dp(8), 0, 0);
        column.addView(guide);
        return column;
    }

    private View addRowButton(@NonNull Context context) {
        MaterialButton add = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        add.setText(R.string.settings_extra_keys_add_row);
        add.setIconResource(R.drawable.ic_symbol_add);
        add.setMinHeight(dp(48));
        add.setOnClickListener(v -> {
            draft.addRow(currentPage);
            edited();
            refreshRows();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        add.setLayoutParams(params);
        return add;
    }

    private void showRowOptions(int row) {
        final int page = currentPage;
        ExtraKeyOptionsSheets.showRow(requireContext(), row + 1,
            draft.canMoveRow(page, row, -1), draft.canMoveRow(page, row, 1),
            new ExtraKeyOptionsSheets.RowActions() {
                @Override
                public void moveUp() {
                    moveRow(page, row, -1);
                }

                @Override
                public void moveDown() {
                    moveRow(page, row, 1);
                }

                @Override
                public void delete() {
                    ExtraKeysTargetDrafts.Snapshot before = drafts.snapshot();
                    draft.removeRow(page, row);
                    edited();
                    refreshRows();
                    offerUndo(R.string.settings_extra_keys_row_deleted, row + 1, before, page);
                }
            });
    }

    private void moveRow(int page, int row, int delta) {
        if (!draft.moveRow(page, row, delta)) return;
        edited();
        refreshRows();
    }

    private void addKeyTo(int row) {
        ExtraKeyActionPicker.show(requireContext(),
            getString(R.string.settings_extra_keys_picker_adding_to, row + 1),
            R.string.settings_extra_keys_picker_confirm_add, picked -> {
                draft.addKey(currentPage, row, picked);
                edited();
                refreshRows();
            }, null);
    }

    private void openKeySheet(@NonNull ExtraKeysLayoutModel.Key key) {
        final int page = currentPage;
        new ExtraKeyDetailSheet(requireContext(), key, displayMap, new ExtraKeyDetailSheet.Host() {
            @Override
            public void onKeyChanged() {
                edited();
                refreshRows();
            }

            @Override
            public void onRemoveKey() {
                ExtraKeysTargetDrafts.Snapshot before = drafts.snapshot();
                draft.removeKey(page, key);
                edited();
                refreshRows();
                offerUndo(R.string.settings_extra_keys_key_removed, null, before, page);
            }

            @Override
            public boolean canMoveKey(int delta) {
                return draft.canMoveKey(page, key, delta);
            }

            @Override
            public void onMoveKey(int delta) {
                if (!draft.moveKey(page, key, delta)) return;
                edited();
                refreshRows();
            }
        }).show();
    }

    /** One card per row of the page. */
    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {

        final class Holder extends RecyclerView.ViewHolder {
            final RowCardView card;

            Holder(@NonNull RowCardView card) {
                super(card);
                this.card = card;
            }
        }

        @Override
        public int getItemCount() {
            return draft.page(currentPage).rowCount();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RowCardView card = new RowCardView(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(GAP_DP);
            card.setLayoutParams(params);
            return new Holder(card);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.card.bind(position, holder);
        }
    }

    /** Drag a row by its handle; the draft follows the card as it passes the others. */
    private final class DragCallback extends ItemTouchHelper.Callback {
        private boolean moved;

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder,
                              @NonNull RecyclerView.ViewHolder target) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from < 0 || to < 0 || from == to) return false;
            int step = to > from ? 1 : -1;
            for (int row = from; row != to; row += step) draft.moveRow(currentPage, row, step);
            adapter.notifyItemMoved(from, to);
            moved = true;
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

        @Override
        public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder.itemView.setAlpha(0.9f);
                viewHolder.itemView.setElevation(dp(6));
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView,
                              @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
            viewHolder.itemView.setElevation(0f);
            if (!moved) return;
            moved = false;
            edited();
            // The cards were swapped in place; rebind so every "Row N" matches its place.
            recyclerView.post(ExtraKeysEditorFragment.this::refreshRows);
        }
    }

    /** A row: handle, "Row N", ⋮, then the caps scrolling sideways beside a pinned dashed +. */
    private final class RowCardView extends MaterialCardView {
        private final ImageView handle;
        private final TextView title;
        private final ImageView more;
        private final LinearLayout caps;
        private final TextView add;
        private int row;

        RowCardView(@NonNull Context context) {
            super(context, null, com.google.android.material.R.attr.materialCardViewOutlinedStyle);
            LinearLayout body = column(context);
            body.setPadding(dp(8), dp(4), dp(8), dp(12));
            addView(body);

            LinearLayout header = row(context);
            handle = new ImageView(context);
            handle.setImageResource(R.drawable.ic_drag_grip_24);
            handle.setColorFilter(colorSubtle);
            handle.setContentDescription(getString(R.string.settings_extra_keys_drag_handle));
            handle.setPadding(dp(12), dp(12), dp(12), dp(12));
            header.addView(handle, new LinearLayout.LayoutParams(dp(48), dp(48)));
            title = new TextView(context);
            title.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            title.setTextColor(colorText);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            more = new ImageView(context);
            more.setImageResource(R.drawable.ic_editor_more_vert);
            more.setColorFilter(colorText);
            more.setPadding(dp(12), dp(12), dp(12), dp(12));
            more.setBackgroundResource(android.R.drawable.list_selector_background);
            more.setContentDescription(getString(R.string.settings_extra_keys_row_options_cd));
            more.setOnClickListener(v -> showRowOptions(row));
            header.addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
            body.addView(header);

            LinearLayout line = row(context);
            line.setPadding(dp(4), 0, dp(4), 0);
            HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            caps = new LinearLayout(context);
            caps.setOrientation(LinearLayout.HORIZONTAL);
            scroll.addView(caps, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            line.addView(scroll, new LinearLayout.LayoutParams(0, dp(KEY_CAP_DP), 1f));

            add = new TextView(context);
            add.setText("+");
            add.setGravity(Gravity.CENTER);
            add.setTextColor(colorText);
            add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
            add.setContentDescription(getString(R.string.settings_extra_keys_add_key_cd));
            GradientDrawable dashed = new GradientDrawable();
            dashed.setColor(Color.TRANSPARENT);
            dashed.setStroke(Math.max(1, dp(1)), colorSubtle, dp(4), dp(3));
            dashed.setCornerRadius(dp(12));
            add.setBackground(dashed);
            add.setOnClickListener(v -> addKeyTo(row));
            LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                dp(KEY_CAP_DP), dp(KEY_CAP_DP));
            addParams.setMarginStart(dp(GAP_DP));
            line.addView(add, addParams);
            body.addView(line);
        }

        @SuppressLint("ClickableViewAccessibility")
        void bind(int rowIndex, @NonNull RecyclerView.ViewHolder holder) {
            this.row = rowIndex;
            Context context = getContext();
            title.setText(getString(R.string.settings_extra_keys_row, rowIndex + 1));
            handle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder);
                    return true;
                }
                return false;
            });
            caps.removeAllViews();
            List<ExtraKeysLayoutModel.Key> keys = draft.page(currentPage).row(rowIndex);
            for (int i = 0; i < keys.size(); i++) {
                ExtraKeysLayoutModel.Key key = keys.get(i);
                TextView cap = capView(context, key);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(KEY_CAP_DP));
                cap.setMinWidth(dp(KEY_CAP_DP));
                cap.setMaxWidth(dp(2 * KEY_CAP_DP));
                if (i > 0) params.setMarginStart(dp(GAP_DP));
                caps.addView(cap, params);
            }
        }
    }

    private TextView capView(@NonNull Context context, @NonNull ExtraKeysLayoutModel.Key key) {
        TextView cap = new TextView(context);
        cap.setGravity(Gravity.CENTER);
        cap.setTextColor(colorText);
        cap.setSingleLine(true);
        cap.setEllipsize(TextUtils.TruncateAt.END);
        cap.setPadding(dp(3), 0, dp(3), 0);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(cap, 8, 16, 1,
            TypedValue.COMPLEX_UNIT_SP);
        cap.setText(NerdFontSpans.span(context, ExtraKeyActionLabels.capText(key, displayMap)));
        String title = ExtraKeyActionLabels.title(context, key);
        cap.setContentDescription(title);
        // Marked, never refused: a key that does nothing where these keys go is drawn like a dead
        // key on the bar, and stays tappable and editable.
        com.termux.app.wall.PaneWallPage place = target.markingPlace();
        if (place != null && !ExtraKeyEligibility.isUsable(key.key, place)) {
            cap.setTextColor(ColorUtils.setAlphaComponent(colorText, ExtraKeyCapView.DISABLED_LABEL_ALPHA));
            cap.setContentDescription(title + ", "
                + getString(R.string.settings_extra_keys_does_nothing_on, targetName(target)));
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(colorCap);
        background.setCornerRadius(dp(12));
        cap.setBackground(background);
        cap.setOnClickListener(v -> openKeySheet(key));
        return cap;
    }

    // ------------------------------------------------------------- save bar

    /** True when the window is short (a phone in landscape), whatever the orientation says. */
    private boolean isShort() {
        return getResources().getConfiguration().screenHeightDp < 480;
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The activity handles configChanges, so this view survives rotation: restyle it.
        applyCompact();
    }

    /** Compact save bar and no hint row when short; the stock sizes otherwise. */
    private void applyCompact() {
        if (saveBar == null || cancelBtn == null || saveButton == null) return;
        boolean compact = isShort();
        saveBar.setPadding(dp(16), dp(compact ? 2 : 10), dp(16), dp(compact ? 2 : 12));
        int h = dp(compact ? 40 : 48);
        MaterialButton[] buttons = {cancelBtn, saveButton};
        if (stockButton == null) {
            stockButton = new int[buttons.length][];
            for (int i = 0; i < buttons.length; i++) {
                MaterialButton b = buttons[i];
                stockButton[i] = new int[]{b.getInsetTop(), b.getInsetBottom(),
                    b.getMinHeight(), b.getMinimumHeight()};
            }
        }
        for (int i = 0; i < buttons.length; i++) {
            MaterialButton b = buttons[i];
            int[] stock = stockButton[i];
            // View minimum height is what measure honours; TextView.setMinHeight alone loses to
            // the style's 48dp-with-insets, so set both and drop the 4dp insets when compact.
            b.setMinHeight(h);
            b.setMinimumHeight(h);
            b.setInsetTop(compact ? 0 : stock[0]);
            b.setInsetBottom(compact ? 0 : stock[1]);
            if (!compact) { b.setMinHeight(stock[2]); b.setMinimumHeight(stock[3]); }
            b.requestLayout();
        }
        if (hintView != null) setShown(hintView, !compact && drafts.isOwn(target));
    }

    private View saveBar(@NonNull Context context) {
        LinearLayout bar = row(context);
        bar.setBackgroundColor(MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurface, Color.BLACK));
        MaterialButton cancelButton = new MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancelBtn = cancelButton;
        cancelButton.setText(android.R.string.cancel);
        cancelButton.setOnClickListener(v -> cancel());
        bar.addView(cancelButton);
        View spacer = new View(context);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        saveButton = new MaterialButton(context);
        saveButton.setText(R.string.settings_extra_keys_save_changes);
        saveButton.setSingleLine(true);
        saveButton.setEllipsize(TextUtils.TruncateAt.END);
        saveButton.setOnClickListener(v -> save());
        bar.addView(saveButton);
        return bar;
    }

    private void updateSaveBar() {
        if (saveButton == null) return;
        saveButton.setEnabled(isDirty());
    }

    // -------------------------------------------------------------- helpers

    private static LinearLayout column(@NonNull Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private static LinearLayout row(@NonNull Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private int dp(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
