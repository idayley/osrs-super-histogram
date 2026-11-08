package com.superhistogram.ui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.combat.FlickClassifier;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.junit.Test;

/** The nine modes as lenses over one shared timeline. */
public class LensTest
{
	/** The same state as {@code s} (every tracker and setting kept) in another mode. */
	static BarState inMode(BarState s, Mode mode)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub)
			.showLoginTime(s.showLoginTime).mode(mode).nowMs(s.nowMs).tickMs(s.tickMs).smooth(s.smooth)
			.nowLinePct(s.nowLinePct).beat(s.beat).flicks(s.flicks).weaponSpeed(s.weaponSpeed)
			.actionsRow(s.actionsRow).showEatCosts(s.showEatCosts).run(s.run).timeToFinish(s.timeToFinish)
			.wastedTicks(s.wastedTicks).xpDrops(s.xpDrops).xpDots(s.xpDots).alch(s.alch).gather(s.gather)
			.alchTopRow(s.alchTopRow).rightSide(s.rightSide).agility(s.agility)
			.build();
	}

	/** A populated timeline (clicks, spans, hits, XP) with every tracker null: no beat, flicks, run, idle... */
	private static BarState withoutTrackers(Mode mode)
	{
		BarState g = SampleStates.general();
		return BarState.builder()
			.timeline(g.timeline).nowTick(g.nowTick).xp(g.xp).clock(g.clock)
			.runEnergyPct(g.runEnergyPct).modeName("x").modeSub("y").showLoginTime(true).mode(mode)
			.nowMs(g.nowMs).wastedTicks(true).timeToFinish(true)
			.build();
	}

	private static BufferedImage blank(BarLayout l)
	{
		return new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
	}

	private static Graphics2D graphics(BufferedImage img)
	{
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g;
	}

	private static int painted(BufferedImage img)
	{
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					n++;
				}
			}
		}
		return n;
	}

	@Test
	public void everyLensWithoutItsTrackerDrawsNoOverlay()
	{
		BarPainter p = new BarPainter(null);
		for (int[] size : new int[][]{{1026, 62}, {519, 62}, {1026, 46}, {600, 30}})
		{
			BarLayout l = BarLayout.of(size[0], size[1]);
			for (Mode m : Mode.values())
			{
				Lens lens = p.lens(m);
				for (BarState s : new BarState[]{withoutTrackers(m), SampleStates.empty(m)})
				{
					BufferedImage img = blank(l);
					Graphics2D g = graphics(img);
					try
					{
						lens.overlay(g, l, s);
						lens.paintRight(g, l, s);
					}
					finally
					{
						g.dispose();
					}
					BufferedImage over = blank(l);
					Graphics2D og = graphics(over);
					try
					{
						lens.overlay(og, l, s);
					}
					finally
					{
						og.dispose();
					}
					assertEquals(m + " overlay at " + size[0] + "x" + size[1], 0, painted(over));
				}
			}
		}
	}

	@Test
	public void everyModeHasALensWithAFullEmphasisArray()
	{
		BarPainter p = new BarPainter(null);
		for (Mode m : Mode.values())
		{
			float[] e = p.lens(m).emphasis(SampleStates.general());
			assertNotNull(m.toString(), e);
			assertEquals(m.toString(), Lane.values().length, e.length);
		}
	}

	@Test
	public void onlyMinimalChangesTheBase()
	{
		// Replay findings 3, 6, 7: no mode dims a lane; only the Minimal baseline (a setting) hides them.
		BarPainter p = new BarPainter(null);
		BarState s = SampleStates.general();
		for (Mode m : Mode.values())
		{
			float[] want = m == Mode.MINIMAL ? new float[]{1, 0, 0, 0, 0} : new float[]{1, 1, 1, 1, 1};
			assertArrayEquals(m.toString(), want, p.lens(m).emphasis(s), 0f);
		}
	}

	/** Pixels inside the timeline (the readouts either side left out). */
	private static int[] timelinePixels(BufferedImage img, BarLayout l)
	{
		int x0 = (int) Math.ceil(l.timelineX), x1 = (int) Math.floor(l.timelineX + l.timelineW);
		return img.getRGB(x0, 0, x1 - x0, l.height, null, 0, x1 - x0);
	}

	private static BufferedImage renderBase(BarLayout l, BarState s)
	{
		BufferedImage img = blank(l);
		Graphics2D g = img.createGraphics();
		try
		{
			BarPainter p = new BarPainter(null);
			p.baseOnly = true;
			p.paint(g, l, s, SampleStates.NOW_MS);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}

	@Test
	public void lensesShareTheBase()
	{
		BarState[] samples = {
			SampleStates.general(), SampleStates.lazyFlick(), SampleStates.oneTick(FlickClassifier.Kind.OK),
			SampleStates.cooking(SampleStates.ProdPhase.RUNNING), SampleStates.gathering(SampleStates.GatherPhase.CHOPPING),
			SampleStates.alch(null, null), SampleStates.agility(SampleStates.AgilityPhase.MID_LAP),
		};
		for (int[] size : new int[][]{{519, 62}, {1026, 62}, {1026, 40}, {1026, 80}})
		{
			BarLayout l = BarLayout.of(size[0], size[1]);
			for (BarState sample : samples)
			{
				int[] general = timelinePixels(renderBase(l, inMode(sample, Mode.GENERAL)), l);
				for (Mode m : Mode.values())
				{
					int[] px = timelinePixels(renderBase(l, inMode(sample, m)), l);
					assertArrayEquals(sample.mode + " through " + m + " at " + size[0] + "x" + size[1], general, px);
				}
			}
		}
	}
}
