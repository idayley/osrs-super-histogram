package com.superhistogram.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

/** A mode switch eases the lane emphasis and fades the new lens's overlay in; the base never moves. */
public class ModeFadeTest
{
	private static final float D = Lens.DIM;
	private static final float[] FULL = {1, 1, 1, 1, 1}, ONE_TICK = {D, D, 1, D, 1};

	@Test
	public void firstPaintShowsTheLensAtOnce()
	{
		BarPainter p = new BarPainter(null);
		assertArrayEquals(ONE_TICK, p.laneAlpha(Mode.ONE_TICK, ONE_TICK, 1000), 0f);
		assertEquals(1f, p.overlayAlpha(1000), 0f);
	}

	@Test
	public void emphasisEasesToTheNewLensOverTheFade()
	{
		BarPainter p = new BarPainter(null);
		p.laneAlpha(Mode.GENERAL, FULL, 1000);
		// Right after the switch: lanes still as General drew them, no overlay yet.
		assertArrayEquals(FULL, p.laneAlpha(Mode.ONE_TICK, ONE_TICK, 2000), 0f);
		assertEquals(0f, p.overlayAlpha(2000), 0f);

		float[] mid = p.laneAlpha(Mode.ONE_TICK, ONE_TICK, 2000 + BarPainter.FADE_MS / 2);
		assertTrue(mid[Lane.DOING.ordinal()] < 1f && mid[Lane.DOING.ordinal()] > D);
		assertEquals(1f, mid[Lane.PRAYER.ordinal()], 0f);
		assertEquals(0.5f, p.overlayAlpha(2000 + BarPainter.FADE_MS / 2), 0.01f);

		assertArrayEquals(ONE_TICK, p.laneAlpha(Mode.ONE_TICK, ONE_TICK, 2000 + BarPainter.FADE_MS), 0f);
		assertEquals(1f, p.overlayAlpha(2000 + BarPainter.FADE_MS), 0f);
	}

	@Test
	public void switchingBackMidFadeStartsFromWhatIsShown()
	{
		BarPainter p = new BarPainter(null);
		p.laneAlpha(Mode.GENERAL, FULL, 1000);
		p.laneAlpha(Mode.ONE_TICK, ONE_TICK, 2000);
		float[] mid = p.laneAlpha(Mode.ONE_TICK, ONE_TICK, 2000 + BarPainter.FADE_MS / 2);
		assertArrayEquals(mid, p.laneAlpha(Mode.GENERAL, FULL, 2000 + BarPainter.FADE_MS / 2), 0f);
		assertArrayEquals(FULL, p.laneAlpha(Mode.GENERAL, FULL, 2000 + BarPainter.FADE_MS / 2 + BarPainter.FADE_MS), 0f);
	}

	private static int[] timeline(BarPainter p, BarLayout l, BarState s, long nowMs)
	{
		BufferedImage img = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			p.paint(g, l, s, nowMs);
		}
		finally
		{
			g.dispose();
		}
		int x0 = (int) Math.ceil(l.timelineX), x1 = (int) Math.floor(l.timelineX + l.timelineW);
		return img.getRGB(x0, 0, x1 - x0, l.height, null, 0, x1 - x0);
	}

	@Test
	public void theTimelineIsUnchangedAtTheSwitchThenTheNewLensTakesOver()
	{
		BarLayout l = BarLayout.of(1026, 62);
		BarState flick = SampleStates.lazyFlick();
		BarState general = LensTest.inMode(flick, Mode.GENERAL);
		BarPainter p = new BarPainter(null);
		long t = SampleStates.NOW_MS;
		int[] before = timeline(p, l, general, t - 1000);
		assertArrayEquals(before, timeline(p, l, flick, t));
		assertFalse(java.util.Arrays.equals(before, timeline(p, l, flick, t + BarPainter.FADE_MS)));
	}
}
