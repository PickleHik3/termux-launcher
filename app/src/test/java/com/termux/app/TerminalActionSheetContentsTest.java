package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.termux.R;
import com.termux.app.terminal.TerminalActionMenu;
import com.termux.app.terminal.TerminalSheetController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What the terminal long-press menu offers, and which rows it hides.
 *
 * <p>Written because <em>Style</em> — the upstream entry into Termux:Styling — had silently gone
 * missing from this fork: the string stayed, translated into eleven locales and referenced by
 * nothing, and issue #11 was someone noticing. A menu is easy to edit and hard to spot a hole in,
 * so the entries that answer a reported bug are asserted rather than left to review. Style now sits
 * under <em>More</em>, which is a place it can go missing from just as quietly.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalActionSheetContentsTest {

    @Test
    public void theTopCardIsInTheOrderTheUserAskedFor() {
        TermuxActivity activity = activity();
        setClipboardText(activity, "echo hi");

        assertEquals(Arrays.asList(string(R.string.action_select_url),
                string(R.string.action_search_scrollback),
                string(R.string.action_open_settings),
                string(R.string.action_paste),
                string(R.string.action_more)),
            titles(TerminalActionMenu.buildTopRows(activity)));
    }

    @Test
    public void pasteIsHiddenWhileTheClipboardIsEmpty() {
        TermuxActivity activity = activity();

        assertFalse("pasting nothing is not an action worth a row",
            titles(TerminalActionMenu.buildTopRows(activity)).contains(string(R.string.action_paste)));
    }

    @Test
    public void pasteAppearsOnceTheClipboardHoldsText() {
        TermuxActivity activity = activity();
        setClipboardText(activity, "ls -la");

        assertTrue(titles(TerminalActionMenu.buildTopRows(activity))
            .contains(string(R.string.action_paste)));
    }

    /**
     * Same shape as the Paste rule, for the same reason: {@code clipboard.copy_selected} answers
     * {@code no_selection} with nothing selected, so an always-present Copy could only ever fail.
     */
    @Test
    public void copyIsHiddenWithoutASelection() {
        TermuxActivity activity = activity();

        assertFalse(titles(TerminalActionMenu.buildTopRows(activity))
            .contains(string(R.string.action_copy_selection)));
    }

    @Test
    public void theSheetOffersStyle() {
        assertTrue("the terminal long-press menu must offer Style, under More",
            titles(TerminalActionMenu.buildMoreRows(activity(), 1234))
                .contains(string(R.string.action_style_terminal)));
    }

    @Test
    public void moreHoldsEverythingTheTopCardDemoted() {
        List<String> titles = titles(TerminalActionMenu.buildMoreRows(activity(), 1234));
        for (int title : new int[] {R.string.action_command_palette, R.string.action_share_transcript,
                R.string.action_style_terminal, R.string.action_set_background_image,
                R.string.action_enable_background_image, R.string.action_glass_lab,
                R.string.action_reset_terminal}) {
            assertTrue(string(title), titles.contains(string(title)));
        }
        assertTrue("Kill process keeps the pid it kills in its label",
            titles.contains(string(R.string.action_kill_process, 1234)));
    }

    /** More stacks a second card; back has to give the first one back, not the terminal. */
    @Test
    public void backPopsMoreRatherThanClosingTheMenu() {
        TermuxActivity activity = laidOutActivity();
        assertTrue(TerminalActionMenu.show(activity, 1234));
        TerminalSheetController sheet = activity.getTerminalSheetController();

        assertTrue(clickRow(sheet.topCard(), string(R.string.action_more)));
        assertEquals(2, sheet.depth());

        activity.onBackPressed();

        assertEquals("More went, the menu under it stayed", 1, sheet.depth());
        assertNotNull(findRow(sheet.topCard(), string(R.string.action_select_url)));
    }

    @NonNull
    private static List<String> titles(@NonNull List<TerminalActionMenu.Row> rows) {
        List<String> titles = new ArrayList<>();
        for (TerminalActionMenu.Row row : rows) titles.add(row.toString());
        return titles;
    }

    private static boolean clickRow(@Nullable View card, @NonNull String label) {
        View row = findRow(card, label);
        return row != null && row.performClick();
    }

    /** The row view carrying {@code label}, found the way a finger finds it: by what it reads. */
    @Nullable
    private static View findRow(@Nullable View view, @NonNull String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) {
            return view.getParent() instanceof View ? (View) view.getParent() : view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findRow(group.getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }

    private static void setClipboardText(@NonNull Context context, @NonNull String text) {
        ClipboardManager clipboard =
            (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text));
    }

    private static TermuxActivity activity() {
        return Robolectric.buildActivity(TermuxActivity.class).get();
    }

    private static TermuxActivity laidOutActivity() {
        TermuxActivity activity = activity();
        activity.setContentView(R.layout.activity_termux);
        return activity;
    }

    private static String string(int resId, Object... formatArgs) {
        Context context = ApplicationProvider.getApplicationContext();
        return formatArgs.length == 0
            ? context.getString(resId) : context.getString(resId, formatArgs);
    }
}
