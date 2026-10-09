package com.termux.app.launcher.widget.builtin;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * The calendar events the agenda and calendar widgets share: today and the next two weeks, read
 * from the calendar provider on the widgets' background thread.
 *
 * <p>One source per {@link BuiltinWidgetServices}. It runs only while a widget is subscribed:
 * the first subscriber registers a provider observer (debounced to one reload), the minute tick
 * (to reload when the day turns) and the permission listener; the last one to leave unregisters
 * all three. The last result is kept across that gap so a widget coming back draws at once.
 * While unsubscribed the source holds no reference to the services, so the weak map can let
 * both go with the page.</p>
 */
final class CalendarEventsSource {
    /** Told on the main thread whenever a new snapshot replaces the last. */
    interface Listener { void onCalendarEvents(@NonNull Snapshot snapshot); }

    /** What the provider said at one moment. */
    static final class Snapshot {
        static final Snapshot NONE = new Snapshot(false, false, Collections.emptyList());

        /** False until the first read since the source was made. */
        final boolean loaded;
        /** False when calendar access is not granted: {@link #events} is then empty. */
        final boolean permitted;
        /** In {@link CalendarEvent#ORDER}. */
        @NonNull final List<CalendarEvent> events;

        Snapshot(boolean loaded, boolean permitted, @NonNull List<CalendarEvent> events) {
            this.loaded = loaded; this.permitted = permitted; this.events = events;
        }
    }

    /** Today plus this many days are read. */
    static final int WINDOW_DAYS = 14;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long DEBOUNCE_MS = 300L;
    /** A bound on what one read keeps, whatever the calendar holds. */
    private static final int MAX_EVENTS = 400;

    private static final String[] PROJECTION = {
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.CALENDAR_COLOR,
    };
    private static final String SELECTION = CalendarContract.Instances.VISIBLE + "=1"
        + " AND (" + CalendarContract.Instances.STATUS + " IS NULL OR "
        + CalendarContract.Instances.STATUS + "!=" + CalendarContract.Events.STATUS_CANCELED + ")"
        + " AND (" + CalendarContract.Instances.SELF_ATTENDEE_STATUS + " IS NULL OR "
        + CalendarContract.Instances.SELF_ATTENDEE_STATUS + "!="
        + CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED + ")";

    private static final Map<BuiltinWidgetServices, CalendarEventsSource> SOURCES = new WeakHashMap<>();

    /** The source shared by every widget on {@code services}' page. Main thread only. */
    @NonNull static CalendarEventsSource of(@NonNull BuiltinWidgetServices services) {
        CalendarEventsSource source = SOURCES.get(services);
        if (source == null) {
            source = new CalendarEventsSource(services.context());
            SOURCES.put(services, source);
        }
        return source;
    }

    static boolean hasPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull private final Context context;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    @Nullable private BuiltinWidgetServices active;
    @NonNull private Snapshot snapshot = Snapshot.NONE;
    private int generation;
    @Nullable private LocalDate lastDay;
    @Nullable private ZoneId lastZone;
    @Nullable private ContentObserver observer;

    private final Runnable reloadTask = this::reload;
    private final BuiltinWidgetServices.TickListener tick = this::onTick;
    private final BuiltinWidgetServices.PermissionListener permission = this::reload;

