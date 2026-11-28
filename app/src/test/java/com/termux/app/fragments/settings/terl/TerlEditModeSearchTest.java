package com.termux.app.fragments.settings.terl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.PopupMenu;

import androidx.fragment.app.FragmentActivity;

import com.termux.app.editmode.EditModeController;
import com.termux.app.editmode.EditModeDoor;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowPopupMenu;

import java.util.List;

/** Terl's search reaches edit mode's controls: found, routed, copied (#454). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerlEditModeSearchTest {

    private final Context context = RuntimeEnvironment.getApplication();

    @Before
    public void terlOn() {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        preferences.getSharedPreferences().edit().putBoolean(TERMUX_APP.KEY_TERL_DESKTOP, true).commit();
    }

    private TerlSettingsSearch.Entry editModeHit(String query, String title) {
        List<TerlSettingsSearch.Entry> hits = TerlSettingsIndex.build(context).search(query, 60);
        for (TerlSettingsSearch.Entry hit : hits) {
            if (hit.kind == TerlSettingsSearch.Kind.EDIT_MODE && hit.title.equals(title)) return hit;
        }
        throw new AssertionError(title + " not among " + hits.size() + " hits for " + query);
    }

    @Test
    public void everyControlOnACardIsFoundWithItsCard() {
        assertEquals("BAR", editModeHit("margin", "Margin").card);
        assertEquals("WINDOWS", editModeHit("gap", "Gap").card);
        assertEquals("WALLPAPER", editModeHit("dim", "Dim").card);
        assertEquals("HOME", editModeHit("columns", "Grid columns").card);
        assertEquals("GLOBAL", editModeHit("custom", "Custom opacity").card);
        assertEquals("GLOBAL", editModeHit("custom", "Custom blur").card);
    }

    @Test
    public void appearanceAndEditFindTheEditModeDoor() {
        for (String query : new String[] {"appearance", "edit"}) {
            TerlSettingsSearch.Entry hit = editModeHit(query, "Edit mode");
            assertEquals(EditModeDoor.AT_REST, hit.card);
        }
    }

    @Test
    public void underTerminalAHiddenHomeCardControlIsFoundWithItsNote() {
        TerlSettings.registry(context).set(com.termux.app.settings.UseAsAdapter.ID, TERMUX_APP.USE_AS_TERMINAL);
        TerlSettingsSearch.Entry columns = editModeHit("columns", "Grid columns");
        assertEquals("HOME", columns.card);
        assertTrue(columns.breadcrumb, columns.breadcrumb.endsWith("Home is off"));
        // The other cards carry no note.
        assertTrue(editModeHit("margin", "Margin").breadcrumb, !editModeHit("margin", "Margin").breadcrumb.contains("Home is off"));
    }

    @Test
    public void theNavigatorRoutesAHitToEditModeWithItsCardExtra() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).get();
        TerlSettingsSearch.Entry hit = editModeHit("opacity", "Windows opacity");
        TerlSettingsNavigator.open(activity, hit);
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals("WINDOWS", started.getStringExtra(EditModeDoor.EXTRA_CARD));
        assertEquals(EditModeController.Card.WINDOWS,
            EditModeDoor.cardOf(started.getStringExtra(EditModeDoor.EXTRA_CARD)));
    }

    @Test
    public void holdingAnEditModeHitOffersCopyAsConfigWithItsLine() {
        TerlSettings.registry(context).set("look.keyboard.opacity", "35");
        TerlSettingsSearch.Entry hit = editModeHit("opacity", "Keyboard opacity");
        assertEquals("look.keyboard.opacity", hit.id);
        assertTrue(TerlRowMenu.show(new View(context), hit.id, () -> { }));
        PopupMenu menu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(menu);
        // Copy as config comes first; Reset follows while the value is off its default.
        assertEquals("Copy as config", menu.getMenu().getItem(0).getTitle().toString());
        shadowOf(menu).getOnMenuItemClickListener().onMenuItemClick(menu.getMenu().getItem(0));
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("look.keyboard.opacity = 35", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }
}
