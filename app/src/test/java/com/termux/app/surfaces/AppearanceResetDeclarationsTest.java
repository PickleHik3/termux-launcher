package com.termux.app.surfaces;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;

import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingDeclarations;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * The declarations in the Appearance reset group are exactly the declared app-preferences keys
 * the Appearance reset removes: a key the reset removes by name is declared in the group, and the
 * group names nothing the reset keeps.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppearanceResetDeclarationsTest {

    @Test
    public void declaredKeysInTheAppearanceGroupAreTheOnesTheResetRemoves() {
        List<String> inGroup = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (SettingDeclaration declaration : SettingDeclarations.ALL) {
            if (declaration.store != SettingDeclaration.Store.APP_PREFERENCES) continue;
            if (declaration.resetGroup == SettingDeclaration.ResetGroup.APPEARANCE)
                inGroup.add(declaration.id);
            if (AppearanceReset.inScope(declaration.key)) removed.add(declaration.id);
        }
        assertEquals(inGroup, removed);
    }
}
