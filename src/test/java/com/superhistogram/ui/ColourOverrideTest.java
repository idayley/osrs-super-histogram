package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class ColourOverrideTest
{
	private static final Color RED = new Color(255, 0, 0), BLUE = new Color(0, 0, 255);
	private static final Colours OVERRIDE = new Colours(new Color(1, 2, 3, 255), RED, BLUE, new Color(0, 255, 0));

	private static BufferedImage render(BarState s, Colours c)
	{
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage img = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			BarPainter p = new BarPainter(null);
			if (c != null)
			{
				p.setColours(c);
			}
			p.paint(g, l, s, SampleStates.NOW_MS);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}

	private static boolean has(BufferedImage img, Color c)
	{
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				int a = img.getRGB(x, y);
				if (a >>> 24 == 255 && (a & 0xffffff) == (c.getRGB() & 0xffffff))
				{
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void backgroundOverrideChangesTheGroundPixel()
	{
		assertEquals(Palette.GROUND.getRGB(), render(null, null).getRGB(513, 31));
		assertEquals(new Color(1, 2, 3, 255).getRGB(), render(null, OVERRIDE).getRGB(513, 31));
	}

	@Test
	public void damageOverrideChangesAlchCoins()
	{
		assertTrue(has(render(SampleStates.alchText(200L), null), Palette.DAMAGE));
		BufferedImage o = render(SampleStates.alchText(200L), OVERRIDE);
		assertTrue(has(o, RED));
		assertTrue(!has(o, Palette.DAMAGE));
	}

	@Test
	public void mintOverrideChangesAfkReadout()
	{
		BarState afk = SampleStates.afk(SampleStates.general());
		assertTrue(has(render(afk, null), Palette.MINT));
		BufferedImage o = render(afk, OVERRIDE);
		assertTrue(has(o, BLUE));
		assertTrue(!has(o, Palette.MINT));
	}

	@Test
	public void runEnergyOverrideTintsTheRunningPill()
	{
		BufferedImage d = render(SampleStates.general(), null);
		// Only the run-energy colour changes, so any difference comes from the running pill.
		BufferedImage o = render(SampleStates.general(),
			new Colours(Palette.GROUND, Palette.DAMAGE, Palette.MINT, new Color(0, 255, 0)));
		boolean differs = false;
		for (int x = 0; x < d.getWidth() && !differs; x++)
		{
			for (int y = 0; y < d.getHeight() && !differs; y++)
			{
				differs = d.getRGB(x, y) != o.getRGB(x, y);
			}
		}
		assertTrue(differs);
	}

	@Test
	public void overrideDoesNotLeakIntoLaterPaints()
	{
		render(null, OVERRIDE);
		assertEquals(Palette.GROUND.getRGB(), render(null, null).getRGB(513, 31));
	}
}
