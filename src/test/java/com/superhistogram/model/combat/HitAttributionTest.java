package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HitAttributionTest
{
	@Test
	public void singleAttackerAttributed()
	{
		HitAttribution a = new HitAttribution();
		a.onSwing(7, 100);
		assertEquals(7, a.attribute(100));
		assertEquals(7, a.attribute(105));
		assertEquals(-1, a.attribute(106));
	}

	@Test
	public void ambiguousAttributesNone()
	{
		HitAttribution a = new HitAttribution();
		a.onSwing(7, 100);
		a.onSwing(9, 100);
		assertEquals(-1, a.attribute(100));
	}

	@Test
	public void twoAttackersInWindowAttributesNone()
	{
		HitAttribution a = new HitAttribution();
		a.onSwing(7, 98);
		a.onSwing(9, 100);
		assertEquals(-1, a.attribute(100));
	}

	@Test
	public void oldSwingDoesNotCount()
	{
		HitAttribution a = new HitAttribution();
		a.onSwing(7, 90);
		a.onSwing(9, 100);
		assertEquals(9, a.attribute(100));
	}

	@Test
	public void forgetRemovesNpc()
	{
		HitAttribution a = new HitAttribution();
		a.onSwing(7, 100);
		a.forget(7);
		assertEquals(-1, a.attribute(100));
	}

	@Test
	public void resetClearsAll()
	{
		HitAttribution a = new HitAttribution();
		a.onSwing(7, 100);
		a.reset();
		assertEquals(-1, a.attribute(100));
	}
}
