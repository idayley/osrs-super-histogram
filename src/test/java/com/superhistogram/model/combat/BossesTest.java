package com.superhistogram.model.combat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BossesTest
{
	@Test
	public void knownBossesMatch()
	{
		assertTrue(Bosses.isBoss("Zulrah"));
		assertTrue(Bosses.isBoss("vorkath"));
		assertTrue(Bosses.isBoss("General Graardor"));
		assertTrue(Bosses.isBoss("  Kree'arra "));
		assertTrue(Bosses.isBoss("Sol Heredit"));
		assertTrue(Bosses.isBoss("The Mimic"));
		assertTrue(Bosses.isBoss("Pestilent Bloat"));
		assertTrue(Bosses.isBoss("JalTok-Jad"));
		assertFalse(Bosses.isBoss("Kalphite King"));
		assertFalse(Bosses.isBoss("Goblin"));
		assertFalse(Bosses.isBoss(null));
	}
}
