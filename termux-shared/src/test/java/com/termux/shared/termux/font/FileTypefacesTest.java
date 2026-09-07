package com.termux.shared.termux.font;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.graphics.Typeface;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The cache exists so a file-loaded face is never owned only by the render thread's strike cache;
 * that holds as long as the same file always yields the same instance for the life of the process.
 */
@RunWith(RobolectricTestRunner.class)
public class FileTypefacesTest {

    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Before
    public void resetCache() {
        FileTypefaces.clear();
    }

    @Test
    public void theSameFileYieldsTheSameInstanceForTheLifeOfTheProcess() throws IOException {
        File font = write("font.ttf", 64);

        Typeface first = FileTypefaces.load(font);
        Typeface second = FileTypefaces.load(font);

        assertNotNull(first);
        assertSame(first, second);
        assertEquals(1, FileTypefaces.size());
    }

    @Test
    public void aFileThatChangedOnDiskLoadsAgainAndTheOldFaceStaysCached() throws IOException {
        File font = write("font.ttf", 64);
        Typeface before = FileTypefaces.load(font);

        write("font.ttf", 128);
        Typeface after = FileTypefaces.load(font);

        assertNotSame(before, after);
        assertSame(after, FileTypefaces.load(font));
        assertEquals("the replaced face must stay alive beside the new one",
            2, FileTypefaces.size());
    }

    @Test
    public void differentFilesAreDifferentEntries() throws IOException {
        Typeface regular = FileTypefaces.load(write("regular.ttf", 64));
        Typeface bold = FileTypefaces.load(write("bold.ttf", 64));

        assertNotSame(regular, bold);
        assertEquals(2, FileTypefaces.size());
    }

    private File write(String name, int bytes) throws IOException {
        File file = new File(temporary.getRoot(), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[bytes]);
        }
        return file;
    }
}
