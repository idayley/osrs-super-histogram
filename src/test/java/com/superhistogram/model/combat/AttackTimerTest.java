package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttackTimerTest
{
	@Test
	public void attackSetsReadyByWeaponSpeed()
	{
		AttackTimer t = new AttackTimer();
		assertEquals(0, t.onAttack(100, 4));
		assertEquals(104, t.readyTick());
		assertEquals(100, t.lastAttackTick());
	}

	@Test
	public void lateAttackReportsLostTicks()
	{
		AttackTimer t = new AttackTimer();
		t.onAttack(100, 4);
		assertEquals(1, t.onAttack(105, 4));
	}

	@Test
	public void pauseIsNotLate()
	{
		AttackTimer t = new AttackTimer();
		t.onAttack(100, 4);
		assertEquals(0, t.onAttack(130, 4));
	}

	@Test
	public void eatPushesReady()
	{
		AttackTimer t = new AttackTimer();
		t.onAttack(100, 4);
		assertEquals(3, t.onEat(101, FoodKind.NORMAL));
		assertEquals(107, t.readyTick());
	}

	@Test
	public void secondFoodSameTickBecomesCombo()
	{
		AttackTimer t = new AttackTimer();
		t.onAttack(100, 4);
		t.onEat(101, FoodKind.NORMAL);
		assertEquals(5, t.onEat(101, FoodKind.KARAMBWAN));
		assertEquals(109, t.readyTick());
	}

	@Test
	public void twoSharksSameTickCountOnce()
	{
		AttackTimer t = new AttackTimer();
		t.onAttack(100, 4);
		t.onEat(101, FoodKind.NORMAL);
		assertEquals(3, t.onEat(101, FoodKind.NORMAL));
		assertEquals(107, t.readyTick());
	}

	@Test
	public void eatBeforeFirstAttackIsFree()
	{
		AttackTimer t = new AttackTimer();
		assertEquals(0, t.onEat(5, FoodKind.NORMAL));
	}

	@Test
	public void blockAnimationBeforeReadyRejected()
	{
		AttackTimer t = new AttackTimer();
		t.onAttack(100, 4);
		assertFalse(t.canAttack(102));
		assertTrue(t.canAttack(103));
	}

	@Test
	public void speedIsPerAttack()
	{
		AttackTimer t = new AttackTimer();
		t.onAttack(100, 4);
		t.onAttack(104, 6);
		assertEquals(110, t.readyTick());
	}
}
