package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Timeline;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class SmoothScrollTest
{
	private static final BarLayout L = BarLayout.of(519, 62);

	private static BarState state(boolean smooth, long tickMs)
	{
		return BarState.builder().nowTick(10).tickMs(tickMs).smooth(smooth).build();
	}

	@Test
	public void glidesOneSlotAcrossATick()
	{
		BarState s = state(true, 1000);
		assertEquals(0f, BarPainter.glide(L, s, 1000), 0.001f);
		assertEquals(L.tickWidth / 2f, BarPainter.glide(L, s, 1300), 0.001f);
		assertEquals(L.tickWidth, BarPainter.glide(L, s, 1600), 0.001f);
	}

	@Test
	public void stopsAtOneSlotWhenTicksArriveLate()
	{
		assertEquals(L.tickWidth, BarPainter.glide(L, state(true, 1000), 5000), 0.001f);
	}

	@Test
	public void stepsPerTickWhenTurnedOff()
	{
		assertEquals(0f, BarPainter.glide(L, state(false, 1000), 1300), 0f);
	}

	@Test
	public void ongoingPillReachesTheNowLineMidTick()
	{
		Timeline t = new Timeline(64);
		t.openSpan("action", 5);
		BarState s = BarState.builder().timeline(t).nowTick(10).tickMs(1000).smooth(true).build();
		BufferedImage img = new BufferedImage(519, 62, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		new BarPainter(null).paint(g, L, s, 1300);
		g.dispose();
		int x = (int) TimelinePainter.nowX(L) - 4;
		int y = (int) (L.laneY(Lane.DOING) + L.laneH(Lane.DOING) / 2f);
		assertEquals(Palette.ACTION.getRGB(), img.getRGB(x, y));
	}

	@Test
	public void labelRidesThePillsRightEnd()
	{
		assertEquals(400f - 9f - 30f, TimelinePainter.labelX(100f, 400f, 30f, 1f), 0.001f);
		// A short pill keeps the label 9 px in from its start.
		assertEquals(109f, TimelinePainter.labelX(100f, 140f, 30f, 1f), 0.001f);
	}

	@Test
	public void stoppedPrayerEasesBackInsteadOfSnapping()
	{
		Timeline t = new Timeline(64);
		t.at(10);
		com.superhistogram.model.Span sp = t.openSpan("pray-melee", 2);
		t.closeSpan("pray-melee", 8);
		BarState s = BarState.builder().timeline(t).nowTick(10).tickMs(1000).smooth(true).build();
		float end = TimelinePainter.x(L, s, 9);
		// Right as it closes: still at the now line, where the open bar was drawn.
		assertEquals(TimelinePainter.nowX(L), TimelinePainter.spanEndX(L.withGlide(0f), s, sp), 0.001f);
		// By the end of the tick: at its real end.
		assertEquals(end, TimelinePainter.spanEndX(L.withGlide(L.tickWidth), s, sp), 0.001f);
	}

	private static BarLayout live(float glide)
	{
		return L.withGlide(glide, L.tickWidth);
	}

	@Test
	public void smoothNowSitsInTheTickInProgress()
	{
		// Just after a tick "now" is at the start of the slot in progress, by its end at the now line.
		assertEquals(TimelinePainter.nowX(L) - L.tickWidth, TimelinePainter.liveX(live(0f)), 0.001f);
		assertEquals(TimelinePainter.nowX(L), TimelinePainter.liveX(live(L.tickWidth)), 0.001f);
	}

	@Test
	public void prayerPillEndsWhereTheGameTurnedItOffWithoutShrinking()
	{
		Timeline t = new Timeline(64);
		t.at(9);
		com.superhistogram.model.Span sp = t.openSpan("pray-melee", 5);
		t.at(10).overheadClicks.add(0.9f);
		BarState s = BarState.builder().timeline(t).nowTick(10).tickMs(1000).smooth(true).build();
		// Clicked off but the game still has it on: the pill keeps reaching "now".
		assertEquals(TimelinePainter.liveX(live(0.95f * L.tickWidth)),
			TimelinePainter.spanEndX(live(0.95f * L.tickWidth), s, sp), 0.001f);
		float reached = TimelinePainter.liveX(live(L.tickWidth)) - L.tickWidth; // where it got to, one slot on
		// The game turns it off next tick: last on tick 10, so it ends at the end of slot 10 and stays there.
		t.at(11);
		t.closeSpan("pray-melee", 10);
		BarState next = BarState.builder().timeline(t).nowTick(11).tickMs(1600).smooth(true).build();
		float end = TimelinePainter.x(L, next, 11);
		assertEquals(end, TimelinePainter.spanEndX(live(L.tickWidth), next, sp), 0.001f);
		assertTrue("never shrinks behind where it got to", end >= reached - 0.001f);
	}
}
