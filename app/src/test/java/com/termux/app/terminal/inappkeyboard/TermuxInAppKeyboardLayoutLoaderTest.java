package com.termux.app.terminal.inappkeyboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.LayoutModifier;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class TermuxInAppKeyboardLayoutLoaderTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context mContext;
    private File mLayoutFile;
    private List<String> mErrors;
    private TermuxInAppKeyboardLayoutLoader mLoader;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication();
        mLayoutFile = new File(temporaryFolder.newFolder("keyboard"), "layout.xml");
        mErrors = new ArrayList<>();
        mLoader = new TermuxInAppKeyboardLayoutLoader(
            mContext, mLayoutFile, Runnable::run, Runnable::run,
            new LayoutModifier.LayoutOptions(true, false, true),
            (diagnostic, userMessage) -> mErrors.add(diagnostic));
    }

    @Test
    public void missingFileLoadsBundledQwerty() {
        List<KeyboardData> delivered = new ArrayList<>();

        mLoader.recheck(delivered::add);

        assertEquals(1, delivered.size());
        assertEquals("QWERTY (US)", delivered.get(0).name);
        assertEquals(4, delivered.get(0).rows.size());
        assertTrue(mErrors.isEmpty());
    }

    @Test
    public void oversizedFileIsRejectedAndFallsBackToBundled() throws Exception {
        try (FileOutputStream output = new FileOutputStream(mLayoutFile)) {
            byte[] block = new byte[8192];
            long remaining = TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES + 1L;
            while (remaining > 0) {
                int count = (int) Math.min(block.length, remaining);
                output.write(block, 0, count);
                remaining -= count;
            }
        }
        List<KeyboardData> delivered = new ArrayList<>();

        mLoader.recheck(delivered::add);

        assertEquals(1, delivered.size());
        assertEquals("QWERTY (US)", delivered.get(0).name);
        assertEquals(1, mErrors.size());
        assertTrue(mErrors.get(0).contains("exceeds"));
    }

    @Test
    public void malformedChangeRetainsLastKnownGoodAndReportsOnce() throws Exception {
        write("<keyboard name='working' bottom_row='false'><row><key c='a'/></row></keyboard>");
        List<KeyboardData> delivered = new ArrayList<>();
        mLoader.recheck(delivered::add);
        KeyboardData working = delivered.get(0);

        long changedTime = Math.max(System.currentTimeMillis(), mLayoutFile.lastModified() + 2000L);
        write("<keyboard name='broken'><row><key c='a'></row></keyboard>");
        assertTrue(mLayoutFile.setLastModified(changedTime));
        mLoader.recheck(delivered::add);
        mLoader.recheck(delivered::add);

        assertEquals(1, delivered.size());
        assertSame(working, mLoader.getLastKnownGood());
        assertEquals(1, mErrors.size());
        assertTrue(mErrors.get(0).contains(mLayoutFile.getCanonicalPath()));
        assertTrue(mErrors.get(0).contains("line"));
    }

    @Test
    public void validLayoutReloadsWhenSignatureChanges() throws Exception {
        List<KeyboardData> delivered = new ArrayList<>();
        write("<keyboard name='one' bottom_row='false'><row><key c='1'/></row></keyboard>");
        mLoader.recheck(delivered::add);
        KeyboardData first = delivered.get(0);

        long changedTime = Math.max(System.currentTimeMillis(), mLayoutFile.lastModified() + 2000L);
        write("<keyboard name='second' bottom_row='false'><row><key c='2'/></row></keyboard>");
        assertTrue(mLayoutFile.setLastModified(changedTime));
        mLoader.recheck(delivered::add);

        assertEquals(2, delivered.size());
        assertEquals("one", first.name);
        assertEquals("second", delivered.get(1).name);
        assertNotNull(mLoader.getLastKnownGood());
        assertSame(delivered.get(1), mLoader.getLastKnownGood());
        assertTrue(mErrors.isEmpty());
    }

    @Test
    public void unchangedSignatureDoesNotScheduleBackgroundWork() throws Exception {
        CountingExecutor executor = new CountingExecutor();
        mLoader = new TermuxInAppKeyboardLayoutLoader(
            mContext, mLayoutFile, executor, Runnable::run,
            new LayoutModifier.LayoutOptions(true, false, true),
            (diagnostic, userMessage) -> mErrors.add(diagnostic));
        write("<keyboard name='one' bottom_row='false'><row><key c='1'/></row></keyboard>");

        mLoader.recheck(data -> { });
        mLoader.recheck(data -> { });

        assertEquals(1, executor.executeCount);
    }

    private static final class CountingExecutor implements Executor {
        private int executeCount;

        @Override
        public void execute(Runnable command) {
            executeCount++;
            command.run();
        }
    }

    private void write(String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(mLayoutFile)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
