package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import android.app.Application;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class TaiModelStoreDeleteGuardTest {

    @Test
    public void deleteUserModel_requiresExplicitConfirmation() {
        TaiModelStore store = new TaiModelStore(ApplicationProvider.<Context>getApplicationContext());
        TaiModelStore.DeleteResult blocked = store.deleteUserModel("assistant", false, false);

        assertFalse(blocked.ok);
        assertEquals(TaiModelStore.ERROR_DELETE_REQUIRES_CONFIRMATION, blocked.errorCode);
    }

    @Test
    public void deleteUserModel_blocksLoadedActiveModelBeforeDeleting() {
        TaiModelStore store = new TaiModelStore(ApplicationProvider.<Context>getApplicationContext());
        TaiModelStore.DeleteResult blocked = store.deleteUserModel("assistant", true, true);

        assertFalse(blocked.ok);
        assertEquals(TaiModelStore.ERROR_ACTIVE_MODEL_LOADED, blocked.errorCode);

        TaiModelStore.DeleteResult allowed = store.deleteUserModel("assistant", false, true);
        assertTrue(allowed.ok);
    }
}
