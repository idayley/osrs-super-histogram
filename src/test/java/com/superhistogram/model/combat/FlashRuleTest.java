package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;

import com.superhistogram.model.combat.FlashRule.Flash;
import org.junit.Test;

public class FlashRuleTest
{
	@Test
	public void bossSkippedByDefault()
	{
		assertEquals(Flash.NONE, FlashRule.decide(true, false, true, false, true));
	}

	@Test
	public void bossFlashesWhenAllowed()
	{
		assertEquals(Flash.HIT, FlashRule.decide(true, true, true, false, true));
	}

	@Test
	public void missFaintOrHidden()
	{
		assertEquals(Flash.MISS, FlashRule.decide(true, false, false, true, true));
		assertEquals(Flash.NONE, FlashRule.decide(true, false, false, true, false));
	}

	@Test
	public void offFlashesNothing()
	{
		assertEquals(Flash.NONE, FlashRule.decide(false, true, false, false, true));
	}
}
