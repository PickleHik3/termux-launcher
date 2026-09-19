package com.termux.app.help;

import android.app.Application;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/**
 * The guide manifest as the topic page reads it: the clip a topic owns, the neighbour's clip a
 * topic borrows, and the silence of a topic the recording run could not capture. Read from the
 * real file shipped in the assets, so a re-recording that renames or drops a clip fails here
 * rather than on a reader's screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class HelpClipsTest {

    @After public void tearDown() {
        HelpClips.forget();
    }

    private HelpClips clips() {
        return HelpClips.of(RuntimeEnvironment.getApplication());
    }

    @Test public void topicWithItsOwnRecordingGetsIt() {
        HelpClips.Clip clip = clips().forTopic("places");
        assertNotNull(clip);
        assertEquals("places", clip.id);
        assertEquals("help-guide/places.mp4", clip.assetPath);
        assertTrue("the manifest says how wide the strip is", clip.width > 0);
        assertTrue("the manifest says how tall the strip is", clip.height > 0);
        assertFalse("every clip describes its gesture in a sentence", clip.alt.isEmpty());
    }

    @Test public void topicThatCouldNotBeRecordedGetsNothing() {
        assertNull(clips().forTopic("setup"));
        assertNull(clips().forTopic("move_panes"));
        assertNull(clips().forTopic("display_apps"));
    }

    @Test public void topicBorrowingAnotherTopicsClipGetsThatClip() {
        HelpClips.Clip borrowed = clips().forTopic("fix_keyboard");
        assertNotNull(borrowed);
        assertEquals("keyboard", borrowed.id);
        assertEquals("help-guide/keyboard.mp4", borrowed.assetPath);
        assertEquals(clips().forTopic("keyboard").id, borrowed.id);
    }

    @Test public void aTopicShowingSeveralClipsShowsTheFirst() {
        HelpClips.Clip clip = HelpClips.parse("{\"clips\":{\"one\":{\"video\":\"one.mp4\","
            + "\"width\":600,\"height\":100}},\"topics\":[{\"topic\":\"t\",\"status\":\"covered\","
            + "\"clips\":[\"one\",\"two\"],\"alt\":\"a\"}]}").forTopic("t");
        assertNotNull(clip);
        assertEquals("one", clip.id);
    }

    @Test public void unknownTopicAndNoTopicGetNothing() {
        assertNull(clips().forTopic("not_a_topic"));
        assertNull(clips().forTopic(null));
    }

    @Test public void everyClipTheManifestPromisesIsInTheAssets() throws Exception {
        HelpClips clips = clips();
        assertTrue("the manifest covers most of the help topics", clips.size() > 30);
        for (HelpTopics.Entry entry : HelpTopics.all()) {
            HelpClips.Clip clip = clips.forTopic(entry.id);
            if (clip == null) continue;
            RuntimeEnvironment.getApplication().getAssets().open(clip.assetPath).close();
        }
    }

    @Test public void manifestTopicIdsAreHelpTopicIds() {
        // A clip nobody can reach is a recording wasted, and a typo in an id is silent otherwise.
        for (String topicId : clips().topicIds())
            assertNotNull("no help topic called " + topicId, HelpTopics.entry(topicId));
    }
}
