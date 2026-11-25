package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.dock.AzEdgeCrossingCard;
import com.termux.app.page.Side;
import com.termux.app.place.ChromeShape.Box;
import com.termux.app.place.LayoutFixtures;
import com.termux.app.place.PlaceLayout;
import com.termux.app.place.RestCards;
import com.termux.app.place.RestCards.CardId;
import com.termux.app.place.RestCards.StackView;
import com.termux.app.place.StackEnd;
import com.termux.app.wall.PageRef;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The A–Z bar on a side edge, one card per side (#307 views B): the other side's card is laid
 * out once at the rest from the answer's parked A–Z card, invisible there, shown only as a
 * crossing toward its side begins, and the stack's writer slides the edge stack's host out with
 * the leaving side and the crossing host in with the arriving side. A bar both rests draw at one
 * rect stays one host, still.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AzEdgeCrossingCardTest {

    private static final int WIDTH = 1080;

    private final SparseArray<View> mViews = new SparseArray<>();
    private CountingHost mCrossing;
    private int mPrepared;
    private int mDressed;
    private int mReleased;
    private AzEdgeCrossingCard mCard;

    /** A crossing host that counts the layouts asked of it. */
    private static final class CountingHost extends FrameLayout {
        int layouts;

        CountingHost(Context context) {
            super(context);
        }

        @Override public void setLayoutParams(android.view.ViewGroup.LayoutParams params) {
            layouts++;
            super.setLayoutParams(params);
        }
    }

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        RelativeLayout parent = new RelativeLayout(context);
        mCrossing = new CountingHost(context);
        mCrossing.setVisibility(View.GONE);
        parent.addView(mCrossing, new RelativeLayout.LayoutParams(0, 0));
        mCrossing.layouts = 0;
        mViews.put(R.id.place_az_bar_crossing_host, mCrossing);
        mCard = new AzEdgeCrossingCard(new AzEdgeCrossingCard.Host() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @NonNull @Override public DisplayMetrics displayMetrics() {
                DisplayMetrics metrics = new DisplayMetrics();
                metrics.widthPixels = WIDTH;
                metrics.heightPixels = 2412;
                return metrics;
            }
            @Override public void prepareBar(@NonNull FrameLayout host,
                                             @NonNull PlaceLayout.Edge edge) { mPrepared++; }
            @Override public void dressBar(@NonNull FrameLayout host,
                                           @NonNull PlaceLayout.Edge edge, boolean home) {
                mDressed++;
            }
            @Override public void releaseBar(@NonNull FrameLayout host) { mReleased++; }
        });
    }

    private static RestCards answer(LayoutFixtures.Fixture f, PageRef front, boolean up,
                                    boolean floating) {
        return RestCards.of(f.cardsRest(front, up, floating));
    }

    /**
     * Both sides draw the bar on the left edge at different lengths: at each rest the other
     * side's card stands on the crossing host at its parked rect, invisible, laid out once; it
     * shows as a crossing toward its side begins, and between two Home pages it does not.
     */
    @Test public void theOtherSidesBarIsLaidOutOnceAndShownOnlyByACrossing() {
        int split = 0;
        for (boolean floating : new boolean[] {false, true})
            for (PageRef front : new PageRef[] {PageRef.HOME, PageRef.TERMINAL}) {
                RestCards answer = answer(LayoutFixtures.AZ_LEFT, front, false, floating);
                String label = front + (floating ? " FLOATING" : " DOCKED");
                RestCards.Card parked = null;
                for (RestCards.Card card : answer.parked())
                    if (card.id == CardId.AZ) parked = card;
                mCard.applyAtRest(answer);
                if (parked == null) {
                    assertFalse(label, mCard.on());
                    assertEquals(label, View.GONE, mCrossing.getVisibility());
                    continue;
                }
                split++;
                assertTrue(label, mCard.on());
                assertEquals(label + " the parked card is the other side's",
                    front.isHome() ? StackView.TERMINAL_AZ_EDGE : StackView.AZ_EDGE, parked.view);
                assertEquals(label, !front.isHome(), mCard.carriesHome());
                RelativeLayout.LayoutParams params =
                    (RelativeLayout.LayoutParams) mCrossing.getLayoutParams();
                assertEquals(label, Math.round(parked.box.left), params.leftMargin);
                assertEquals(label, Math.round(parked.box.top), params.topMargin);
                assertEquals(label, Math.round(parked.box.height()), params.height);
                assertEquals(label, Math.round(parked.box.width()), params.width);
                assertEquals(label + " invisible at a rest", View.INVISIBLE,
                    mCrossing.getVisibility());
                assertEquals(PlaceLayout.Edge.LEFT, AzEdgeCrossingCard.edgeOf(parked.box, WIDTH));
                int layouts = mCrossing.layouts;
                mCard.applyAtRest(answer);
                assertEquals(label + " laid out once per rest", layouts, mCrossing.layouts);

                mCard.onCrossingBegin(front.isHome(), front.isHome());
                assertEquals(label + " a crossing within one side shows nothing", View.INVISIBLE,
                    mCrossing.getVisibility());
                int dressed = mDressed;
                mCard.onCrossingBegin(front.isHome(), !front.isHome());
                assertEquals(label + " shown as the crossing begins", View.VISIBLE,
                    mCrossing.getVisibility());
                assertEquals(label + " dressed once", dressed + 1, mDressed);
                assertEquals(label + " the crossing lays nothing out", layouts, mCrossing.layouts);
                int released = mReleased;
                mCard.applyAtRest(answer);
                assertEquals(label + " its glass let go at the next rest", released + 1,
                    mReleased);
                assertEquals(label, View.INVISIBLE, mCrossing.getVisibility());
            }
        assertTrue("the left-edge bars stand apart somewhere", split > 0);
    }

    /** Not apart, or with no A–Z card off the dock, the crossing host is gone. */
    @Test public void withoutASecondCardTheCrossingHostIsGone() {
        mCard.applyAtRest(null);
        assertFalse(mCard.on());
        assertNull(mCard.box());
        assertEquals(View.GONE, mCrossing.getVisibility());
        mCard.applyAtRest(answer(LayoutFixtures.PONG, PageRef.HOME, false, true));
        assertEquals(View.GONE, mCrossing.getVisibility());
    }

    private StackRenderer renderer(View edgeHost) {
        Context context = RuntimeEnvironment.getApplication();
        FrameLayout container = new FrameLayout(context);
        container.layout(0, 0, WIDTH, 600);
        mViews.put(R.id.accessory_stack_container, container);
        mViews.put(R.id.place_az_bar_host, edgeHost);
        return new StackRenderer(new StackRenderer.Views() {
            @Nullable @Override public View view(int id) { return mViews.get(id); }
            @Override public int keyboardDesiredHeightPx() { return 0; }
            @Override public boolean underKeyboardBandsShown() { return false; }
            @Override public boolean keyboardShown() { return false; }
        });
    }

    private static void cards(StackRenderer renderer, Box home, Box terminal) {
        for (boolean atHome : new boolean[] {true, false}) {
            StackEnd end = renderer.end(atHome);
            end.clearCards();
            end.setCard(StackView.AZ_EDGE, CardId.AZ, Side.HOME.ordinal(), atHome, home);
            end.setCard(StackView.TERMINAL_AZ_EDGE, CardId.AZ, Side.TERMINAL.ordinal(), !atHome,
                terminal);
        }
    }

    /**
     * Home to the Terminal with the two bars apart: the edge stack's host slides out with Home and
     * the crossing host in with the Terminal, a wall apart; at both rests both stand at 0.
     */
    @Test public void theRendererSlidesEachBarWithItsSide() {
        View edge = new FrameLayout(RuntimeEnvironment.getApplication());
        StackRenderer renderer = renderer(edge);
        cards(renderer, new Box(0f, 239f, 60f, 2230f), new Box(0f, 239f, 60f, 2163f));
        renderer.setSidesApart(true, 0f, 0);
        renderer.apply();
        assertEquals(0f, edge.getTranslationX(), 0f);
        assertEquals(0f, mCrossing.getTranslationX(), 0f);
        for (float p : new float[] {0f, 0.25f, 0.5f, 1f}) {
            renderer.setEnds(true, false, p);
            renderer.apply();
            assertEquals("Home's bar at " + p, -p * WIDTH, edge.getTranslationX(), 0.01f);
            assertEquals("the Terminal's bar at " + p, (1f - p) * WIDTH,
                mCrossing.getTranslationX(), 0.01f);
        }
        renderer.setEnds(false, true, 0.25f);
        renderer.apply();
        assertEquals("the Terminal's bar leaving", -0.25f * WIDTH * -1f,
            edge.getTranslationX(), 0.01f);
        assertEquals("Home's bar arriving", -0.75f * WIDTH, mCrossing.getTranslationX(), 0.01f);
        renderer.onLanding();
        renderer.apply();
        assertEquals(0f, edge.getTranslationX(), 0f);
        assertEquals(0f, mCrossing.getTranslationX(), 0f);
    }

    /** One bar both rests draw at one rect stays put across the crossing. */
    @Test public void aSharedBarStaysPut() {
        View edge = new FrameLayout(RuntimeEnvironment.getApplication());
        StackRenderer renderer = renderer(edge);
        Box bar = new Box(0f, 239f, 60f, 2163f);
        for (boolean atHome : new boolean[] {true, false}) {
            StackEnd end = renderer.end(atHome);
            end.clearCards();
            end.setCard(StackView.AZ_EDGE, CardId.AZ, Side.HOME.ordinal(), true, bar);
        }
        renderer.setSidesApart(true, 0f, 0);
        renderer.setEnds(true, false, 0.4f);
        renderer.apply();
        assertEquals(0f, edge.getTranslationX(), 0f);
        assertNotNull(edge);
    }
}
