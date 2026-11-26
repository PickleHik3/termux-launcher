package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.view.View;
import android.view.ViewGroup;

import com.termux.app.page.PageHost;
import com.termux.app.statusbar.PaneRowPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The Assistant's card holds the status row's room at its top, as a Terminal's top pane does:
 * the chat and the Chats list start below the row; the glass and the veil keep the whole card.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantPageStatusRowTest {

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 1800);
    }

    @Test
    public void theChatStartsBelowTheRowAndTheGlassKeepsTheWholeCard() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        AssistantPageView view = new AssistantPageView(activity, PageHost.NONE);
        activity.setContentView(view, new ViewGroup.LayoutParams(1080, 1800));
        float density = activity.getResources().getDisplayMetrics().density;
        int room = PaneRowPolicy.pageRoomPx(com.termux.app.page.PageKind.ASSISTANT, true,
            PaneRowPolicy.rowHeightPx(density));

        view.setStatusRowPx(room);
        layout(view);
        assertEquals(room, view.statusRowPx());
        // The chat and the Chats list start below the row; everything else keeps the whole card.
        int moved = 0;
        for (int i = 0; i < view.getChildCount(); i++) {
            View child = view.getChildAt(i);
            boolean list = child instanceof AssistantChatView || child instanceof AssistantChatsView;
            if (list) moved++;
            String name = child.getClass().getSimpleName();
            assertEquals(name, list ? room : 0,
                ((ViewGroup.MarginLayoutParams) child.getLayoutParams()).topMargin);
            // The Chats list is laid out only while it is open.
            if (child.getVisibility() != View.GONE)
                assertEquals(name, list ? room : 0, child.getTop());
        }
        assertEquals(2, moved);

        // The strip: the room goes back.
        view.setStatusRowPx(0);
        layout(view);
        for (int i = 0; i < view.getChildCount(); i++) {
            assertEquals(0, view.getChildAt(i).getTop());
            assertEquals(0, ((ViewGroup.MarginLayoutParams) view.getChildAt(i).getLayoutParams())
                .topMargin);
        }
    }
}
