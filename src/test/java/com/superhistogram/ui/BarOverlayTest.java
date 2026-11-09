package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarPlacement;
import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarSize;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;

public class BarOverlayTest
{
	private static final BarPlacement.Rect CHAT = new BarPlacement.Rect(0, 338, 519, 142);

	private static BarOverlay overlay()
	{
		return new BarOverlay(() -> null, null);
	}

	private static BufferedImage canvas()
	{
		return new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB);
	}

	private static Dimension render(BarOverlay o, BufferedImage img)
	{
		Graphics2D g = img.createGraphics();
		try
		{
			return o.render(g);
		}
		finally
		{
			g.dispose();
		}
	}

	private static boolean painted(BufferedImage img, int x, int y)
	{
		return (img.getRGB(x, y) >>> 24) != 0;
	}

	@Test
	public void drawsOverWidgetsSoItCoversTheChat()
	{
		assertEquals(OverlayLayer.ABOVE_WIDGETS, overlay().getLayer());
	}

	@Test
	public void replaceChatPaintsOnlyInsideTheChatAreaBottom()
	{
		BarOverlay o = overlay();
		o.setChatArea(() -> CHAT);
		o.applyPosition(BarPosition.REPLACE_CHAT);
		BufferedImage img = canvas();
		render(o, img);
		assertTrue(painted(img, 259, 338 + 142 - 31));   // middle of the bar
		assertFalse(painted(img, 259, 338 + 142 - 70));  // above the bar: chat text area left alone
		assertFalse(painted(img, 600, 338 + 142 - 31));  // right of the chat: inventory side untouched
		assertFalse(o.isMovable());
		assertEquals(OverlayPosition.DYNAMIC, o.getPosition());
	}

	@Test
	public void aboveChatPaintsJustAboveTheChatArea()
	{
		BarOverlay o = overlay();
		o.setChatArea(() -> CHAT);
		o.applyPosition(BarPosition.ABOVE_CHAT);
		BufferedImage img = canvas();
		render(o, img);
		assertTrue(painted(img, 259, 338 - 31));
		assertFalse(painted(img, 259, 338 + 20));
	}

	@Test
	public void unknownChatFallsBackToAPlainBar()
	{
		BarOverlay o = overlay();
		assertEquals(new Dimension(519, 62), render(o, canvas()));
	}

	@Test
	public void floatingIsMovableAndKeepsItsLocation()
	{
		BarOverlay o = overlay();
		o.setChatArea(() -> CHAT);
		o.applyPosition(BarPosition.FLOATING);
		assertEquals(OverlayPosition.DYNAMIC, o.getPosition());
		assertTrue(o.isMovable());
		assertEquals(new Dimension(519, 62), render(o, canvas()));
		o.setPreferredLocation(new Point(300, 200));
		o.applyPosition(BarPosition.REPLACE_CHAT);
		assertNull(o.getPreferredLocation());
		assertFalse(o.isMovable());
		o.applyPosition(BarPosition.FLOATING);
		assertEquals(new Point(300, 200), o.getPreferredLocation());
	}

	@Test
	public void floatingStartsJustAboveTheChatUntilMoved()
	{
		BarOverlay o = overlay();
		o.setChatArea(() -> CHAT);
		render(o, canvas());
		assertEquals(new Point(0, 338 - 62), o.getPreferredLocation());
		o.setPreferredLocation(new Point(120, 40));
		render(o, canvas());
		assertEquals(new Point(120, 40), o.getPreferredLocation());
	}

	@Test
	public void sizePresetSetsTheHeight()
	{
		BarOverlay o = overlay();
		o.applyPosition(BarPosition.REPLACE_CHAT);
		o.setChatArea(() -> CHAT);
		o.applySize(BarSize.LARGE.height());
		assertEquals(new Dimension(519, 80), o.getPreferredSize());
		BufferedImage img = canvas();
		render(o, img);
		assertTrue(painted(img, 259, 338 + 142 - 75));
		o.applySize(BarSize.SMALL.height());
		assertEquals(new Dimension(519, 40), o.getPreferredSize());
		// Any height in between, not only the presets.
		o.applySize(71);
		assertEquals(new Dimension(519, 71), o.getPreferredSize());
	}

	@Test
	public void applyDefaultSizeOnlyWhenNoSavedSize()
	{
		BarOverlay o = overlay();
		o.setPreferredSize(new Dimension(700, 50));
		o.applySizeIfUnset(BarSize.NORMAL.height());
		assertEquals(new Dimension(700, 50), o.getPreferredSize());
		BarOverlay fresh = overlay();
		fresh.applySizeIfUnset(BarSize.NORMAL.height());
		assertEquals(new Dimension(519, 62), fresh.getPreferredSize());
	}
}
