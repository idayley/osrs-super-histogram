package com.superhistogram.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class XpTrackerTest
{
	@Test
	public void firstStatIsBaselineNotDrop()
	{
		Timeline t = new Timeline(512);
		XpTracker x = new XpTracker(t);
		x.onStat("Fishing", 1_000_000, 5);
		assertEquals(0, t.at(5).xp.size());
		assertEquals(0, x.sessionXp());
		assertNull(x.lastDrop());
		assertEquals(1_000_000, x.currentXp("Fishing"));
		assertEquals(-1, x.currentXp("Cooking"));
	}

	@Test
	public void deltaBecomesDrop()
	{
		Timeline t = new Timeline(512);
		XpTracker x = new XpTracker(t);
		x.onStat("Fishing", 1_000_000, 5);
		x.onStat("Fishing", 1_000_050, 7);
		assertEquals(1, t.at(7).xp.size());
		assertEquals("Fishing", t.at(7).xp.get(0).skill);
		assertEquals(50, t.at(7).xp.get(0).amount);
		assertEquals(50, x.sessionXp());
		assertEquals(50, x.lastDrop().amount);
		assertEquals("Fishing", x.topSkill());
	}

	@Test
	public void hopDoesNotCreateDrops()
	{
		Timeline t = new Timeline(512);
		XpTracker x = new XpTracker(t);
		x.onStat("Fishing", 1_000_000, 5);
		x.onStat("Fishing", 1_000_000, 9);
		assertEquals(0, t.at(9).xp.size());
		assertEquals(0, x.sessionXp());
	}

	@Test
	public void loginScreenResetsBaselineNotSession()
	{
		Timeline t = new Timeline(512);
		XpTracker x = new XpTracker(t);
		x.onStat("Fishing", 1_000_000, 5);
		x.onStat("Fishing", 1_000_050, 7);
		x.onLoginScreen();
		x.onStat("Fishing", 1_000_050, 20);
		assertEquals(0, t.at(20).xp.size());
		assertEquals(50, x.sessionXp());
		x.onStat("Fishing", 1_000_080, 22);
		assertEquals(80, x.sessionXp());
	}

	@Test
	public void perHour()
	{
		Timeline t = new Timeline(512);
		XpTracker x = new XpTracker(t);
		x.onStat("Fishing", 0, 1);
		x.onStat("Fishing", 50_000, 2);
		assertEquals(50_000, x.xpPerHour("Fishing", 3_600_000));
		assertEquals(0, x.xpPerHour("Fishing", 0));
		assertEquals(0, x.xpPerHour("Cooking", 3_600_000));
	}

	@Test
	public void sessionXpPerSkill()
	{
		Timeline t = new Timeline(512);
		XpTracker x = new XpTracker(t);
		x.onStat("Magic", 1_000_000, 1);
		x.onStat("Fishing", 500, 1);
		x.onStat("Magic", 1_000_065, 2);
		x.onStat("Magic", 1_000_130, 3);
		x.onStat("Fishing", 550, 3);
		assertEquals(130, x.sessionXp("Magic"));
		assertEquals(0, x.sessionXp("Cooking"));
	}
}
