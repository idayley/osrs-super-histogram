package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
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
}