    private CalendarEventsSource(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** The last result; {@link Snapshot#NONE} before the first. */
    @NonNull Snapshot snapshot() { return snapshot; }

    void subscribe(@NonNull BuiltinWidgetServices services, @NonNull Listener listener) {
        if (listeners.contains(listener)) return;
        listeners.add(listener);
        if (listeners.size() == 1) {
            active = services;
            services.addTickListener(tick);
            services.addPermissionListener(permission);
            reload();
        }
    }

    void unsubscribe(@NonNull Listener listener) {
        if (!listeners.remove(listener) || !listeners.isEmpty()) return;
        BuiltinWidgetServices services = active;
        active = null;
        if (services != null) {
            services.removeTickListener(tick);
            services.removePermissionListener(permission);
        }
        main.removeCallbacks(reloadTask);
        generation++;
        unregisterObserver();
    }

    private void onTick() {
        if (active == null) return;
        ZoneId zone = ZoneId.systemDefault();
        if (!zone.equals(lastZone) || !LocalDate.now(zone).equals(lastDay)) reload();
    }

    private void scheduleReload() {
        main.removeCallbacks(reloadTask);
        main.postDelayed(reloadTask, DEBOUNCE_MS);
    }

    /** Reads the window again now; the answer replaces the snapshot when it lands. */
    private void reload() {
        BuiltinWidgetServices services = active;
        if (services == null) return;
        main.removeCallbacks(reloadTask);
        int ticket = ++generation;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        lastDay = today;
        lastZone = zone;
        if (!hasPermission(context)) {
            unregisterObserver();
            publish(new Snapshot(true, false, Collections.emptyList()));
            return;
        }
        registerObserver();
        long from = CalendarWidgetFormats.startOfDay(today, zone);
        long until = CalendarWidgetFormats.startOfDay(today.plusDays(WINDOW_DAYS + 1), zone);
        try {
            services.io().execute(() -> {
                List<CalendarEvent> events;
                boolean permitted = true;
                try {
                    events = query(from, until, zone);
                } catch (SecurityException denied) {
                    events = Collections.emptyList();
                    permitted = false;
                }
                Snapshot next = new Snapshot(true, permitted, events);
                main.post(() -> {
                    if (ticket != generation || listeners.isEmpty()) return;
                    publish(next);
                });
            });
        } catch (RejectedExecutionException ignored) {
            // The page is being torn down; nobody is left to draw the answer.
        }
    }

    private void publish(@NonNull Snapshot next) {
        snapshot = next;
        for (Listener listener : new ArrayList<>(listeners)) listener.onCalendarEvents(next);
    }

    /** Occurrences touching [{@code from}, {@code until}), normalised and sorted. Background thread. */
    @NonNull private List<CalendarEvent> query(long from, long until, @NonNull ZoneId zone) {
        // All-day occurrences are stored at UTC midnights, so read a day either side and trim
        // once their bounds are local.
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, from - DAY_MS);
        ContentUris.appendId(uri, until + DAY_MS);
        List<CalendarEvent> events = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(uri.build(), PROJECTION, SELECTION, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) return events;
            while (cursor.moveToNext() && events.size() < MAX_EVENTS) {
                int color = cursor.isNull(7) ? 0 : cursor.getInt(7);
                if (color == 0 && !cursor.isNull(8)) color = cursor.getInt(8);
                CalendarEvent event = CalendarEvent.of(cursor.getLong(0), cursor.getLong(1),
                    cursor.getLong(2), cursor.getInt(3) != 0, cursor.getString(4),
                    cursor.getString(5), cursor.getString(6), color, zone);
                boolean inWindow = event.startMs < until && (event.endMs > from
                    || (event.endMs == event.startMs && event.startMs >= from));
                if (inWindow) {
                    events.add(event);
                }
            }
        } catch (SecurityException denied) {
            throw denied;
        } catch (RuntimeException unreadable) {
            // A provider that fails mid-read leaves what was read; the next change reads again.
        }
        Collections.sort(events, CalendarEvent.ORDER);
        return events;
    }

    private void registerObserver() {
        if (observer != null) return;
        ContentObserver next = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { scheduleReload(); }
        };
        try {
            context.getContentResolver().registerContentObserver(CalendarContract.CONTENT_URI,
                true, next);
            observer = next;
        } catch (RuntimeException ignored) {
            // No calendar provider: nothing will ever change, the tick still turns the day.
        }
    }

    private void unregisterObserver() {
        ContentObserver current = observer;
        observer = null;
        if (current == null) return;
        try { context.getContentResolver().unregisterContentObserver(current); }
        catch (RuntimeException ignored) { }
    }
}
