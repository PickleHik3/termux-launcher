package com.termux.app.firstrun;

import static com.termux.app.firstrun.FirstRunPermissionsCard.Item;
import static com.termux.app.firstrun.FirstRunPermissionsCard.Row;
import static com.termux.app.firstrun.FirstRunPermissionsCard.State;
import static com.termux.app.firstrun.FirstRunPermissionsCard.Tap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Whether the card is offered at all, and which of its rows carry something to press.
 *
 * <p>The table that matters is granted / refused / never asked, on a fresh install and on an
 * update: a fresh install always opens on the card, an update sees it only while something is
 * still ungranted and only until it has been answered once.
 */
public class FirstRunPermissionsCardTest {

    private static final boolean FRESH = false;
    private static final boolean UPDATER = true;
    private static final boolean SEEN = true;
    private static final boolean UNSEEN = false;
    /** Settings asking for the tour again, rather than a launch asking nothing of the user. */
    private static final boolean REPLAY = true;
    private static final boolean OPENED = false;
    /** Whether the card is already on the screen when a restart asks whether to raise one. */
    private static final boolean CARD_UP = true;
    private static final boolean NO_CARD = false;

    @Test
    public void aFreshInstallAlwaysOpensOnTheCard() {
        for (State wallpaper : State.values()) {
            for (State weather : State.values()) {
                assertTrue(wallpaper + "/" + weather,
                    FirstRunPermissionsCard.shouldShow(FRESH, UNSEEN, wallpaper, weather, OPENED));
            }
        }
        // Even with everything already granted, and even with no weather row to show.
        assertTrue(FirstRunPermissionsCard.shouldShow(FRESH, UNSEEN, State.GRANTED, null, OPENED));
        assertTrue(FirstRunPermissionsCard.shouldShow(FRESH, UNSEEN, State.GRANTED,
            State.GRANTED, OPENED));
    }

    @Test
    public void anUpdaterSeesItOnlyWhileSomethingIsStillUngranted() {
        assertFalse(FirstRunPermissionsCard.shouldShow(UPDATER, UNSEEN, State.GRANTED,
            State.GRANTED, OPENED));
        assertFalse(FirstRunPermissionsCard.shouldShow(UPDATER, UNSEEN, State.GRANTED, null,
            OPENED));
        assertTrue(FirstRunPermissionsCard.shouldShow(UPDATER, UNSEEN, State.DENIED,
            State.GRANTED, OPENED));
        assertTrue(FirstRunPermissionsCard.shouldShow(UPDATER, UNSEEN, State.NOT_ASKED,
            State.GRANTED, OPENED));
        assertTrue(FirstRunPermissionsCard.shouldShow(UPDATER, UNSEEN, State.GRANTED,
            State.NOT_ASKED, OPENED));
        assertTrue(FirstRunPermissionsCard.shouldShow(UPDATER, UNSEEN, State.GRANTED,
            State.DENIED, OPENED));
    }

    @Test
    public void anUpdaterIsNeverAskedTwice() {
        for (State wallpaper : State.values())
            for (State weather : State.values())
                assertFalse(wallpaper + "/" + weather,
                    FirstRunPermissionsCard.shouldShow(UPDATER, SEEN, wallpaper, weather, OPENED));
    }

    @Test
    public void aFreshInstallStillOpensOnTheCardEvenIfTheFlagWasSomehowSet() {
        // The fresh-install answer does not consult the seen flag at all: the card is the first
        // thing that install shows, and the flag is only written on the way out of it.
        assertTrue(FirstRunPermissionsCard.shouldShow(FRESH, SEEN, State.GRANTED, State.GRANTED,
            OPENED));
    }

    @Test
    public void aTourReplayIsTheTourAndNothingElse() {
        // Settings' "play the tour again" walks the launcher; it never re-opens the setup card,
        // however the permissions stand and whether or not the card has been answered before.
        for (State wallpaper : State.values()) {
            for (State weather : State.values()) {
                assertFalse("fresh " + wallpaper + "/" + weather, FirstRunPermissionsCard
                    .shouldShow(FRESH, UNSEEN, wallpaper, weather, REPLAY));
                assertFalse("updater " + wallpaper + "/" + weather, FirstRunPermissionsCard
                    .shouldShow(UPDATER, UNSEEN, wallpaper, weather, REPLAY));
            }
        }
        assertFalse(FirstRunPermissionsCard.shouldShow(FRESH, UNSEEN, State.DENIED, null,
            REPLAY));
    }

    @Test
    public void aRestartWithTheSetupUnansweredRaisesTheCardAgain() {
        // The process can die with the card up — it is the very first thing a fresh install shows
        // — and the activity that comes back has to raise it again, or nothing ever starts the
        // tour and both stored flags stay false.
        for (State wallpaper : State.values())
            for (State weather : State.values())
                assertTrue(wallpaper + "/" + weather, FirstRunPermissionsCard
                    .shouldResume(FRESH, UNSEEN, wallpaper, weather, NO_CARD));
        // And on an update, on the same terms the cold start uses.
        assertTrue(FirstRunPermissionsCard.shouldResume(UPDATER, UNSEEN, State.DENIED,
            State.GRANTED, NO_CARD));
    }

