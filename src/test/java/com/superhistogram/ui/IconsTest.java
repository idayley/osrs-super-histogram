package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.ui.Icons.Icon;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import org.junit.Test;

public class IconsTest
{
	private static final double EPS = 1e-3;

	@Test
	public void everyIconHasBoundsInsideItsBox()
	{
		for (Icon k : Icon.values())
		{
			for (double u : new double[]{1, 2, 0.5})
			{
				double cx = 100, base = 50;
				Shape s = Icons.icon(k, cx, base, u);
				assertNotNull(k.name(), s);
				Rectangle2D b = s.getBounds2D();
				assertFalse(k + " empty", b.isEmpty());
				assertTrue(k + " left", b.getMinX() >= cx - 6 * u - EPS);
				assertTrue(k + " right", b.getMaxX() <= cx + 6 * u + EPS);
				assertTrue(k + " top", b.getMinY() >= base - 12 * u - EPS);
				assertTrue(k + " bottom", b.getMaxY() <= base + EPS);
			}
		}
	}

	@Test
	public void everyIconSitsOnTheBaseline()
	{
		for (Icon k : Icon.values())
		{
			if (k == Icon.COIN_RING || k == Icon.STUMP_TOP)
			{
				continue; // inner parts drawn over COIN and STUMP, which carry the baseline
			}
			Rectangle2D b = Icons.icon(k, 0, 0, 1).getBounds2D();
			assertEquals(k + " baseline", 0, b.getMaxY(), 0.05);
		}
	}

	@Test
	public void iconsScaleLinearlyWithU()
	{
		for (Icon k : Icon.values())
		{
			Rectangle2D one = Icons.icon(k, 0, 0, 1).getBounds2D();
			Rectangle2D two = Icons.icon(k, 0, 0, 2).getBounds2D();
			assertEquals(k + " x", one.getMinX() * 2, two.getMinX(), EPS);
			assertEquals(k + " y", one.getMinY() * 2, two.getMinY(), EPS);
			assertEquals(k + " w", one.getWidth() * 2, two.getWidth(), EPS);
			assertEquals(k + " h", one.getHeight() * 2, two.getHeight(), EPS);
		}
	}

	@Test
	public void iconsFollowCentreAndBaseline()
	{
		for (Icon k : Icon.values())
		{
			Rectangle2D a = Icons.icon(k, 0, 0, 1).getBounds2D();
			Rectangle2D b = Icons.icon(k, 30, 20, 1).getBounds2D();
			assertEquals(k + " dx", a.getMinX() + 30, b.getMinX(), EPS);
			assertEquals(k + " dy", a.getMinY() + 20, b.getMinY(), EPS);
		}
	}
}
