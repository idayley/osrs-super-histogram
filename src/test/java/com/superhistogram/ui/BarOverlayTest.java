package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarSize;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;

public class BarOverlayTest
{
	private static BarOverlay overlay()
	{
		return new BarOverlay(() -> null, null);
	}

	private static Dimension render(BarOverlay o)
	{
		Graphics2D g = new BufferedImage(1100, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			return o.render(g);
		}
		finally
		{
			g.dispose();
		}
	}

	@Test
	public void replaceChatIsBottomLeftAtBarHeight()
	{
		BarOverlay o = overlay();
		o.applyPosition(BarPosition.REPLACE_CHAT);
		assertEquals(OverlayPosition.BOTTOM_LEFT, o.getPosition());
		assertEquals(new Dimension(1026, 62), render(o));
	}

	@Test
	public void aboveChatAddsChatboxHeightAndFallsBack()
	{
		BarOverlay o = overlay();
		o.applyPosition(BarPosition.ABOVE_CHAT);
		o.setChatHeight(() -> 142);
		assertEquals(new Dimension(1026, 62 + 142), render(o));
		o.setChatHeight(() -> 0);
		assertEquals(new Dimension(1026, 62 + 165), render(o));
	}

	@Test
	public void floatingIsMovableAndKeepsItsLocation()
	{
		BarOverlay o = overlay();
		o.applyPosition(BarPosition.FLOATING);
		assertEquals(OverlayPosition.DYNAMIC, o.getPosition());
		assertTrue(o.isMovable());
		o.setPreferredLocation(new Point(300, 200));
		o.applyPosition(BarPosition.REPLACE_CHAT);
		assertNull(o.getPreferredLocation());
		assertFalse(o.isMovable());
		o.applyPosition(BarPosition.FLOATING);
		assertEquals(new Point(300, 200), o.getPreferredLocation());
	}

	@Test
	public void sizeSetsPreferredSizeAndKeepsWidth()
	{
		BarOverlay o = overlay();
		o.applySize(BarSize.LARGE);
		assertEquals(new Dimension(1026, 80), o.getPreferredSize());
		assertEquals(new Dimension(1026, 80), render(o));
		o.applySize(BarSize.SMALL);
		assertEquals(new Dimension(1026, 40), o.getPreferredSize());
	}

	@Test
	public void applyDefaultSizeOnlyWhenNoSavedSize()
	{
		BarOverlay o = overlay();
		o.setPreferredSize(new Dimension(700, 50));
		o.applySizeIfUnset(BarSize.NORMAL);
		assertEquals(new Dimension(700, 50), o.getPreferredSize());
		BarOverlay fresh = overlay();
		fresh.applySizeIfUnset(BarSize.NORMAL);
		assertEquals(new Dimension(1026, 62), fresh.getPreferredSize());
	}
}