    @Test
    public void aRestartRaisesNothingOverACardAlreadyUp() {
        assertFalse(FirstRunPermissionsCard.shouldResume(FRESH, UNSEEN, State.NOT_ASKED,
            State.NOT_ASKED, CARD_UP));
        assertFalse(FirstRunPermissionsCard.shouldResume(UPDATER, UNSEEN, State.DENIED,
            State.DENIED, CARD_UP));
    }

    @Test
    public void aRestartWithTheSetupAlreadyAnsweredRaisesNothing() {
        assertFalse(FirstRunPermissionsCard.shouldResume(UPDATER, SEEN, State.DENIED,
            State.DENIED, NO_CARD));
        assertFalse(FirstRunPermissionsCard.shouldResume(UPDATER, UNSEEN, State.GRANTED,
            State.GRANTED, NO_CARD));
        assertFalse(FirstRunPermissionsCard.shouldResume(UPDATER, UNSEEN, State.GRANTED, null,
            NO_CARD));
    }

    @Test
    public void everyRowCarriesATitleAndASentence() {
        List<Row> rows = FirstRunPermissionsCard.rows(State.NOT_ASKED, State.NOT_ASKED, true,
            false);
        assertEquals(3, rows.size());
        assertEquals(Item.WALLPAPER, rows.get(0).item);
        assertEquals(Item.WEATHER, rows.get(1).item);
        assertEquals(Item.DISPLAY, rows.get(2).item);
        for (Row row : rows) {
            assertNotEquals("no title for " + row.item, 0, row.titleRes);
            assertNotEquals("no copy for " + row.item, 0, row.copyRes);
        }
    }

    @Test
    public void aRowIsLeftOutRatherThanShownDead() {
        List<Row> noWeather = FirstRunPermissionsCard.rows(State.NOT_ASKED, null, true, false);
        assertEquals(2, noWeather.size());
        assertEquals(Item.DISPLAY, noWeather.get(1).item);

        List<Row> noDisplay = FirstRunPermissionsCard.rows(State.NOT_ASKED, State.NOT_ASKED,
            false, false);
        assertEquals(2, noDisplay.size());
        assertEquals(Item.WEATHER, noDisplay.get(1).item);

        assertEquals(1, FirstRunPermissionsCard.rows(State.NOT_ASKED, null, false, false).size());
    }

    @Test
    public void onlyAnUnansweredPermissionRowCarriesAButton() {
        List<Row> rows = FirstRunPermissionsCard.rows(State.GRANTED, State.DENIED, true, false);
        Row wallpaper = rows.get(0);
        Row weather = rows.get(1);
        assertFalse(wallpaper.hasButton());
        assertEquals(0, wallpaper.buttonRes());
        // A refused row keeps its button, so a second tap can ask again.
        assertTrue(weather.hasButton());
        assertNotEquals(0, weather.buttonRes());
        assertTrue(FirstRunPermissionsCard.rows(State.NOT_ASKED, null, false, false)
            .get(0).hasButton());
    }

    @Test
    public void anAnsweredPermissionRowSaysWhereItStands() {
        List<Row> rows = FirstRunPermissionsCard.rows(State.GRANTED, State.DENIED, false, false);
        assertNotEquals(0, rows.get(0).statusRes());
        assertNotEquals(0, rows.get(1).statusRes());
        assertNotEquals(rows.get(0).statusRes(), rows.get(1).statusRes());
        // Never asked has nothing to report yet: the button is the whole of the row's state.
        assertEquals(0, FirstRunPermissionsCard.rows(State.NOT_ASKED, null, false, false)
            .get(0).statusRes());
    }

    @Test
    public void theDisplayRowIsASwitchAndNeverAButton() {
        Row off = FirstRunPermissionsCard.rows(State.GRANTED, null, true, false).get(1);
        Row on = FirstRunPermissionsCard.rows(State.GRANTED, null, true, true).get(1);
        assertTrue(off.isSwitch);
        assertTrue(on.isSwitch);
        assertFalse(off.hasButton());
        assertFalse(on.hasButton());
        assertEquals(0, off.statusRes());
        assertEquals(0, on.statusRes());
        assertFalse(off.isOn());
        assertTrue(on.isOn());
    }

    @Test
    public void aRowRefusedTwiceOpensTheSettingsPageInsteadOfAskingAgain() {
        assertEquals(Tap.REQUEST, FirstRunPermissionsCard.tapFor(State.NOT_ASKED, true));
        assertEquals(Tap.REQUEST, FirstRunPermissionsCard.tapFor(State.DENIED, true));
        assertEquals(Tap.OPEN_SETTINGS, FirstRunPermissionsCard.tapFor(State.DENIED, false));
        assertEquals(Tap.NONE, FirstRunPermissionsCard.tapFor(State.GRANTED, true));
        assertEquals(Tap.NONE, FirstRunPermissionsCard.tapFor(State.GRANTED, false));
    }
}
