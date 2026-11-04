package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.os.Build;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A finger swiping the drawer's pages sideways takes the search keyboard down, like a list drag. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AppDrawerPagesSwipeHidesKeyboardTest {
    private AppDrawerContentView content;
    private int dragStarts;

    @Before public void setUp() {
        content = new AppDrawerContentView(RuntimeEnvironment.getApplication());
        content.setViewType(AppDrawerViewType.HORIZONTAL);
        content.setKeyboardScrollListener(new AppDrawerContentView.KeyboardScrollListener() {
            @Override public void onListScrolledDown(int dy) {}
            @Override public void onListScrollIdle() {}
            @Override public void onListDragStarted() { dragStarts++; }
        });
    }

    @Test public void aSidewaysDragOnThePagesHidesTheKeyboard() throws Exception {
        dispatchPagerScrollState(RecyclerView.SCROLL_STATE_DRAGGING);
        assertEquals(1, dragStarts);
    }

    @Test public void aProgrammaticPageSettleDoesNot() throws Exception {
        // An icon dragged to the edge turns the page by settling, never by a finger drag.
        dispatchPagerScrollState(RecyclerView.SCROLL_STATE_SETTLING);
        dispatchPagerScrollState(RecyclerView.SCROLL_STATE_IDLE);
        assertEquals(0, dragStarts);
    }

    private void dispatchPagerScrollState(int state) throws Exception {
        Field pagerField = AppDrawerContentView.class.getDeclaredField("mHorizontalPager");
        pagerField.setAccessible(true);
        RecyclerView pager = (RecyclerView) pagerField.get(content);
        Method dispatch = RecyclerView.class.getDeclaredMethod("dispatchOnScrollStateChanged", int.class);
        dispatch.setAccessible(true);
        dispatch.invoke(pager, state);
    }
}
