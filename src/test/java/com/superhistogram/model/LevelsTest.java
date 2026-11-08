package com.superhistogram.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LevelsTest
{
	@Test
	public void level99At13034431()
	{
		assertEquals(13_034_431L, Levels.xpForLevel(99));
		assertEquals(99, Levels.levelFor(13_034_431L));
		assertEquals(98, Levels.levelFor(13_034_430L));
		assertEquals(99, Levels.levelFor(200_000_000L));
	}

	@Test
	public void level2At83()
	{
		assertEquals(83L, Levels.xpForLevel(2));
		assertEquals(2, Levels.levelFor(83));
		assertEquals(1, Levels.levelFor(82));
		assertEquals(1, Levels.levelFor(0));
		assertEquals(0L, Levels.xpForLevel(1));
	}
}
