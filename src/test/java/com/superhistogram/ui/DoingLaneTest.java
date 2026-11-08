package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.collect.FightCollector;
import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.Span;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.RightSide;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** The doing lane at the chat width (519 x 62): one pill per tick range, the specific activity winning. */
public class DoingLaneTest
{
	private static final BarLayout L = BarLayout.of(519, 62);
	private static final int NOW = SampleStates.NOW_TICK;

	private static BufferedImage paint(BarLayout l, BarState s)
	{
		BufferedImage img = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		BaseTimeline.paint(g, l, s, null);
		g.dispose();
		return img;
	}

	/** Colour at the centre of a tick's slot in the doing lane. */
	private static int at(BarLayout l, BarState s, int tick)
	{
		int x = Math.round(TimelinePainter.x(l, s, tick) + l.tickWidth / 2f);
		int y = Math.round(l.laneY(Lane.DOING) + l.laneH(Lane.DOING) / 2f);
		return paint(l, s).getRGB(x, y);
	}

	private static void action(BarState s, int from, int to)
	{
		s.timeline.openSpan("action", from);
		s.timeline.closeSpan("action", to);
	}

	private static BarState bare(Timeline t)
	{
		return BarState.builder().timeline(t).nowTick(NOW).mode(Mode.GENERAL).nowMs(SampleStates.NOW_MS).build();
	}

	@Test
	public void specificActivityHidesTheGenericPill()
	{
		BarState s = SampleStates.cooking(SampleStates.ProdPhase.RUNNING);
		action(s, NOW - 6, NOW - 4);
		assertEquals(Palette.COOK_PILL.getRGB(), at(L, s, NOW - 5));
	}

	@Test
	public void movementShowsWhereNothingElseIs()
	{
		Timeline t = new Timeline(64);
		t.at(NOW);
		t.openSpan("run", NOW - 8);
		t.closeSpan("run", NOW - 5);
		BarState s = bare(t);
		int c = at(L, s, NOW - 7);
		Color run = Palette.runEnergy();
		assertEquals(run.getRed(), (c >> 16) & 0xff, 2);
		assertEquals(run.getGreen(), (c >> 8) & 0xff, 2);
		assertEquals(run.getBlue(), c & 0xff, 2);
		assertEquals(Math.round(Palette.FAINT_ALPHA * 255), c >>> 24, 2);
	}

	@Test
	public void gatherPillIsInTheDoingLane()
	{
		BarState s = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING);
		assertEquals(Palette.WC_PILL.getRGB(), at(L, s, NOW - 5));
	}

	@Test
	public void fightWinsOverRunningAndIsInTheDoingLane()
	{
		Timeline t = new Timeline(64);
		t.at(NOW);
		new FightCollector(t).onTick(NOW - 8, "Abyssal demon");
		t.openSpan("run", NOW - 6);
		t.closeSpan("run", NOW - 4);
		BarState s = bare(t);
		assertEquals(Palette.FIGHT.getRGB(), at(L, s, NOW - 5));
		// Nothing of the fight outside the doing lane.
		BufferedImage img = paint(L, s);
		int x = Math.round(TimelinePainter.x(L, s, NOW - 5) + L.tickWidth / 2f);
		for (int y = 0; y < L.height; y++)
		{
			boolean inLane = y >= L.laneY(Lane.DOING) - 1 && y <= L.laneY(Lane.DOING) + L.laneH(Lane.DOING) + 1;
			boolean ruler = y >= L.laneY(Lane.RULER) - 1;
			if (!inLane && !ruler)
			{
				assertEquals("y " + y, 0, img.getRGB(x, y) >>> 24);
			}
		}
	}

	@Test
	public void alchCastHidesTheGenericAction()
	{
		BarState s = SampleStates.alch(AlchTopRow.GOLD, RightSide.PER_HOUR);
		action(s, SampleStates.CAST_MID + 1, SampleStates.CAST_MID + 3);
		assertEquals(Palette.PRAYER_MAGIC.getRGB(), at(L, s, SampleStates.CAST_MID + 2));
	}

	@Test
	public void nullTrackersDrawNothingAndDontThrow()
	{
		Timeline t = new Timeline(64);
		t.at(NOW);
		for (BarLayout l : Arrays.asList(L, BarLayout.of(519, 50), BarLayout.of(519, 30)))
		{
			paint(l, bare(t));
			paint(l, SampleStates.withoutRun(SampleStates.cooking(SampleStates.ProdPhase.RUNNING)));
			paint(l, SampleStates.withoutGather(SampleStates.gathering(SampleStates.GatherPhase.CHOPPING)));
			paint(l, SampleStates.withoutAlch(SampleStates.alch(AlchTopRow.GOLD, RightSide.PER_HOUR)));
			paint(l, SampleStates.withoutAgility(SampleStates.agility(SampleStates.AgilityPhase.MID_LAP)));
		}
		BufferedImage img = paint(L, bare(t));
		int y = Math.round(L.laneY(Lane.DOING) + L.laneH(Lane.DOING) / 2f);
		for (int x = 0; x < L.width; x++)
		{
			assertEquals(0, img.getRGB(x, y) >>> 24);
		}
	}

	@Test
	public void gapsCutCoveredRanges()
	{
		List<int[]> g = DoingLane.gaps(0, 10, Arrays.asList(new int[]{2, 4}, new int[]{6, 12}));
		assertEquals(2, g.size());
		assertTrue(Arrays.equals(new int[]{0, 2}, g.get(0)));
		assertTrue(Arrays.equals(new int[]{4, 6}, g.get(1)));
	}
}
