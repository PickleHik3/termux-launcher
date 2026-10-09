package com.termux.launcherctl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LauncherCtlNotificationRoutesTest {
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void handles_onlyTheMethodsEachRouteAnswers() {
        assertTrue(LauncherCtlNotificationRoutes.handles("GET", "/v1/notifications"));
        assertTrue(LauncherCtlNotificationRoutes.handles("GET", "/v1/notifications/apps"));
        assertTrue(LauncherCtlNotificationRoutes.handles("GET", "/v1/notifications/active"));
        assertTrue(LauncherCtlNotificationRoutes.handles("POST", "/v1/notifications/clear"));
        assertFalse(LauncherCtlNotificationRoutes.handles("POST", "/v1/notifications"));
        assertFalse(LauncherCtlNotificationRoutes.handles("GET", "/v1/notifications/clear"));
        assertFalse(LauncherCtlNotificationRoutes.handles("DELETE", "/v1/notifications"));
        assertFalse(LauncherCtlNotificationRoutes.handles("POST", "/v1/notify"));
        assertFalse(LauncherCtlNotificationRoutes.handles("GET", "/v1/notifications/recent"));
    }

    @Test
    public void parseFilter_readsEveryArgumentFromTheQueryString() {
        Map<String, String> params = LauncherCtlApiServer.queryParameters(
            "app=Work%20Mail&since=7d&until=1d&query=invoice+due&limit=25&format=text");

        LauncherCtlNotificationStore.Filter filter =
            LauncherCtlNotificationRoutes.parseFilter(params, NOW, UTC);

        assertEquals("Work Mail", filter.app);
        assertEquals("invoice due", filter.query);
        assertEquals(NOW - TimeUnit.DAYS.toMillis(7), filter.sinceMs);
        assertEquals(NOW - TimeUnit.DAYS.toMillis(1), filter.untilMs);
        assertEquals(25, filter.limit);
    }

    @Test
    public void parseFilter_defaultsAndClampsTheLimit() {
        assertEquals(LauncherCtlNotificationStore.DEFAULT_LIMIT,
            LauncherCtlNotificationRoutes.parseFilter(new HashMap<>(), NOW, UTC).limit);
        Map<String, String> params = new HashMap<>();
        params.put("limit", "99999");
        assertEquals(LauncherCtlNotificationStore.MAX_LIMIT,
            LauncherCtlNotificationRoutes.parseFilter(params, NOW, UTC).limit);
    }

    @Test
    public void parseFilter_blankArgumentsAreNotFilters() {
        Map<String, String> params = new HashMap<>();
        params.put("app", "  ");
        params.put("since", "");
        LauncherCtlNotificationStore.Filter filter =
            LauncherCtlNotificationRoutes.parseFilter(params, NOW, UTC);
        assertNull(filter.app);
        assertEquals(0L, filter.sinceMs);
    }

    @Test
    public void parseFilter_rejectsValuesItCannotRead() {
        for (String[] bad : new String[][]{{"limit", "0"}, {"limit", "many"}, {"since", "lastweek"},
            {"until", "2026-13-40"}}) {
            Map<String, String> params = new HashMap<>();
            params.put(bad[0], bad[1]);
            try {
                LauncherCtlNotificationRoutes.parseFilter(params, NOW, UTC);
                fail("expected a rejection of " + bad[0] + "=" + bad[1]);
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.getMessage().isEmpty());
            }
        }
    }

    @Test
    public void parseFilter_rejectsUntilBeforeSince() {
        Map<String, String> params = new HashMap<>();
        params.put("since", "1d");
        params.put("until", "7d");
        try {
            LauncherCtlNotificationRoutes.parseFilter(params, NOW, UTC);
            fail("expected until before since to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("before"));
        }
    }

    @Test
    public void parseTime_relativeAges() {
        assertEquals(NOW - TimeUnit.MINUTES.toMillis(90), LauncherCtlNotificationRoutes.parseTime("90m", NOW, UTC, false));
        assertEquals(NOW - TimeUnit.HOURS.toMillis(12), LauncherCtlNotificationRoutes.parseTime("12h", NOW, UTC, false));
        assertEquals(NOW - TimeUnit.DAYS.toMillis(7), LauncherCtlNotificationRoutes.parseTime("7d", NOW, UTC, false));
        assertEquals(NOW - TimeUnit.DAYS.toMillis(14), LauncherCtlNotificationRoutes.parseTime("2w", NOW, UTC, false));
    }

    @Test
    public void parseTime_isoForms() {
        long midnight = LauncherCtlNotificationRoutes.parseTime("2026-09-28", NOW, UTC, false);
        assertEquals(java.time.Instant.parse("2026-09-28T00:00:00Z").toEpochMilli(), midnight);
        long endOfDay = LauncherCtlNotificationRoutes.parseTime("2026-09-28", NOW, UTC, true);
        assertEquals(java.time.Instant.parse("2026-09-29T00:00:00Z").toEpochMilli() - 1, endOfDay);
        assertEquals(java.time.Instant.parse("2026-09-28T10:15:00Z").toEpochMilli(),
            LauncherCtlNotificationRoutes.parseTime("2026-09-28T10:15:00", NOW, UTC, false));
        assertEquals(java.time.Instant.parse("2026-09-28T08:15:00Z").toEpochMilli(),
            LauncherCtlNotificationRoutes.parseTime("2026-09-28T10:15:00+02:00", NOW, UTC, false));
        assertEquals(NOW, LauncherCtlNotificationRoutes.parseTime(Long.toString(NOW), NOW, UTC, false));
    }

    @Test
    public void rowsText_isOneLinePerMessageWithAHint() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            List<LauncherCtlNotificationEvent> rows = Arrays.asList(
                new LauncherCtlNotificationEvent(1, "k", "com.mail", "Work Mail", null, "Ann",
                    null, "Invoice due Friday\nplease pay", null, null, null,
                    java.time.Instant.parse("2026-09-28T14:03:00Z").toEpochMilli(), 0, 0, 0, false),
                new LauncherCtlNotificationEvent(2, "k2", "com.chat", "Chat", "Project", "Project",
                    "Ben", "on my way", null, null, null,
                    java.time.Instant.parse("2026-09-28T13:00:00Z").toEpochMilli(), 0, 0, 0, true));

            String text = LauncherCtlNotificationRoutes.rowsText(rows, "Turn it on.");

            assertEquals("# Turn it on.\n"
                + "2026-09-28 14:03 · Work Mail · Ann — Invoice due Friday please pay\n"
                + "2026-09-28 13:00 · Chat · Project — Ben: on my way\n", text);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    public void rowsText_saysWhenThereIsNothing() {
        assertEquals("(no notifications)\n",
            LauncherCtlNotificationRoutes.rowsText(Collections.emptyList(), null));
    }

    @Test
    public void appsText_listsCountsAndLastSeen() throws Exception {
        JSONArray apps = new JSONArray();
        apps.put(new JSONObject().put("package", "com.mail").put("app", "Work Mail")
            .put("count", 12).put("lastSeen", 1_800_000_000_000L));
        String text = LauncherCtlNotificationRoutes.appsText(apps, null);
        assertTrue(text.startsWith("Work Mail · com.mail · 12 messages · last "));
    }

    /** Every notification route is rate limited under the key the server looks it up by. */
    @Test
    public void notificationRoutes_haveRateLimiters() throws Exception {
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        Method init = LauncherCtlApiServer.class.getDeclaredMethod("initializeRateLimiters");
        init.setAccessible(true);
        init.invoke(server);
        Field field = LauncherCtlApiServer.class.getDeclaredField("rateLimiters");
        field.setAccessible(true);
        Map<?, ?> limiters = (Map<?, ?>) field.get(server);
        for (String key : new String[]{"GET:/v1/notifications", "GET:/v1/notifications/apps",
            "GET:/v1/notifications/active", "POST:/v1/notifications/clear"}) {
            assertTrue(key, limiters.containsKey(key));
            assertTrue(key, limiters.containsKey(LauncherCtlApiServer.rateLimitKey(
                key.substring(0, key.indexOf(':')), key.substring(key.indexOf(':') + 1))));
        }
    }
}
