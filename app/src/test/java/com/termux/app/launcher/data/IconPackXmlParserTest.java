package com.termux.app.launcher.data;

import android.app.Application;
import android.os.Build;

import com.termux.app.launcher.model.IconPackInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Runs under Robolectric because {@code XmlPullParserFactory} is an android.jar stub on the bare
 * JVM classpath — {@code newInstance()} throws "not mocked" there. Robolectric supplies a real
 * pull-parser implementation, which is what the production icon-pack path uses too.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class IconPackXmlParserTest {
    @Test
    public void parsesComponentInfoItemsAndCalendarPrefixes() throws Exception {
        String appfilter = "<resources>"
            + "<item component=\"ComponentInfo{com.example/.MainActivity}\" drawable=\"ic_example\"/>"
            + "<calendar component=\"ComponentInfo{com.calendar/com.calendar.Main}\" prefix=\"calendar_\"/>"
            + "<iconback img1=\"back_one\" img2=\"back_two\"/>"
            + "<iconmask img1=\"mask_one\"/>"
            + "<iconupon img1=\"upon_one\" img2=\"upon_two\"/>"
            + "<scale factor=\"0.85\"/>"
            + "</resources>";

        IconPack pack = IconPackXmlParser.parse(info(), parser(appfilter), null);

        assertEquals("ic_example", pack.drawableForComponent("com.example", ".MainActivity", 15));
        assertEquals("calendar_15", pack.drawableForComponent("com.calendar", "com.calendar.Main", 15));
        assertEquals(0.85f, pack.scale, 0.001f);
        assertEquals(2, pack.iconBacks.size());
        assertEquals("back_two", pack.iconBacks.get(1));
        assertEquals("mask_one", pack.iconMasks.get(0));
        assertEquals(2, pack.iconUpons.size());
    }

    @Test
    public void parsesDrawableXmlAndKeepsFirstDuplicate() throws Exception {
        String drawable = "<resources>"
            + "<item drawable=\"ic_alpha\" name=\"Alpha\"/>"
            + "<item drawable=\"ic_alpha\" name=\"Duplicate\"/>"
            + "<item drawable=\"ic_beta\"/>"
            + "</resources>";

        IconPack pack = IconPackXmlParser.parse(info(), null, parser(drawable));

        assertEquals(2, pack.drawableItems().size());
        assertNotNull(pack.drawableItem("ic_alpha"));
        assertEquals("Alpha", pack.drawableItem("ic_alpha").label);
        assertNotNull(pack.drawableItem("ic_beta"));
    }

    @Test
    public void ignoresInvalidComponents() throws Exception {
        String appfilter = "<resources><item component=\"bad\" drawable=\"ic_bad\"/></resources>";

        IconPack pack = IconPackXmlParser.parse(info(), parser(appfilter), null);

        assertNull(pack.drawableForComponent("bad", "Main", 1));
    }

    private static IconPackInfo info() {
        return new IconPackInfo("pack.example", "Pack", 1, false);
    }

    private static XmlPullParser parser(String xml) throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        return parser;
    }
}
