package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** The newest screenshot is read from a fake media store, followed on change, and never without the permission. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class RecentScreenshotTest {

    private static final Uri COLLECTION = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    private Application mApplication;
    private FakeMedia mMedia;
    private final List<RecentScreenshot.Shot> mHeard = new ArrayList<>();
    private int mAnswers;
    private RecentScreenshot mSource;

    @Before
    public void setUp() {
        mApplication = RuntimeEnvironment.getApplication();
        mMedia = Robolectric.setupContentProvider(FakeMedia.class, MediaStore.AUTHORITY);
        mSource = new RecentScreenshot(mApplication, Runnable::run, new Handler(Looper.getMainLooper()),
            shot -> {
                mAnswers++;
                mHeard.add(shot);
            });
    }

    @Test
    public void theNewestScreenshotIsReadOnStartBeforeAndroidTen() {
        grant();
        mMedia.row(7, 1_700_000_000L, "image/png");

        mSource.start();
        idle();

        assertEquals(1, mAnswers);
        RecentScreenshot.Shot shot = mHeard.get(0);
        assertEquals(7, shot.id);
        assertEquals("image/png", shot.mime);
        assertEquals(1_700_000_000_000L, shot.takenMillis);
        assertEquals(Uri.withAppendedPath(COLLECTION, "7"), shot.uri());
        assertEquals("_data LIKE ?", mMedia.selection);
        assertEquals("%/Screenshots/%", mMedia.args[0]);
        assertEquals("date_added DESC", mMedia.sort);
    }

    @Test
    @Config(sdk = 29)
    public void fromAndroidTenScreenshotsAreFoundByRelativePathAndNotWhilePending() {
        grant();
        mMedia.row(3, 1_700_000_000L, "image/jpeg");

        mSource.start();
        idle();

        assertEquals(3, mHeard.get(0).id);
        assertEquals("relative_path LIKE ? AND is_pending = 0", mMedia.selection);
        assertEquals("%Screenshots/%", mMedia.args[0]);
    }

    @Test
    public void aChangeIsReadOnceItSettles() {
        grant();
        mSource.start();
        idle();
        assertEquals(1, mAnswers);
        assertNull("an empty store has no screenshot", mHeard.get(0));

        // A screenshot inserts a pending row and then finishes it: two changes, one read.
        mMedia.row(9, 1_700_000_100L, "image/png");
        mApplication.getContentResolver().notifyChange(COLLECTION, null);
        mApplication.getContentResolver().notifyChange(COLLECTION, null);
        idleFor(RecentScreenshot.REQUERY_DEBOUNCE_MS - 1);
        assertEquals(1, mMedia.queries);
        idleFor(1);

        assertEquals(2, mMedia.queries);
        assertEquals(2, mAnswers);
        assertEquals(9, mHeard.get(1).id);
    }

    @Test
    public void stoppedNothingIsFollowedAndALateAnswerIsDropped() {
        grant();
        mMedia.row(7, 1_700_000_000L, "image/png");
        mSource.start();
        // The answer is posted, not yet delivered, when the keys go down.
        mSource.stop();
        idle();
        assertEquals(0, mAnswers);
        assertTrue(shadowOf(mApplication.getContentResolver()).getContentObservers(COLLECTION).isEmpty());

        mSource.start();
        idle();
        mSource.stop();
        assertTrue(shadowOf(mApplication.getContentResolver()).getContentObservers(COLLECTION).isEmpty());
        mApplication.getContentResolver().notifyChange(COLLECTION, null);
        idleFor(RecentScreenshot.REQUERY_DEBOUNCE_MS);
        assertEquals(2, mMedia.queries);
        assertEquals(1, mAnswers);
    }

    @Test
    public void withoutThePermissionNothingIsReadOrFollowed() {
        mMedia.row(7, 1_700_000_000L, "image/png");

        mSource.start();
        idle();

        assertEquals(0, mMedia.queries);
        assertEquals(0, mAnswers);
        assertTrue(shadowOf(mApplication.getContentResolver()).getContentObservers(COLLECTION).isEmpty());
    }

    @Test
    public void aStoreThatRefusesHasNoScreenshot() {
        grant();
        mMedia.refuse = true;

        mSource.start();
        idle();

        assertEquals(1, mAnswers);
        assertNull(mHeard.get(0));
    }

    @Test
    public void onlyARowWithAConcretePictureTypeAndATimeIsAShot() {
        assertNotNull(RecentScreenshot.fromRow(1, 1_700_000_000L, "image/png"));
        assertEquals("image/png", RecentScreenshot.fromRow(1, 1_700_000_000L, "IMAGE/PNG").mime);
        assertNull(RecentScreenshot.fromRow(1, 1_700_000_000L, null));
        assertNull(RecentScreenshot.fromRow(1, 1_700_000_000L, "video/mp4"));
        assertNull(RecentScreenshot.fromRow(1, 1_700_000_000L, "image/*"));
        assertNull(RecentScreenshot.fromRow(1, 0, "image/png"));
    }

    private void grant() {
        shadowOf(mApplication).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void idleFor(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    /** The media store's images, as rows of id, date added in seconds and type. */
    public static final class FakeMedia extends ContentProvider {
        private final List<Object[]> rows = new ArrayList<>();
        String selection;
        String[] args;
        String sort;
        int queries;
        boolean refuse;

        void row(long id, long dateAddedSeconds, String mime) {
            rows.add(0, new Object[] {id, dateAddedSeconds, mime});
        }

        @Override public boolean onCreate() { return true; }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {
            queries++;
            if (refuse)
                throw new SecurityException("no access");
            this.selection = selection;
            this.args = selectionArgs;
            this.sort = sortOrder;
            MatrixCursor cursor = new MatrixCursor(projection);
            for (Object[] row : rows)
                cursor.addRow(row);
            return cursor;
        }

        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    }
}
