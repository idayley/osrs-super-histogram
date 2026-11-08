package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpTracker;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
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
		// The brief names (5,31), but that point lies inside the h/2 rounded end;
		// (5,5) is the corner pixel that the rounding cuts away.
		assertEquals(0, alpha(img, 5, 5));
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
	public void noActionsRowAt46()
	{
		assertTrue(hasWhiteInActionsBand(render(1026, 62, SampleStates.singleClick(), null), 62));
		assertFalse(hasWhiteInActionsBand(render(1026, 46, SampleStates.singleClick(), null), 46));
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
		return TimelinePainter.x(l, s, tick);
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
		BarLayout l46 = BarLayout.of(1026, 46);
		float x46 = tickX(l46, s, b);
		assertFalse(hasPixel(render(1026, 46, s, null), x46 - 4 * l46.u, x46 + 4 * l46.u, 55 * l46.u, 60 * l46.u,
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
		float x = tickX(l, on, t);
		assertTrue(hasPixel(render(1026, 62, on, null), x - 3 * u, x + 3 * u, 38 * u, 51 * u, Palette.WHITE));
		assertFalse(hasPixel(render(1026, 62, off, null), x - 3 * u, x + 3 * u, 38 * u, 51 * u, Palette.WHITE));
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
				if (ruler || caretBox)
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
}
