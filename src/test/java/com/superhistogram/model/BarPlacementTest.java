package com.superhistogram.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BarPlacementTest
{
	@Test
	public void replaceChatAndFloatingAreNotLifted()
	{
		assertEquals(0, BarPlacement.lift(BarPosition.REPLACE_CHAT, 142));
		assertEquals(0, BarPlacement.lift(BarPosition.FLOATING, 142));
	}

	@Test
	public void aboveChatLiftsByChatboxHeight()
	{
		assertEquals(142, BarPlacement.lift(BarPosition.ABOVE_CHAT, 142));
		assertEquals(142 + 62, BarPlacement.blockHeight(BarPosition.ABOVE_CHAT, 62, 142));
	}

	@Test
	public void unknownChatboxHeightFallsBackTo165()
	{
		assertEquals(165, BarPlacement.lift(BarPosition.ABOVE_CHAT, 0));
		assertEquals(165, BarPlacement.lift(BarPosition.ABOVE_CHAT, -3));
	}

	@Test
	public void nullPositionIsReplaceChat()
	{
		assertEquals(62, BarPlacement.blockHeight(null, 62, 142));
	}

	@Test
	public void sizeHeights()
	{
		assertEquals(40, BarSize.SMALL.height());
		assertEquals(62, BarSize.NORMAL.height());
		assertEquals(80, BarSize.LARGE.height());
		assertEquals(1026, BarSize.WIDTH);
	}
}
