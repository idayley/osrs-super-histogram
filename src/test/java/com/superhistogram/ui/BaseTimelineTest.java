package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.Span;
import com.superhistogram.model.combat.ActionsRow;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.junit.Test;

/**
 * The shared timeline at the chat width (519 x 62): meleeSlayer has, in view, a melee prayer at NOW-6..-5, a
 * blocked hit at NOW-6, a free eat at NOW-5, an attack at NOW-3 and XP drops at NOW-2.
 */
public class BaseTimelineTest
{
	private static final BarLayout L = BarLayout.of(519, 62);
	private static final int NOW = SampleStates.NOW_TICK;

	private static float[] only(Lane lane)
	{
		float[] a = new float[Lane.values().length];
		a[lane.ordinal()] = 1f;
		return a;
	}

	private static float[] all()
	{
		float[] a = new float[Lane.values().length];
		java.util.Arrays.fill(a, 1f);
		return a;
	}

	/** Paints onto a transparent image (or the bar ground) with no smooth-scroll translation. */
	private static BufferedImage paint(BarLayout l, BarState s, float[] alpha, boolean ground)
	{
		BufferedImage img = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if (ground)
		{
			g.setColor(Palette.ground());
			g.fillRect(0, 0, l.width, l.height);
		}
		BaseTimeline.paint(g, l, s, alpha);
		g.dispose();
		return img;
	}

	/** Bounds of the painted (non-transparent) pixels in columns x0..x1; null when none. */
	private static Rectangle painted(BufferedImage img, int x0, int x1)
	{
		Rectangle r = null;
		for (int x = Math.max(0, x0); x <= Math.min(img.getWidth() - 1, x1); x++)
		{
			for (int y = 0; y < img.getHeight(); y++)
			{
				if ((img.getRGB(x, y) >>> 24) > 0)
				{
					if (r == null)
					{
						r = new Rectangle(x, y, 1, 1);
					}
					else
					{
						r.add(x, y);
					}
				}
			}
		}
		return r;
	}

	private static void inside(Rectangle r, BarLayout l, Lane lane)
	{
		assertNotNull(lane + ": nothing painted", r);
		float y0 = l.laneY(lane), y1 = y0 + l.laneH(lane);
		assertTrue(lane + " top " + r.y + " < " + y0, r.y >= Math.floor(y0) - 1);
		assertTrue(lane + " bottom " + (r.y + r.height - 1) + " > " + y1, r.y + r.height - 1 <= Math.ceil(y1));
	}

	private static int tx(int tick)
	{
		return (int) TimelinePainter.x(L, SampleStates.meleeSlayer(), tick);
	}

	@Test
	public void prayerSitsInThePrayerLane()
	{
		BarState s = SampleStates.meleeSlayer();
		Span melee = s.timeline.spansBetween(NOW - 6, NOW - 5).stream()
			.filter(sp -> sp.kind.equals("pray-melee")).findFirst().get();
		int x = (int) ((TimelinePainter.spanStartX(L, s, melee) + TimelinePainter.spanEndX(L, s, melee)) / 2f);
		int y = (int) (L.laneY(Lane.PRAYER) + L.laneH(Lane.PRAYER) / 2f);
		assertEquals(Palette.PRAYER_MELEE.getRGB(), paint(L, s, all(), true).getRGB(x, y));
		// With the prayer lane at alpha 0 the same pixel is plain ground.
		float[] noPrayer = all();
		noPrayer[Lane.PRAYER.ordinal()] = 0f;
		assertEquals(paint(L, s, new float[Lane.values().length], true).getRGB(x, y),
			paint(L, s, noPrayer, true).getRGB(x, y));
	}

	@Test
	public void swordsEatsAndClicksStayInTheInputsLane()
	{
		BarState s = SampleStates.meleeSlayer();
		BufferedImage img = paint(L, s, only(Lane.INPUTS), false);
		inside(painted(img, 0, L.width), L, Lane.INPUTS);
		// The sword (attack at NOW-3) and the fish (eat at NOW-5) are both there.
		assertNotNull(painted(img, tx(NOW - 3), tx(NOW - 3) + 10));
		assertNotNull(painted(img, tx(NOW - 5) + 10, tx(NOW - 5) + 22));
	}

