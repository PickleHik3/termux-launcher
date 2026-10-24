package com.termux.app.sound;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** dbus-send's printed GetAll reply, read back into a player's state. */
public class MprisReplyTest {

    /** What dbus-send 1.14 prints for a playing kew, indentation and all. */
    static final String KEW = "method return time=1729850000.123456 sender=:1.7 -> destination=:1.9 serial=42 reply_serial=2\n"
        + "   array [\n"
        + "      dict entry(\n"
        + "         string \"PlaybackStatus\"\n"
        + "         variant             string \"Playing\"\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"LoopStatus\"\n"
        + "         variant             string \"None\"\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"Rate\"\n"
        + "         variant             double 1\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"Metadata\"\n"
        + "         variant             array [\n"
        + "               dict entry(\n"
        + "                  string \"mpris:trackid\"\n"
        + "                  variant                      object path \"/org/kew/tracklist/3\"\n"
        + "               )\n"
        + "               dict entry(\n"
        + "                  string \"xesam:title\"\n"
        + "                  variant                      string \"So What\"\n"
        + "               )\n"
        + "               dict entry(\n"
        + "                  string \"xesam:artist\"\n"
        + "                  variant                      array [\n"
        + "                        string \"Miles Davis\"\n"
        + "                        string \"John Coltrane\"\n"
        + "                     ]\n"
        + "               )\n"
        + "               dict entry(\n"
        + "                  string \"xesam:album\"\n"
        + "                  variant                      string \"Kind of Blue\"\n"
        + "               )\n"
        + "               dict entry(\n"
        + "                  string \"mpris:length\"\n"
        + "                  variant                      int64 562000000\n"
        + "               )\n"
        + "               dict entry(\n"
        + "                  string \"mpris:artUrl\"\n"
        + "                  variant                      string \"file:///home/u/Music/Kind%20of%20Blue/cover.jpg\"\n"
        + "               )\n"
        + "            ]\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"Position\"\n"
        + "         variant             int64 61500000\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"CanGoNext\"\n"
        + "         variant             boolean true\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"CanGoPrevious\"\n"
        + "         variant             boolean false\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"CanPlay\"\n"
        + "         variant             boolean true\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"CanPause\"\n"
        + "         variant             boolean true\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"CanSeek\"\n"
        + "         variant             boolean true\n"
        + "      )\n"
        + "      dict entry(\n"
        + "         string \"CanControl\"\n"
        + "         variant             boolean true\n"
        + "      )\n"
        + "   ]\n";

    @Test public void aPlayingPlayerIsReadWhole() {
        PlayerState state = MprisReply.parse("org.mpris.MediaPlayer2.kew", 4242, KEW);

        assertEquals(PlayerState.Status.PLAYING, state.status);
        assertEquals("So What", state.title);
        assertEquals("Miles Davis, John Coltrane", state.artist);
        assertEquals("Kind of Blue", state.album);
        assertEquals("/org/kew/tracklist/3", state.trackId);
        assertEquals("file:///home/u/Music/Kind%20of%20Blue/cover.jpg", state.artUrl);
        assertEquals(562_000L, state.lengthMs);
        assertEquals(61_500L, state.positionMs);
        assertEquals(1f, state.rate, 0f);
        assertEquals(4242, state.pid);
        assertTrue(state.canPlay);
        assertTrue(state.canPause);
        assertTrue(state.canNext);
        assertFalse(state.canPrevious);
        assertTrue(state.canSeek);
        assertEquals("kew", state.shortName());
    }

    @Test public void aPlayerThatCannotBeControlledOffersNoControlsWhateverItsFlagsSay() {
        String reply = KEW.replace("string \"CanControl\"\n         variant             boolean true",
            "string \"CanControl\"\n         variant             boolean false");
        PlayerState state = MprisReply.parse("org.mpris.MediaPlayer2.kew", 0, reply);

        assertFalse(state.canPlay);
        assertFalse(state.canPause);
        assertFalse(state.canNext);
        assertFalse(state.canSeek);
    }

    @Test public void missingPiecesAreUnknownNotZero() {
        String reply = "method return time=1 sender=:1.3 -> destination=:1.4 serial=5 reply_serial=2\n"
            + "   array [\n"
            + "      dict entry(\n"
            + "         string \"PlaybackStatus\"\n"
            + "         variant             string \"Paused\"\n"
            + "      )\n"
            + "      dict entry(\n"
            + "         string \"Metadata\"\n"
            + "         variant             array [\n"
            + "            ]\n"
            + "      )\n"
            + "   ]\n";
        PlayerState state = MprisReply.parse("org.mpris.MediaPlayer2.mpv.instance12", 0, reply);

        assertEquals(PlayerState.Status.PAUSED, state.status);
        assertNull(state.title);
        assertNull(state.artist);
        assertEquals(-1L, state.lengthMs);
        assertEquals(-1L, state.positionMs);
        assertFalse(state.canPlay);
        assertEquals("mpv", state.shortName());
    }

    @Test public void anArtistGivenAsOneStringAndATitleOverSeveralLinesAreKept() {
        String reply = "   array [\n"
            + "      dict entry(\n"
            + "         string \"Metadata\"\n"
            + "         variant             array [\n"
            + "               dict entry(\n"
            + "                  string \"xesam:title\"\n"
            + "                  variant                      string \"Line one\n"
            + "line two\"\n"
            + "               )\n"
            + "               dict entry(\n"
            + "                  string \"xesam:artist\"\n"
            + "                  variant                      string \"Solo\"\n"
            + "               )\n"
            + "            ]\n"
            + "      )\n"
            + "   ]\n";
        PlayerState state = MprisReply.parse("org.mpris.MediaPlayer2.x", 0, reply);

        assertEquals("Line one\nline two", state.title);
        assertEquals("Solo", state.artist);
        assertEquals(PlayerState.Status.STOPPED, state.status);
    }

    @Test public void anErrorOrGarbageIsNoState() {
        assertNull(MprisReply.parse("org.mpris.MediaPlayer2.x", 0,
            "Error org.freedesktop.DBus.Error.ServiceUnknown: The name is not activatable\n"));
        assertNull(MprisReply.parse("org.mpris.MediaPlayer2.x", 0, "   array [\n      dict entry(\n"));
        assertNull(MprisReply.parse("org.mpris.MediaPlayer2.x", 0, null));
        assertNull(MprisReply.parse("org.mpris.MediaPlayer2.x", 0, ""));
    }
}
