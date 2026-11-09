package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;

import org.json.JSONObject;
import org.junit.Test;

/** A {@code look} that changed the grid asks for a re-dress exactly once; anything else never. */
public class LookCommandChangeTest {

    private final LookGrid grid = new LookGrid(new LookGrid.MemoryStore());
    private int changes;

    private JSONObject run(String json) throws Exception {
        return LookCommand.handle(grid, new JSONObject(json), () -> changes++);
    }

    @Test
    public void set_reportsOneChange() throws Exception {
        JSONObject set = run("{\"op\":\"set\",\"property\":\"opacity\",\"value\":\"95\"}");
        assertTrue(set.toString(), set.getBoolean("ok"));
        assertEquals(1, changes);
    }

    @Test
    public void reset_ofACellThatHoldsValuesReportsOneChange() throws Exception {
        run("{\"op\":\"set\",\"property\":\"opacity\",\"value\":\"95\"}");
        run("{\"op\":\"set\",\"property\":\"frost\",\"value\":\"0\"}");
        changes = 0;
        JSONObject reset = run("{\"op\":\"reset\"}");
        assertTrue(reset.toString(), reset.getBoolean("ok"));
        assertEquals(2, reset.getInt("reset"));
        assertEquals(1, changes);
    }

    @Test
    public void get_reportsNoChange() throws Exception {
        run("{\"op\":\"set\",\"property\":\"opacity\",\"value\":\"95\"}");
        changes = 0;
        JSONObject get = run("{\"op\":\"get\",\"property\":\"opacity\"}");
        assertTrue(get.toString(), get.getBoolean("ok"));
        assertEquals(0, changes);
    }

    @Test
    public void badProperty_reportsNoChange() throws Exception {
        JSONObject bad = run("{\"op\":\"set\",\"property\":\"sparkle\",\"value\":\"95\"}");
        assertFalse(bad.toString(), bad.optBoolean("ok", false));
        assertEquals(0, changes);
    }

    @Test
    public void badValue_reportsNoChange() throws Exception {
        JSONObject bad = run("{\"op\":\"set\",\"property\":\"rim\",\"value\":\"loud\"}");
        assertFalse(bad.toString(), bad.optBoolean("ok", false));
        assertEquals(0, changes);
    }

    @Test
    public void reset_ofAnEmptyCellReportsNoChange() throws Exception {
        JSONObject reset = run("{\"op\":\"reset\",\"property\":\"opacity\",\"element\":\"dock\"}");
        assertTrue(reset.toString(), reset.getBoolean("ok"));
        assertEquals(0, reset.getInt("reset"));
        assertEquals(0, changes);
    }
}