	@Test
	public void xpTextStaysInTheTopLane()
	{
		BarState s = SampleStates.meleeSlayer();
		BufferedImage img = paint(L, s, only(Lane.TOP), false);
		// Columns from the XP at NOW-2 on, clear of the hit line at NOW-6.
		inside(painted(img, tx(NOW - 2) - 4, L.width), L, Lane.TOP);
	}

	@Test
	public void hitLineRunsFromTopToPrayerButNotIntoInputs()
	{
		BarState s = SampleStates.meleeSlayer();
		BufferedImage img = paint(L, s, only(Lane.TOP), false);
		int x = tx(NOW - 6);
		int top = (int) L.laneY(Lane.TOP) + 1, prayerBottom = (int) (L.laneY(Lane.PRAYER) + L.laneH(Lane.PRAYER)) - 1;
		assertTrue((img.getRGB(x, top) >>> 24) > 0);
		assertTrue((img.getRGB(x, prayerBottom) >>> 24) > 0);
		for (int y = (int) L.laneY(Lane.INPUTS); y < L.height; y++)
		{
			assertEquals("y " + y, 0, img.getRGB(x, y) >>> 24);
		}
	}

	@Test
	public void actionsRowOffHidesSwordsAndEats()
	{
		BufferedImage img = paint(L, SampleStates.meleeSlayer(ActionsRow.OFF), only(Lane.INPUTS), false);
		assertNull(painted(img, tx(NOW - 3), tx(NOW - 3) + 10));
		assertNull(painted(img, tx(NOW - 5) + 10, tx(NOW - 5) + 22));
	}

	@Test
	public void attacksOnlyHidesEats()
	{
		BufferedImage img = paint(L, SampleStates.meleeSlayer(ActionsRow.ATTACKS), only(Lane.INPUTS), false);
		assertNotNull(painted(img, tx(NOW - 3), tx(NOW - 3) + 10));
		assertNull(painted(img, tx(NOW - 5) + 10, tx(NOW - 5) + 22));
	}

	@Test
	public void twoRowBarHasNoInputs()
	{
		BarLayout l = BarLayout.of(1026, 42);
		assertNull(painted(paint(l, SampleStates.meleeSlayer(), only(Lane.INPUTS), false), 0, l.width));
		// The prayer pills sit in the lower half of the middle band.
		inside(painted(paint(l, SampleStates.meleeSlayer(), only(Lane.PRAYER), false), 0, l.width), l, Lane.PRAYER);
	}

	@Test
	public void oneRowBarDrawsPrayerAsAStrip()
	{
		BarLayout l = BarLayout.of(1026, 30);
		inside(painted(paint(l, SampleStates.meleeSlayer(), only(Lane.PRAYER), false), 0, l.width), l, Lane.PRAYER);
	}

	@Test
	public void sameBaseInEveryMode()
	{
		// withMode keeps the default actions row (attacks), so the reference uses it too.
		BarState lazy = SampleStates.meleeSlayer(ActionsRow.ATTACKS);
		BufferedImage a = paint(L, lazy, all(), true);
		for (Mode m : new Mode[]{Mode.GENERAL, Mode.ONE_TICK, Mode.MINIMAL})
		{
			BufferedImage b = paint(L, SampleStates.withMode(lazy, m, "x", "y"), all(), true);
			for (int x = 0; x < L.width; x++)
			{
				for (int y = 0; y < L.height; y++)
				{
					assertEquals(m + " at " + x + "," + y, a.getRGB(x, y), b.getRGB(x, y));
				}
			}
		}
	}

	@Test
	public void emptyStatesDrawOnlyTheRuler()
	{
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = paint(l, null, all(), false);
		inside(painted(img, 0, l.width), l, Lane.RULER);
		inside(painted(paint(l, SampleStates.empty(Mode.GENERAL), all(), false), 0, l.width), l, Lane.RULER);
	}
}
