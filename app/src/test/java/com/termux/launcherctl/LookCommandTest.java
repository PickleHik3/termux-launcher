package com.termux.launcherctl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.LookGrid;

import org.json.JSONObject;
import org.junit.Test;

public class LookCommandTest {

    private final LookGrid grid = new LookGrid(new LookGrid.MemoryStore());

    private JSONObject run(String json) throws Exception {
        return LookCommand.handle(grid, new JSONObject(json));
    }

    @Test
    public void set_onAnElementWritesThatElementsEverywhereCell() throws Exception {
        JSONObject set = run("{\"op\":\"set\",\"property\":\"opacity\",\"value\":\"60\",\"element\":\"dock\"}");
        assertTrue(set.toString(), set.getBoolean("ok"));
        assertEquals(60, grid.value(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, LookGrid.NO_PAGE));
        assertFalse(grid.has(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE));

        JSONObject get = run("{\"op\":\"get\",\"property\":\"opacity\",\"element\":\"dock\"}");
        assertEquals("60", get.getString("value"));
        assertEquals("dock, everywhere", get.getString("from"));
        JSONObject other = run("{\"op\":\"get\",\"property\":\"opacity\",\"element\":\"status\"}");
        assertEquals("shipped", other.getString("from"));
    }

    @Test
    public void set_withoutAnElementWritesBaseAndAPageNamesItsCell() throws Exception {
        run("{\"op\":\"set\",\"property\":\"tint\",\"value\":\"30\"}");
        assertTrue(grid.has(LookGrid.Property.TINT, null, LookGrid.NO_PAGE));
        run("{\"op\":\"set\",\"property\":\"tint\",\"value\":\"10\",\"page\":2}");
        JSONObject get = run("{\"op\":\"get\",\"property\":\"tint\",\"element\":\"dock\",\"page\":2}");
        assertEquals("10", get.getString("value"));
        assertEquals("all glass on page 2", get.getString("from"));
    }

    @Test
    public void values_takeTheirOwnWords() throws Exception {
        run("{\"op\":\"set\",\"property\":\"colour\",\"value\":\"tertiary\"}");
        assertEquals(LookGrid.TINT_HUE_TERTIARY,
            grid.value(LookGrid.Property.TINT_HUE, null, LookGrid.NO_PAGE));
        run("{\"op\":\"set\",\"property\":\"rim\",\"value\":\"bright\"}");
        assertEquals(LookGrid.RIM_GRADIENT, grid.value(LookGrid.Property.RIM, null, LookGrid.NO_PAGE));
        run("{\"op\":\"set\",\"property\":\"frost\",\"value\":\"5\"}");
        assertEquals(5, grid.value(LookGrid.Property.FROST, null, LookGrid.NO_PAGE));
        run("{\"op\":\"set\",\"property\":\"corners\",\"value\":\"auto\"}");
        assertEquals(LookGrid.CORNERS_AUTO, grid.value(LookGrid.Property.CORNERS, null, LookGrid.NO_PAGE));
        run("{\"op\":\"set\",\"property\":\"opacity\",\"value\":\"900\"}");
        assertEquals(100, grid.value(LookGrid.Property.OPACITY, null, LookGrid.NO_PAGE));
    }

    @Test
    public void frost_inDpLandsOnTheNearestStop() throws Exception {
        run("{\"op\":\"set\",\"property\":\"frost\",\"value\":\"0dp\"}");
        assertEquals(0, grid.value(LookGrid.Property.FROST, null, LookGrid.NO_PAGE));
    }

    @Test
    public void reset_putsTheCellBackToFollowing() throws Exception {
        run("{\"op\":\"set\",\"property\":\"opacity\",\"value\":\"60\",\"element\":\"dock\"}");
        run("{\"op\":\"set\",\"property\":\"edge\",\"value\":\"40\",\"element\":\"dock\"}");
        run("{\"op\":\"set\",\"property\":\"edge\",\"value\":\"10\"}");
        JSONObject one = run("{\"op\":\"reset\",\"property\":\"opacity\",\"element\":\"dock\"}");
        assertEquals(1, one.getInt("reset"));
        assertFalse(grid.has(LookGrid.Property.OPACITY, LookGrid.Element.DOCK, LookGrid.NO_PAGE));
        JSONObject all = run("{\"op\":\"reset\",\"element\":\"dock\"}");
        assertEquals(1, all.getInt("reset"));
        assertFalse(grid.has(LookGrid.Property.EDGE, LookGrid.Element.DOCK, LookGrid.NO_PAGE));
        assertTrue(grid.has(LookGrid.Property.EDGE, null, LookGrid.NO_PAGE));
    }

    @Test
    public void badRequests_areRefusedWithoutWriting() throws Exception {
        assertEquals(400, run("{\"op\":\"set\",\"property\":\"opacity\",\"value\":\"x\"}").getInt("_statusCode"));
        assertEquals(400, run("{\"op\":\"set\",\"property\":\"bogus\",\"value\":\"1\"}").getInt("_statusCode"));
        assertEquals(400, run("{\"op\":\"get\",\"property\":\"opacity\",\"element\":\"nope\"}").getInt("_statusCode"));
        assertEquals(400, run("{\"op\":\"set\",\"property\":\"margin\",\"value\":\"4\",\"element\":\"dock\"}")
            .getInt("_statusCode"));
        assertEquals(400, run("{\"op\":\"set\",\"property\":\"frost\",\"value\":\"9\"}").getInt("_statusCode"));
        assertEquals(400, run("{\"op\":\"zap\"}").getInt("_statusCode"));
        assertTrue(grid.gridKeys().isEmpty());
    }
}
