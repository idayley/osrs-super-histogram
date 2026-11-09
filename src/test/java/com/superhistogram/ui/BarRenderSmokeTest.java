package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpTracker;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.ui.SampleStates.ProdPhase;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class BarRenderSmokeTest
{
	static BufferedImage render(int w, int h, BarState s, Pulse pulse)
	{
		BarLayout l = BarLayout.of(w, h);
		BufferedImage img = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			new BarPainter(pulse).paint(g, l, s, SampleStates.NOW_MS);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}

	private static int alpha(BufferedImage img, int x, int y)
	{
		return img.getRGB(x, y) >>> 24;
	}

	@Test
	public void rendersAtDefault()
	{
		BufferedImage img = render(1026, 62, SampleStates.general(), new Pulse());
		assertEquals(1026, img.getWidth());
		assertEquals(62, img.getHeight());
		// (1,1) is a corner pixel that the squircle corner cuts away.
		assertEquals(0, alpha(img, 1, 1));
		assertNotEquals(0, alpha(img, 513, 31));
	}

	@Test
	public void rendersAtExtremes()
	{
		for (Mode m : Mode.values())
		{
			BarState s = SampleStates.withMode(SampleStates.general(), m, "General", "auto");
			render(220, 22, s, new Pulse());
			render(360, 28, s, new Pulse());
			render(4000, 160, s, new Pulse());
			render(1, 1, s, null);
		}
	}

	@Test
	public void rendersWithEmptyState()
	{
		for (Mode m : Mode.values())
		{
			render(1026, 62, SampleStates.empty(m), new Pulse());
			render(620, 32, SampleStates.empty(m), null);
		}
		render(1026, 62, null, null);
	}

	@Test
	public void metronomeOutlineAndMintBlinkPaint()
	{
		int quiet = img(new Pulse()).getRGB(513, 1);
		Pulse cue = new Pulse();
		cue.tick(SampleStates.NOW_MS, true);
		assertTrue(brightness(img(cue).getRGB(513, 1)) > brightness(quiet) + 100);
		Pulse done = new Pulse();
		done.done(SampleStates.NOW_MS);
		int mint = img(done).getRGB(513, 31);
		assertTrue(((mint >> 8) & 0xff) > ((quiet >> 8) & 0xff) + 50);
	}

	private static BufferedImage img(Pulse p)
	{
		return render(1026, 62, SampleStates.empty(Mode.GENERAL), p);
	}

	private static int brightness(int argb)
	{
		return ((argb >> 16) & 0xff) + ((argb >> 8) & 0xff) + (argb & 0xff);
	}

	@Test
	public void noActionsRowBelowFullLanes()
	{
		// Bars shorter than the full-lanes size (44 px) have no actions row.
		int h = BarLayout.FULL_LANES_FROM - 2;
		assertTrue(hasWhiteInActionsBand(render(1026, 62, SampleStates.singleClick(), null), 62));
		assertFalse(hasWhiteInActionsBand(render(1026, h, SampleStates.singleClick(), null), h));
	}

	/** Looks for opaque pure-white pixels in the timeline between 42u and 50u. */
	private static boolean hasWhiteInActionsBand(BufferedImage img, int h)
	{
		BarLayout l = BarLayout.of(img.getWidth(), h);
		int y0 = (int) Math.floor(42 * l.u), y1 = (int) Math.ceil(50 * l.u);
		for (int y = y0; y < y1 && y < img.getHeight(); y++)
		{
			for (int x = (int) l.timelineX; x < l.timelineX + l.timelineW; x++)
			{
				int argb = img.getRGB(x, y);
				int a = argb >>> 24, r = (argb >> 16) & 0xff, gr = (argb >> 8) & 0xff, b = argb & 0xff;
				if (a == 255 && r >= 250 && gr >= 250 && b >= 250)
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void longRightTextDoesNotOverlapLoginTime()
	{
		BarState base = SampleStates.general();
		base.xp.onStat("Construction", 1_000_000, 190);
		base.xp.onStat("Construction", 2_234_567, 195);
		BarLayout l = BarLayout.of(720, 62);
		BufferedImage img = render(720, 62, base, null);

		// Same bar with an empty timeline and no XP: only the login time paints in the box.
		Timeline t = new Timeline(512);
		BarState loginOnly = BarState.builder()
			.timeline(t).nowTick(-1).xp(new XpTracker(t)).idle(base.idle).clock(base.clock)
			.runEnergyPct(64).modeName("General").modeSub("auto").mode(Mode.GENERAL).nowMs(base.nowMs)
			.build();
		BufferedImage ref = render(720, 62, loginOnly, null);

		Graphics2D g = img.createGraphics();
		int tw = g.getFontMetrics(Fonts.semiBold(11f * l.u)).stringWidth(base.clock.format(base.nowMs));
		g.dispose();
		int x1 = (int) (l.width - 26f * l.u), x0 = x1 - tw - 2;
		int y0 = (int) (14f * l.u), y1 = (int) (31f * l.u);
		int differing = 0;
		for (int y = y0; y < y1; y++)
		{
			for (int x = x0; x < x1; x++)
			{
				if (img.getRGB(x, y) != ref.getRGB(x, y))
				{
					differing++;
				}
			}
		}
		assertEquals("right readout text leaks into the login-time box", 0, differing);

		// And nothing paints past the rounded right end's inner edge on the readout rows.
		for (int y = (int) (14f * l.u); y < (int) (50f * l.u); y++)
		{
			for (int x = x1 + 1; x < l.width; x++)
			{
				assertEquals("text past the right limit at " + x + "," + y, ref.getRGB(x, y), img.getRGB(x, y));
			}
		}
	}

	// ---- multi-xp ----

	private static final java.awt.Color STR = Palette.skill("Strength"), HP = Palette.skill("Hitpoints"),
		MAG = Palette.skill("Magic"), FLE = Palette.skill("Fletching");

	@Test
	public void numbersSideBySide()
	{
		BarState s = SampleStates.multiXp(com.superhistogram.model.XpDrops.NUMBERS, true);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u;
		int minHp = Integer.MAX_VALUE, maxStr = -1;
		float x0 = tickX(l, s, SampleStates.NOW_TICK - 10) - 8 * u;
		for (int y = (int) (6 * u); y < (int) (16 * u); y++)
		{
			for (int x = (int) x0; x < x0 + 120 * u; x++)
			{
				if (opaque(img, x, y, STR))
				{
					maxStr = Math.max(maxStr, x);
				}
				if (opaque(img, x, y, HP))
				{
					minHp = Math.min(minHp, x);
				}
			}
		}
		assertTrue("no Strength", maxStr >= 0);
		assertTrue("no Hitpoints", minHp != Integer.MAX_VALUE);
		assertTrue("Strength must lie left of Hitpoints", maxStr < minHp);
	}

	@Test
	public void dotsHiddenUnderNumbers()
	{
		BarState s = SampleStates.multiXp(com.superhistogram.model.XpDrops.NUMBERS, true);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u;
		float hx = tickX(l, s, SampleStates.NOW_TICK - 5) - 1.4f * u;
		float sx = tickX(l, s, SampleStates.NOW_TICK - 3) - 1.4f * u;
		assertFalse(hasPixel(img, hx - 2 * u, hx + 2 * u, 10 * u, 14 * u, FLE));
		assertTrue(hasPixel(img, sx - 2 * u, sx + 2 * u, 10 * u, 14 * u, FLE));
	}

	@Test
	public void dotsOffShowsFrequentAsNumbers()
	{
		BarState s = SampleStates.multiXp(com.superhistogram.model.XpDrops.NUMBERS, false);
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x = tickX(l, s, SampleStates.NOW_TICK - 3);
		assertTrue(hasPixel(render(1026, 62, s, null), x + 4 * u, x + 14 * u, 6 * u, 16 * u, FLE));
	}

	@Test
	public void dotsOnlyDrawsDots()
	{
		BarState s = SampleStates.multiXp(com.superhistogram.model.XpDrops.DOTS_ONLY, true);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u, x = tickX(l, s, SampleStates.NOW_TICK - 10);
		assertTrue(hasPixel(img, x - 1.4f * u - 2 * u, x - 1.4f * u + 2 * u, 10 * u, 14 * u, STR));
		assertFalse(hasPixel(img, x + 4 * u, x + 18 * u, 5 * u, 17 * u, STR));
	}

	@Test
	public void xpDropsOffHidesXp()
	{
		BarState s = SampleStates.multiXp(com.superhistogram.model.XpDrops.OFF, true);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u;
		for (java.awt.Color c : new java.awt.Color[]{STR, HP, MAG, FLE})
		{
			assertFalse(hasPixel(img, 0, l.width, 5 * u, 17 * u, c));
		}
	}


	@Test
	public void combatXpInTheTopLane()
	{
		BarState s = SampleStates.lazyFlick();
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float x0 = l.timelineX, x1 = l.timelineX + l.timelineW;
		assertTrue(hasPixel(img, x0, x1, l.laneY(Lane.TOP), l.laneY(Lane.TOP) + l.laneH(Lane.TOP), STR));
		assertFalse(hasPixel(img, x0, x1, l.laneY(Lane.PRAYER), l.height, STR));
	}

	// ---- lazy flick ----

	private static boolean opaque(BufferedImage img, int x, int y, java.awt.Color c)
	{
		int argb = img.getRGB(x, y);
		return (argb >>> 24) == 255 && (argb & 0xffffff) == (c.getRGB() & 0xffffff);
	}

	/** Any opaque pixel of colour c with x in [x0, x1) and y in [y0, y1) (clamped to the image). */
	private static boolean hasPixel(BufferedImage img, float x0, float x1, float y0, float y1, java.awt.Color c)
	{
		for (int y = Math.max(0, (int) Math.floor(y0)); y < Math.min(img.getHeight(), Math.ceil(y1)); y++)
		{
			for (int x = Math.max(0, (int) Math.floor(x0)); x < Math.min(img.getWidth(), Math.ceil(x1)); x++)
			{
				if (opaque(img, x, y, c))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static float tickX(BarLayout l, BarState s, int tick)
	{
		return TimelinePainter.x(BarPainter.timelineLayout(l, s), s, tick);
	}

	@Test
	public void lazyFlickRendersAtThreeSizes()
	{
		BarState s = SampleStates.lazyFlick();
		render(1026, 62, s, new Pulse());
		render(1026, 46, s, new Pulse());
		render(600, 30, s, new Pulse());
	}

	@Test
	public void lazyFlickWithIdleBeatIsSafe()
	{
		for (ActionsRow row : ActionsRow.values())
		{
			BarState s = SampleStates.lazyFlick(row, true, Beat.Verdict.ON_TIME);
			BarState idle = BarState.builder()
				.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
				.modeName("Lazy flick").modeSub("4t").mode(Mode.LAZY_FLICK).nowMs(s.nowMs)
				.beat(new Beat()).actionsRow(row).build();
			render(1026, 62, idle, new Pulse());
			render(1026, 30, idle, null);
		}
	}

	@Test
	public void caretUnderBeatTick()
	{
		BarState s = SampleStates.lazyFlick();
		int b = SampleStates.FLICK_PRESS + 16;
		BarLayout l62 = BarLayout.of(1026, 62);
		float x = tickX(l62, s, b), u = l62.u;
		assertTrue(hasPixel(render(1026, 62, s, null), x - 4 * u, x + 4 * u, 55 * u, 60 * u, Palette.WHITE));
		BarLayout l42 = BarLayout.of(1026, 42);
		float x42 = tickX(l42, s, b);
		assertFalse(hasPixel(render(1026, 42, s, null), x42 - 4 * l42.u, x42 + 4 * l42.u, 55 * l42.u, 60 * l42.u,
			Palette.WHITE));
	}

	@Test
	public void swordsHiddenWhenActionsRowOff()
	{
		int t = SampleStates.NOW_TICK - 15;
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u;
		BarState on = SampleStates.lazyFlick(ActionsRow.ATTACKS, true, Beat.Verdict.ON_TIME);
		BarState off = SampleStates.lazyFlick(ActionsRow.OFF, true, Beat.Verdict.ON_TIME);
		float x = BaseTimeline.swordX(l, tickX(l, on, t)), y0 = l.laneY(Lane.INPUTS), y1 = y0 + l.laneH(Lane.INPUTS);
		BufferedImage onImg = render(1026, 62, on, null), offImg = render(1026, 62, off, null);
		boolean sword = false;
		for (int y = (int) y0; y < (int) Math.ceil(y1); y++)
		{
			for (int px = (int) (x - 2 * u); px <= (int) (x + 2 * u); px++)
			{
				sword |= brightness(onImg.getRGB(px, y)) > brightness(offImg.getRGB(px, y)) + 60;
			}
		}
		assertTrue("sword only with the actions row on", sword);
	}

	@Test
	public void eatCostsNeedAttacksAndEats()
	{
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u;
		int normal = SampleStates.EAT_NORMAL, free = SampleStates.EAT_FREE;
		BarState all = SampleStates.lazyFlick(ActionsRow.ATTACKS_EATS, true, Beat.Verdict.ON_TIME);
		BarState noCost = SampleStates.lazyFlick(ActionsRow.ATTACKS_EATS, false, Beat.Verdict.ON_TIME);
		BarState attacks = SampleStates.lazyFlick(ActionsRow.ATTACKS, true, Beat.Verdict.ON_TIME);
		float nx = tickX(l, all, normal) + l.tickWidth / 2, fx = tickX(l, all, free) + l.tickWidth / 2;
		BufferedImage a = render(1026, 62, all, null);
		assertTrue(hasPixel(a, nx + 9 * u, nx + 24 * u, 41 * u, 49 * u, Palette.LOST));
		assertTrue(hasPixel(a, fx + 9 * u, fx + 24 * u, 41 * u, 49 * u, Palette.MINT));
		BufferedImage n = render(1026, 62, noCost, null);
		assertFalse(hasPixel(n, nx + 9 * u, nx + 24 * u, 41 * u, 49 * u, Palette.LOST));
		assertFalse(hasPixel(n, fx + 9 * u, fx + 24 * u, 41 * u, 49 * u, Palette.MINT));
		BufferedImage at = render(1026, 62, attacks, null);
		assertFalse(hasPixel(at, nx + 9 * u, nx + 24 * u, 41 * u, 49 * u, Palette.LOST));
		assertFalse(hasPixel(at, fx + 9 * u, fx + 24 * u, 41 * u, 49 * u, Palette.MINT));
	}

	@Test
	public void trailVerdictPaints()
	{
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, rx = CombatPainter.rightX(l);
		BarState late = SampleStates.withoutLogin(SampleStates.lazyFlick(ActionsRow.ATTACKS, true, Beat.Verdict.LATE));
		BarState ok = SampleStates.withoutLogin(SampleStates.lazyFlick(ActionsRow.ATTACKS, true, Beat.Verdict.ON_TIME));
		assertTrue(hasPixel(render(1026, 62, late, null), rx + 140 * u, rx + 190 * u, 22 * u, 40 * u, Palette.WHITE));
		assertFalse(hasPixel(render(1026, 62, late, null), rx + 140 * u, rx + 190 * u, 22 * u, 40 * u, Palette.MINT));
		assertTrue(hasPixel(render(1026, 62, ok, null), rx + 140 * u, rx + 190 * u, 22 * u, 40 * u, Palette.MINT));
		assertFalse(hasPixel(render(1026, 62, ok, null), rx + 140 * u, rx + 190 * u, 22 * u, 40 * u, Palette.WHITE));
	}

	@Test
	public void lazyFlickTrailDoesNotOverlapLoginTime()
	{
		for (int w : new int[]{1026, 720})
		{
			for (Beat.Verdict last : new Beat.Verdict[]{Beat.Verdict.EARLY, Beat.Verdict.ON_TIME})
			{
				BarState s = SampleStates.lazyFlick(ActionsRow.ATTACKS_EATS, true, last);
				BarLayout l = BarLayout.of(w, 62);
				BufferedImage img = render(w, 62, s, null);
				BufferedImage ref = render(w, 62, SampleStates.withoutBeat(s), null);
				Graphics2D g = img.createGraphics();
				int tw = g.getFontMetrics(Fonts.semiBold(11f * l.u)).stringWidth(s.clock.format(s.nowMs));
				g.dispose();
				int x1 = (int) (l.width - 26f * l.u), x0 = x1 - tw - 2;
				for (int y = (int) (14f * l.u); y < (int) (50f * l.u); y++)
				{
					for (int x = x0; x < l.width; x++)
					{
						assertEquals("w=" + w + " " + last + " paint over login time at " + x + "," + y,
							ref.getRGB(x, y), img.getRGB(x, y));
					}
				}
				// And the trail did paint something in the readout area.
				boolean painted = false;
				for (int y = 0; y < 62 && !painted; y++)
				{
					for (int x = (int) CombatPainter.rightX(l); x < x0 && !painted; x++)
					{
						painted = img.getRGB(x, y) != ref.getRGB(x, y);
					}
				}
				assertTrue("w=" + w + " " + last + " trail not painted", painted);
			}
		}
	}

	@Test
	public void nothingAheadOfNowExceptNextBeat()
	{
		BarState s = SampleStates.lazyFlick();
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u, now = TimelinePainter.nowX(l), right = CombatPainter.rightX(l);
		float cx = tickX(l, s, s.beat.nextBeat());
		int rulerTop = (int) Math.floor(l.height - 9f * u) - 1, rulerBottom = (int) Math.ceil(l.height - 6f * u) + 1;
		int ground = Palette.GROUND.getRGB();
		for (int x = (int) now + 3; x < right; x++)
		{
			for (int y = 0; y < l.height; y++)
			{
				boolean ruler = y >= rulerTop && y <= rulerBottom;
				boolean caretBox = Math.abs(x - cx) <= 4 * u + 1 && y >= 55 * u - 1 && y <= 60 * u + 1;
				// The next beat's guide line up from its caret, and its countdown at the top.
				boolean guide = Math.abs(x - cx) <= 1 * u + 1;
				boolean countdown = Math.abs(x - cx) <= 6 * u + 1 && y <= 10 * u;
				if (ruler || caretBox || guide || countdown)
				{
					continue;
				}
				assertEquals("paint ahead of now at " + x + "," + y, ground, img.getRGB(x, y));
			}
		}
		assertTrue(s.beat.nextBeat() > s.nowTick);
	}

	// ---- 1-tick flick ----

	@Test
	public void oneTickRendersAtThreeSizes()
	{
		BarState s = SampleStates.oneTick(FlickClassifier.Kind.OK);
		render(1026, 62, s, new Pulse());
		render(1026, 46, s, new Pulse());
		render(600, 30, s, new Pulse());
		render(220, 22, SampleStates.empty(Mode.ONE_TICK), null);
	}

	@Test
	public void oneTickVerdictPaints()
	{
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, f = l.height / 52f, rx = CombatPainter.rightX(l);
		BarState ok = SampleStates.withoutLogin(SampleStates.oneTick(FlickClassifier.Kind.OK));
		BarState split = SampleStates.withoutLogin(SampleStates.oneTick(FlickClassifier.Kind.SPLIT));
		assertTrue(hasPixel(render(1026, 62, ok, null), rx + 128 * u, rx + 160 * u, 17 * f, 36 * f, Palette.MINT));
		assertFalse(hasPixel(render(1026, 62, ok, null), rx + 128 * u, rx + 160 * u, 17 * f, 36 * f, Palette.WHITE));
		assertTrue(hasPixel(render(1026, 62, split, null), rx + 128 * u, rx + 160 * u, 17 * f, 36 * f, Palette.WHITE));
		assertFalse(hasPixel(render(1026, 62, split, null), rx + 128 * u, rx + 160 * u, 17 * f, 36 * f, Palette.MINT));
	}

	@Test
	public void splitBinsAreWhite()
	{
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, f = l.height / 52f, hx = CombatPainter.rightX(l) + 16 * u;
		BarState s = SampleStates.withoutLogin(SampleStates.oneTick(FlickClassifier.Kind.OK));
		BufferedImage img = render(1026, 62, s, null);
		assertTrue(hasPixel(img, hx + 80 * u, hx + 96 * u, 9 * f, 35 * f, Palette.WHITE));
		assertFalse(hasPixel(img, hx + 80 * u, hx + 96 * u, 9 * f, 35 * f, Palette.MINT));
		assertTrue(hasPixel(img, hx, hx + 80 * u, 9 * f, 35 * f, Palette.MINT));
		assertFalse(hasPixel(img, hx, hx + 80 * u, 9 * f, 35 * f, Palette.WHITE));
	}

	@Test
	public void oneTickHistogramDoesNotOverlapLoginTime()
	{
		for (int w : new int[]{1026, 720})
		{
			for (FlickClassifier.Kind last : new FlickClassifier.Kind[]{FlickClassifier.Kind.OK,
				FlickClassifier.Kind.DRAIN})
			{
				BarState s = SampleStates.oneTick(last);
				BarLayout l = BarLayout.of(w, 62);
				BufferedImage img = render(w, 62, s, null);
				BufferedImage ref = render(w, 62, SampleStates.withoutFlicks(s), null);
				Graphics2D g = img.createGraphics();
				int tw = g.getFontMetrics(Fonts.semiBold(11f * l.u)).stringWidth(s.clock.format(s.nowMs));
				g.dispose();
				int x1 = (int) (l.width - 26f * l.u), x0 = x1 - tw - 2;
				for (int y = (int) (14f * l.u); y < (int) (50f * l.u); y++)
				{
					for (int x = x0; x < l.width; x++)
					{
						assertEquals("w=" + w + " " + last + " paint over login time at " + x + "," + y,
							ref.getRGB(x, y), img.getRGB(x, y));
					}
				}
				boolean painted = false;
				for (int y = 0; y < 62 && !painted; y++)
				{
					for (int x = (int) CombatPainter.rightX(l); x < x0 && !painted; x++)
					{
						painted = img.getRGB(x, y) != ref.getRGB(x, y);
					}
				}
				assertTrue("w=" + w + " " + last + " readout not painted", painted);
			}
		}
	}

	// ---- production ----

	private static final int NOW = SampleStates.NOW_TICK;

	@Test
	public void productionRendersAtThreeSizes()
	{
		for (ProdPhase p : ProdPhase.values())
		{
			BarState s = SampleStates.cooking(p);
			render(1026, 62, s, new Pulse());
			render(1026, 46, s, new Pulse());
			render(600, 30, s, new Pulse());
		}
	}

	@Test
	public void cookedPillSolidRemainingFaint()
	{
		BarState s = SampleStates.cooking(ProdPhase.RUNNING);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u;
		int y = (int) (l.laneY(Lane.DOING) + l.laneH(Lane.DOING) / 2f);
		assertEquals(Palette.COOK_PILL.getRGB(), img.getRGB((int) tickX(l, s, NOW - 10), y));
		int x = (int) (tickX(l, s, NOW + 1) + 16 * u);
		assertEquals(Palette.GROUND.getRGB(), img.getRGB(x, y));
		// ... but the outline is there at the pill's top edge.
		assertTrue(hasPixelAlpha(img, x, x + 1, l.laneY(Lane.DOING) - 1, l.laneY(Lane.DOING) + 2 * u));
	}

	private static boolean hasPixelAlpha(BufferedImage img, float x0, float x1, float y0, float y1)
	{
		for (int y = (int) y0; y < (int) Math.ceil(y1); y++)
		{
			for (int x = (int) x0; x < (int) Math.ceil(x1); x++)
			{
				if (img.getRGB(x, y) != Palette.GROUND.getRGB())
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void finishFlagAheadOfNow()
	{
		BarState s = SampleStates.cooking(ProdPhase.RUNNING);
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, fx = tickX(l, s, NOW + 2);
		assertTrue(hasPixel(render(1026, 62, s, null), fx - u, fx + 9 * u, 42 * u, 60 * u, Palette.WHITE));
		BarLayout l42 = BarLayout.of(1026, 42);
		float fx42 = tickX(l42, s, NOW + 2);
		assertFalse(hasPixel(render(1026, 42, s, null), fx42 - l42.u, fx42 + 9 * l42.u, 42 * l42.u, 60 * l42.u,
			Palette.WHITE));
		BarState off = SampleStates.cooking(ProdPhase.RUNNING, false, true);
		assertFalse(hasPixel(render(1026, 62, off, null), fx - u, fx + 9 * u, 42 * u, 60 * u, Palette.WHITE));
	}

	@Test
	public void wastedLabelOnlyWhenEnabled()
	{
		BarState on = SampleStates.cooking(ProdPhase.BANKED);
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x0 = tickX(l, on, NOW - 5), x1 = tickX(l, on, NOW - 2);
		assertTrue(hasPixel(render(1026, 62, on, null), x0, x1, 34 * u, 42 * u, Palette.LOST));
		BarState off = SampleStates.cooking(ProdPhase.BANKED, true, false);
		assertFalse(hasPixel(render(1026, 62, off, null), x0, x1, 34 * u, 42 * u, Palette.LOST));
	}

	@Test
	public void burntIsGrey()
	{
		BarState s = SampleStates.cooking(ProdPhase.RUNNING);
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x = tickX(l, s, SampleStates.cookingItemTick(SampleStates.BURNT_B, 27, NOW - 2));
		assertTrue(hasPixel(render(1026, 62, s, null), x - 12 * u, x + 12 * u, 6 * u, 16 * u, Palette.BURNT));
	}

	@Test
	public void productionRightDoesNotOverlapLoginTime()
	{
		for (int w : new int[]{1026, 720})
		{
			for (ProdPhase p : ProdPhase.values())
			{
				BarState s = SampleStates.cooking(p);
				BarLayout l = BarLayout.of(w, 62);
				BufferedImage img = render(w, 62, s, null);
				BufferedImage ref = render(w, 62, SampleStates.withoutRun(s), null);
				Graphics2D g = img.createGraphics();
				int tw = g.getFontMetrics(Fonts.semiBold(11f * l.u)).stringWidth(s.clock.format(s.nowMs));
				g.dispose();
				int x1 = (int) (l.width - 26f * l.u), x0 = x1 - tw - 2;
				for (int y = (int) (14f * l.u); y < (int) (50f * l.u); y++)
				{
					for (int x = x0; x < l.width; x++)
					{
						assertEquals("w=" + w + " " + p + " paint over login time at " + x + "," + y,
							ref.getRGB(x, y), img.getRGB(x, y));
					}
				}
				boolean painted = false;
				for (int y = 0; y < 62 && !painted; y++)
				{
					for (int x = (int) CombatPainter.rightX(l); x < x0 && !painted; x++)
					{
						painted = img.getRGB(x, y) != ref.getRGB(x, y);
					}
				}
				assertTrue("w=" + w + " " + p + " readout not painted", painted);
			}
		}
	}

	@Test
	public void bankingHeadlineKeepsWastedTicksWhenNarrow()
	{
		BarState s = SampleStates.cooking(ProdPhase.BANKED);
		BarLayout l = BarLayout.of(720, 62);
		BufferedImage img = render(720, 62, s, null);
		Graphics2D g = img.createGraphics();
		float x = l.timelineX + l.timelineW + 10f * l.u;
		float room = ReadoutPainter.rightLimit(g, l, s) - x;
		String full = com.superhistogram.ui.modes.ProductionView.rightLines(s)[0];
		java.awt.Font f = Fonts.bold(20f * l.u);
		// Font-dependent: only meaningful when the full headline overflows with the current face.
		org.junit.Assume.assumeTrue("full headline fits with this font", g.getFontMetrics(f).stringWidth(full) > room);
		String picked = ReadoutPainter.pickHeadline(g, f, room, full,
			com.superhistogram.ui.modes.ProductionView.headlineFallbacks(s));
		g.dispose();
		assertEquals("Bank +3", picked);
		assertTrue(hasPixel(img, x, x + room, 14 * l.u, 36 * l.u, Palette.MINT));
	}

	@Test
	public void finishedRunHasNoOutline()
	{
		BarState s = SampleStates.cooking(ProdPhase.DONE);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		int x = (int) (tickX(l, s, NOW - 3) + 8 * l.u), y = (int) (22 * l.u + 1);
		assertEquals(Palette.GROUND.getRGB(), img.getRGB(x, y));
	}

	// ---- high alch ----

	private static final java.awt.Color TEXT = Palette.TEXT, DMG = Palette.DAMAGE;

	@Test
	public void alchRendersAtThreeSizes()
	{
		for (com.superhistogram.model.skill.AlchTopRow top : com.superhistogram.model.skill.AlchTopRow.values())
		{
			for (com.superhistogram.model.RightSide side : com.superhistogram.model.RightSide.values())
			{
				BarState s = SampleStates.alch(top, side);
				render(1026, 62, s, new Pulse());
				render(1026, 46, s, new Pulse());
				render(600, 30, s, new Pulse());
			}
		}
	}

	private static BarState alchGold()
	{
		return SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.GOLD,
			com.superhistogram.model.RightSide.PER_HOUR);
	}

	@Test
	public void castPillIsMagicBlue()
	{
		BarState s = alchGold();
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		int x = (int) (tickX(l, s, SampleStates.CAST_MID) + 16 * l.u), y = (int) (29 * l.u);
		assertTrue(opaque(img, x, y, Palette.PRAYER_MAGIC));
	}

	@Test
	public void goldHasCoin()
	{
		for (com.superhistogram.model.skill.AlchTopRow top : com.superhistogram.model.skill.AlchTopRow.values())
		{
			BarState s = SampleStates.alch(top, com.superhistogram.model.RightSide.PER_HOUR);
			BarLayout l = BarLayout.of(1026, 62);
			float u = l.u, cx = tickX(l, s, SampleStates.CAST_LATE) - 1 * u, cy = 11.5f * u;
			boolean has = hasPixel(render(1026, 62, s, null), cx - u, cx + u, cy - u, cy + u, DMG);
			assertEquals(top.toString(), top != com.superhistogram.model.skill.AlchTopRow.XP, has);
		}
	}

	@Test
	public void goldModeSkipsMagicDropXpModeKeepsIt()
	{
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage gold = render(1026, 62, alchGold(), null);
		assertFalse(hasPixel(gold, 0, l.width, 5 * l.u, 17 * l.u, MAG));
		BarState xp = SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.XP,
			com.superhistogram.model.RightSide.PER_HOUR);
		assertTrue(hasPixel(render(1026, 62, xp, null), 0, l.width, 5 * l.u, 17 * l.u, MAG));
		BarState both = SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.BOTH,
			com.superhistogram.model.RightSide.PER_HOUR);
		assertTrue(hasPixel(render(1026, 62, both, null), 0, l.width, 5 * l.u, 17 * l.u, MAG));
	}

	@Test
	public void castShowsHaCoins()
	{
		BarState s = alchGold();
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u, x = tickX(l, s, SampleStates.CAST_MID);
		assertTrue(hasPixel(img, x + 5 * u, x + 25 * u, 7 * u, 16 * u, DMG));
		assertFalse(hasPixel(img, x + 5 * u, x + 25 * u, 7 * u, 16 * u, TEXT));
	}

	@Test
	public void unknownHaShowsQuestionMark()
	{
		BarState s = alchGold();
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u, x = tickX(l, s, SampleStates.CAST_LAST);
		assertTrue(hasPixel(img, x + 5 * u, x + 25 * u, 7 * u, 16 * u, Palette.DIM));
		assertFalse(hasPixel(img, x + 5 * u, x + 25 * u, 7 * u, 16 * u, DMG));
	}

	@Test
	public void lostTicksLabel()
	{
		BarState s = alchGold();
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x0 = tickX(l, s, SampleStates.CAST_LATE - 2), x1 = tickX(l, s, SampleStates.CAST_LATE);
		assertTrue(hasPixel(render(1026, 62, s, null), x0, x1, 34 * u, 42 * u, Palette.LOST));
		BarState off = SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.GOLD,
			com.superhistogram.model.RightSide.PER_HOUR, false, false);
		assertFalse(hasPixel(render(1026, 62, off, null), x0, x1, 34 * u, 42 * u, Palette.LOST));
	}

	@Test
	public void nextCastCaretAhead()
	{
		BarState s = alchGold();
		BarLayout l = BarLayout.of(1026, 62);
		float x = tickX(l, s, NOW + 3), u = l.u;
		assertTrue(hasPixelAlpha(render(1026, 62, s, null), x - 3 * u, x + 3 * u, 57 * u, 60 * u));
		BarLayout l42 = BarLayout.of(1026, 42);
		float x42 = tickX(l42, s, NOW + 3);
		// Two rows: no caret (the ruler lane there is the same with or without the alch tracker).
		BufferedImage with42 = render(1026, 42, s, null), without42 = render(1026, 42, SampleStates.withoutAlch(s), null);
		for (int y = (int) (40 * l42.u); y < 42; y++)
		{
			for (int px = (int) (x42 - 3 * l42.u); px <= (int) (x42 + 3 * l42.u); px++)
			{
				assertEquals(without42.getRGB(px, y), with42.getRGB(px, y));
			}
		}
	}

	@Test
	public void alchFletchShowsDots()
	{
		BarState s = SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.GOLD,
			com.superhistogram.model.RightSide.PER_HOUR, true);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u, x = tickX(l, s, NOW - 4);
		assertTrue(hasPixel(img, x - 1.4f * u - 2 * u, x - 1.4f * u + 2 * u, 10 * u, 14 * u, FLE));
		assertFalse(hasPixel(img, x + 4 * u, x + 20 * u, 5 * u, 17 * u, FLE));
	}

	@Test
	public void alchRightDoesNotOverlapLoginTime()
	{
		for (int w : new int[]{1026, 720})
		{
			for (com.superhistogram.model.skill.AlchTopRow top : com.superhistogram.model.skill.AlchTopRow.values())
			{
				for (com.superhistogram.model.RightSide side : com.superhistogram.model.RightSide.values())
				{
					BarState s = SampleStates.alch(top, side);
					BarLayout l = BarLayout.of(w, 62);
					BufferedImage img = render(w, 62, s, null);
					BufferedImage ref = render(w, 62, SampleStates.withoutAlch(s), null);
					Graphics2D g = img.createGraphics();
					int tw = g.getFontMetrics(Fonts.semiBold(11f * l.u)).stringWidth(s.clock.format(s.nowMs));
					g.dispose();
					int x1 = (int) (l.width - 26f * l.u), x0 = x1 - tw - 2;
					String id = "w=" + w + " " + top + " " + side;
					for (int y = (int) (14f * l.u); y < (int) (50f * l.u); y++)
					{
						for (int x = x0; x < l.width; x++)
						{
							assertEquals(id + " paint over login time at " + x + "," + y, ref.getRGB(x, y),
								img.getRGB(x, y));
						}
					}
					boolean painted = false;
					for (int y = 0; y < 62 && !painted; y++)
					{
						for (int x = (int) CombatPainter.rightX(l); x < x0 && !painted; x++)
						{
							painted = img.getRGB(x, y) != ref.getRGB(x, y);
						}
					}
					assertTrue(id + " readout not painted", painted);
				}
			}
		}
	}

	// ---- gathering (gathering.html) ----

	@Test
	public void gatheringRendersAtThreeSizes()
	{
		java.util.List<BarState> all = new java.util.ArrayList<>();
		for (SampleStates.GatherPhase p : SampleStates.GatherPhase.values())
		{
			all.add(SampleStates.gathering(p));
		}
		all.add(SampleStates.firemaking());
		all.add(SampleStates.gatheringFall());
		all.add(SampleStates.gatheringSkill("Mining", 440));
		all.add(SampleStates.gatheringSkill("Fishing", 335));
		for (BarState s : all)
		{
			render(1026, 62, s, new Pulse());
			render(1026, 46, s, new Pulse());
			render(600, 30, s, new Pulse());
		}
	}

	@Test
	public void pillHasWoodcuttingColour()
	{
		BarState s = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		assertEquals(Palette.WC_PILL.getRGB(), img.getRGB((int) (tickX(l, s, NOW - 12) + 16 * l.u), (int) (29 * l.u)));
	}

	@Test
	public void rollNotchesDarkenPill()
	{
		BarState s = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING);
		BarLayout l = BarLayout.of(1026, 62);
		int px = (int) tickX(l, s, NOW - 12);
		int c = render(1026, 62, s, null).getRGB(px, (int) (l.laneY(Lane.DOING) + l.laneH(Lane.DOING) / 2f));
		java.awt.Color wc = Palette.WC_PILL;
		assertTrue(((c >> 16) & 255) <= wc.getRed() - 20);
		assertTrue(((c >> 8) & 255) <= wc.getGreen() - 20);
		assertTrue((c & 255) <= wc.getBlue() - 20);
		// One row has no notches.
		BarLayout l1 = BarLayout.of(600, 30);
		BufferedImage one = render(600, 30, s, null);
		int y = (int) (TimelinePainter.pillY(l1) + TimelinePainter.pillH(l1) / 2f);
		int x = (int) tickX(l1, s, NOW - 12);
		assertEquals(Palette.WC_PILL.getRGB(), one.getRGB(x, y));
	}

	@Test
	public void stumpMarksFall()
	{
		BarState s = SampleStates.gatheringFall();
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x = tickX(l, s, NOW - 8);
		assertTrue(hasPixel(render(1026, 62, s, null), x + 2 * u, x + 10 * u, 40 * u, 50 * u, Palette.TEXT));
		// No stump before the tree falls, and not on the off-screen fall of the long sample.
		BarState early = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING);
		float ex = tickX(l, early, NOW - 8);
		assertFalse(hasPixel(render(1026, 62, early, null), ex + 2 * u, ex + 10 * u, 40 * u, 50 * u, Palette.TEXT));
		// A fall on the newest tick draws one too.
		BarState felled = SampleStates.gathering(SampleStates.GatherPhase.FELLED);
		float fx = tickX(l, felled, NOW);
		assertTrue(hasPixel(render(1026, 62, felled, null), fx + 2 * u, fx + 10 * u, 40 * u, 50 * u, Palette.TEXT));
	}

	@Test
	public void reclickLabelOnlyWhenEnabled()
	{
		BarState on = SampleStates.gatheringFall();
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x0 = tickX(l, on, NOW - 8) + 13 * u, x1 = tickX(l, on, NOW - 6);
		assertTrue(hasPixel(render(1026, 62, on, null), x0, x1, 34 * u, 42 * u, Palette.LOST));
		BarState off = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING, true, false);
		assertFalse(hasPixel(render(1026, 62, off, null), x0, x1, 34 * u, 42 * u, Palette.LOST));
	}

	@Test
	public void reclickLabelOffWhenWastedTicksOff()
	{
		BarState s = SampleStates.gatheringFall();
		BarState off = BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub).mode(s.mode).nowMs(s.nowMs)
			.gather(s.gather).wastedTicks(false).build();
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x0 = tickX(l, s, NOW - 8) + 13 * u, x1 = tickX(l, s, NOW - 6);
		assertFalse(hasPixel(render(1026, 62, off, null), x0, x1, 34 * u, 42 * u, Palette.LOST));
	}

	@Test
	public void logBeforeXp()
	{
		BarState s = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING);
		BarLayout l = BarLayout.of(1026, 62);
		float u = l.u, x = tickX(l, s, NOW - 10);
		assertTrue(hasPixel(render(1026, 62, s, null), x - 16 * u, x - 5 * u, 6 * u, 16 * u, Palette.LOG));
		BarLayout l1 = BarLayout.of(600, 30);
		assertFalse(hasPixel(render(600, 30, s, null), 0, 600, 0, 30, Palette.LOG));
		assertTrue(l1.rows == 1);
	}

	@Test
	public void noFinishFlagForEstimate()
	{
		BarState s = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING);
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		float u = l.u;
		for (int y = (int) (42 * u); y < (int) (60 * u); y++)
		{
			for (int x = (int) tickX(l, s, NOW + 1); x < (int) tickX(l, s, NOW + 4); x++)
			{
				int argb = img.getRGB(x, y);
				boolean white = (argb >>> 24) >= 200 && ((argb >> 16) & 255) >= 240 && ((argb >> 8) & 255) >= 240
					&& (argb & 255) >= 240;
				assertFalse("white at " + x + "," + y, white);
			}
		}
	}

	@Test
	public void gatheringRightDoesNotOverlapLoginTime()
	{
		for (int w : new int[]{1026, 720})
		{
			java.util.List<BarState> all = new java.util.ArrayList<>();
			for (SampleStates.GatherPhase p : SampleStates.GatherPhase.values())
			{
				all.add(SampleStates.gathering(p));
			}
			all.add(SampleStates.firemaking());
			for (BarState s : all)
			{
				BarLayout l = BarLayout.of(w, 62);
				BufferedImage img = render(w, 62, s, null);
				BufferedImage ref = render(w, 62, SampleStates.withoutGather(s), null);
				Graphics2D g = img.createGraphics();
				int tw = g.getFontMetrics(Fonts.semiBold(11f * l.u)).stringWidth(s.clock.format(s.nowMs));
				g.dispose();
				int x1 = (int) (l.width - 26f * l.u), x0 = x1 - tw - 2;
				for (int y = (int) (14f * l.u); y < (int) (50f * l.u); y++)
				{
					for (int x = x0; x < l.width; x++)
					{
						assertEquals("w=" + w + " paint over login time at " + x + "," + y,
							ref.getRGB(x, y), img.getRGB(x, y));
					}
				}
				boolean painted = false;
				for (int y = 0; y < 62 && !painted; y++)
				{
					for (int x = (int) CombatPainter.rightX(l); x < x0 && !painted; x++)
					{
						painted = img.getRGB(x, y) != ref.getRGB(x, y);
					}
				}
				assertTrue("w=" + w + " readout not painted", painted);
			}
		}
	}

	private static BarState withSub(BarState s, String name, String sub)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(name).modeSub(sub)
			.showLoginTime(s.showLoginTime).mode(s.mode).nowMs(s.nowMs)
			.gather(s.gather).wastedTicks(s.wastedTicks).timeToFinish(s.timeToFinish).build();
	}

	@Test
	public void longModeSubStaysInLeftReadout()
	{
		BarState base = SampleStates.gathering(SampleStates.GatherPhase.CHOPPING);
		String longName = "Woodcutting and other very long names";
		String longSub = "auto \u00b7 An extremely long fishing spot name that cannot fit";
		for (int w : new int[]{1026, 720})
		{
			BarLayout l = BarLayout.of(w, 62);
			BufferedImage lng = render(w, 62, withSub(base, longName, longSub), null);
			BufferedImage shortImg = render(w, 62, withSub(base, "W", "a"), null);
			int x0 = (int) l.timelineX - (int) (1f * l.u);
			for (int y = 0; y < 62; y++)
			{
				for (int x = x0; x < (int) l.timelineX + (int) (4f * l.u); x++)
				{
					assertEquals("w=" + w + " text reaches the timeline at " + x + "," + y,
						shortImg.getRGB(x, y), lng.getRGB(x, y));
				}
			}
			// Nothing below the sub line moves or gets overpainted.
			int gaugeTop = (int) (l.height / 2f + 18f * l.u);
			for (int y = gaugeTop; y < 62; y++)
			{
				for (int x = (int) (22f * l.u); x < (int) l.timelineX; x++)
				{
					assertEquals("w=" + w + " below-sub area changed at " + x + "," + y,
						shortImg.getRGB(x, y), lng.getRGB(x, y));
				}
			}
		}
	}

	@Test
	public void gatherPillNeverLeftOfTimeline()
	{
		BarState s = SampleStates.gatheringFall();
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = render(1026, 62, s, null);
		int y = (int) (TimelinePainter.pillY(l) + TimelinePainter.pillH(l) / 2f);
		for (int x = (int) (l.timelineX - 6f * l.u); x < (int) l.timelineX; x++)
		{
			assertFalse("pill at " + x, Palette.WC_PILL.getRGB() == img.getRGB(x, y));
		}
	}

	// ---- agility ----

	private static boolean near(int argb, java.awt.Color c, int tol)
	{
		return (argb >>> 24) >= 250 && Math.abs(((argb >> 16) & 255) - c.getRed()) <= tol
			&& Math.abs(((argb >> 8) & 255) - c.getGreen()) <= tol && Math.abs((argb & 255) - c.getBlue()) <= tol;
	}

	private static boolean hasNear(BufferedImage img, float x0, float x1, float y0, float y1, java.awt.Color c)
	{
		for (int y = Math.max(0, (int) Math.floor(y0)); y < Math.min(img.getHeight(), Math.ceil(y1)); y++)
		{
			for (int x = Math.max(0, (int) Math.floor(x0)); x < Math.min(img.getWidth(), Math.ceil(x1)); x++)
			{
				if (near(img.getRGB(x, y), c, 8))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static final int S3 = SampleStates.lapThreeStart(SampleStates.AgilityPhase.MID_LAP);
	/** Agility no longer zooms its ticks, so a whole lap (34 ticks back) only fits on a wide bar. */
	private static final int WIDE = 2000;

	@Test
	public void agilityRendersAtThreeSizes()
	{
		for (SampleStates.AgilityPhase p : SampleStates.AgilityPhase.values())
		{
			BarState s = SampleStates.agility(p);
			render(1026, 62, s, new Pulse());
			render(1026, 46, s, new Pulse());
			render(600, 30, s, new Pulse());
			render(1026, 22, s, null);
		}
	}

	@Test
	public void everyModeKeepsTickWidth()
	{
		BarLayout l = BarLayout.of(1026, 62);
		assertSame(l, BarPainter.timelineLayout(l, SampleStates.cooking(ProdPhase.RUNNING)));
		assertSame(l, BarPainter.timelineLayout(l, null));
		assertSame(l, BarPainter.timelineLayout(l, SampleStates.agility(SampleStates.AgilityPhase.MID_LAP)));
	}

	@Test
	public void obstaclePillIsAgilityBlue()
	{
		BarState s = SampleStates.agility(SampleStates.AgilityPhase.MID_LAP);
		BarLayout l = BarLayout.of(WIDE, 62);
		float tw = BarPainter.timelineLayout(l, s).tickWidth;
		BufferedImage img = render(WIDE, 62, s, null);
		assertTrue(opaque(img, (int) (tickX(l, s, S3 + 3) + tw / 2), (int) (l.laneY(Lane.DOING) + l.laneH(Lane.DOING) / 2f), Palette.skill("Agility")));
	}

	@Test
	public void obstacleNumberDrawn()
	{
		BarState s = SampleStates.agility(SampleStates.AgilityPhase.MID_LAP);
		BarLayout l = BarLayout.of(WIDE, 62);
		BufferedImage img = render(WIDE, 62, s, null);
		float x = tickX(l, s, S3), u = l.u;
		boolean dark = false;
		for (int y = (int) l.laneY(Lane.DOING); y < (int) (l.laneY(Lane.DOING) + l.laneH(Lane.DOING)) && !dark; y++)
		{
			for (int px = (int) (x + 7 * u); px <= (int) (x + 13 * u) && !dark; px++)
			{
				int argb = img.getRGB(px, y);
				dark = ((argb >> 16) & 255) < 60 && ((argb >> 8) & 255) < 60 && (argb & 255) < 60;
			}
		}
		assertTrue("no dark number pixel", dark);
	}

	@Test
	public void lapStartLine()
	{
		BarState s = SampleStates.agility(SampleStates.AgilityPhase.MID_LAP);
		BarLayout l = BarLayout.of(WIDE, 62);
		BufferedImage img = render(WIDE, 62, s, null);
		BufferedImage ref = render(WIDE, 62, SampleStates.withoutAgility(s), null);
		int lx = (int) Math.floor(tickX(l, s, S3) - 2 * l.u), y = (int) (12 * l.u);
		boolean lit = false;
		for (int x = lx - 1; x <= lx + 1; x++)
		{
			int a = img.getRGB(x, y), b = ref.getRGB(x, y);
			lit |= ((a >> 16) & 255) >= ((b >> 16) & 255) + 20 && ((a >> 8) & 255) >= ((b >> 8) & 255) + 20
				&& (a & 255) >= (b & 255) + 20;
		}
		assertTrue("lap line", lit);
		float x0 = tickX(l, s, S3);
		assertTrue("lap label", hasNear(img, x0 + 2 * l.u, x0 + 30 * l.u, 51 * l.u, 60 * l.u, Palette.DIM));
	}

	@Test
	public void lostLabelOnlyWhenEnabled()
	{
		BarLayout l = BarLayout.of(1026, 62);
		BarState on = SampleStates.agility(SampleStates.AgilityPhase.MID_LAP);
		BarState off = SampleStates.agility(SampleStates.AgilityPhase.MID_LAP, false);
		float x0 = tickX(l, on, S3 + 31), x1 = tickX(l, on, SampleStates.NOW_TICK);
		assertTrue(hasNear(render(1026, 62, on, null), x0, x1, 34 * l.u, 42 * l.u, Palette.LOST));
		assertFalse(hasNear(render(1026, 62, off, null), x0, x1, 34 * l.u, 42 * l.u, Palette.LOST));
	}

	@Test
	public void markOfGraceIsMint()
	{
		BarState s = SampleStates.agility(SampleStates.AgilityPhase.MID_LAP);
		BarLayout l = BarLayout.of(WIDE, 62);
		float u = l.u, cx = tickX(l, s, S3 + 16) - 9 * u, cy = TimelinePainter.xpY(l) - 3.5f * u;
		assertTrue(hasNear(render(WIDE, 62, s, null), cx - 3 * u, cx + 3 * u, cy - 3 * u, cy + 3 * u, Palette.MINT));
	}

	@Test
	public void agilityRightDoesNotOverlapLoginTime()
	{
		for (int w : new int[]{1026, 720})
		{
			for (SampleStates.AgilityPhase p : SampleStates.AgilityPhase.values())
			{
				BarState s = SampleStates.agility(p);
				BarLayout l = BarLayout.of(w, 62);
				BufferedImage img = render(w, 62, s, null);
				BufferedImage ref = render(w, 62, SampleStates.withoutAgility(s), null);
				Graphics2D g = img.createGraphics();
				int tw = g.getFontMetrics(Fonts.semiBold(11f * l.u)).stringWidth(s.clock.format(s.nowMs));
				g.dispose();
				int x1 = (int) (l.width - 26f * l.u), x0 = x1 - tw - 2;
				for (int y = (int) (14f * l.u); y < (int) (50f * l.u); y++)
				{
					for (int x = x0; x < l.width; x++)
					{
						assertEquals("w=" + w + " " + p + " paint over login time at " + x + "," + y,
							ref.getRGB(x, y), img.getRGB(x, y));
					}
				}
				boolean painted = false;
				for (int y = 0; y < 62 && !painted; y++)
				{
					for (int x = (int) CombatPainter.rightX(l); x < x0 && !painted; x++)
					{
						painted = img.getRGB(x, y) != ref.getRGB(x, y);
					}
				}
				assertTrue("w=" + w + " " + p + " readout not painted", painted);
			}
		}
	}

	@Test
	public void agilitySmallLineFitsAtDefaultSize()
	{
		BarLayout l = BarLayout.of(1026, 62);
		for (SampleStates.AgilityPhase p : SampleStates.AgilityPhase.values())
		{
			BarState s = SampleStates.agility(p);
			String[] lines = com.superhistogram.ui.modes.AgilityView.rightLines(s);
			Graphics2D g = render(1026, 62, s, null).createGraphics();
			java.awt.Font f = Fonts.semiBold(10.5f * l.u);
			float room = ReadoutPainter.rightLimit(g, l, s) - (l.timelineX + l.timelineW + com.superhistogram.ui.modes.AgilityView.RIGHT_INSET * l.u);
			assertTrue(f.canDisplay('\u2212'));
			assertEquals(p + " " + lines[1], lines[1], ReadoutPainter.pickSmall(g, f, room, lines[1]));
			assertTrue(p + " " + lines[1], f.getStringBounds(lines[1], g.getFontRenderContext()).getWidth() <= room);
			g.dispose();
		}
	}
}
