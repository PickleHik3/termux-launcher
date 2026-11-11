package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

/** The consent policy against fakes: no views, no native requests. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class ClipboardReadConsentTest {

    private static final class FakeRequest implements ClipboardReadConsent.Request {
        final boolean pasted;
        final boolean listing;
        String answer; // "deny", or "allow:<text>"
        int answers;

        FakeRequest(boolean pasted, boolean listing) {
            this.pasted = pasted;
            this.listing = listing;
        }

        @Override public void allow(String text) { answers++; if (answer == null) answer = "allow:" + text; }
        @Override public void deny() { answers++; if (answer == null) answer = "deny"; }
        @Override public boolean isPasted() { return pasted; }
        @Override public boolean isListingOnly() { return listing; }
    }

    private static final class FakeUi implements ClipboardReadConsent.Ui {
        boolean settingOn = true;
        boolean visible = true;
        Object front;
        final Map<Object, String> names = new HashMap<>();
        String clipboard = "clip";
        int sheets;
        String sheetName;
        boolean sheetOpen;
        ClipboardReadConsent.Callback callback;

        @Override public boolean isReadSettingOn() { return settingOn; }
        @Override public boolean isLauncherVisible() { return visible; }
        @Override public Object frontSession() { return front; }
        @Override public String programName(Object session) { return names.get(session); }
        @Override public String readClipboard() { return clipboard; }
        @Override public boolean showSheet(String programName, ClipboardReadConsent.Callback cb) {
            sheets++;
            sheetName = programName;
            sheetOpen = true;
            callback = cb;
            return true;
        }
        @Override public void closeSheet() {
            if (!sheetOpen) return;
            sheetOpen = false;
            callback.onDismissed();
        }
    }

    private SharedPreferences store;
    private Context context;
    private FakeUi ui;
    private ClipboardReadGrants grants;
    private ClipboardReadConsent consent;
    private final Object a = new Object();
    private final Object b = new Object();

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("clipboard-read-consent-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        ui = new FakeUi();
        ui.front = a;
        ui.names.put(a, "vim");
        ui.names.put(b, "cat");
        grants = new ClipboardReadGrants(new TermuxAppSharedPreferences(context, store, null));
        consent = new ClipboardReadConsent(ui, grants);
    }

    @Test
    public void settingOffDeniesAtOnceWithoutASheet() {
        ui.settingOn = false;
        FakeRequest r = new FakeRequest(false, false);
        consent.onRequest(a, r);
        assertEquals("deny", r.answer);
        assertEquals(0, ui.sheets);
    }

    @Test
    public void pastedAndListingOnlyNeedNoConsent() {
        FakeRequest pasted = new FakeRequest(true, false);
        FakeRequest listing = new FakeRequest(false, true);
        consent.onRequest(a, pasted);
        consent.onRequest(a, listing);
        assertEquals("allow:clip", pasted.answer);
        assertEquals("allow:clip", listing.answer);
        assertEquals(0, ui.sheets);
    }

    @Test
    public void aPastedReadIsAllowedEvenWithTheSettingOff() {
        ui.settingOn = false;
        FakeRequest pasted = new FakeRequest(true, false);
        consent.onRequest(a, pasted);
        assertEquals("allow:clip", pasted.answer);
    }

    @Test
    public void aRequestFromAPaneNotInFrontIsQueuedThenShownWhenItComesToFront() {
        FakeRequest r = new FakeRequest(false, false);
        consent.onRequest(b, r);
        assertNull(r.answer);
        assertEquals(0, ui.sheets);
        ui.front = b;
        consent.onFrontMaybeChanged();
        assertEquals(1, ui.sheets);
        assertEquals("cat", ui.sheetName);
        ui.callback.onChoice(ClipboardReadConsent.Choice.ONCE);
        assertEquals("allow:clip", r.answer);
    }

    @Test
    public void aGrantedRequestWaitsForTheLauncherToBeVisible() {
        grants.allowAlways("vim");
        ui.visible = false;
        FakeRequest r = new FakeRequest(false, false);
        consent.onRequest(a, r);
        assertNull(r.answer);
        assertEquals(0, ui.sheets);
        ui.visible = true;
        consent.onFrontMaybeChanged();
        assertEquals("allow:clip", r.answer);
        assertEquals(0, ui.sheets);
    }

    @Test
    public void sessionAndAlwaysGrantsSkipTheSheet() {
        grants.allowForSession(a, "vim");
        FakeRequest first = new FakeRequest(false, false);
        consent.onRequest(a, first);
        assertEquals("allow:clip", first.answer);
        grants.allowAlways("cat");
        ui.front = b;
        FakeRequest second = new FakeRequest(false, false);
        consent.onRequest(b, second);
        assertEquals("allow:clip", second.answer);
        assertEquals(0, ui.sheets);
    }

    @Test
    public void twoRequestsFromOneSessionShareOneSheetAndItsDecision() {
        FakeRequest r1 = new FakeRequest(false, false);
        FakeRequest r2 = new FakeRequest(false, false);
        consent.onRequest(a, r1);
        consent.onRequest(a, r2);
        assertEquals(1, ui.sheets);
        ui.callback.onChoice(ClipboardReadConsent.Choice.DENY);
        assertEquals("deny", r1.answer);
        assertEquals("deny", r2.answer);
        assertEquals(1, r1.answers);
        assertEquals(1, ui.sheets);
    }

    @Test
    public void aSecondSessionWaitsForTheOpenSheetThenGetsItsOwn() {
        FakeRequest ra = new FakeRequest(false, false);
        FakeRequest rb = new FakeRequest(false, false);
        consent.onRequest(a, ra);
        ui.front = b;
        consent.onRequest(b, rb);
        assertEquals(1, ui.sheets);
        assertNull(rb.answer);
        ui.callback.onChoice(ClipboardReadConsent.Choice.ONCE);
        assertEquals("allow:clip", ra.answer);
        assertEquals(2, ui.sheets);
        assertEquals("cat", ui.sheetName);
        ui.callback.onChoice(ClipboardReadConsent.Choice.DENY);
        assertEquals("deny", rb.answer);
    }

    @Test
    public void onceStoresNothingSessionAndAlwaysStoreTheirGrants() {
        FakeRequest once = new FakeRequest(false, false);
        consent.onRequest(a, once);
        ui.callback.onChoice(ClipboardReadConsent.Choice.ONCE);
        assertFalse(grants.isSessionGranted(a, "vim"));
        assertFalse(grants.isAlwaysGranted("vim"));

        FakeRequest session = new FakeRequest(false, false);
        consent.onRequest(a, session);
        ui.callback.onChoice(ClipboardReadConsent.Choice.SESSION);
        assertTrue(grants.isSessionGranted(a, "vim"));
        assertFalse(grants.isAlwaysGranted("vim"));

        ui.front = b;
        FakeRequest always = new FakeRequest(false, false);
        consent.onRequest(b, always);
        ui.callback.onChoice(ClipboardReadConsent.Choice.ALWAYS);
        assertTrue(grants.isAlwaysGranted("cat"));
        assertEquals("allow:clip", always.answer);
    }

    @Test
    public void dismissingTheSheetDeniesOnceAndAChoiceThenDismissDoesNotDenyAgain() {
        FakeRequest r = new FakeRequest(false, false);
        consent.onRequest(a, r);
        ui.callback.onDismissed();
        assertEquals("deny", r.answer);
        assertEquals(1, r.answers);

        FakeRequest allowed = new FakeRequest(false, false);
        consent.onRequest(a, allowed);
        ui.callback.onChoice(ClipboardReadConsent.Choice.ONCE);
        assertEquals("allow:clip", allowed.answer);
        assertEquals(1, allowed.answers);
    }

    @Test
    public void aSessionEndDeniesItsWaitingRequestsClosesItsSheetAndForgetsItsGrants() {
        grants.allowForSession(a, "vim");
        FakeRequest queued = new FakeRequest(false, false);
        ui.visible = false;
        consent.onRequest(b, queued);
        consent.onSessionFinished(b);
        assertEquals("deny", queued.answer);

        grants.allowForSession(b, "cat");
        ui.visible = true;
        ui.front = a;
        grants.forgetSession(a);
        FakeRequest open = new FakeRequest(false, false);
        FakeRequest more = new FakeRequest(false, false);
        consent.onRequest(a, open);
        consent.onRequest(a, more);
        assertTrue(ui.sheetOpen);
        consent.onSessionFinished(a);
        assertEquals("deny", open.answer);
        assertEquals("deny", more.answer);
        assertFalse(ui.sheetOpen);
        assertFalse(grants.isSessionGranted(a, "vim"));
    }

    @Test
    public void anUnknownNameAsksWithoutAProgramNameAndNeverGrantsAlways() {
        ui.names.remove(a);
        FakeRequest r = new FakeRequest(false, false);
        consent.onRequest(a, r);
        assertEquals(1, ui.sheets);
        assertNull(ui.sheetName);
        ui.callback.onChoice(ClipboardReadConsent.Choice.ALWAYS);
        assertEquals("allow:clip", r.answer);
        assertFalse(grants.isAlwaysGranted(null));
        // Without a name nothing is remembered, so the next request asks again.
        FakeRequest next = new FakeRequest(false, false);
        consent.onRequest(a, next);
        assertEquals(2, ui.sheets);
    }
}
