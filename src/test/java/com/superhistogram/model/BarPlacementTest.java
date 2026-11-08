package com.superhistogram.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BarPlacementTest
{
	// Fixed-mode chat text area: 519 x 142 at (0, 338) on a 765 x 503 canvas.
	private static final BarPlacement.Rect CHAT = new BarPlacement.Rect(0, 338, 519, 142);

	@Test
	public void replaceChatSitsAtTheBottomOfTheChatAreaAtItsWidth()
	{
		BarPlacement.Rect r = BarPlacement.place(BarPosition.REPLACE_CHAT, CHAT, 62);
		assertEquals(new BarPlacement.Rect(0, 338 + 142 - 62, 519, 62), r);
	}

	@Test
	public void aboveChatSitsJustAboveTheChatArea()
	{
		BarPlacement.Rect r = BarPlacement.place(BarPosition.ABOVE_CHAT, CHAT, 62);
		assertEquals(new BarPlacement.Rect(0, 338 - 62, 519, 62), r);
	}

	@Test
	public void floatingIsLeftToRuneLite()
	{
		assertNull(BarPlacement.place(BarPosition.FLOATING, CHAT, 62));
	}

	@Test
	public void heightFollowsTheSizePreset()
	{
		assertEquals(40, BarPlacement.place(BarPosition.REPLACE_CHAT, CHAT, BarSize.SMALL.height()).h);
		assertEquals(338 + 142 - 80, BarPlacement.place(BarPosition.REPLACE_CHAT, CHAT, BarSize.LARGE.height()).y);
	}

	@Test
	public void wideResizableChatIsMatched()
	{
		BarPlacement.Rect wide = new BarPlacement.Rect(0, 600, 640, 120);
		assertEquals(640, BarPlacement.place(BarPosition.REPLACE_CHAT, wide, 62).w);
	}

	@Test
	public void unknownChatFallsBackToTheClassicChatSpotOnTheCanvas()
	{
		BarPlacement.Rect f = BarPlacement.fallbackChat(503);
		assertEquals(new BarPlacement.Rect(0, 503 - 165, 519, 142), f);
	}

	@Test
	public void defaultWidthIsTheChatWidth()
	{
		assertEquals(519, BarSize.WIDTH);
	}
}
